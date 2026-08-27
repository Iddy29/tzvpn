package com.vpntz.app.config

/**
 * Record-level codec: `ProfileDocument` ⇄ 88-field pipe record.
 * VPN-TZ original implementation per docs/provenance/WIRE_FORMAT.md §"Pipe record".
 *
 * Emission is strict and always produces the current full-width record.
 * Parsing is deliberately forgiving: short tails, numeric fallbacks per field,
 * unknown trailing fields ignored, forward versions tolerated with a notice.
 */
object ProfileRecordCodec {

    // ---- Resolvers CSV ------------------------------------------------------

    fun encodeResolvers(resolvers: List<ProfileDocument.Resolver>): String =
        resolvers.joinToString(",") { r ->
            "${sanitizeWire(r.host)}:${r.port}:${if (r.authoritative) "1" else "0"}"
        }

    /**
     * Parses the resolver CSV. IPv6 literals contain colons, so parsing anchors
     * on the LAST colon (auth flag) and the previous numeric segment (port);
     * everything before re-joins as the host.
     */
    fun parseResolvers(raw: String): List<ProfileDocument.Resolver> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { entry ->
            if (entry.isBlank()) return@mapNotNull null
            val segs = entry.split(":")
            val auth = segs.lastOrNull()?.equals("1", ignoreCase = true) == true
            val tail = if (segs.size >= 2 && segs[segs.size - 2].toIntOrNull() != null) {
                segs[segs.size - 2]
            } else null
            val host = when {
                segs.size <= 1 -> entry
                tail != null && segs.size > 2 -> segs.dropLast(2).joinToString(":")
                else -> segs.dropLast(1).joinToString(":")
            }.trim()
            ProfileDocument.Resolver(
                host = host,
                port = tail?.toIntOrNull() ?: 53,
                authoritative = auth
            )
        }
    }

    // ---- Document -> record ---------------------------------------------------

    private val b64Text = { value: String -> WireBase64.encode(value) }

    fun encode(doc: ProfileDocument): String {
        val sshChain = doc.sshChainEnabled || ProfileDocument.isSshChained(doc.tunnelToken)
        return listOf(
            VpnTzField.WIRE_VERSION,
            sanitizeWire(doc.tunnelToken),
            sanitizeWire(doc.name),
            sanitizeWire(doc.domain),
            encodeResolvers(doc.resolvers),
            BoolWire(doc.authoritativeMode),
            doc.keepAliveInterval.toString(),
            doc.congestionControl,
            doc.tcpListenPort.toString(),
            sanitizeWire(doc.tcpListenHost),
            BoolWire(doc.gsoEnabled),
            sanitizeWire(doc.dnsttPublicKey),
            sanitizeWire(doc.socksUsername ?: ""),
            sanitizeWire(doc.socksPassword ?: ""),
            BoolWire(sshChain),
            sanitizeWire(doc.sshUsername),
            sanitizeWire(doc.sshPassword),
            doc.sshPort.toString(),
            "0",
            sanitizeWire(doc.sshHost),
            "0",
            doc.dohUrl, // URLs may legitimately carry colons/slashes — not sanitized
            doc.dnsTransport,
            doc.sshAuthType,
            b64Text(doc.sshPrivateKey),
            b64Text(doc.sshKeyPassphrase),
            b64Text(doc.torBridgeLines),
            BoolWire(doc.dnsttAuthoritative),
            doc.naivePort.toString(),
            sanitizeWire(doc.naiveUsername),
            b64Text(doc.naivePassword),
            BoolWire(doc.isLocked),
            sanitizeWire(doc.lockPasswordHash),
            doc.expirationDate.toString(),
            BoolWire(doc.allowSharing),
            sanitizeWire(doc.boundDeviceId),
            BoolWire(doc.resolversHidden),
            if (doc.resolversHidden) encodeResolvers(doc.hiddenResolvers) else "",
            BoolWire(doc.noizdnsStealth),
            doc.dnsPayloadSize.toString(),
            doc.socks5ServerPort.toString(),
            BoolWire(doc.vaydnsDnsttCompat),
            sanitizeWire(doc.vaydnsRecordType),
            doc.vaydnsMaxQnameLen.toString(),
            formatDouble(doc.vaydnsRps),
            doc.vaydnsIdleTimeout.toString(),
            doc.vaydnsKeepalive.toString(),
            doc.vaydnsUdpTimeout.toString(),
            doc.vaydnsMaxNumLabels.toString(),
            doc.vaydnsClientIdSize.toString(),
            BoolWire(doc.sshTlsEnabled),
            sanitizeWire(doc.sshTlsSni),
            sanitizeWire(doc.sshHttpProxyHost),
            doc.sshHttpProxyPort.toString(),
            sanitizeWire(doc.sshHttpProxyCustomHost),
            BoolWire(doc.sshWsEnabled),
            sanitizeWire(doc.sshWsPath),
            BoolWire(doc.sshWsUseTls),
            sanitizeWire(doc.sshWsCustomHost),
            b64Text(doc.sshPayload),
            doc.resolverMode,
            doc.rrSpreadCount.toString(),
            sanitizeWire(doc.vlessUuid),
            sanitizeWire(doc.vlessSecurity),
            sanitizeWire(doc.vlessTransport),
            sanitizeWire(doc.vlessWsPath),
            sanitizeWire(doc.cdnIp),
            doc.cdnPort.toString(),
            BoolWire(doc.sniFragmentEnabled),
            sanitizeWire(doc.sniFragmentStrategy),
            doc.sniFragmentDelayMs.toString(),
            "", // F71: legacy SNI slot frozen since v28
            BoolWire(doc.chPaddingEnabled),
            BoolWire(doc.wsHeaderObfuscation),
            BoolWire(doc.wsPaddingEnabled),
            doc.sniSpoofTtl.toString(),
            sanitizeWire(doc.fakeDecoyHost),
            doc.tcpMaxSeg.toString(),
            sanitizeWire(doc.vlessSni),
            sanitizeWire(doc.vlessRealityPubKey),
            sanitizeWire(doc.vlessRealityShortId),
            sanitizeWire(doc.vlessRealityFp),
            sanitizeWire(doc.hy2Password),
            sanitizeWire(doc.hy2Sni),
            BoolWire(doc.hy2Insecure),
            sanitizeWire(doc.hy2Obfs),
            sanitizeWire(doc.hy2ObfsPassword)
        ).joinToString(VpnTzField.DELIMITER).also {
            require(it.split(VpnTzField.DELIMITER).size == VpnTzField.CURRENT_LENGTH) {
                "internal wire length mismatch"
            }
        }
    }

    // ---- record -> Document -----------------------------------------------------

    class ParseException(message: String) : Exception(message)

    sealed interface ParseOutcome {
        data class Ok(val document: ProfileDocument, val newerThanKnown: Boolean) : ParseOutcome
        data class Bad(val reason: String) : ParseOutcome
    }

    fun parse(record: String): ParseOutcome {
        val parts = Record.split(record)
        val versionToken = parts.firstOrNull()?.trim() ?: return ParseOutcome.Bad("empty record")

        // The first field must be a version number for all records this layer reads.
        if (versionToken.toIntOrNull() == null) {
            return ParseOutcome.Bad("not a VPN-TZ record (version field missing)")
        }

        val r = Record(parts)
        val token = ProfileDocument.normalizeToken(r[VpnTzField.TUNNEL_TOKEN])
            ?: return ParseOutcome.Bad("unknown tunnel type '${r[VpnTzField.TUNNEL_TOKEN]}'")

        val hiddenRaw = r[VpnTzField.HIDDEN_RESOLVERS]
        val visible = r[VpnTzField.RESOLVERS]
        val resolversVisible = parseResolvers(visible)
        val hiddenList = parseResolvers(hiddenRaw)
        val resolversHiddenFlag = r.bool(VpnTzField.RESOLVERS_HIDDEN)

        val doc = ProfileDocument(
            tunnelToken = token,
            name = r[VpnTzField.NAME],
            domain = r[VpnTzField.DOMAIN],
            resolvers = resolversVisible,
            authoritativeMode = r.bool(VpnTzField.AUTHORITATIVE_MODE),
            keepAliveInterval = r.int(VpnTzField.KEEP_ALIVE_INTERVAL, 5_000),
            congestionControl = normalizeOr(
                r[VpnTzField.CONGESTION_CONTROL], "bbr", setOf("bbr", "dcubic")),
            tcpListenPort = r.int(VpnTzField.TCP_LISTEN_PORT, 1_080),
            tcpListenHost = r[VpnTzField.TCP_LISTEN_HOST],
            gsoEnabled = r.bool(VpnTzField.GSO_ENABLED),
            dnsttPublicKey = r[VpnTzField.DNSTT_PUBLIC_KEY],
            socksUsername = r[VpnTzField.SOCKS_USERNAME].takeIf { it.isNotEmpty() },
            socksPassword = r[VpnTzField.SOCKS_PASSWORD].takeIf { it.isNotEmpty() },
            sshChainEnabled = r.bool(VpnTzField.SSH_CHAIN_ENABLED),
            sshUsername = r[VpnTzField.SSH_USERNAME],
            sshPassword = r[VpnTzField.SSH_PASSWORD],
            sshPort = r.int(VpnTzField.SSH_PORT, 22),
            sshHost = r[VpnTzField.SSH_HOST],
            dohUrl = r[VpnTzField.DOH_URL],
            dnsTransport = normalizeOr(
                r[VpnTzField.DNS_TRANSPORT], "udp", setOf("udp", "tcp", "dot", "doh")),
            sshAuthType = normalizeOr(
                r[VpnTzField.SSH_AUTH_TYPE], "password", setOf("password", "key")),
            sshPrivateKey = decodeB64TextField(r[VpnTzField.SSH_PRIVATE_KEY_B64]),
            sshKeyPassphrase = decodeB64TextField(r[VpnTzField.SSH_KEY_PASSPHRASE_B64]),
            torBridgeLines = decodeB64TextField(r[VpnTzField.TOR_BRIDGE_LINES_B64]),
            dnsttAuthoritative = r.bool(VpnTzField.DNSTT_AUTHORITATIVE),
            naivePort = r.int(VpnTzField.NAIVE_PORT, 443),
            naiveUsername = r[VpnTzField.NAIVE_USERNAME],
            naivePassword = decodeB64TextField(r[VpnTzField.NAIVE_PASSWORD_B64]),
            isLocked = r.bool(VpnTzField.IS_LOCKED),
            lockPasswordHash = r[VpnTzField.LOCK_PASSWORD_HASH],
            expirationDate = r.long(VpnTzField.EXPIRATION_DATE, 0L),
            allowSharing = r.bool(VpnTzField.ALLOW_SHARING),
            boundDeviceId = r[VpnTzField.BOUND_DEVICE_ID],
            resolversHidden = resolversHiddenFlag,
            hiddenResolvers = hiddenList,
            noizdnsStealth = r.bool(VpnTzField.NOIZDNS_STEALTH),
            dnsPayloadSize = r.int(VpnTzField.DNS_PAYLOAD_SIZE, 0),
            socks5ServerPort = r.int(VpnTzField.SOCKS5_SERVER_PORT, 1_080),
            vaydnsDnsttCompat = r.bool(VpnTzField.VAYDNS_DNSTT_COMPAT),
            vaydnsRecordType = normalizeOr(
                r[VpnTzField.VAYDNS_RECORD_TYPE], "txt",
                setOf("txt", "cname", "a", "aaaa", "mx", "ns", "srv", "null", "caa")),
            vaydnsMaxQnameLen = r.int(VpnTzField.VAYDNS_MAX_QNAME_LEN, 101),
            vaydnsRps = r.dbl(VpnTzField.VAYDNS_RPS, 0.0),
            vaydnsIdleTimeout = r.int(VpnTzField.VAYDNS_IDLE_TIMEOUT, 0),
            vaydnsKeepalive = r.int(VpnTzField.VAYDNS_KEEPALIVE, 0),
            vaydnsUdpTimeout = r.int(VpnTzField.VAYDNS_UDP_TIMEOUT, 0),
            vaydnsMaxNumLabels = r.int(VpnTzField.VAYDNS_MAX_NUM_LABELS, 0),
            vaydnsClientIdSize = r.int(VpnTzField.VAYDNS_CLIENT_ID_SIZE, 0),
            sshTlsEnabled = r.bool(VpnTzField.SSH_TLS_ENABLED),
            sshTlsSni = r[VpnTzField.SSH_TLS_SNI],
            sshHttpProxyHost = r[VpnTzField.SSH_HTTP_PROXY_HOST],
            sshHttpProxyPort = r.int(VpnTzField.SSH_HTTP_PROXY_PORT, 8_080),
            sshHttpProxyCustomHost = r[VpnTzField.SSH_HTTP_PROXY_CUSTOM_HOST],
            sshWsEnabled = r.bool(VpnTzField.SSH_WS_ENABLED),
            sshWsPath = r.textOrBlankDefault(VpnTzField.SSH_WS_PATH, "/"),
            sshWsUseTls = r.boolTrueWhenAbsent(VpnTzField.SSH_WS_USE_TLS),
            sshWsCustomHost = r[VpnTzField.SSH_WS_CUSTOM_HOST],
            sshPayload = decodeB64TextField(r[VpnTzField.SSH_PAYLOAD_B64]),
            resolverMode = normalizeOr(
                r[VpnTzField.RESOLVER_MODE], "fanout", setOf("roundrobin", "fanout")),
            rrSpreadCount = r.int(VpnTzField.RR_SPREAD_COUNT, 3),
            vlessUuid = r[VpnTzField.VLESS_UUID],
            vlessSecurity = r[VpnTzField.VLESS_SECURITY],
            vlessTransport = r[VpnTzField.VLESS_TRANSPORT],
            vlessWsPath = r.textOrBlankDefault(VpnTzField.VLESS_WS_PATH, "/"),
            cdnIp = r[VpnTzField.CDN_IP],
            cdnPort = r.int(VpnTzField.CDN_PORT, 443),
            sniFragmentEnabled = r.boolTrueWhenAbsent(VpnTzField.SNI_FRAGMENT_ENABLED),
            sniFragmentStrategy = r.textOrBlankDefault(VpnTzField.SNI_FRAGMENT_STRATEGY, "sni_split"),
            sniFragmentDelayMs = r.int(VpnTzField.SNI_FRAGMENT_DELAY_MS, 100),
            chPaddingEnabled = r.bool(VpnTzField.CH_PADDING_ENABLED),
            wsHeaderObfuscation = r.textOrBlankDefault(VpnTzField.WS_HEADER_OBFUSCATION, "1") == "1",
            wsPaddingEnabled = r.bool(VpnTzField.WS_PADDING_ENABLED),
            sniSpoofTtl = r.int(VpnTzField.SNI_SPOOF_TTL, 8),
            fakeDecoyHost = r[VpnTzField.FAKE_DECOY_HOST],
            tcpMaxSeg = r.int(VpnTzField.TCP_MAX_SEG, 0),
            vlessSni = r[VpnTzField.VLESS_SNI],
            vlessRealityPubKey = r[VpnTzField.VLESS_REALITY_PUB_KEY],
            vlessRealityShortId = r[VpnTzField.VLESS_REALITY_SHORT_ID],
            vlessRealityFp = r.textOrBlankDefault(VpnTzField.VLESS_REALITY_FP, "chrome"),
            hy2Password = r[VpnTzField.HY2_PASSWORD],
            hy2Sni = r[VpnTzField.HY2_SNI],
            hy2Insecure = r.bool(VpnTzField.HY2_INSECURE),
            hy2Obfs = r[VpnTzField.HY2_OBFS],
            hy2ObfsPassword = r[VpnTzField.HY2_OBFS_PASSWORD]
        )
        return ParseOutcome.Ok(doc, versionToken.toInt() > VpnTzField.MAX_KNOWN_VERSION)
    }

    private fun normalizeOr(raw: String, default: String, allowed: Set<String>): String {
        val cleaned = raw.trim().lowercase()
        return if (cleaned in allowed) cleaned else default
    }

    /** b64 text fields degrade to "" when corrupted — they are optional content. */
    private fun decodeB64TextField(value: String): String =
        try {
            String(WireBase64.decode(value), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }

    /** Locale-independent double rendering matching observable baseline ("0.0"). */
    private fun formatDouble(d: Double): String =
        if (d % 1.0 == 0.0 && !d.toString().contains('E')) "%.1f".format(java.util.Locale.US, d) else d.toString()
}
