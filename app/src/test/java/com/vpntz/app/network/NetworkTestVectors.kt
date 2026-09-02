package com.vpntz.app.network

import java.net.InetAddress

/**
 * Shared, hand-assembled wire fixtures for the Phase 3 network-kit tests.
 *
 * These are constructed byte-by-byte per the relevant RFCs so tests do not
 * depend on the production builder for their own verification inputs (which
 * would be circular).
 */
object NetworkTestVectors {

    // ---------- IP/TCP ----------

    /** Build a valid IPv4 TCP data packet (IHL=5, no options) with a payload. */
    fun ipv4Tcp(src: String, dst: String, sport: Int, dport: Int, seq: Int, ack: Int, payload: ByteArray): ByteArray {
        val srcB = InetAddress.getByName(src).address
        val dstB = InetAddress.getByName(dst).address
        val total = 20 + 20 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total ushr 8).toByte(); p[3] = (total and 0xFF).toByte()
        p[8] = 64; p[9] = 6 // TTL, TCP
        System.arraycopy(srcB, 0, p, 12, 4)
        System.arraycopy(dstB, 0, p, 16, 4)
        var o = 20
        p[o] = (sport ushr 8).toByte(); p[o + 1] = (sport and 0xFF).toByte()
        p[o + 2] = (dport ushr 8).toByte(); p[o + 3] = (dport and 0xFF).toByte()
        p[o + 4] = (seq ushr 24).toByte(); p[o + 5] = (seq ushr 16).toByte()
        p[o + 6] = (seq ushr 8).toByte(); p[o + 7] = (seq and 0xFF).toByte()
        p[o + 8] = (ack ushr 24).toByte(); p[o + 9] = (ack ushr 16).toByte()
        p[o + 10] = (ack ushr 8).toByte(); p[o + 11] = (ack and 0xFF).toByte()
        p[o + 12] = 0x50 // data offset 5
        p[o + 13] = (TcpFlags.PSH or TcpFlags.ACK).toByte()
        p[o + 14] = 0xFF.toByte(); p[o + 15] = 0xFF.toByte() // window
        System.arraycopy(payload, 0, p, o + 20, payload.size)
        return p
    }

    fun ipv6Tcp(src: String, dst: String, sport: Int, dport: Int, payload: ByteArray): ByteArray {
        val srcB = InetAddress.getByName(src).address
        val dstB = InetAddress.getByName(dst).address
        val tcpLen = 20 + payload.size
        val total = 40 + tcpLen
        val p = ByteArray(total)
        p[0] = 0x60
        p[4] = ((tcpLen ushr 8) and 0xFF).toByte()
        p[5] = (tcpLen and 0xFF).toByte()
        p[6] = 0x06 // next header TCP
        p[7] = 64.toByte() // hop limit
        System.arraycopy(srcB, 0, p, 8, 16)
        System.arraycopy(dstB, 0, p, 24, 16)
        var o = 40
        p[o] = (sport ushr 8).toByte(); p[o + 1] = (sport and 0xFF).toByte()
        p[o + 2] = (dport ushr 8).toByte(); p[o + 3] = (dport and 0xFF).toByte()
        p[o + 12] = 0x50
        p[o + 13] = (TcpFlags.PSH or TcpFlags.ACK).toByte()
        p[o + 14] = 0xFF.toByte(); p[o + 15] = 0xFF.toByte()
        System.arraycopy(payload, 0, p, o + 20, payload.size)
        return p
    }

    fun ipv4Udp(src: String, dst: String, sport: Int, dport: Int, payload: ByteArray): ByteArray {
        val srcB = InetAddress.getByName(src).address
        val dstB = InetAddress.getByName(dst).address
        val udpLen = 8 + payload.size
        val total = 20 + udpLen
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total ushr 8).toByte(); p[3] = (total and 0xFF).toByte()
        p[9] = 17 // UDP
        System.arraycopy(srcB, 0, p, 12, 4)
        System.arraycopy(dstB, 0, p, 16, 4)
        p[20] = (sport ushr 8).toByte(); p[21] = (sport and 0xFF).toByte()
        p[22] = (dport ushr 8).toByte(); p[23] = (dport and 0xFF).toByte()
        p[24] = (udpLen ushr 8).toByte(); p[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(payload, 0, p, 28, payload.size)
        return p
    }

    // ---------- DNS ----------

    fun dnsQuery(qtype: Int, label: String = "a"): ByteArray {
        val lbl = label.toByteArray(Charsets.US_ASCII)
        val payload = ByteArray(12 + 1 + lbl.size + 1 + 2 + 2)
        payload[2] = 0x01 // RD
        payload[5] = 0x01 // QDCOUNT = 1
        var o = 12
        payload[o++] = lbl.size.toByte()
        System.arraycopy(lbl, 0, payload, o, lbl.size); o += lbl.size
        payload[o++] = 0 // root
        payload[o++] = ((qtype ushr 8) and 0xFF).toByte()
        payload[o++] = (qtype and 0xFF).toByte() // QTYPE
        payload[o++] = 0
        payload[o] = 1 // QCLASS IN
        return payload
    }

    // ---------- TLS ClientHello ----------

    /** Full TLS record (5-byte header + handshake payload). */
    fun tlsClientHello(host: String, withSni: Boolean = true): ByteArray {
        var extTotal: Int
        var sniExt: ByteArray
        if (withSni) {
            val hostB = host.toByteArray(Charsets.US_ASCII)
            val extDataLen = 5 + hostB.size // serverNameListLen(2) + nameType(1) + nameLen(2) + host
            val serverListLen = 3 + hostB.size // nameType(1) + nameLen(2) + host
            sniExt = ByteArray(4 + extDataLen)
            // extension type 0x0000 (SNI) would be zeros already
            sniExt[2] = ((extDataLen ushr 8) and 0xFF).toByte()
            sniExt[3] = (extDataLen and 0xFF).toByte()
            sniExt[4] = ((serverListLen ushr 8) and 0xFF).toByte()
            sniExt[5] = (serverListLen and 0xFF).toByte()
            sniExt[6] = 0 // nameType host_name
            sniExt[7] = ((hostB.size ushr 8) and 0xFF).toByte()
            sniExt[8] = (hostB.size and 0xFF).toByte()
            System.arraycopy(hostB, 0, sniExt, 9, hostB.size)
            extTotal = sniExt.size
        } else {
            sniExt = ByteArray(0)
            extTotal = 0
        }

        val payload = ByteArray(4 + 2 + 32 + 1 + 2 + 2 + 1 + 1 + 2 + extTotal)
        var pos = 0
        payload[pos++] = 0x01 // ClientHello
        val hsLen = payload.size - 4
        payload[pos++] = ((hsLen ushr 16) and 0xFF).toByte()
        payload[pos++] = ((hsLen ushr 8) and 0xFF).toByte()
        payload[pos++] = (hsLen and 0xFF).toByte()
        payload[pos++] = 0x03.toByte(); payload[pos++] = 0x03.toByte() // version
        pos += 32 // random
        payload[pos++] = 0 // session id len
        payload[pos++] = 0; payload[pos++] = 2 // cipher suites len
        payload[pos++] = 0x00; payload[pos++] = 0x13 // one suite
        payload[pos++] = 1 // compression methods len
        payload[pos++] = 0x00 // null
        payload[pos++] = 0; payload[pos++] = extTotal.toByte() // extensions len
        if (withSni) System.arraycopy(sniExt, 0, payload, pos, extTotal)

        val record = ByteArray(5 + payload.size)
        record[0] = 0x16
        record[1] = 0x03; record[2] = 0x03
        val recLen = payload.size
        record[3] = ((recLen ushr 8) and 0xFF).toByte()
        record[4] = (recLen and 0xFF).toByte()
        System.arraycopy(payload, 0, record, 5, payload.size)
        return record
    }

    /** Handshake payload = [tlsClientHello] minus the 5-byte record header. */
    fun clientHelloPayload(record: ByteArray): ByteArray = record.copyOfRange(5, record.size)
}
