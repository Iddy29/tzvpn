package com.vpntz.app.network

/**
 * DNS (RFC 1035) helpers used by the SOCKS bridges to short-circuit AAAA
 * lookups: many tunnel resolvers only speak IPv4, so answering an AAAA query
 * with an empty ANCOUNT lets the client fall back to A.
 *
 * Pure JVM and deterministic. Unlike the legacy importer this kit never throws
 * on malformed input — undersized or truncated queries simply return false/null.
 */
object DnsUtils {

    private const val QTYPE_AAAA: Int = 28
    private const val DNS_HEADER_LEN = 12

    /**
     * True if an encoded DNS query asks for AAAA (IPv6). A DNS header is
     * [DNS_HEADER_LEN] bytes followed by the QNAME (label length bytes) and a
     * QTYPE byte pair.
     */
    fun isAAAAQuery(payload: ByteArray): Boolean {
        if (payload.size < 14) return false // too short to hold a query header + label

        // Skip the 12-byte header, then walk the QNAME labels.
        var offset = DNS_HEADER_LEN
        while (offset < payload.size) {
            val labelLen = payload[offset].toInt() and 0xFF
            if (labelLen == 0) {
                offset++ // root label terminator
                break
            }
            if (offset + 1 + labelLen > payload.size) return false // truncated label
            offset += 1 + labelLen
        }

        // QTYPE is the next 2 bytes.
        if (offset < 0 || offset + 2 > payload.size) return false
        val qtype = ((payload[offset].toInt() and 0xFF) shl 8) or (payload[offset + 1].toInt() and 0xFF)
        return qtype == QTYPE_AAAA
    }

    /**
     * Build a synthetic NODATA response for an AAAA query by copying the query
     * header, flipping QR to response, preserving opcode/RD, setting RA, and
     * zeroing ANCOUNT/NSCOUNT/ARCOUNT. Returns null if the query is too short
     * to be a valid DNS message.
     */
    fun buildAAAANoDataResponse(query: ByteArray): ByteArray? {
        if (query.size < DNS_HEADER_LEN) return null

        val response = query.copyOf()
        // Byte 2: QR=1 (response), keep opcode; clear AA/TC/RD then set RD.
        response[2] = ((query[2].toInt() and 0xFF) or 0x80).toByte()
        // Byte 3: keep the upper flags nibble (RD/RD-ish), set RA. RCODE stays 0.
        response[3] = ((query[3].toInt() and 0xF0) or 0x80).toByte()

        // Zero ANCOUNT, NSCOUNT, ARCOUNT; QDCOUNT is inherited from the query.
        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0

        return response
    }

    /**
     * Normalize a domain to lowercase and strip a trailing dot, or null if it
     * is blank. Mirrors the casing used when matching SNI/Host against the
     * routing table.
     */
    fun normalizeDomain(host: String): String? =
        host.trim().trimEnd('.').lowercase().ifEmpty { null }
}
