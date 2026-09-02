package com.vpntz.app.service

import android.content.Context
import android.content.Intent
import com.vpntz.app.data.local.datastore.PreferencesDataStore
import com.vpntz.app.data.repository.VpnRepositoryImpl
import com.vpntz.app.domain.model.ConnectionState
import com.vpntz.app.domain.model.ProfileChain
import com.vpntz.app.domain.model.ServerProfile
import com.vpntz.app.domain.model.TrafficStats
import com.vpntz.app.domain.repository.ChainRepository
import com.vpntz.app.domain.repository.ProfileRepository
import com.vpntz.app.widget.VpnWidgetCompactProvider
import com.vpntz.app.widget.VpnWidgetProvider
import com.vpntz.app.util.DeviceIdUtil
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vpnRepository: VpnRepositoryImpl,
    private val profileRepository: ProfileRepository,
    private val chainRepository: ChainRepository,
    private val preferencesDataStore: PreferencesDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Canonical lifecycle state machine; the single source of truth for [connectionState]. */
    private val stateMachine = VpnStateMachine()

    val connectionState: StateFlow<ConnectionState> = stateMachine.state

    private val _dnsWarning = MutableStateFlow<String?>(null)
    val dnsWarning: StateFlow<String?> = _dnsWarning.asStateFlow()

    val trafficStats: StateFlow<TrafficStats> = vpnRepository.trafficStats

    /** Distilled DNS-pool scan progress (per-profile pool feature). */
    val dnsPoolScanState: StateFlow<com.vpntz.app.tunnel.DnsPoolScanState> = vpnRepository.dnsPoolScanState

    private var pendingProfile: ServerProfile? = null
    private var pendingChain: ProfileChain? = null

    init {
        // Observe VPN repository state
        scope.launch {
            vpnRepository.connectionState.collect { state ->
                stateMachine.sync(state)
            }
        }

        // Push state changes to home screen widget
        scope.launch {
            connectionState.collect { state ->
                VpnWidgetProvider.notifyStateChanged(context, state)
                VpnWidgetCompactProvider.notifyStateChanged(context, state)
            }
        }
    }

    fun getDeviceId(): String {
        return DeviceIdUtil.getScrambledDeviceId(context)
    }

    fun connect(profile: ServerProfile) {
        if (!stateMachine.canConnect()) {
            return
        }

        if (profile.isExpired) {
            stateMachine.onError("This profile has expired")
            return
        }
        if (profile.boundDeviceId.isNotEmpty() && profile.boundDeviceId != getDeviceId()) {
            stateMachine.onError("This profile is bound to a different device")
            return
        }

        pendingProfile = profile
        stateMachine.beginConnect()
        _dnsWarning.value = null

        // Set active profile immediately so it shows on the main screen
        scope.launch {
            profileRepository.setActiveProfile(profile.id)
        }

        // Start VPN service
        val intent = Intent(context, VpnTzService::class.java).apply {
            action = VpnTzService.ACTION_CONNECT
            putExtra(VpnTzService.EXTRA_PROFILE_ID, profile.id)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun reconnect(profile: ServerProfile) {
        if (profile.isExpired) {
            stateMachine.onError("This profile has expired")
            return
        }
        if (profile.boundDeviceId.isNotEmpty() && profile.boundDeviceId != getDeviceId()) {
            stateMachine.onError("This profile is bound to a different device")
            return
        }

        pendingProfile = profile
        stateMachine.beginReconnect()
        _dnsWarning.value = null

        scope.launch {
            profileRepository.setActiveProfile(profile.id)
        }

        // Send CONNECT directly — the service handles stopping the old connection
        // (disconnectJob?.join()) before starting the new one.
        val intent = Intent(context, VpnTzService::class.java).apply {
            action = VpnTzService.ACTION_CONNECT
            putExtra(VpnTzService.EXTRA_PROFILE_ID, profile.id)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun connectChain(chain: ProfileChain, firstProfile: ServerProfile) {
        if (!stateMachine.canConnect()) {
            return
        }

        pendingProfile = firstProfile
        pendingChain = chain
        stateMachine.beginConnect()
        _dnsWarning.value = null

        val intent = Intent(context, VpnTzService::class.java).apply {
            action = VpnTzService.ACTION_CONNECT
            putExtra(VpnTzService.EXTRA_CHAIN_ID, chain.id)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun disconnect() {
        if (!stateMachine.canDisconnect()) {
            return
        }

        stateMachine.beginDisconnect()

        val intent = Intent(context, VpnTzService::class.java).apply {
            action = VpnTzService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    fun onVpnEstablished() {
        val profile = pendingProfile ?: return

        // Tunnels are already started by VpnTzService before calling this method.
        // Just do bookkeeping here - save the profile as last connected.
        scope.launch {
            preferencesDataStore.setLastConnectedProfileId(profile.id)
            profileRepository.updateLastConnectedAt(profile.id)
        }
    }

    fun onVpnDisconnected() {
        // Reset repository state without going through the full disconnect flow
        // (which would redundantly stop tunnels and emit Disconnecting state that
        // can race with a new Connecting state if the user reconnects quickly).
        //
        // Preserve an Error state from onVpnError (or a direct push like the
        // DNS pool exhaustion path) — the service teardown immediately follows
        // any connect-time failure, and blanket-resetting here would replace a
        // useful error message with a blank "Not Connected" before the user
        // gets to read it.
        val current = stateMachine.state.value
        if (current !is ConnectionState.Error) {
            stateMachine.onDisconnected()
            vpnRepository.updateConnectionState(ConnectionState.Disconnected)
        }
        _dnsWarning.value = null
        pendingProfile = null
    }

    fun onVpnError(error: String) {
        scope.launch {
            stateMachine.onError(friendlyError(error))
        }
    }

    private fun friendlyError(raw: String): String {
        // Map raw Java/Go exception messages to user-friendly text
        val lower = raw.lowercase()
        return when {
            // Timeouts (Java + Go)
            lower.contains("i/o timeout") || lower.contains("dial tcp") && lower.contains("timeout") ->
                "DNS tunnel timed out — server may be unreachable or blocked"
            lower.contains("context deadline exceeded") ->
                "Connection timed out — server took too long to respond"
            lower.contains("sockettimeoutexception") || lower.contains("read timed out") || lower.contains("connect timed out") ->
                "Connection timed out — server may be unreachable or blocked"
            // Connection errors
            lower.contains("connectionexception") || lower.contains("connection refused") ->
                "Connection refused — server may be down"
            lower.contains("unknownhostexception") || lower.contains("unable to resolve host") || lower.contains("no such host") ->
                "DNS lookup failed — check your internet connection"
            lower.contains("network is unreachable") || lower.contains("networkunreachable") || lower.contains("no route to host") ->
                "Network unreachable — check your internet connection"
            // TLS/SSL
            lower.contains("sslexception") || lower.contains("ssl handshake") || lower.contains("tls handshake") ->
                "Secure connection failed — TLS handshake error"
            // Resets & broken pipes
            lower.contains("econnreset") || lower.contains("connection reset") ->
                "Connection was reset — the server closed the connection"
            lower.contains("broken pipe") || lower.contains("eof") && lower.contains("unexpected") ->
                "Connection lost — the tunnel was interrupted"
            // Permission
            lower.contains("permission denied") ->
                "Permission denied — check VPN permissions"
            else -> raw
        }
    }

    fun setDnsWarning(message: String?) {
        _dnsWarning.value = message
    }

    fun refreshTrafficStats() {
        vpnRepository.refreshTrafficStats()
    }

    suspend fun getProfileById(id: Long): ServerProfile? {
        return profileRepository.getProfileById(id)
    }

    suspend fun getActiveProfile(): ServerProfile? {
        return profileRepository.getActiveProfile().first()
    }

    suspend fun shouldAutoConnect(): Boolean {
        return preferencesDataStore.autoConnectOnBoot.first()
    }

    suspend fun getLastConnectedProfile(): ServerProfile? {
        val lastProfileId = preferencesDataStore.lastConnectedProfileId.first() ?: return null
        return profileRepository.getProfileById(lastProfileId)
    }
}
