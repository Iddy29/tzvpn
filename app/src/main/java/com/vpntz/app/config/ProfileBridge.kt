package com.vpntz.app.config

import com.vpntz.app.domain.model.CongestionControl
import com.vpntz.app.domain.model.DnsTransport
import com.vpntz.app.domain.model.ResolverMode
import com.vpntz.app.domain.model.ServerProfile
import com.vpntz.app.domain.model.SshAuthType
import com.vpntz.app.domain.model.TunnelType
import com.vpntz.app.config.crypto.VaultCrypto

/**
 * Translation between the independent [ProfileDocument] wire model and the
 * application-domain [ServerProfile] entity.
 *
 * PROVENANCE BRIDGE (Phase 1): this file exists solely so the configuration
 * layer can be adopted behind the existing repository surface without touching
 * other layers. It will be retired when the domain model itself is replaced
 * (planned Phase 2) — it must never gain business logic of its own.
 */
object ProfileBridge {

    // ---- Document -> Domain ---------------------------------------------------

    fun toDomain(doc: ProfileDocument): ServerProfile {
        val resolvedToken = doc.tunnelToken.removeSuffix("_ssh")
        val chained = doc.sshChainEnabled || doc.tunnelToken.endsWith("_ssh") || doc.tunnelToken == "ssh"
        val tunnelType = enumFromCanonical(resolvedToken, chained)
        val hiddenFlag = doc.resolversHidden || doc.hiddenResolvers.isNotEmpty()
        return ServerProfile(
            name = doc.name,
            domain = doc.domain,
            resolvers = doc.resolvers.map(::toDomainResolver),
            authoritativeMode = doc.authoritativeMode,
            keepAliveInterval = doc.keepAliveInterval,
            congestionControl = CongestionControl.fromValue(doc.congestionControl),
            gsoEnabled = doc.gsoEnabled,
            tcpListenPort = doc.tcpListenPort,
            tcpListenHost = doc.tcpListenHost,
            socksUsername = doc.socksUsername,
            socksPassword = doc.socksPassword,
            tunnelType = tunnelType,
            dnsttPublicKey = doc.dnsttPublicKey,
            sshUsername = doc.sshUsername,
            sshPassword = doc.sshPassword,
            sshPort = doc.sshPort,
            sshHost = doc.sshHost,
            dohUrl = doc.dohUrl,
            dnsTransport = DnsTransport.fromValue(doc.dnsTransport),
            sshAuthType = SshAuthType.fromValue(doc.sshAuthType),
            sshPrivateKey = doc.sshPrivateKey,
            sshKeyPassphrase = doc.sshKeyPassphrase,
            torBridgeLines = doc.torBridgeLines,
            dnsttAuthoritative = doc.dnsttAuthoritative,
            naivePort = doc.naivePort,
            naiveUsername = doc.naiveUsername,
            naivePassword = doc.naivePassword,
            isLocked = doc.isLocked,
            lockPasswordHash = doc.lockPasswordHash,
            expirationDate = doc.expirationDate,
            allowSharing = doc.allowSharing,
            boundDeviceId = doc.boundDeviceId,
            noizdnsStealth = doc.noizdnsStealth,
            dnsPayloadSize = doc.dnsPayloadSize,
            resolversHidden = hiddenFlag,
            defaultResolvers = doc.hiddenResolvers.map(::toDomainResolver),
            socks5ServerPort = doc.socks5ServerPort,
            vaydnsDnsttCompat = doc.vaydnsDnsttCompat,
            vaydnsRecordType = doc.vaydnsRecordType,
            vaydnsMaxQnameLen = doc.vaydnsMaxQnameLen,
            vaydnsRps = doc.vaydnsRps,
            vaydnsIdleTimeout = doc.vaydnsIdleTimeout,
            vaydnsKeepalive = doc.vaydnsKeepalive,
            vaydnsUdpTimeout = doc.vaydnsUdpTimeout,
            vaydnsMaxNumLabels = doc.vaydnsMaxNumLabels,
            vaydnsClientIdSize = doc.vaydnsClientIdSize,
            sshTlsEnabled = doc.sshTlsEnabled,
            sshTlsSni = doc.sshTlsSni,
            sshHttpProxyHost = doc.sshHttpProxyHost,
            sshHttpProxyPort = doc.sshHttpProxyPort,
            sshHttpProxyCustomHost = doc.sshHttpProxyCustomHost,
            sshWsEnabled = doc.sshWsEnabled,
            sshWsPath = doc.sshWsPath,
            sshWsUseTls = doc.sshWsUseTls,
            sshWsCustomHost = doc.sshWsCustomHost,
            sshPayload = doc.sshPayload,
            resolverMode = ResolverMode.fromValue(doc.resolverMode),
            rrSpreadCount = doc.rrSpreadCount,
            vlessUuid = doc.vlessUuid,
            vlessSecurity = doc.vlessSecurity,
            vlessTransport = doc.vlessTransport,
            vlessWsPath = doc.vlessWsPath,
            cdnIp = doc.cdnIp,
            cdnPort = doc.cdnPort,
            sniFragmentEnabled = doc.sniFragmentEnabled,
            sniFragmentStrategy = doc.sniFragmentStrategy,
            sniFragmentDelayMs = doc.sniFragmentDelayMs,
            chPaddingEnabled = doc.chPaddingEnabled,
            wsHeaderObfuscation = doc.wsHeaderObfuscation,
            wsPaddingEnabled = doc.wsPaddingEnabled,
            sniSpoofTtl = doc.sniSpoofTtl,
            fakeDecoyHost = doc.fakeDecoyHost,
            tcpMaxSeg = doc.tcpMaxSeg,
            vlessSni = doc.vlessSni,
            vlessRealityPubKey = doc.vlessRealityPubKey,
            vlessRealityShortId = doc.vlessRealityShortId,
            vlessRealityFp = doc.vlessRealityFp,
            hy2Password = doc.hy2Password,
            hy2Sni = doc.hy2Sni,
            hy2Insecure = doc.hy2Insecure,
            hy2Obfs = doc.hy2Obfs,
            hy2ObfsPassword = doc.hy2ObfsPassword
        )
    }

    private fun toDomainResolver(r: ProfileDocument.Resolver) =
        com.vpntz.app.domain.model.DnsResolver(r.host, r.port, r.authoritative)

    private fun enumFromCanonical(token: String, chained: Boolean): TunnelType = when (token) {
        "ss", "slipstream" -> if (chained) TunnelType.SLIPSTREAM_SSH else TunnelType.SLIPSTREAM
        "dnstt" -> if (chained) TunnelType.DNSTT_SSH else TunnelType.DNSTT
        "sayedns" -> if (chained) TunnelType.NOIZDNS_SSH else TunnelType.NOIZDNS
        "vaydns" -> if (chained) TunnelType.VAYDNS_SSH else TunnelType.VAYDNS
        "naive" -> if (chained) TunnelType.NAIVE_SSH else TunnelType.NAIVE
        "ssh" -> TunnelType.SSH
        "doh" -> TunnelType.DOH
        "snowflake" -> TunnelType.SNOWFLAKE
        "socks5" -> TunnelType.SOCKS5
        "vless" -> TunnelType.VLESS
        "hysteria2" -> TunnelType.HYSTERIA2
        else -> TunnelType.DNSTT
    }

    // ---- Domain -> Document -----------------------------------------------------

    fun toDocument(profile: ServerProfile): ProfileDocument {
        val baseToken = when (profile.tunnelType) {
            TunnelType.SLIPSTREAM -> "ss"
            TunnelType.DNSTT -> "dnstt"
            TunnelType.NOIZDNS -> "sayedns"
            TunnelType.VAYDNS -> "vaydns"
            TunnelType.NAIVE -> "naive"
            TunnelType.SSH -> "ssh"
            TunnelType.DOH -> "doh"
            TunnelType.SNOWFLAKE -> "snowflake"
            TunnelType.SOCKS5 -> "socks5"
            TunnelType.VLESS -> "vless"
            TunnelType.HYSTERIA2 -> "hysteria2"
            is TunnelType -> when (profile.tunnelType.value) {
                "slipstream_ssh" -> "ss"
                "dnstt_ssh" -> "dnstt"
                "sayedns_ssh" -> "sayedns"
                "vaydns_ssh" -> "vaydns"
                "naive_ssh" -> "naive"
                else -> profile.tunnelType.value.substringBeforeLast('_')
            }
        }
        val token = when (profile.tunnelType) {
            TunnelType.SLIPSTREAM_SSH, TunnelType.DNSTT_SSH, TunnelType.NOIZDNS_SSH,
            TunnelType.VAYDNS_SSH, TunnelType.NAIVE_SSH -> "${baseToken}_ssh"
            else -> baseToken
        }
        return ProfileDocument(
            tunnelToken = token,
            name = profile.name,
            domain = profile.domain,
            resolvers = profile.resolvers.map(::fromDomainResolver),
            authoritativeMode = profile.authoritativeMode,
            keepAliveInterval = profile.keepAliveInterval,
            congestionControl = profile.congestionControl.value,
            tcpListenPort = profile.tcpListenPort,
            tcpListenHost = profile.tcpListenHost,
            gsoEnabled = profile.gsoEnabled,
            dnsttPublicKey = profile.dnsttPublicKey,
            socksUsername = profile.socksUsername,
            socksPassword = profile.socksPassword,
            sshChainEnabled = true,
            sshUsername = profile.sshUsername,
            sshPassword = profile.sshPassword,
            sshPort = profile.sshPort,
            sshHost = profile.sshHost,
            dohUrl = profile.dohUrl,
            dnsTransport = profile.dnsTransport.value,
            sshAuthType = profile.sshAuthType.value,
            sshPrivateKey = profile.sshPrivateKey,
            sshKeyPassphrase = profile.sshKeyPassphrase,
            torBridgeLines = profile.torBridgeLines,
            dnsttAuthoritative = profile.dnsttAuthoritative,
            naivePort = profile.naivePort,
            naiveUsername = profile.naiveUsername,
            naivePassword = profile.naivePassword,
            isLocked = profile.isLocked,
            lockPasswordHash = profile.lockPasswordHash,
            expirationDate = profile.expirationDate,
            allowSharing = profile.allowSharing,
            boundDeviceId = profile.boundDeviceId,
            resolversHidden = profile.resolversHidden,
            hiddenResolvers = profile.defaultResolvers.map(::fromDomainResolver),
            noizdnsStealth = profile.noizdnsStealth,
            dnsPayloadSize = profile.dnsPayloadSize,
            socks5ServerPort = profile.socks5ServerPort,
            vaydnsDnsttCompat = profile.vaydnsDnsttCompat,
            vaydnsRecordType = profile.vaydnsRecordType,
            vaydnsMaxQnameLen = profile.vaydnsMaxQnameLen,
            vaydnsRps = profile.vaydnsRps,
            vaydnsIdleTimeout = profile.vaydnsIdleTimeout,
            vaydnsKeepalive = profile.vaydnsKeepalive,
            vaydnsUdpTimeout = profile.vaydnsUdpTimeout,
            vaydnsMaxNumLabels = profile.vaydnsMaxNumLabels,
            vaydnsClientIdSize = profile.vaydnsClientIdSize,
            sshTlsEnabled = profile.sshTlsEnabled,
            sshTlsSni = profile.sshTlsSni,
            sshHttpProxyHost = profile.sshHttpProxyHost,
            sshHttpProxyPort = profile.sshHttpProxyPort,
            sshHttpProxyCustomHost = profile.sshHttpProxyCustomHost,
            sshWsEnabled = profile.sshWsEnabled,
            sshWsPath = profile.sshWsPath,
            sshWsUseTls = profile.sshWsUseTls,
            sshWsCustomHost = profile.sshWsCustomHost,
            sshPayload = profile.sshPayload,
            resolverMode = profile.resolverMode.value,
            rrSpreadCount = profile.rrSpreadCount,
            vlessUuid = profile.vlessUuid,
            vlessSecurity = profile.vlessSecurity,
            vlessTransport = profile.vlessTransport,
            vlessWsPath = profile.vlessWsPath,
            cdnIp = profile.cdnIp,
            cdnPort = profile.cdnPort,
            sniFragmentEnabled = profile.sniFragmentEnabled,
            sniFragmentStrategy = profile.sniFragmentStrategy,
            sniFragmentDelayMs = profile.sniFragmentDelayMs,
            chPaddingEnabled = profile.chPaddingEnabled,
            wsHeaderObfuscation = profile.wsHeaderObfuscation,
            wsPaddingEnabled = profile.wsPaddingEnabled,
            sniSpoofTtl = profile.sniSpoofTtl,
            fakeDecoyHost = profile.fakeDecoyHost,
            tcpMaxSeg = profile.tcpMaxSeg,
            vlessSni = profile.vlessSni,
            vlessRealityPubKey = profile.vlessRealityPubKey,
            vlessRealityShortId = profile.vlessRealityShortId,
            vlessRealityFp = profile.vlessRealityFp,
            hy2Password = profile.hy2Password,
            hy2Sni = profile.hy2Sni,
            hy2Insecure = profile.hy2Insecure,
            hy2Obfs = profile.hy2Obfs,
            hy2ObfsPassword = profile.hy2ObfsPassword
        )
    }

    private fun fromDomainResolver(r: com.vpntz.app.domain.model.DnsResolver) =
        ProfileDocument.Resolver(r.host, r.port, r.authoritative)

    /** Lock hashing used whenever the document layer creates a locked profile. */
    fun hashLockPassword(password: String): String = VaultCrypto.hashLockPassword(password)
}
