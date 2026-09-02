package com.vpntz.app.tunnel.adapter

/**
 * Pure, JVM-testable translation of an [TunnelAdapterConfig.Hysteria2] into the
 * arguments the existing `Hysteria2Bridge.start` expects (gomobile `hysteria2.Client`,
 * QUIC + optional Salamander obfuscation). `sni` falls back to the server host when
 * blank, matching `VpnTzService.connectHysteria2`.
 */
object Hysteria2BridgeArgs {

    data class Resolved(
        val listenHost: String,
        val listenPort: Int,
        val serverHost: String,
        val serverPort: Int,
        val password: String,
        val sni: String,
        val insecure: Boolean,
        val obfs: String,
        val obfsPassword: String
    )

    fun resolve(config: TunnelAdapterConfig.Hysteria2): Resolved = Resolved(
        listenHost = config.listenHost,
        listenPort = config.listenPort,
        serverHost = config.host,
        serverPort = config.port,
        password = config.password,
        sni = config.sni.ifBlank { config.host },
        insecure = config.insecure,
        obfs = config.obfs,
        obfsPassword = config.obfsPassword
    )
}
