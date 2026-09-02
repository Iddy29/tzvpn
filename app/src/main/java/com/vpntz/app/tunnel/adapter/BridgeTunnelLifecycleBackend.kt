package com.vpntz.app.tunnel.adapter

import android.content.Context
import com.vpntz.app.tunnel.DnsttBridge
import com.vpntz.app.tunnel.NaiveBridge
import com.vpntz.app.tunnel.SlipstreamBridge
import com.vpntz.app.tunnel.VaydnsBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production [TunnelLifecycleBackend] that wires the existing per-protocol
 * bridges. Supports the dns-tunnel family: DNSTT/NoizDNS via `DnsttBridge`,
 * VayDNS via `VaydnsBridge`, Slipstream (tz-kitonga) via `SlipstreamBridge`
 * (Rust/JNI), and Naive via `NaiveBridge` (external `libnaive.so` process).
 * Other config types are rejected until their backends are wired in.
 *
 * Health/stop/cleanup dispatch on the config that was last started, so a single
 * backend instance correctly targets the active protocol. The actual bridge
 * calls are the only Android/native-touching part and are exercised on device;
 * the args translation lives in [DnsttBridgeArgs]/[VaydnsBridgeArgs]/[SlipstreamBridgeArgs]/[NaiveBridgeArgs]
 * and is JVM-tested.
 */
class BridgeTunnelLifecycleBackend(
    private val context: Context? = null
) : TunnelLifecycleBackend {

    @Volatile private var active: TunnelAdapterConfig? = null

    override suspend fun start(config: TunnelAdapterConfig): Result<Unit> {
        active = config
        return when (config) {
            is TunnelAdapterConfig.Dnstt -> startDnstt(config)
            is TunnelAdapterConfig.Vaydns -> startVaydns(config)
            is TunnelAdapterConfig.Slipstream -> startSlipstream(config)
            is TunnelAdapterConfig.Naive -> startNaive(config)
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

    private suspend fun startVaydns(config: TunnelAdapterConfig.Vaydns): Result<Unit> =
        withContext(Dispatchers.IO) {
            val a = VaydnsBridgeArgs.resolve(config)
            VaydnsBridge.startClient(
                dnsServer = a.dnsServer,
                tunnelDomain = a.tunnelDomain,
                publicKey = a.publicKey,
                listenPort = a.listenPort,
                listenHost = a.listenHost,
                dnsttCompat = a.dnsttCompat,
                maxPayload = a.maxPayload,
                recordType = a.recordType,
                maxQnameLen = a.maxQnameLen,
                rps = a.rps,
                idleTimeout = a.idleTimeout,
                keepalive = a.keepalive,
                udpTimeout = a.udpTimeout,
                maxNumLabels = a.maxNumLabels,
                clientIdSize = a.clientIdSize,
                resolverMode = a.resolverMode,
                rrSpreadCount = a.rrSpreadCount
            )
        }

    private suspend fun startSlipstream(config: TunnelAdapterConfig.Slipstream): Result<Unit> =
        withContext(Dispatchers.IO) {
            val a = SlipstreamBridgeArgs.resolve(config)
            SlipstreamBridge.startClient(
                domain = a.domain,
                resolvers = a.resolvers,
                congestionControl = a.congestionControl,
                keepAliveInterval = a.keepAliveInterval,
                tcpListenPort = a.tcpListenPort,
                tcpListenHost = a.tcpListenHost,
                gsoEnabled = a.gsoEnabled,
                debugPoll = a.debugPoll,
                debugStreams = a.debugStreams,
                idlePollIntervalMs = a.idlePollIntervalMs,
                idleTimeoutMs = a.idleTimeoutMs
            )
        }

    private suspend fun startNaive(config: TunnelAdapterConfig.Naive): Result<Unit> =
        withContext(Dispatchers.IO) {
            val ctx = context
            if (ctx == null) {
                Result.failure(IllegalStateException("Naive bridge requires an Android Context"))
            } else {
                val a = NaiveBridgeArgs.resolve(config)
                NaiveBridge.start(
                    context = ctx,
                    listenPort = a.listenPort,
                    listenHost = a.listenHost,
                    serverHost = a.serverHost,
                    serverPort = a.serverPort,
                    username = a.username,
                    password = a.password
                )
            }
        }

    override fun stop() {
        when (active) {
            is TunnelAdapterConfig.Vaydns -> VaydnsBridge.stopClient()
            is TunnelAdapterConfig.Dnstt -> DnsttBridge.stopClient()
            is TunnelAdapterConfig.Slipstream -> SlipstreamBridge.stopClient()
            is TunnelAdapterConfig.Naive -> NaiveBridge.stop()
            else -> Unit
        }
    }

    override fun isRunning(): Boolean = when (active) {
        is TunnelAdapterConfig.Vaydns -> VaydnsBridge.isRunning()
        is TunnelAdapterConfig.Dnstt -> DnsttBridge.isRunning()
        is TunnelAdapterConfig.Slipstream -> SlipstreamBridge.isNativeRunning()
        is TunnelAdapterConfig.Naive -> NaiveBridge.isRunning()
        else -> false
    }

    override fun isHealthy(): Boolean = when (active) {
        is TunnelAdapterConfig.Vaydns -> VaydnsBridge.isClientHealthy()
        is TunnelAdapterConfig.Dnstt -> DnsttBridge.isClientHealthy()
        is TunnelAdapterConfig.Slipstream -> SlipstreamBridge.isClientHealthy()
        is TunnelAdapterConfig.Naive -> NaiveBridge.isClientHealthy()
        else -> false
    }

    override fun cleanup() {
        when (active) {
            is TunnelAdapterConfig.Vaydns -> {
                VaydnsBridge.stopClient()
            }
            is TunnelAdapterConfig.Dnstt -> {
                DnsttBridge.setVpnService(null)
                DnsttBridge.stopClient()
            }
            is TunnelAdapterConfig.Slipstream -> {
                SlipstreamBridge.setVpnService(null)
                SlipstreamBridge.stopClient()
            }
            is TunnelAdapterConfig.Naive -> {
                NaiveBridge.stop()
            }
            else -> Unit
        }
    }
}
