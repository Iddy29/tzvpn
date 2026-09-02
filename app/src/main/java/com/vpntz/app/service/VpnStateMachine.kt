package com.vpntz.app.service

import com.vpntz.app.domain.model.ConnectionState
import com.vpntz.app.domain.model.ServerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Explicit, thread-safe state machine for the VPN connection lifecycle.
 *
 * This is the single source of truth for the connection state exposed to the
 * UI and drives the decisions that [VpnConnectionManager] makes before it
 * talks to the Android [VpnTzService]. It intentionally does not know about
 * Android, tunnelling, or networking: it only models the lifecycle and its
 * transitions, so it can be unit-tested deterministically and reused by the
 * service boundary.
 *
 * States reuse the domain [ConnectionState] (Disconnected / Connecting /
 * Connected / Disconnecting / Error). Error is treated as a terminal-but-
 * recoverable state: it is reachable from almost anywhere and can be followed
 * by a fresh [beginConnect]. Disconnected is reached via [onDisconnected] /
 * [cleanup], which deliberately preserve a pending [ConnectionState.Error] so
 * a useful failure message is not wiped out by teardown.
 *
 * Every mutating operation runs under a lock so concurrent connect/disconnect
 * callbacks cannot interleave their read-modify-write of the state.
 */
class VpnStateMachine {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    /** Current connection state; callers observe this instead of tracking it. */
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val lock = Any()

    // ── read-only predicates ────────────────────────────────────────────────

    /** True when a fresh connect is legal: not already connecting or connected. */
    fun canConnect(): Boolean = synchronized(lock) {
        _state.value is ConnectionState.Disconnected || _state.value is ConnectionState.Error
    }

    /** True when a disconnect (or cancellation) is meaningful: something is in
     *  flight/running or we are in a recoverable error. False once already
     *  disconnected or already disconnecting (guards against redundant dispatch). */
    fun canDisconnect(): Boolean = synchronized(lock) {
        _state.value is ConnectionState.Connected ||
                _state.value is ConnectionState.Connecting ||
                _state.value is ConnectionState.Error
    }

    fun isConnecting(): Boolean = synchronized(lock) { _state.value is ConnectionState.Connecting }
    fun isConnected(): Boolean = synchronized(lock) { _state.value is ConnectionState.Connected }

    // ── transitions (each returns true when it changed the state) ───────────

    /**
     * Begin a fresh connect. Only allowed from Disconnected or Error; rejected
     * from Connecting/Connected to guard against duplicate connects.
     */
    fun beginConnect(): Boolean = synchronized(lock) {
        val s = _state.value
        if (s is ConnectionState.Disconnected || s is ConnectionState.Error) {
            _state.value = ConnectionState.Connecting
            true
        } else {
            false
        }
    }

    /**
     * Begin a reconnect/switch. More lenient than [beginConnect] so the UI can
     * switch profiles or recover without the service having to fully tear down
     * first (the service handles stopping the previous connection).
     */
    fun beginReconnect(): Boolean = synchronized(lock) {
        _state.value = ConnectionState.Connecting
        true
    }

    /**
     * Begin a disconnect. Allowed from Connected/Connecting/Error; rejected
     * (returns false) when already Disconnected or already Disconnecting so a
     * repeated disconnect request does not re-dispatch.
     */
    fun beginDisconnect(): Boolean = synchronized(lock) {
        val s = _state.value
        when (s) {
            is ConnectionState.Disconnected, is ConnectionState.Disconnecting -> false
            else -> {
                _state.value = ConnectionState.Disconnecting
                true
            }
        }
    }

    /**
     * Connection became established. Honours an in-flight Connecting attempt or
     * refreshes an already-Connected state; ignored if nothing is in progress.
     */
    fun onEstablished(profile: ServerProfile, chainName: String? = null, chainId: Long = -1): ConnectionState =
        synchronized(lock) {
            val s = _state.value
            if (s is ConnectionState.Connecting || s is ConnectionState.Connected) {
                val connected = ConnectionState.Connected(profile, chainName = chainName, chainId = chainId)
                _state.value = connected
                connected
            } else {
                s
            }
        }

    /**
     * Connection fully stopped. Collapses Connecting/Connected/Disconnecting to
     * Disconnected, but preserves an [ConnectionState.Error] that arrived just
     * before teardown so the user still sees the failure reason.
     */
    fun onDisconnected(): ConnectionState = synchronized(lock) {
        val s = _state.value
        when (s) {
            is ConnectionState.Error -> s
            else -> {
                _state.value = ConnectionState.Disconnected
                ConnectionState.Disconnected
            }
        }
    }

    /** Terminal failure; reachable from any state and recoverable via [beginConnect]. */
    fun onError(message: String, cause: Throwable? = null): ConnectionState = synchronized(lock) {
        val e = ConnectionState.Error(message, cause)
        _state.value = e
        e
    }

    /** Tear down after a cancelled/abandoned attempt: drop back to Disconnected. */
    fun cleanup(): ConnectionState = onDisconnected()

    /**
     * Absorb an authoritative external state change (e.g. from the service via
     * the VPN repository). Used by the mirroring collector so the machine and
     * the repository stay consistent without inventing extra transitions.
     */
    fun sync(external: ConnectionState): ConnectionState = synchronized(lock) {
        _state.value = external
        external
    }
}
