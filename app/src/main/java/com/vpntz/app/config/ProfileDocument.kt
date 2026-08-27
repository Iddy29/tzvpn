package com.vpntz.app.config

/**
 * Immutable, independently designed profile model of VPN-TZ.
 * Every field carries a declared default so decoders can rebuild partial
 * (short-tail) records from older generators.
 *
 * Validation is declarative: [validate] returns issues instead of throwing,
 * because import must be maximally tolerant while export must be strict.
 */
data class ProfileDocument(
    val tunnelToken: String = "dnstt",
    val name: String = "",
    val domain: String = "",
    val resolvers: List<Resolver> = emptyList(),
    val authoritativeMode: Boolean = false,
    val keepAliveInterval: Int = 5_000,
    val congestionControl: String = "bbr",
    val tcpListenPort: Int = 1_080,
    val tcpListenHost: String = "127.0.0.1",
    val gsoEnabled: Boolean = false,
    val dnsttPublicKey: String = "",
    val socksUsername: String? = null,
    val socksPassword: String? = null,
    val sshChainEnabled: Boolean = false,
    val sshUsername: String = "",
    val sshPassword: String = "",
    val sshPort: Int = 22,
    val sshHost: String = "127.0.0.1",
    val dohUrl: String = "",
    val dnsTransport: String = "udp",
    val sshAuthType: String = "password",
    val sshPrivateKey: String = "",
    val sshKeyPassphrase: String = "",
    val torBridgeLines: String = "",
    val dnsttAuthoritative: Boolean = false,
    val naivePort: Int = 443,
    val naiveUsername: String = "",
    val naivePassword: String = "",
    val isLocked: Boolean = false,
    val lockPasswordHash: String = "",
    val expirationDate: Long = 0,
    val allowSharing: Boolean = false,
    val boundDeviceId: String = "",
    val resolversHidden: Boolean = false,
    val hiddenResolvers: List<Resolver> = emptyList(),
    val noizdnsStealth: Boolean = false,
    val dnsPayloadSize: Int = 0,
    val socks5ServerPort: Int = 1_080,
    val vaydnsDnsttCompat: Boolean = false,
    val vaydnsRecordType: String = "txt",
    val vaydnsMaxQnameLen: Int = 101,
    val vaydnsRps: Double = 0.0,
    val vaydnsIdleTimeout: Int = 0,
    val vaydnsKeepalive: Int = 0,
    val vaydnsUdpTimeout: Int = 0,
    val vaydnsMaxNumLabels: Int = 0,
    val vaydnsClientIdSize: Int = 0,
    val sshTlsEnabled: Boolean = false,
    val sshTlsSni: String = "",
    val sshHttpProxyHost: String = "",
    val sshHttpProxyPort: Int = 8_080,
    val sshHttpProxyCustomHost: String = "",
    val sshWsEnabled: Boolean = false,
    val sshWsPath: String = "/",
    val sshWsUseTls: Boolean = true,
    val sshWsCustomHost: String = "",
    val sshPayload: String = "",
    val resolverMode: String = "roundrobin",
    val rrSpreadCount: Int = 3,
    val vlessUuid: String = "",
    val vlessSecurity: String = "tls",
    val vlessTransport: String = "ws",
    val vlessWsPath: String = "/",
    val cdnIp: String = "",
    val cdnPort: Int = 443,
    val sniFragmentEnabled: Boolean = true,
    val sniFragmentStrategy: String = "micro",
    val sniFragmentDelayMs: Int = 300,
    val chPaddingEnabled: Boolean = false,
    val wsHeaderObfuscation: Boolean = true,
    val wsPaddingEnabled: Boolean = false,
    val sniSpoofTtl: Int = 8,
    val fakeDecoyHost: String = "",
    val tcpMaxSeg: Int = 0,
    val vlessSni: String = "",
    val vlessRealityPubKey: String = "",
    val vlessRealityShortId: String = "",
    val vlessRealityFp: String = "chrome",
    val hy2Password: String = "",
    val hy2Sni: String = "",
    val hy2Insecure: Boolean = false,
    val hy2Obfs: String = "",
    val hy2ObfsPassword: String = ""
) {

    data class Resolver(val host: String, val port: Int = 53, val authoritative: Boolean = false)

    /** A document may only be shared when unlocked or explicitly allowed. */
    fun canExport(): Boolean = !isLocked || allowSharing

    /**
     * Returns non-fatal issues found on this document; empty list means valid.
     * Deliberately returns warnings rather than failing hard — the wire layer
     * treats strictness as an EXPORT-time concern.
     */
    fun validate(): List<String> {
        val issues = mutableListOf<String>()
        if (name.isEmpty()) issues += "profile name is empty"
        if (name.length > MAX_NAME_LENGTH) issues += "profile name too long"
        when (tunnelToken) {
            in TOKEN_REQUIRED_DOMAIN -> if (domain.isBlank()) issues += "domain required for $tunnelToken"
            "socks5" -> if (socksUsername.isNullOrBlank()) issues += "SOCKS5 credentials required"
        }
        if (tcpListenPort !in 1..65_535) issues += "local listen port out of range"
        if (sshChainEnabled && sshPort !in 1..65_535) issues += "SSH port out of range"
        if (vaydnsMaxQnameLen !in 21..255) issues += "VayDNS QNAME length out of range"
        if (rrSpreadCount < 1) issues += "round-robin spread must be >= 1"
        if (isLocked && lockPasswordHash.isEmpty()) issues += "locked profile has no password hash"
        return issues
    }

    companion object {
        const val MAX_NAME_LENGTH = 120

        /** Tokens whose records must carry a tunnel domain. */
        val TOKEN_REQUIRED_DOMAIN =
            setOf("dnstt", "dnstt_ssh", "ss", "slipstream", "sayedns", "sayedns_ssh",
                "vaydns", "vaydns_ssh")

        // ---- Tunnel token mapping --------------------------------------------
        // Parse accepts historical aliases case-insensitively; emit uses canonical.
        private val TOKEN_ALIASES: Map<String, String> = mapOf(
            "ss" to "ss", "slipstream" to "ss",
            "slipstream_ssh" to "slipstream_ssh",
            "dnstt" to "dnstt", "dnstt_ssh" to "dnstt_ssh",
            "sayedns" to "sayedns", "sayedns_ssh" to "sayedns_ssh",
            "ssh" to "ssh", "doh" to "doh", "snowflake" to "snowflake",
            "naive" to "naive", "naive_ssh" to "naive_ssh", "socks5" to "socks5",
            "vaydns" to "vaydns", "vaydns_ssh" to "vaydns_ssh",
            "vless" to "vless", "hysteria2" to "hysteria2"
        )

        private val SSH_CHAIN_TOKENS =
            setOf("ssh", "dnstt_ssh", "ss", "slipstream_ssh", "naive_ssh", "vaydns_ssh")

        fun normalizeToken(raw: String): String? = TOKEN_ALIASES[raw.lowercase()]

        fun isSshChained(token: String): Boolean = token in SSH_CHAIN_TOKENS
    }
}
