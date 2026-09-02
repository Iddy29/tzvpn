package com.vpntz.app.tunnel.adapter

/**
 * Pure, JVM-testable translation of an [TunnelAdapterConfig.Vaydns] into the exact
 * argument set the existing `VaydnsBridge.startClient` expects. Keeps the
 * bridge-call translation owned by the adapter layer and verified without the Go
 * mobile bridge.
 */
object VaydnsBridgeArgs {

    data class Resolved(
        val dnsServer: String,
        val tunnelDomain: String,
        val publicKey: String,
        val listenPort: Int,
        val listenHost: String,
        val dnsttCompat: Boolean,
        val maxPayload: Int,
        val recordType: String,
        val maxQnameLen: Int,
        val rps: Double,
        val idleTimeout: Int,
        val keepalive: Int,
        val udpTimeout: Int,
        val maxNumLabels: Int,
        val clientIdSize: Int,
        val resolverMode: String,
        val rrSpreadCount: Int
    )

    fun resolve(config: TunnelAdapterConfig.Vaydns): Resolved = Resolved(
        dnsServer = config.effectiveDnsServer,
        tunnelDomain = config.domain,
        publicKey = config.publicKey,
        listenPort = config.listenPort,
        listenHost = config.listenHost,
        dnsttCompat = config.dnsttCompat,
        maxPayload = config.maxPayload,
        recordType = config.recordType,
        maxQnameLen = config.maxQnameLen,
        rps = config.rps,
        idleTimeout = config.idleTimeoutSec,
        keepalive = config.keepaliveSec,
        udpTimeout = config.udpTimeoutMs,
        maxNumLabels = config.maxNumLabels,
        clientIdSize = config.clientIdSize,
        resolverMode = config.resolverMode,
        rrSpreadCount = config.rrSpreadCount
    )
}
