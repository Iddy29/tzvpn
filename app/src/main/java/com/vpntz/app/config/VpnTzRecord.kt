package com.vpntz.app.config

/**
 * Positional field table of the VPN-TZ pipe record (v43 era).
 *
 * VPN-TZ original implementation, specified in docs/provenance/WIRE_FORMAT.md.
 * Positions are fixed wire layout shared with older releases and the TzGate
 * Go generator; NEVER reorder or renumber.
 */
enum class VpnTzField {
    VERSION,                 // 0
    TUNNEL_TOKEN,            // 1
    NAME,                    // 2
    DOMAIN,                  // 3
    RESOLVERS,               // 4
    AUTHORITATIVE_MODE,      // 5
    KEEP_ALIVE_INTERVAL,     // 6
    CONGESTION_CONTROL,      // 7
    TCP_LISTEN_PORT,         // 8
    TCP_LISTEN_HOST,         // 9
    GSO_ENABLED,             // 10
    DNSTT_PUBLIC_KEY,        // 11
    SOCKS_USERNAME,          // 12
    SOCKS_PASSWORD,          // 13
    SSH_CHAIN_ENABLED,       // 14
    SSH_USERNAME,            // 15
    SSH_PASSWORD,            // 16
    SSH_PORT,                // 17
    FORWARD_DNS_THROUGH_SSH, // 18 deprecated
    SSH_HOST,                // 19
    USE_SERVER_DNS,          // 20 removed
    DOH_URL,                 // 21
    DNS_TRANSPORT,           // 22
    SSH_AUTH_TYPE,           // 23
    SSH_PRIVATE_KEY_B64,     // 24
    SSH_KEY_PASSPHRASE_B64,  // 25
    TOR_BRIDGE_LINES_B64,    // 26
    DNSTT_AUTHORITATIVE,     // 27
    NAIVE_PORT,              // 28
    NAIVE_USERNAME,          // 29
    NAIVE_PASSWORD_B64,      // 30
    IS_LOCKED,               // 31
    LOCK_PASSWORD_HASH,      // 32
    EXPIRATION_DATE,         // 33
    ALLOW_SHARING,           // 34
    BOUND_DEVICE_ID,         // 35
    RESOLVERS_HIDDEN,        // 36
    HIDDEN_RESOLVERS,        // 37
    NOIZDNS_STEALTH,         // 38
    DNS_PAYLOAD_SIZE,        // 39
    SOCKS5_SERVER_PORT,      // 40
    VAYDNS_DNSTT_COMPAT,     // 41
    VAYDNS_RECORD_TYPE,      // 42
    VAYDNS_MAX_QNAME_LEN,    // 43
    VAYDNS_RPS,              // 44 double string
    VAYDNS_IDLE_TIMEOUT,     // 45
    VAYDNS_KEEPALIVE,        // 46
    VAYDNS_UDP_TIMEOUT,      // 47
    VAYDNS_MAX_NUM_LABELS,   // 48
    VAYDNS_CLIENT_ID_SIZE,   // 49
    SSH_TLS_ENABLED,         // 50
    SSH_TLS_SNI,             // 51
    SSH_HTTP_PROXY_HOST,     // 52
    SSH_HTTP_PROXY_PORT,     // 53
    SSH_HTTP_PROXY_CUSTOM_HOST, // 54
    SSH_WS_ENABLED,          // 55
    SSH_WS_PATH,             // 56
    SSH_WS_USE_TLS,          // 57
    SSH_WS_CUSTOM_HOST,      // 58
    SSH_PAYLOAD_B64,         // 59
    RESOLVER_MODE,           // 60
    RR_SPREAD_COUNT,         // 61
    VLESS_UUID,              // 62
    VLESS_SECURITY,          // 63
    VLESS_TRANSPORT,         // 64
    VLESS_WS_PATH,           // 65
    CDN_IP,                  // 66
    CDN_PORT,                // 67
    SNI_FRAGMENT_ENABLED,    // 68
    SNI_FRAGMENT_STRATEGY,   // 69
    SNI_FRAGMENT_DELAY_MS,   // 70
    LEGACY_VLESS_SNI,        // 71 always empty since v28
    CH_PADDING_ENABLED,      // 72
    WS_HEADER_OBFUSCATION,   // 73
    WS_PADDING_ENABLED,      // 74
    SNI_SPOOF_TTL,           // 75
    FAKE_DECOY_HOST,         // 76
    TCP_MAX_SEG,             // 77
    VLESS_SNI,               // 78
    VLESS_REALITY_PUB_KEY,   // 79
    VLESS_REALITY_SHORT_ID,  // 80
    VLESS_REALITY_FP,        // 81
    HY2_PASSWORD,            // 82
    HY2_SNI,                 // 83
    HY2_INSECURE,            // 84
    HY2_OBFS,                // 85
    HY2_OBFS_PASSWORD;       // 86

    companion object {
        /** Wire length emitted today. Short records are legal (older generators). */
        val CURRENT_LENGTH: Int get() = entries.size
        const val WIRE_VERSION = "43"
        /** Exports above this version trigger a "newer app" notice on import. */
        const val MAX_KNOWN_VERSION = 43
        const val DELIMITER = "|"
    }
}

/** Resilient accessors over a split record; every failure falls back to [fallback]. */
internal class Record(private val parts: List<String>) {
    operator fun get(field: VpnTzField): String =
        if (field.ordinal < parts.size) parts[field.ordinal] else ""

    fun bool(field: VpnTzField): Boolean =
        get(field) == "1" || get(field) == "true"

    /** Absent field (short legacy record) defaults to TRUE; present values parse strictly. */
    fun boolTrueWhenAbsent(field: VpnTzField): Boolean {
        if (field.ordinal >= parts.size) return true
        return parts[field.ordinal] == "1" || parts[field.ordinal] == "true"
    }

    /** Text field whose blank wire form means the given default. */
    fun textOrBlankDefault(field: VpnTzField, default: String): String =
        get(field).ifBlank { default }

    fun int(field: VpnTzField, fallback: Int): Int = get(field).toIntOrNull() ?: fallback

    fun long(field: VpnTzField, fallback: Long): Long = get(field).toLongOrNull() ?: fallback

    fun dbl(field: VpnTzField, fallback: Double): Double =
        get(field).toDoubleOrNull() ?: fallback

    companion object {
        fun split(record: String): List<String> = record.split(VpnTzField.DELIMITER)
    }
}

internal fun BoolWire(value: Boolean): String = if (value) "1" else "0"

/** Removes field-delimiters so user text cannot shift positions. */
internal fun sanitizeWire(value: String): String = value.replace(VpnTzField.DELIMITER, "")
