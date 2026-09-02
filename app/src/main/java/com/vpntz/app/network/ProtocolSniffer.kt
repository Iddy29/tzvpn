package com.vpntz.app.network

import java.io.InputStream

/**
 * Sniffs a domain name from the opening bytes of a stream: a TLS ClientHello
 * SNI (the primary target of real-world clients) or an HTTP `Host:` header.
 *
 * The TUN interface only exposes IPs, so domain-based routing needs a name to
 * match against the routing table; this sniffer extracts one opportunistically.
 *
 * Pure JVM and deterministic on a given byte prefix. It never throws and never
 * logs; [sniff] returns whatever it can read and is safe to call on a stream
 * that may already be mid-flight.
 */
object ProtocolSniffer {

    private const val MAX_SNIFF_SIZE = 4096

    data class SniffResult(
        val domain: String?,
        val bufferedData: ByteArray,
        val bufferedLength: Int
    )

    /**
     * Read up to [MAX_SNIFF_SIZE] bytes from [clientInput] and try to extract a
     * domain from a TLS ClientHello SNI or HTTP Host header. The [timeoutMs]
     * is accepted for API parity with the legacy signature; the caller's stream
     * owns read blocking.
     *
     * Returns the sniffed domain (if any) along with the buffered bytes that may
     * be prepended to the upstream stream.
     */
    @Suppress("UNUSED_PARAMETER")
    fun sniff(clientInput: InputStream, timeoutMs: Int = 3000): SniffResult {
        val buffer = ByteArray(MAX_SNIFF_SIZE)
        var totalRead = 0

        try {
            val bytesRead = clientInput.read(buffer, 0, buffer.size)
            if (bytesRead > 0) totalRead = bytesRead
        } catch (_: Exception) {
            return SniffResult(null, buffer, 0)
        }

        if (totalRead == 0) {
            return SniffResult(null, buffer, 0)
        }

        val domain = extractTlsSni(buffer, totalRead) ?: extractHttpHost(buffer, totalRead)
        return SniffResult(domain, buffer, totalRead)
    }

    /**
     * Extract an SNI hostname from a TLS ClientHello record.
     *
     * Layout (RFC 8446 §4.1.2):
     *   [0]    ContentType = 0x16 (Handshake)
     *   [1-2]  ProtocolVersion
     *   [3-4]  Record length
     *   [5]    HandshakeType = 0x01 (ClientHello)
     *   [6-8]  Handshake length (3 bytes)
     *   [9-10] ClientVersion
     *   [11..] Random (32 bytes), SessionID, CipherSuites, Compression, Extensions
     *   Inside extensions, type 0x0000 carries the ServerName (SNI) list.
     */
    private fun extractTlsSni(buf: ByteArray, len: Int): String? {
        if (len < 44) return null
        if (buf[0].toInt() and 0xFF != 0x16) return null // not a Handshake record
        if (buf[5].toInt() and 0xFF != 0x01) return null // not a ClientHello

        val recordLength = ((buf[3].toInt() and 0xFF) shl 8) or (buf[4].toInt() and 0xFF)
        if (5 + recordLength > len) return null // incomplete record

        var pos = 43 // after record header (5) + handshake header (4) + version (2) + random (32)

        if (pos >= len) return null
        val sessionIdLen = buf[pos].toInt() and 0xFF
        pos += 1 + sessionIdLen

        if (pos + 2 > len) return null
        val cipherSuitesLen = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
        pos += 2 + cipherSuitesLen

        if (pos >= len) return null
        val compMethodsLen = buf[pos].toInt() and 0xFF
        pos += 1 + compMethodsLen

        if (pos + 2 > len) return null
        val extensionsLen = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
        pos += 2

        val extensionsEnd = pos + extensionsLen
        if (extensionsEnd > len) return null

        while (pos + 4 <= extensionsEnd) {
            val extType = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
            val extLen = ((buf[pos + 2].toInt() and 0xFF) shl 8) or (buf[pos + 3].toInt() and 0xFF)
            pos += 4

            if (extType == 0x0000 && extLen > 0) {
                var sniPos = pos + 2
                if (sniPos + 3 > len) return null
                val nameType = buf[sniPos].toInt() and 0xFF
                val nameLen = ((buf[sniPos + 1].toInt() and 0xFF) shl 8) or (buf[sniPos + 2].toInt() and 0xFF)
                sniPos += 3

                if (nameType == 0x00 && nameLen > 0 && sniPos + nameLen <= len) {
                    return String(buf, sniPos, nameLen, Charsets.US_ASCII).lowercase()
                }
            }

            pos += extLen
        }

        return null
    }

    /**
     * Extract the `Host:` header value from the first bytes of an HTTP request.
     * A request is accepted only if it starts with a known HTTP method token.
     */
    private fun extractHttpHost(buf: ByteArray, len: Int): String? {
        if (len < 16) return null

        val start = String(buf, 0, minOf(len, 10), Charsets.US_ASCII)
        val httpMethods = listOf(
            "GET ", "POST ", "PUT ", "DELETE ", "HEAD ", "OPTIONS ", "PATCH ", "CONNECT "
        )
        if (httpMethods.none { start.startsWith(it) }) return null

        val text = String(buf, 0, len, Charsets.US_ASCII)
        val hostIdx = text.indexOf("\r\nHost:", ignoreCase = true)
        if (hostIdx < 0) return null

        val valueStart = hostIdx + 7 // length of "\r\nHost:"
        val lineEnd = text.indexOf("\r\n", valueStart)
        if (lineEnd < 0) return null

        var host = text.substring(valueStart, lineEnd).trim()

        // Strip an explicit port suffix unless the value is an IPv6 literal.
        val colonIdx = host.lastIndexOf(':')
        if (colonIdx > 0) {
            val afterColon = host.substring(colonIdx + 1)
            if (afterColon.isNotEmpty() && afterColon.all { it.isDigit() }) {
                host = host.substring(0, colonIdx)
            }
        }

        return host.lowercase().ifEmpty { null }
    }
}
