package com.vpntz.app.tunnel.adapter

/**
 * Pure, JVM-testable translation of an [TunnelAdapterConfig.Vless] into the
 * arguments for the existing VLESS bridges. VLESS has two engines:
 *
 *  - `VlessBridge` (CDN/WebSocket/TLS, pure-Kotlin, SNI fragmentation) — see [resolveCdn]
 *  - `VlessRealityBridge` (xtls REALITY over TCP, gomobile) — see [resolveReality]
 *
 * The correct target is chosen by [TunnelAdapterConfig.Vless.security] == "reality".
 */
object VlessBridgeArgs {

    data class CdnResolved(
        val listenPort: Int,
        val listenHost: String,
        val cdnIp: String,
        val cdnPort: Int,
        val serverDomain: String,
        val vlessUuid: String,
        val security: String,
        val transport: String,
        val wsPath: String,
        val fragmentEnabled: Boolean,
        val fragmentStrategy: String,
        val fragmentDelayMs: Int,
        val sniSpoofTtl: Int,
        val fakeDecoyHost: String,
        val tcpMaxSeg: Int,
        val vlessSni: String,
        val chPaddingEnabled: Boolean,
        val wsHeaderObfuscation: Boolean,
        val wsPaddingEnabled: Boolean
    )

    data class RealityResolved(
        val listenPort: Int,
        val listenHost: String,
        val serverHost: String,
        val serverPort: Int,
        val uuid: String,
        val sni: String,
        val publicKey: String,
        val shortId: String,
        val fingerprint: String
    )

    fun isReality(config: TunnelAdapterConfig.Vless): Boolean = config.security == "reality"

    fun resolveCdn(config: TunnelAdapterConfig.Vless): CdnResolved = CdnResolved(
        listenPort = config.listenPort,
        listenHost = config.listenHost,
        cdnIp = config.cdnIp,
        cdnPort = config.cdnPort,
        serverDomain = config.host,
        vlessUuid = config.uuid,
        security = config.security,
        transport = config.transport,
        wsPath = config.wsPath,
        fragmentEnabled = config.sniFragmentEnabled,
        fragmentStrategy = config.sniFragmentStrategy,
        fragmentDelayMs = config.sniFragmentDelayMs,
        sniSpoofTtl = config.sniSpoofTtl,
        fakeDecoyHost = config.fakeDecoyHost,
        tcpMaxSeg = config.tcpMaxSeg,
        vlessSni = config.sni,
        chPaddingEnabled = config.chPaddingEnabled,
        wsHeaderObfuscation = config.wsHeaderObfuscation,
        wsPaddingEnabled = config.wsPaddingEnabled
    )

    fun resolveReality(config: TunnelAdapterConfig.Vless): RealityResolved = RealityResolved(
        listenPort = config.listenPort,
        listenHost = config.listenHost,
        serverHost = config.cdnIp.ifBlank { config.host },
        serverPort = config.cdnPort,
        uuid = config.uuid,
        sni = config.sni.ifBlank { config.host },
        publicKey = config.realityPubKey,
        shortId = config.realityShortId,
        fingerprint = config.realityFp
    )
}
