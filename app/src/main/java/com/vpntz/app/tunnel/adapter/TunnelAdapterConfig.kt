package com.vpntz.app.tunnel.adapter

import com.vpntz.app.domain.model.DnsResolver
import com.vpntz.app.domain.model.ServerProfile
import com.vpntz.app.domain.model.TunnelType

/** Slipstream client idle-poll / idle-timeout defaults (ms), matching `VpnRepositoryImpl`. */
private const val DEFAULT_SLIPSTREAM_IDLE_POLL_MS = 10_000
private const val DEFAULT_SLIPSTREAM_IDLE_TIMEOUT_MS = 120_000

/**
 * Typed, protocol-specific configuration handed to a [TunnelAdapter].
 *
 * These are the *leaf* fields a bridge needs to start a tunnel. They are
 * produced from a validated [ServerProfile] by [TunnelConfigMapper], so the
 * upper layers never see native bridge-specific keys. Any remaining deep
 * translation (DNS-address formatting by transport, DNS-pool scanning, the
 * connect-time DNS prober/auto-tune) stays in the app's
 * `VpnRepositoryImpl`/bridge glue and is exercised on-device.
 */
sealed class TunnelAdapterConfig {

    data class Dnstt(
        val domain: String,
        val publicKey: String,
        val authoritative: Boolean,
        val resolvers: List<DnsResolver>,
        val effectiveDnsServer: String,
        val listenPort: Int,
        val listenHost: String,
        val maxPayload: Int,
        val resolverMode: String,
        val rrSpreadCount: Int,
        val noizdns: Boolean,
        val noizStealth: Boolean,
        val socksProxyAddr: String?,
        val socksProxyUser: String?,
        val socksProxyPass: String?
    ) : TunnelAdapterConfig()

    data class Vaydns(
        val domain: String,
        val publicKey: String,
        val resolvers: List<DnsResolver>,
        val effectiveDnsServer: String,
        val listenPort: Int,
        val listenHost: String,
        val maxPayload: Int,
        val resolverMode: String,
        val rrSpreadCount: Int,
        val dnsttCompat: Boolean,
        val recordType: String,
        val maxQnameLen: Int,
        val rps: Double,
        val idleTimeoutSec: Int,
        val keepaliveSec: Int,
        val udpTimeoutMs: Int,
        val maxNumLabels: Int,
        val clientIdSize: Int
    ) : TunnelAdapterConfig()

    data class Slipstream(
        val domain: String,
        val resolvers: List<DnsResolver>,
        val listenPort: Int,
        val listenHost: String,
        val congestionControl: String,
        val keepAliveInterval: Int,
        val gsoEnabled: Boolean,
        val debugLogging: Boolean,
        val idlePollIntervalMs: Int,
        val idleTimeoutMs: Int
    ) : TunnelAdapterConfig()

    data class Naive(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val listenPort: Int,
        val listenHost: String
    ) : TunnelAdapterConfig()

    data class Vless(
        val host: String,
        val port: Int,
        val uuid: String,
        val security: String,
        val transport: String,
        val wsPath: String,
        val sni: String,
        val cdnIp: String,
        val cdnPort: Int,
        val sniFragmentEnabled: Boolean,
        val sniFragmentStrategy: String,
        val sniFragmentDelayMs: Int,
        val sniSpoofTtl: Int,
        val fakeDecoyHost: String,
        val tcpMaxSeg: Int,
        val chPaddingEnabled: Boolean,
        val wsHeaderObfuscation: Boolean,
        val wsPaddingEnabled: Boolean,
        val realityPubKey: String,
        val realityShortId: String,
        val realityFp: String,
        val listenPort: Int,
        val listenHost: String
    ) : TunnelAdapterConfig()

    data class Ssh(
        val host: String,
        val port: Int,
        val username: String,
        val authType: String,
        val password: String,
        val privateKey: String,
        val keyPassphrase: String,
        val listenPort: Int,
        val listenHost: String,
        val forwardDnsThroughSsh: Boolean,
        val remoteDnsHost: String,
        val remoteDnsFallback: String,
        val payload: String,
        val tlsEnabled: Boolean,
        val tlsSni: String,
        val httpProxyHost: String,
        val httpProxyPort: Int,
        val wsEnabled: Boolean,
        val wsPath: String,
        val wsUseTls: Boolean,
        val wsCustomHost: String,
        val wsTlsSni: String
    ) : TunnelAdapterConfig()

    data class Doh(
        val url: String,
        val listenPort: Int,
        val listenHost: String
    ) : TunnelAdapterConfig()

    data class Hysteria2(
        val host: String,
        val port: Int,
        val password: String,
        val sni: String,
        val insecure: Boolean,
        val obfs: String,
        val obfsPassword: String,
        val listenPort: Int,
        val listenHost: String
    ) : TunnelAdapterConfig()

    data class Snowflake(
        val bridges: String,
        val listenPort: Int,
        val listenHost: String,
        // Runtime values supplied by the caller (Snowflake PT + Tor SOCKS ports,
        // and an optional upstream SOCKS5 for chaining).
        val snowflakePtPort: Int = 0,
        val torSocksPort: Int = 0,
        val upstreamSocksAddr: java.net.InetSocketAddress? = null
    ) : TunnelAdapterConfig()
}

/** Listen address + programmatic flags the service injects (not from the profile). */
data class TunnelRuntimeDefaults(
    val listenPort: Int,
    val listenHost: String,
    val resolvers: List<DnsResolver> = emptyList(),
    val debugLogging: Boolean = false
)

/**
 * Pure translation of a validated [ServerProfile] + [TunnelRuntimeDefaults]
 * into a protocol-specific [TunnelAdapterConfig].
 *
 * Throws [IllegalArgumentException] when a required field is missing so bad
 * configs fail fast; this is unit-tested per protocol without any native code.
 */
class TunnelConfigMapper {

    fun map(type: TunnelType, profile: ServerProfile, runtime: TunnelRuntimeDefaults): TunnelAdapterConfig =
        when (type) {
            TunnelType.DNSTT, TunnelType.DNSTT_SSH -> dnstt(profile, runtime, noizdns = false)
            TunnelType.NOIZDNS, TunnelType.NOIZDNS_SSH -> dnstt(profile, runtime, noizdns = true)
            TunnelType.VAYDNS, TunnelType.VAYDNS_SSH -> vaydns(profile, runtime)
            TunnelType.SLIPSTREAM, TunnelType.SLIPSTREAM_SSH -> slipstream(profile, runtime)
            TunnelType.NAIVE, TunnelType.NAIVE_SSH -> naive(profile, runtime)
            TunnelType.VLESS -> vless(profile, runtime)
            TunnelType.SNOWFLAKE -> snowflake(profile, runtime)
            TunnelType.SOCKS5 -> throw IllegalArgumentException("SOCKS5 is a standalone proxy, not a tunnel adapter")
            TunnelType.HYSTERIA2 -> hysteria2(profile, runtime)
            TunnelType.SSH -> ssh(profile, runtime)
            TunnelType.DOH -> doh(profile, runtime, needValidUrl = true)
        }

    private fun dnstt(profile: ServerProfile, runtime: TunnelRuntimeDefaults, noizdns: Boolean): TunnelAdapterConfig.Dnstt {
        require(profile.domain.isNotBlank()) { "DNSTT tunnel domain is required" }
        require(profile.dnsttPublicKey.isNotBlank()) { "DNSTT public key is required" }
        val resolvers = runtime.resolvers.ifEmpty { profile.resolvers }
        return TunnelAdapterConfig.Dnstt(
            domain = profile.domain,
            publicKey = profile.dnsttPublicKey,
            authoritative = profile.dnsttAuthoritative,
            resolvers = resolvers,
            // Leaf default (UDP host:port). The runtime layer replaces this with
            // the transport-aware formatted address (DoH/DoT/TCP + preflight).
            effectiveDnsServer = resolvers.joinToString(",") { "${it.host}:${it.port}" }.ifBlank { "8.8.8.8:53" },
            listenPort = runtime.listenPort,
            listenHost = runtime.listenHost,
            maxPayload = profile.dnsPayloadSize,
            resolverMode = profile.resolverMode.value,
            rrSpreadCount = profile.rrSpreadCount,
            noizdns = noizdns,
            noizStealth = profile.noizdnsStealth,
            socksProxyAddr = null,
            socksProxyUser = null,
            socksProxyPass = null
        )
    }

    private fun vaydns(profile: ServerProfile, runtime: TunnelRuntimeDefaults): TunnelAdapterConfig.Vaydns {
        require(profile.domain.isNotBlank()) { "VayDNS tunnel domain is required" }
        require(profile.dnsttPublicKey.isNotBlank()) { "VayDNS public key is required" }
        val resolvers = runtime.resolvers.ifEmpty { profile.resolvers }
        return TunnelAdapterConfig.Vaydns(
            domain = profile.domain,
            publicKey = profile.dnsttPublicKey,
            resolvers = resolvers,
            // Leaf default (UDP host:port). The runtime layer replaces this with
            // the transport-aware formatted address (DoH/DoT/TCP + preflight).
            effectiveDnsServer = resolvers.joinToString(",") { "${it.host}:${it.port}" }.ifBlank { "8.8.8.8:53" },
            listenPort = runtime.listenPort,
            listenHost = runtime.listenHost,
            maxPayload = profile.dnsPayloadSize,
            resolverMode = profile.resolverMode.value,
            rrSpreadCount = profile.rrSpreadCount,
            dnsttCompat = profile.vaydnsDnsttCompat,
            recordType = profile.vaydnsRecordType,
            maxQnameLen = profile.vaydnsMaxQnameLen,
            rps = profile.vaydnsRps,
            idleTimeoutSec = profile.vaydnsIdleTimeout,
            keepaliveSec = profile.vaydnsKeepalive,
            udpTimeoutMs = profile.vaydnsUdpTimeout,
            maxNumLabels = profile.vaydnsMaxNumLabels,
            clientIdSize = profile.vaydnsClientIdSize
        )
    }

    private fun slipstream(profile: ServerProfile, runtime: TunnelRuntimeDefaults): TunnelAdapterConfig.Slipstream {
        require(profile.domain.isNotBlank()) { "tz-kitonga (slipstream) domain is required" }
        return TunnelAdapterConfig.Slipstream(
            domain = profile.domain,
            resolvers = runtime.resolvers.ifEmpty { profile.resolvers },
            listenPort = runtime.listenPort,
            listenHost = runtime.listenHost,
            congestionControl = profile.congestionControl.value,
            keepAliveInterval = profile.keepAliveInterval,
            gsoEnabled = profile.gsoEnabled,
            debugLogging = runtime.debugLogging,
            idlePollIntervalMs = DEFAULT_SLIPSTREAM_IDLE_POLL_MS,
            idleTimeoutMs = DEFAULT_SLIPSTREAM_IDLE_TIMEOUT_MS
        )
    }

    private fun naive(profile: ServerProfile, runtime: TunnelRuntimeDefaults): TunnelAdapterConfig.Naive {
        require(profile.domain.isNotBlank()) { "NaiveProxy host is required" }
        return TunnelAdapterConfig.Naive(
            host = profile.domain,
            port = profile.naivePort,
            username = profile.naiveUsername,
            password = profile.naivePassword,
            listenPort = runtime.listenPort,
            listenHost = runtime.listenHost
        )
    }

    private fun vless(profile: ServerProfile, runtime: TunnelRuntimeDefaults): TunnelAdapterConfig.Vless {
        require(profile.domain.isNotBlank()) { "VLESS host is required" }
        require(profile.vlessUuid.isNotBlank()) { "VLESS uuid is required" }
        return TunnelAdapterConfig.Vless(
            host = profile.domain,
            port = profile.cdnPort,
            uuid = profile.vlessUuid,
            security = profile.vlessSecurity,
            transport = profile.vlessTransport,
            wsPath = profile.vlessWsPath,
            sni = profile.vlessSni,
            cdnIp = profile.cdnIp,
            cdnPort = profile.cdnPort,
            sniFragmentEnabled = profile.sniFragmentEnabled,
            sniFragmentStrategy = profile.sniFragmentStrategy,
            sniFragmentDelayMs = profile.sniFragmentDelayMs,
            sniSpoofTtl = profile.sniSpoofTtl,
            fakeDecoyHost = profile.fakeDecoyHost,
            tcpMaxSeg = profile.tcpMaxSeg,
            chPaddingEnabled = profile.chPaddingEnabled,
            wsHeaderObfuscation = profile.wsHeaderObfuscation,
            wsPaddingEnabled = profile.wsPaddingEnabled,
            realityPubKey = profile.vlessRealityPubKey,
            realityShortId = profile.vlessRealityShortId,
            realityFp = profile.vlessRealityFp,
            listenPort = runtime.listenPort,
            listenHost = runtime.listenHost
        )
    }

    private fun snowflake(profile: ServerProfile, runtime: TunnelRuntimeDefaults): TunnelAdapterConfig.Snowflake =
        TunnelAdapterConfig.Snowflake(
            bridges = profile.torBridgeLines,
            listenPort = runtime.listenPort,
            listenHost = runtime.listenHost
        )

    private fun ssh(profile: ServerProfile, runtime: TunnelRuntimeDefaults): TunnelAdapterConfig.Ssh {
        require(profile.domain.isNotBlank()) { "SSH host is required" }
        return TunnelAdapterConfig.Ssh(
            host = profile.domain,
            port = profile.sshPort,
            username = profile.sshUsername,
            authType = profile.sshAuthType.name,
            password = profile.sshPassword,
            privateKey = profile.sshPrivateKey,
            keyPassphrase = profile.sshKeyPassphrase,
            listenPort = runtime.listenPort,
            listenHost = runtime.listenHost,
            forwardDnsThroughSsh = false,
            remoteDnsHost = "8.8.8.8",
            remoteDnsFallback = "1.1.1.1",
            payload = profile.sshPayload,
            tlsEnabled = profile.sshTlsEnabled,
            tlsSni = profile.sshTlsSni,
            httpProxyHost = profile.sshHttpProxyHost,
            httpProxyPort = profile.sshHttpProxyPort,
            wsEnabled = profile.sshWsEnabled,
            wsPath = profile.sshWsPath,
            wsUseTls = profile.sshWsUseTls,
            wsCustomHost = profile.sshWsCustomHost,
            wsTlsSni = ""
        )
    }

    private fun doh(profile: ServerProfile, runtime: TunnelRuntimeDefaults, needValidUrl: Boolean): TunnelAdapterConfig.Doh {
        if (needValidUrl) require(profile.domain.isNotBlank()) { "DoH server host is required" }
        require(profile.tunnelType == TunnelType.DOH) { "DoH config requested for ${profile.tunnelType}" }
        return TunnelAdapterConfig.Doh(
            url = profile.domain,
            listenPort = runtime.listenPort,
            listenHost = runtime.listenHost
        )
    }

    private fun hysteria2(profile: ServerProfile, runtime: TunnelRuntimeDefaults): TunnelAdapterConfig.Hysteria2 {
        require(profile.domain.isNotBlank()) { "Hysteria2 server host is required" }
        return TunnelAdapterConfig.Hysteria2(
            host = profile.domain,
            port = profile.cdnPort,
            password = profile.hy2Password,
            sni = profile.hy2Sni,
            insecure = profile.hy2Insecure,
            obfs = profile.hy2Obfs,
            obfsPassword = profile.hy2ObfsPassword,
            listenPort = runtime.listenPort,
            listenHost = runtime.listenHost
        )
    }
}
