package com.vpntz.app.tunnel.adapter

import android.content.Context
import com.vpntz.app.tunnel.DnsttBridge
import com.vpntz.app.tunnel.NaiveBridge
import com.vpntz.app.tunnel.SlipstreamBridge
import com.vpntz.app.tunnel.SnowflakeBridge
import com.vpntz.app.tunnel.VaydnsBridge
import com.vpntz.app.tunnel.VlessBridge
import com.vpntz.app.tunnel.VlessRealityBridge
import com.vpntz.app.tunnel.SshTunnelBridge
import com.vpntz.app.tunnel.DohBridge
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
            is TunnelAdapterConfig.Vless -> startVless(config)
            is TunnelAdapterConfig.Snowflake -> startSnowflake(config)
            is TunnelAdapterConfig.Ssh -> startSsh(config)
            is TunnelAdapterConfig.Doh -> startDoh(config)
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

    private suspend fun startVless(config: TunnelAdapterConfig.Vless): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (VlessBridgeArgs.isReality(config)) {
                val a = VlessBridgeArgs.resolveReality(config)
                VlessRealityBridge.start(
                    listenPort = a.listenPort,
                    listenHost = a.listenHost,
                    serverHost = a.serverHost,
                    serverPort = a.serverPort,
                    uuid = a.uuid,
                    sni = a.sni,
                    publicKey = a.publicKey,
                    shortId = a.shortId,
                    fingerprint = a.fingerprint
                )
            } else {
                val a = VlessBridgeArgs.resolveCdn(config)
                VlessBridge.start(
                    listenPort = a.listenPort,
                    listenHost = a.listenHost,
                    cdnIp = a.cdnIp,
                    cdnPort = a.cdnPort,
                    serverDomain = a.serverDomain,
                    vlessUuid = a.vlessUuid,
                    security = a.security,
                    transport = a.transport,
                    wsPath = a.wsPath,
                    fragmentEnabled = a.fragmentEnabled,
                    fragmentStrategy = a.fragmentStrategy,
                    fragmentDelayMs = a.fragmentDelayMs,
                    sniSpoofTtl = a.sniSpoofTtl,
                    fakeDecoyHost = a.fakeDecoyHost,
                    tcpMaxSeg = a.tcpMaxSeg,
                    vlessSni = a.vlessSni,
                    chPaddingEnabled = a.chPaddingEnabled,
                    wsHeaderObfuscation = a.wsHeaderObfuscation,
                    wsPaddingEnabled = a.wsPaddingEnabled
                )
            }
        }

    private fun isReality(config: TunnelAdapterConfig): Boolean =
        (config as? TunnelAdapterConfig.Vless)?.let { VlessBridgeArgs.isReality(it) } == true

    private suspend fun startSnowflake(config: TunnelAdapterConfig.Snowflake): Result<Unit> =
        withContext(Dispatchers.IO) {
            val ctx = context
            if (ctx == null) {
                Result.failure(IllegalStateException("Snowflake/Tor bridge requires an Android Context"))
            } else {
                val a = SnowflakeBridgeArgs.resolve(config)
                SnowflakeBridge.startClient(
                    context = ctx,
                    snowflakePort = a.snowflakePort,
                    torSocksPort = a.torSocksPort,
                    listenHost = a.listenHost,
                    bridgeLines = a.bridgeLines,
                    upstreamSocksAddr = a.upstreamSocksAddr
                )
            }
        }

    private suspend fun startSsh(config: TunnelAdapterConfig.Ssh): Result<Unit> =
        withContext(Dispatchers.IO) {
            val a = SshBridgeArgs.resolve(config)
            when (a.transport) {
                SshTransport.WEB_SOCKET -> SshTunnelBridge.startOverWebSocket(
                    sshHost = a.sshHost, sshPort = a.sshPort,
                    sshUsername = a.sshUsername, sshPassword = a.sshPassword,
                    wsPath = a.wsPath, wsUseTls = a.wsUseTls, wsCustomHost = a.wsCustomHost,
                    wsTlsSni = a.wsTlsSni, listenPort = a.listenPort, listenHost = a.listenHost,
                    blockDirectDns = !a.forwardDnsThroughSsh,
                    sshAuthType = a.sshAuthType, sshPrivateKey = a.sshPrivateKey,
                    sshKeyPassphrase = a.sshKeyPassphrase,
                    remoteDnsHost = a.remoteDnsHost, remoteDnsFallback = a.remoteDnsFallback
                )
                SshTransport.HTTP_PROXY -> SshTunnelBridge.startOverHttpProxy(
                    sshHost = a.sshHost, sshPort = a.sshPort,
                    sshUsername = a.sshUsername, sshPassword = a.sshPassword,
                    proxyHost = a.proxyHost, proxyPort = a.proxyPort,
                    customHostHeader = a.customHostHeader, listenPort = a.listenPort,
                    listenHost = a.listenHost, blockDirectDns = !a.forwardDnsThroughSsh,
                    sshAuthType = a.sshAuthType, sshPrivateKey = a.sshPrivateKey,
                    sshKeyPassphrase = a.sshKeyPassphrase,
                    remoteDnsHost = a.remoteDnsHost, remoteDnsFallback = a.remoteDnsFallback,
                    tlsEnabled = a.tlsEnabled, tlsSni = a.tlsSni
                )
                SshTransport.DIRECT -> SshTunnelBridge.startDirect(
                    tunnelHost = a.sshHost, tunnelPort = a.sshPort,
                    sshUsername = a.sshUsername, sshPassword = a.sshPassword,
                    listenPort = a.listenPort, listenHost = a.listenHost,
                    forwardDnsThroughSsh = a.forwardDnsThroughSsh,
                    sshAuthType = a.sshAuthType, sshPrivateKey = a.sshPrivateKey,
                    sshKeyPassphrase = a.sshKeyPassphrase,
                    remoteDnsHost = a.remoteDnsHost, remoteDnsFallback = a.remoteDnsFallback,
                    tlsEnabled = a.tlsEnabled, tlsSni = a.tlsSni, sshPayload = a.sshPayload
                )
            }
        }

    private suspend fun startDoh(config: TunnelAdapterConfig.Doh): Result<Unit> =
        withContext(Dispatchers.IO) {
            val a = DohBridgeArgs.resolve(config)
            DohBridge.start(
                dohUrl = a.dohUrl,
                listenPort = a.listenPort,
                listenHost = a.listenHost,
                localAuthUsername = a.localAuthUsername,
                localAuthPassword = a.localAuthPassword,
                upstreamSocksAddr = a.upstreamSocksAddr
            )
        }

    override fun stop() {
        when (val c = active) {
            is TunnelAdapterConfig.Vaydns -> VaydnsBridge.stopClient()
            is TunnelAdapterConfig.Dnstt -> DnsttBridge.stopClient()
            is TunnelAdapterConfig.Slipstream -> SlipstreamBridge.stopClient()
            is TunnelAdapterConfig.Naive -> NaiveBridge.stop()
            is TunnelAdapterConfig.Vless -> if (VlessBridgeArgs.isReality(c)) VlessRealityBridge.stop() else VlessBridge.stop()
            is TunnelAdapterConfig.Snowflake -> SnowflakeBridge.stopClient()
            is TunnelAdapterConfig.Ssh -> SshTunnelBridge.stop()
            is TunnelAdapterConfig.Doh -> DohBridge.stop()
            else -> Unit
        }
    }

    override fun isRunning(): Boolean = when (val c = active) {
        is TunnelAdapterConfig.Vaydns -> VaydnsBridge.isRunning()
        is TunnelAdapterConfig.Dnstt -> DnsttBridge.isRunning()
        is TunnelAdapterConfig.Slipstream -> SlipstreamBridge.isNativeRunning()
        is TunnelAdapterConfig.Naive -> NaiveBridge.isRunning()
        is TunnelAdapterConfig.Vless -> if (VlessBridgeArgs.isReality(c)) VlessRealityBridge.isRunning() else VlessBridge.isRunning()
        is TunnelAdapterConfig.Snowflake -> SnowflakeBridge.isRunning()
        is TunnelAdapterConfig.Ssh -> SshTunnelBridge.isRunning()
        is TunnelAdapterConfig.Doh -> DohBridge.isRunning()
        else -> false
    }

    override fun isHealthy(): Boolean = when (val c = active) {
        is TunnelAdapterConfig.Vaydns -> VaydnsBridge.isClientHealthy()
        is TunnelAdapterConfig.Dnstt -> DnsttBridge.isClientHealthy()
        is TunnelAdapterConfig.Slipstream -> SlipstreamBridge.isClientHealthy()
        is TunnelAdapterConfig.Naive -> NaiveBridge.isClientHealthy()
        is TunnelAdapterConfig.Vless -> if (VlessBridgeArgs.isReality(c)) VlessRealityBridge.isClientHealthy() else VlessBridge.isClientHealthy()
        is TunnelAdapterConfig.Snowflake -> SnowflakeBridge.isClientHealthy()
        is TunnelAdapterConfig.Ssh -> SshTunnelBridge.isClientHealthy()
        is TunnelAdapterConfig.Doh -> DohBridge.isClientHealthy()
        else -> false
    }

    override fun cleanup() {
        when (val c = active) {
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
            is TunnelAdapterConfig.Vless -> if (VlessBridgeArgs.isReality(c)) VlessRealityBridge.stop() else VlessBridge.stop()
            is TunnelAdapterConfig.Snowflake -> {
                SnowflakeBridge.stopClient()
            }
            is TunnelAdapterConfig.Ssh -> {
                SshTunnelBridge.stop()
            }
            is TunnelAdapterConfig.Doh -> {
                DohBridge.stop()
            }
            else -> Unit
        }
    }
}
