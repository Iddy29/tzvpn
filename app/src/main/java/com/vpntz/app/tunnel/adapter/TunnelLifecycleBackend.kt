package com.vpntz.app.tunnel.adapter

/**
 * Seam between a protocol [TunnelAdapter] and the existing bridge/native
 * implementation.
 *
 * The real implementation (in the app's glue layer) delegates to the per-protocol
 * Kotlin bridges (`DnsttBridge`, `SlipstreamBridge`, …) and/or `VpnRepositoryImpl`
 * methods that start the proxy, SOCKS bridge and tun2socks. It is intentionally an
 * interface so a [TunnelAdapter] can be unit-tested against a recording fake without
 * touching native code.
 *
 * Nothing here is Android-specific; lifecycle ownership is explicit and all
 * resources are released via [cleanup]/[stop].
 */
interface TunnelLifecycleBackend {

    /** Long-running start. Mirrors the existing bridges' `startClient`/`start`. */
    suspend fun start(config: TunnelAdapterConfig): Result<Unit>

    /** Best-effort stop, safe to call from any state (idempotent). */
    fun stop()

    /** True when the underlying engine reports it is running. */
    fun isRunning(): Boolean

    /** True when the running engine also reports healthy. */
    fun isHealthy(): Boolean

    /** Release every resource held by the engine; must never throw. */
    fun cleanup()
}
