package com.vpntz.app.tunnel.adapter

import java.net.InetSocketAddress

/**
 * Pure, JVM-testable translation of an [TunnelAdapterConfig.Snowflake] into the
 * arguments the existing `SnowflakeBridge.startClient` expects (the Snowflake PT
 * + Tor process stack, or a lyrebird/obfs4 PT based on the bridge lines). Android
 * [android.content.Context] is deliberately excluded — it is injected by the
 * lifecycle backend.
 */
object SnowflakeBridgeArgs {

    data class Resolved(
        val snowflakePort: Int,
        val torSocksPort: Int,
        val listenHost: String,
        val bridgeLines: String,
        val upstreamSocksAddr: InetSocketAddress?
    )

    fun resolve(config: TunnelAdapterConfig.Snowflake): Resolved = Resolved(
        snowflakePort = config.snowflakePtPort,
        torSocksPort = config.torSocksPort,
        listenHost = config.listenHost,
        bridgeLines = config.bridges,
        upstreamSocksAddr = config.upstreamSocksAddr
    )
}
