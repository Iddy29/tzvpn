package com.vpntz.app.tunnel.adapter

/**
 * Pure, JVM-testable translation of an [TunnelAdapterConfig.Dnstt] into the exact
 * argument set the existing `DnsttBridge.startClient` expects. Keeping this here
 * means the bridge-call translation is owned by the adapter layer and verified
 * without touching the Go mobile bridge.
 */
object DnsttBridgeArgs {

    data class Resolved(
        val dnsServer: String,
        val tunnelDomain: String,
        val publicKey: String,
        val listenPort: Int,
        val listenHost: String,
        val authoritativeMode: Boolean,
        val noizMode: Boolean,
        val stealthMode: Boolean,
        val maxPayload: Int,
        val socksProxyAddr: String?,
        val socksProxyUser: String?,
        val socksProxyPass: String?,
        val resolverMode: String,
        val rrSpreadCount: Int
    )

    fun resolve(config: TunnelAdapterConfig.Dnstt): Resolved = Resolved(
        dnsServer = config.effectiveDnsServer,
        tunnelDomain = config.domain,
        publicKey = config.publicKey,
        listenPort = config.listenPort,
        listenHost = config.listenHost,
        authoritativeMode = config.authoritative,
        noizMode = config.noizdns,
        stealthMode = config.noizStealth,
        maxPayload = config.maxPayload,
        socksProxyAddr = config.socksProxyAddr,
        socksProxyUser = config.socksProxyUser,
        socksProxyPass = config.socksProxyPass,
        resolverMode = config.resolverMode,
        rrSpreadCount = config.rrSpreadCount
    )
}
