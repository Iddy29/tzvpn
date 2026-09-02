package com.vpntz.app.tunnel.adapter

import com.vpntz.app.tunnel.DnsttBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production [TunnelLifecycleBackend] that wires the existing per-protocol
 * bridges. For now it implements the dns-tunnel family (DNSTT/NoizDNS, both
 * driven by `DnsttBridge`); other config types are rejected with an
 * [UnsupportedOperationException] until their backends are wired in.
 *
 * The actual bridge call is the only Android-touching part and is exercised on
 * device; the args translation lives in [DnsttBridgeArgs] and is JVM-tested.
 */
class BridgeTunnelLifecycleBackend : TunnelLifecycleBackend {

    override suspend fun start(config: TunnelAdapterConfig): Result<Unit> {
        return when (config) {
            is TunnelAdapterConfig.Dnstt -> startDnstt(config)
            else -> Result.failure(UnsupportedOperationException(
                "No bridge backend wired for ${config::class.simpleName}"
            ))
        }
    }

    private suspend fun startDnstt(config: TunnelAdapterConfig.Dnstt): Result<Unit> =
        withContext(Dispatchers.IO) {
            val a = DnsttBridgeArgs.resolve(config)
            DnsttBridge.startClient(
                dnsServer = a.dnsServer,
                tunnelDomain = a.tunnelDomain,
                publicKey = a.publicKey,
                listenPort = a.listenPort,
                listenHost = a.listenHost,
                authoritativeMode = a.authoritativeMode,
                noizMode = a.noizMode,
                stealthMode = a.stealthMode,
                maxPayload = a.maxPayload,
                socksProxyAddr = a.socksProxyAddr,
                socksProxyUser = a.socksProxyUser,
                socksProxyPass = a.socksProxyPass,
                resolverMode = a.resolverMode,
                rrSpreadCount = a.rrSpreadCount
            )
        }

    override fun stop() {
        DnsttBridge.stopClient()
    }

    override fun isRunning(): Boolean = DnsttBridge.isRunning()

    override fun isHealthy(): Boolean = DnsttBridge.isClientHealthy()

    override fun cleanup() {
        DnsttBridge.setVpnService(null)
        DnsttBridge.stopClient()
    }
}
