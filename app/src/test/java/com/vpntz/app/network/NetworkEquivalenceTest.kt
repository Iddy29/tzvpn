package com.vpntz.app.network

import com.vpntz.app.network.IpPacketParser as NewIpParser
import com.vpntz.app.network.TcpPacketBuilder as NewTcpBuilder
import com.vpntz.app.network.DnsUtils as NewDns
import com.vpntz.app.network.ProtocolSniffer as NewSniffer
import com.vpntz.app.tunnel.IpPacketParser as LegacyIpParser
import com.vpntz.app.tunnel.TcpPacketBuilder as LegacyTcpBuilder
import com.vpntz.app.tunnel.DnsUtils as LegacyDns
import com.vpntz.app.tunnel.ProtocolSniffer as LegacySniffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Behavior-preservation evidence for Phase 3: the re-authored `network` kit
 * must be byte-for-byte equivalent to the legacy `tunnel` implementation on a
 * representative input matrix. This pins the rewrite to the frozen baseline
 * before callers are switched over.
 */
class NetworkEquivalenceTest {

    private val ipv4: Inet4Address = InetAddress.getByName("10.0.0.1") as Inet4Address
    private val ipv4b: Inet4Address = InetAddress.getByName("10.0.0.2") as Inet4Address
    private val ipv6: Inet6Address = InetAddress.getByName("2001:db8::1") as Inet6Address
    private val ipv6b: Inet6Address = InetAddress.getByName("2001:db8::2") as Inet6Address

    // ---------- TcpPacketBuilder ----------

    @Test
    fun buildersAreByteIdenticalV4() {
        val packets = mutableListOf<ByteArray?>()
        packets += LegacyTcpBuilder.buildSynAck(ipv4, 12345, ipv4b, 443, 1000L, 0L)
        packets += LegacyTcpBuilder.buildAck(ipv4, 12345, ipv4b, 443, 1001L, 2000L)
        packets += LegacyTcpBuilder.buildFinAck(ipv4, 12345, ipv4b, 443, 1002L, 2001L)
        packets += LegacyTcpBuilder.buildRst(ipv4, 12345, ipv4b, 443, 1003L, 2002L)
        packets += LegacyTcpBuilder.buildDataPacket(ipv4, 12345, ipv4b, 443, 1004L, 2003L, "hello world".toByteArray())

        val rebuilt = mutableListOf<ByteArray?>()
        rebuilt += NewTcpBuilder.buildSynAck(ipv4, 12345, ipv4b, 443, 1000L, 0L)
        rebuilt += NewTcpBuilder.buildAck(ipv4, 12345, ipv4b, 443, 1001L, 2000L)
        rebuilt += NewTcpBuilder.buildFinAck(ipv4, 12345, ipv4b, 443, 1002L, 2001L)
        rebuilt += NewTcpBuilder.buildRst(ipv4, 12345, ipv4b, 443, 1003L, 2002L)
        rebuilt += NewTcpBuilder.buildDataPacket(ipv4, 12345, ipv4b, 443, 1004L, 2003L, "hello world".toByteArray())

        assertEquals("same number of packets", packets.size, rebuilt.size)
        packets.zip(rebuilt).forEachIndexed { i, (legacy, replacement) ->
            assertArrayEquals("v4 packet #$i", legacy, replacement)
        }
    }

    @Test
    fun buildersAreByteIdenticalV6() {
        val packets = mutableListOf<ByteArray?>()
        packets += LegacyTcpBuilder.buildSynAck(ipv6, 60000, ipv6b, 8080, 1L, 2L)
        packets += LegacyTcpBuilder.buildAck(ipv6, 60000, ipv6b, 8080, 3L, 4L)
        packets += LegacyTcpBuilder.buildFinAck(ipv6, 60000, ipv6b, 8080, 5L, 6L)
        packets += LegacyTcpBuilder.buildRst(ipv6, 60000, ipv6b, 8080, 7L, 8L)
        packets += LegacyTcpBuilder.buildDataPacket(ipv6, 60000, ipv6b, 8080, 9L, 10L, "payload".toByteArray())

        val rebuilt = mutableListOf<ByteArray?>()
        rebuilt += NewTcpBuilder.buildSynAck(ipv6, 60000, ipv6b, 8080, 1L, 2L)
        rebuilt += NewTcpBuilder.buildAck(ipv6, 60000, ipv6b, 8080, 3L, 4L)
        rebuilt += NewTcpBuilder.buildFinAck(ipv6, 60000, ipv6b, 8080, 5L, 6L)
        rebuilt += NewTcpBuilder.buildRst(ipv6, 60000, ipv6b, 8080, 7L, 8L)
        rebuilt += NewTcpBuilder.buildDataPacket(ipv6, 60000, ipv6b, 8080, 9L, 10L, "payload".toByteArray())

        assertEquals("same number of packets", packets.size, rebuilt.size)
        packets.zip(rebuilt).forEachIndexed { i, (legacy, replacement) ->
            assertArrayEquals("v6 packet #$i", legacy, replacement)
        }
    }

    @Test
    fun buildDataPacketEmptyPayloadMatches() {
        val legacy = LegacyTcpBuilder.buildDataPacket(ipv4, 1, ipv4b, 2, 0, 0, ByteArray(0))
        val rebuilt = NewTcpBuilder.buildDataPacket(ipv4, 1, ipv4b, 2, 0, 0, ByteArray(0))
        assertArrayEquals(legacy, rebuilt)
    }

    @Test
    fun mismatchedAddressFamiliesReturnNullInBoth() {
        assertNull(LegacyTcpBuilder.buildSynAck(ipv4, 1, ipv6, 2, 0, 0))
        assertNull(NewTcpBuilder.buildSynAck(ipv4, 1, ipv6, 2, 0, 0))
    }

    // ---------- IpPacketParser ----------

    @Test
    fun parserMatchesLegacyOnIPv4Tcp() {
        val packet = LegacyTcpBuilder.buildDataPacket(ipv4, 12345, ipv4b, 443, 1004L, 2003L, "payload".toByteArray())!!
        val legacy = LegacyIpParser.parse(packet)
        val rebuilt = NewIpParser.parse(packet)
        assertParsersEqual(packet, legacy, rebuilt)
    }

    @Test
    fun parserMatchesLegacyOnIPv4SynAck() {
        val packet = LegacyTcpBuilder.buildSynAck(ipv4, 443, ipv4b, 5001, 0L, 1000L)!!
        assertParsersEqual(packet, LegacyIpParser.parse(packet), NewIpParser.parse(packet))
    }

    @Test
    fun parserMatchesLegacyOnIPv6Tcp() {
        val packet = LegacyTcpBuilder.buildDataPacket(ipv6, 1111, ipv6b, 2222, 3L, 4L, "abc".toByteArray())!!
        assertParsersEqual(packet, LegacyIpParser.parse(packet), NewIpParser.parse(packet))
    }

    @Test
    fun parserMatchesLegacyOnIPv4Udp() {
        // Hand-built IPv4 UDP datagram (no TCP header).
        val packet = buildIPv4Udp("10.0.0.1", "10.0.0.2", 53, 5353, "dns".toByteArray())
        assertParsersEqual(packet, LegacyIpParser.parse(packet), NewIpParser.parse(packet))
    }

    @Test
    fun parserMatchesLegacyExtractConnectionKey() {
        val packet = LegacyTcpBuilder.buildDataPacket(ipv4, 12345, ipv4b, 443, 1004L, 2003L, "x".toByteArray())!!
        val legacy = LegacyIpParser.parse(packet)!!
        val rebuilt = NewIpParser.parse(packet)!!
        val lk = LegacyIpParser.extractConnectionKey(legacy)!!
        val nk = NewIpParser.extractConnectionKey(rebuilt)!!
        assertEquals(lk.srcAddress.hostAddress, nk.srcAddress.hostAddress)
        assertEquals(lk.srcPort, nk.srcPort)
        assertEquals(lk.dstPort, nk.dstPort)
        assertEquals(lk.protocol, nk.protocol)
    }

    // ---------- DnsUtils ----------

    @Test
    fun dnsHelpersMatchLegacy() {
        val aaaa = buildDnsQuery(qtype = 28)
        val a = buildDnsQuery(qtype = 1)

        assertEquals(LegacyDns.isAAAAQuery(aaaa), NewDns.isAAAAQuery(aaaa))
        assertEquals(LegacyDns.isAAAAQuery(a), NewDns.isAAAAQuery(a))

        val legacyResp = LegacyDns.buildAAAANoDataResponse(aaaa)!!
        val newResp = NewDns.buildAAAANoDataResponse(aaaa)!!
        assertArrayEquals(legacyResp, newResp)
        assertEquals("QR bit set", 0x80, newResp[2].toInt() and 0x80)
        assertEquals("no answers", 0, ((newResp[6].toInt() and 0xFF) shl 8) or (newResp[7].toInt() and 0xFF))
    }

    // ---------- ProtocolSniffer ----------

    @Test
    fun snifferMatchesLegacyOnTlsSni() {
        val ch = buildTlsClientHello("example.com")
        val stream = ByteArrayInputStream(ch)
        val legacy = LegacySniffer.sniff(stream)
        stream.reset()
        val rebuilt = NewSniffer.sniff(stream)
        assertEquals(legacy.domain, rebuilt.domain)
        assertEquals(legacy.bufferedLength, rebuilt.bufferedLength)
        assertArrayEquals(legacy.bufferedData.copyOf(legacy.bufferedLength), rebuilt.bufferedData.copyOf(rebuilt.bufferedLength))
        assertEquals("example.com", rebuilt.domain)
    }

    @Test
    fun snifferMatchesLegacyOnHttpHost() {
        val http = "GET / HTTP/1.1\r\nHost: cdn.example.com:8080\r\n\r\n".toByteArray()
        val legacy = LegacySniffer.sniff(ByteArrayInputStream(http))
        val rebuilt = NewSniffer.sniff(ByteArrayInputStream(http))
        assertEquals(legacy.domain, rebuilt.domain)
        assertEquals("cdn.example.com", rebuilt.domain)
    }

    // ---------- helpers ----------

    private fun assertParsersEqual(raw: ByteArray, legacy: com.vpntz.app.tunnel.IpPacket?, rebuilt: IpPacket?) {
        assertEquals("version", legacy?.version, rebuilt?.version)
        assertEquals("protocol", legacy?.protocol, rebuilt?.protocol)
        assertEquals("src", legacy?.srcAddress?.hostAddress, rebuilt?.srcAddress?.hostAddress)
        assertEquals("dst", legacy?.dstAddress?.hostAddress, rebuilt?.dstAddress?.hostAddress)
        assertEquals("isTcp", legacy?.isTcp, rebuilt?.isTcp)
        assertEquals("isUdp", legacy?.isUdp, rebuilt?.isUdp)
        val lt = legacy?.tcpHeader
        val nt = rebuilt?.tcpHeader
        assertEquals("tcp present", lt != null, nt != null)
        if (lt != null && nt != null) {
            assertEquals("srcPort", lt.srcPort, nt.srcPort)
            assertEquals("dstPort", lt.dstPort, nt.dstPort)
            assertEquals("seq", lt.seqNum, nt.seqNum)
            assertEquals("ack", lt.ackNum, nt.ackNum)
            assertEquals("flags", lt.flags, nt.flags)
            assertEquals("win", lt.windowSize, nt.windowSize)
            assertArrayEquals("payload", lt.payload, nt.payload)
        }
        assertEquals("rawData matches", raw.size, rebuilt?.rawData?.size)
    }

    private fun buildDnsQuery(qtype: Int): ByteArray {
        // 12-byte header + 1 label "a" + root + QTYPE + QCLASS
        val b = ByteArray(12 + 1 + 1 + 1 + 2 + 2)
        b[2] = 0x01 // RD
        b[5] = 0x01 // QDCOUNT = 1
        b[12] = 1 // label len
        b[13] = 'a'.code.toByte()
        b[14] = 0 // root
        b[15] = 0; b[16] = (qtype and 0xFF).toByte() // QTYPE
        b[17] = 0; b[18] = 1 // QCLASS IN
        return b
    }

    private fun buildIPv4Udp(src: String, dst: String, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val srcB = InetAddress.getByName(src).address
        val dstB = InetAddress.getByName(dst).address
        val udpLen = 8 + payload.size
        val total = 20 + udpLen
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total ushr 8).toByte(); p[3] = (total and 0xFF).toByte()
        p[8] = 64; p[9] = 17 // TTL, UDP
        System.arraycopy(srcB, 0, p, 12, 4)
        System.arraycopy(dstB, 0, p, 16, 4)
        p[20] = (srcPort ushr 8).toByte(); p[21] = (srcPort and 0xFF).toByte()
        p[22] = (dstPort ushr 8).toByte(); p[23] = (dstPort and 0xFF).toByte()
        p[24] = (udpLen ushr 8).toByte(); p[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(payload, 0, p, 28, payload.size)
        return p
    }

    private fun buildTlsClientHello(host: String): ByteArray {
        val hostB = host.toByteArray(Charsets.US_ASCII)
        val extDataLen = 5 + hostB.size
        val serverListLen = 3 + hostB.size
        val sniExt = ByteArray(4 + extDataLen)
        sniExt[2] = ((extDataLen ushr 8) and 0xFF).toByte()
        sniExt[3] = (extDataLen and 0xFF).toByte()
        sniExt[4] = ((serverListLen ushr 8) and 0xFF).toByte()
        sniExt[5] = (serverListLen and 0xFF).toByte()
        sniExt[6] = 0 // nameType host_name
        sniExt[7] = ((hostB.size ushr 8) and 0xFF).toByte()
        sniExt[8] = (hostB.size and 0xFF).toByte()
        System.arraycopy(hostB, 0, sniExt, 9, hostB.size)
        val extTotal = sniExt.size

        // Build the ClientHello handshake body (record payload) with a single SNI ext.
        val payload = ByteArray(4 + 2 + 32 + 1 + 2 + 2 + 1 + 1 + 2 + extTotal)
        var pos = 0
        payload[pos++] = 0x01 // handshake type ClientHello
        val handshakeLen = payload.size - 4
        payload[pos++] = ((handshakeLen ushr 16) and 0xFF).toByte()
        payload[pos++] = ((handshakeLen ushr 8) and 0xFF).toByte()
        payload[pos++] = (handshakeLen and 0xFF).toByte()
        // version 0x0303
        payload[pos++] = 0x03.toByte(); payload[pos++] = 0x03.toByte()
        pos += 32 // random (zeros)
        payload[pos++] = 0 // sessionId len
        payload[pos++] = 0x00; payload[pos++] = 0x02 // cipher suites len = 2
        payload[pos++] = 0x00; payload[pos++] = 0x13 // one suite
        payload[pos++] = 0x01 // compression methods len
        payload[pos++] = 0x00 // null compression
        payload[pos++] = 0x00; payload[pos++] = extTotal.toByte() // extensions len
        System.arraycopy(sniExt, 0, payload, pos, extTotal)

        val record = ByteArray(5 + payload.size)
        record[0] = 0x16
        record[1] = 0x03; record[2] = 0x03
        val recLen = payload.size
        record[3] = ((recLen ushr 8) and 0xFF).toByte()
        record[4] = (recLen and 0xFF).toByte()
        System.arraycopy(payload, 0, record, 5, payload.size)
        return record
    }
}
