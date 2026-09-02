package com.vpntz.app.tunnel.adapter

import com.vpntz.app.domain.model.TunnelType

/**
 * Uniform lifecycle contract for a protocol tunnel.
 *
 * This is the boundary the upper layers (`VpnConnectionManager` / `VpnTzService`)
 * talk to. It deliberately exposes only lifecycle and health, not protocol-specific
 * details — a protocol's quirks stay in its [TunnelLifecycleBackend] and its
 * [TunnelAdapterConfig].
 *
 * It is independent of UI and of Android: [start] takes the already-translated
 * [TunnelAdapterConfig] and reports a plain [Result]; [stop]/[cleanup] are
 * idempotent; [isRunning]/[isHealthy] are cheap reads.
 */
interface TunnelAdapter {

    /** The protocol this adapter drives. */
    val protocol: TunnelType

    /** Configure + start the tunnel; returns a typed failure on error. */
    suspend fun start(config: TunnelAdapterConfig): Result<Unit>

    /** Stop a running tunnel. Safe to call repeatedly and when not running. */
    fun stop()

    /** True when the engine is running. */
    fun isRunning(): Boolean

    /** True when the engine is running and considered healthy. */
    fun isHealthy(): Boolean

    /** Tear down all resources after stop/failure. Never throws. */
    fun cleanup()
}
