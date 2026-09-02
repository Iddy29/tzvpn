package com.vpntz.app.tunnel.adapter

import java.net.InetSocketAddress

/**
 * Pure, JVM-testable translation of an [TunnelAdapterConfig.Doh] into the
 * arguments the existing `DohBridge.start` accepts. The DoH endpoint URL,
 * listen address, optional local SOCKS auth, and optional upstream SOCKS5 for
 * chaining are all carried here; HTTP/TLS correctness stays in `DohBridge`
 * (OkHttp) and is device/network verified.
 */
object DohBridgeArgs {

    data class Resolved(
        val dohUrl: String,
        val listenPort: Int,
        val listenHost: String,
        val localAuthUsername: String?,
        val localAuthPassword: String?,
        val upstreamSocksAddr: InetSocketAddress?
    )

    fun resolve(config: TunnelAdapterConfig.Doh): Resolved = Resolved(
        dohUrl = config.url,
        listenPort = config.listenPort,
        listenHost = config.listenHost,
        localAuthUsername = config.localAuthUsername,
        localAuthPassword = config.localAuthPassword,
        upstreamSocksAddr = config.upstreamSocksAddr
    )
}
