package com.vpntz.app.config

import java.net.URLDecoder

/**
 * Community-standard URI codecs: VLESS and Hysteria2.
 * VPN-TZ original implementation per WIRE_FORMAT.md §"Community URIs".
 *
 * Error model: hard errors reject the line (invalid structure), soft issues
 * downgrade the line to a warning result (unsupported-but-recognized).
 */
object CommunityUriCodec {

    sealed interface Result {
        data class Ok(val document: ProfileDocument) : Result
        data class SoftReject(val reason: String) : Result
        data class Reject(val reason: String) : Result
    }

    // ------------------------------------------------------------------ VLESS --

    fun parseVless(uri: String): Result {
        try {
            val withoutScheme = uri.substringAfter("://", missingDelimiterValue = "")
            if (withoutScheme.isEmpty()) return Result.Reject("VLESS URI has no body")

            val (mainPart, fragmentName) = splitFragment(withoutScheme, ::decodePlusAsSpace)
            val name = fragmentName?.takeIf { it.isNotBlank() } ?: "VLESS"

            val atIdx = mainPart.indexOf('@')
            if (atIdx < 0) return Result.Reject("Invalid VLESS URI - missing UUID")
            val uuid = mainPart.substring(0, atIdx)
            val afterAt = mainPart.substring(atIdx + 1)

            val queryIdx = afterAt.indexOf('?')
            val hostPort = if (queryIdx >= 0) afterAt.substring(0, queryIdx) else afterAt
            val queryString = if (queryIdx >= 0) afterAt.substring(queryIdx + 1) else ""
            // Baseline-decoded with URLDecoder semantics ('+' becomes space).
            val params = parseQuery(queryString, CommunityUriCodec::decodePlusAsSpace)

            val (server, port) = splitHostPort(hostPort)

            val transportRaw = params["type"] ?: "tcp"
            val normalizedTransport = when (transportRaw) {
                "ws", "websocket" -> "ws"
                "tcp", "raw" -> "tcp"
                else -> return Result.SoftReject(
                    "Only VLESS over WebSocket is supported - '$transportRaw' transport is not available")
            }
            if (normalizedTransport != "ws" && params["security"] != "reality") {
                return Result.SoftReject(
                    "Only VLESS over WebSocket is supported - this config uses '$transportRaw' transport")
            }

            val security = when (val sec = params["security"]) {
                null -> "tls"
                "tls", "reality" -> sec
                "none", "" -> "none"
                else -> "tls"
            }

            val realityPubKey = params["pbk"] ?: ""
            if (security == "reality" && realityPubKey.isBlank()) {
                return Result.Reject("REALITY config is missing pbk= (public key)")
            }
            val uuidHex = uuid.replace("-", "")
            if (uuidHex.length != 32 || uuidHex.any { Character.digit(it, 16) < 0 }) {
                return Result.Reject("Invalid VLESS UUID")
            }

            val wsHost = params["host"] ?: server
            val sni = params["sni"] ?: wsHost
            val cdnIp = params["cdn"] ?: server
            val cdnPort = params["cdn-port"]?.toIntOrNull() ?: port

            val fragmentParam = params["fragment"]
            val fragmentEnabled = when {
                security == "reality" -> false
                fragmentParam != null -> fragmentParam.isNotBlank()
                else -> true
            }
            val fragmentParts = (fragmentParam ?: "").split(',')
            val fragmentDelay = fragmentParts.getOrNull(0)?.toIntOrNull() ?: 300
            val FRAGMENT_STRATEGIES = setOf("sni_split", "half", "multi", "micro", "fake", "disorder")
            val fragmentStrategy = fragmentParts.getOrNull(1)
                ?.takeIf { it in FRAGMENT_STRATEGIES } ?: "micro"

            return Result.Ok(ProfileDocument(
                tunnelToken = "vless",
                name = sanitizeWire(name),
                domain = sanitizeWire(wsHost),
                cdnPort = cdnPort,
                vlessUuid = uuid,
                vlessSecurity = security,
                vlessTransport = normalizedTransport,
                vlessWsPath = params["path"] ?: "/",
                cdnIp = sanitizeWire(cdnIp),
                sniFragmentEnabled = fragmentEnabled,
                sniFragmentStrategy = fragmentStrategy,
                sniFragmentDelayMs = fragmentDelay,
                vlessSni = if (sni != wsHost) sanitizeWire(sni) else "",
                vlessRealityPubKey = realityPubKey,
                vlessRealityShortId = params["sid"] ?: "",
                vlessRealityFp = (params["fp"] ?: "chrome").ifBlank { "chrome" }
            ))
        } catch (e: Exception) {
            return Result.Reject("Failed to parse VLESS URI: ${e.message}")
        }
    }

    fun emitVless(doc: ProfileDocument): String {
        require(doc.tunnelToken == "vless") { "not a vless document" }
        val q = buildList {
            add("type=${doc.vlessTransport}")
            add("security=${doc.vlessSecurity}")
            if (doc.vlessWsPath.isNotBlank()) add("path=" + enc(doc.vlessWsPath))
            doc.cdnIp.takeIf { it.isNotEmpty() }?.let { add("cdn=$it") }
            add("cdn-port=${doc.cdnPort}")
            doc.vlessSni.takeIf { it.isNotEmpty() }?.let { add("sni=" + enc(it)) }
            doc.vlessRealityPubKey.takeIf { it.isNotEmpty() }?.let { add("pbk=$it") }
            doc.vlessRealityShortId.takeIf { it.isNotEmpty() }?.let { add("sid=$it") }
            add("fp=${doc.vlessRealityFp.ifBlank { "chrome" }}")
        }.joinToString("&")
        val authority = "${doc.vlessUuid}@${doc.domain}:${doc.cdnPort}"
        val label = enc(doc.name.ifBlank { "VLESS" })
        return "vless://$authority?$q#$label"
    }

    // -------------------------------------------------------------- HYSTERIA2 --

    private const val SCHEME_HY2 = "hysteria2://"

    fun parseHysteria2(uri: String): Result {
        try {
            var rest = uri.removePrefix("hysteria2://").removePrefix("HYSTERIA2://")
            val (beforeHash, hashName) = splitFragment(rest, ::decodePercentOnly)
            val name = hashName?.takeIf { it.isNotBlank() } ?: "Hysteria2"
            rest = beforeHash

            val atIdx = rest.indexOf('@')
            if (atIdx < 0) return Result.Reject("Invalid Hysteria2 URI (missing password)")
            val password = decodePercentOnly(rest.substring(0, atIdx)).ifBlank { "" }
            val hostPortQuery = rest.substring(atIdx + 1)

            val queryIdx = hostPortQuery.indexOf('?')
            val hostPort = if (queryIdx >= 0) hostPortQuery.substring(0, queryIdx) else hostPortQuery
            // Baseline used Android Uri.decode for hysteria2: percent-only, keeps '+'.
            val params = parseQuery(
                if (queryIdx >= 0) hostPortQuery.substring(queryIdx + 1) else "",
                CommunityUriCodec::decodePercentOnly
            )

            val (host, port) = splitHostPort(hostPort)
            if (host.isBlank()) return Result.Reject("Invalid Hysteria2 URI (missing host)")
            if (password.isEmpty()) return Result.Reject("Invalid Hysteria2 URI (missing password)")

            val obfsRaw = params["obfs"] ?: ""
            val obfs = if (obfsRaw == "none") "" else obfsRaw
            if (obfs.isNotEmpty() && obfs != "salamander") {
                return Result.SoftReject("Unsupported Hysteria2 obfs '$obfs' (only salamander)")
            }

            return Result.Ok(ProfileDocument(
                tunnelToken = "hysteria2",
                name = sanitizeWire(name),
                domain = sanitizeWire(host),
                cdnPort = port,
                hy2Password = password,
                hy2Sni = params["sni"] ?: "",
                hy2Insecure = params["insecure"] in listOf("1", "true", "yes"),
                hy2Obfs = obfs,
                hy2ObfsPassword = params["obfs-password"] ?: ""
            ))
        } catch (e: Exception) {
            return Result.Reject("Failed to parse Hysteria2 URI: ${e.message}")
        }
    }

    fun emitHysteria2(doc: ProfileDocument): String {
        require(doc.tunnelToken == "hysteria2") { "not a hysteria2 document" }
        val auth = encodeUserInfo(doc.hy2Password)
        val q = buildList {
            doc.hy2Sni.takeIf { it.isNotEmpty() }?.let { add("sni=" + enc(it)) }
            if (doc.hy2Insecure) add("insecure=1")
            if (doc.hy2Obfs.isNotEmpty()) add("obfs=${doc.hy2Obfs}")
            doc.hy2ObfsPassword.takeIf { it.isNotEmpty() }?.let { add("obfs-password=" + enc(it)) }
        }.joinToString("&")
        val query = if (q.isEmpty()) "" else "?$q"
        val label = enc(doc.name.ifBlank { "Hysteria2" })
        return "$SCHEME_HY2$auth@${doc.domain}:${doc.cdnPort}$query#$label"
    }

    // -------------------------------------------------------------- utilities --

    fun isVless(line: String): Boolean =
        line.startsWith("vless://", ignoreCase = true)

    fun isHysteria2(line: String): Boolean =
        line.startsWith("hysteria2://", ignoreCase = true)

    /** Returns content + decoded fragment (null when absent). */
    internal fun splitFragment(text: String, decoder: (String) -> String): Pair<String, String?> =
        text.indexOf('#').let { idx ->
            if (idx >= 0) text.substring(0, idx) to decoder(text.substring(idx + 1))
            else text to null
        }

    /**
     * Host/port split with IPv6 support:
     * `[::1]:443` → (::1, 443); `host:443` → (host, 443); bare `host` → (host, 443).
     */
    internal fun splitHostPort(hostPort: String): Pair<String, Int> {
        if (hostPort.startsWith('[')) {
            val close = hostPort.indexOf(']')
                .also { if (it < 0) throw IllegalArgumentException("Invalid IPv6 address") }
            val inner = hostPort.substring(1, close)
            val portPart = if (close + 1 < hostPort.length && hostPort[close + 1] == ':') {
                hostPort.substring(close + 2).toIntOrNull()
            } else null
            return inner to (portPart ?: 443)
        }
        val lastColon = hostPort.lastIndexOf(':')
        val firstColon = hostPort.indexOf(':')
        return when {
            lastColon > 0 && firstColon == lastColon ->
                hostPort.substring(0, lastColon) to
                    (hostPort.substring(lastColon + 1).toIntOrNull() ?: 443)
            lastColon > 0 && firstColon != lastColon ->
                // Un-bracketed multi-colon string — treat entirely as host.
                hostPort to 443
            else -> hostPort to 443
        }
    }

    private fun parseQuery(query: String, decoder: (String) -> String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val key = pair.substring(0, eq)
                key to decoder(pair.substring(eq + 1))
            } else null
        }.toMap()
    }

    /** Standard URLDecoder semantics: '%' escapes AND '+' → space (VLESS params). */
    internal fun decodePlusAsSpace(value: String): String =
        URLDecoder.decode(value, Charsets.UTF_8.name())

    /**
     * Percent-only decoding — '+' stays a literal plus.
     * Used for Hysteria2 params and fragments (Android Uri.decode parity).
     */
    internal fun decodePercentOnly(value: String): String =
        Regex("%([0-9A-Fa-f]{2})").replace(value) {
            it.groupValues[1].toInt(16).toChar().toString()
        }

    private fun decodeComponent(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())

    private fun encodeUserInfo(value: String): String = value
        .replace("%", "%25").replace("@", "%40").replace(":", "%3A").replace("/", "%2F")

    private fun enc(value: String): String = value
        .replace("%", "%25").replace(" ", "%20").replace("#", "%23")
        .replace("&", "%26").replace("=", "%3D").replace("?", "%3F")
}
