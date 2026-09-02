package com.vpntz.app.tunnel.adapter

/**
 * Pure, JVM-testable translation of an [TunnelAdapterConfig.Naive] into the
 * server/listen arguments the existing `NaiveBridge.start` expects. Android
 * [android.content.Context] is deliberately excluded: it is injected by the
 * lifecycle backend, not derived from the profile.
 */
object NaiveBridgeArgs {

    data class Resolved(
        val serverHost: String,
        val serverPort: Int,
        val username: String,
        val password: String,
        val listenPort: Int,
        val listenHost: String
    )

    fun resolve(config: TunnelAdapterConfig.Naive): Resolved = Resolved(
        serverHost = config.host,
        serverPort = config.port,
        username = config.username,
        password = config.password,
        listenPort = config.listenPort,
        listenHost = config.listenHost
    )
}
