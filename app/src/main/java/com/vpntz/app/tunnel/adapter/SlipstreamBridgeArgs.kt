package com.vpntz.app.tunnel.adapter

import com.vpntz.app.tunnel.ResolverConfig

/**
 * Pure, JVM-testable translation of an [TunnelAdapterConfig.Slipstream] into the
 * exact argument set the existing `SlipstreamBridge.startClient` expects (the
 * Rust/JNI DNS-tunnel client). Keeps the bridge-call translation owned by the
 * adapter layer and verified without the native `libslipstream.so`.
 */
object SlipstreamBridgeArgs {

    data class Resolved(
        val domain: String,
        val resolvers: List<ResolverConfig>,
        val congestionControl: String,
        val keepAliveInterval: Int,
        val tcpListenPort: Int,
        val tcpListenHost: String,
        val gsoEnabled: Boolean,
        val debugPoll: Boolean,
        val debugStreams: Boolean,
        val idlePollIntervalMs: Int,
        val idleTimeoutMs: Int
    )

    fun resolve(config: TunnelAdapterConfig.Slipstream): Resolved = Resolved(
        domain = config.domain,
        resolvers = config.resolvers.distinctBy { "${it.host}:${it.port}" }.map {
            ResolverConfig(host = it.host, port = it.port, authoritative = it.authoritative)
        },
        congestionControl = config.congestionControl,
        keepAliveInterval = config.keepAliveInterval,
        tcpListenPort = config.listenPort,
        tcpListenHost = config.listenHost,
        gsoEnabled = config.gsoEnabled,
        debugPoll = config.debugLogging,
        debugStreams = config.debugLogging,
        idlePollIntervalMs = config.idlePollIntervalMs,
        idleTimeoutMs = config.idleTimeoutMs
    )
}
