package com.vpntz.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpPacketParserTest {

    @Test
    fun parsesIpv4Tcp() {
        val pkt = NetworkTestVectors.ipv4Tcp("10.0.0.1", "10.0.0.2", 1234, 443, 0x01020304, 0x05060708, "hi".toByteArray())
        val packet = IpPacketParser.parse(pkt)!!
        assertEquals(4, packet.version)
        assertTrue(packet.isTcp)
        assertEquals("10.0.0.1", packet.srcAddress.hostAddress)
        assertEquals("10.0.0.2", packet.dstAddress.hostAddress)
        val tcp = packet.tcpHeader!!
        assertEquals(1234, tcp.srcPort)
        assertEquals(443, tcp.dstPort)
        assertEquals(0x01020304L, tcp.seqNum)
        assertEquals(0x05060708L, tcp.ackNum)
        assertTrue(tcp.isPsh)
        assertTrue(tcp.isAck)
        assertEquals("hi", String(tcp.payload, Charsets.US_ASCII))
    }

    @Test
    fun parsesIpv6Tcp() {
        val pkt = NetworkTestVectors.ipv6Tcp("2001:db8::1", "2001:db8::2", 1111, 2222, "xy".toByteArray())
        val packet = IpPacketParser.parse(pkt)!!
        assertEquals(6, packet.version)
        assertTrue(packet.isTcp)
        assertEquals(1111, packet.tcpHeader!!.srcPort)
        assertEquals(2222, packet.tcpHeader!!.dstPort)
        assertEquals(java.net.InetAddress.getByName("2001:db8::1").hostAddress, packet.srcAddress.hostAddress)
    }

    @Test
    fun parsesIpv4UdpWithoutTcpHeader() {
        val pkt = NetworkTestVectors.ipv4Udp("10.0.0.1", "10.0.0.2", 5353, 53, ByteArray(0))
        val packet = IpPacketParser.parse(pkt)!!
        assertTrue(packet.isUdp)
        assertFalse(packet.isTcp)
        assertNull(packet.tcpHeader)
    }

    @Test
    fun extractConnectionKeyIsNullForNonTcp() {
        val udp = IpPacketParser.parse(NetworkTestVectors.ipv4Udp("10.0.0.1", "10.0.0.2", 1, 2, ByteArray(0)))!!
        assertNull(IpPacketParser.extractConnectionKey(udp))
    }

    @Test
    fun extractConnectionKeyUsesFiveTuple() {
        val pkt = NetworkTestVectors.ipv4Tcp("10.0.0.1", "10.0.0.2", 1234, 443, 1, 2, ByteArray(0))
        val key = IpPacketParser.extractConnectionKey(IpPacketParser.parse(pkt)!!)!!
        assertEquals("10.0.0.1", key.srcAddress.hostAddress)
        assertEquals(1234, key.srcPort)
        assertEquals("10.0.0.2", key.dstAddress.hostAddress)
        assertEquals(443, key.dstPort)
        assertEquals(Protocol.TCP, key.protocol)
    }

    @Test
    fun connectionKeyReverseSwapsEndpoints() {
        val pkt = NetworkTestVectors.ipv4Tcp("10.0.0.1", "10.0.0.2", 1234, 443, 1, 2, ByteArray(0))
        val key = IpPacketParser.extractConnectionKey(IpPacketParser.parse(pkt)!!)!!
        val rev = key.reverse()
        assertEquals("10.0.0.2", rev.srcAddress.hostAddress)
        assertEquals(443, rev.srcPort)
        assertEquals("10.0.0.1", rev.dstAddress.hostAddress)
        assertEquals(1234, rev.dstPort)
        assertEquals(Protocol.TCP, rev.protocol)
    }

    // ---------- malformed / boundary ----------

    @Test
    fun returnsNullForEmpty() {
        assertNull(IpPacketParser.parse(ByteArray(0)))
    }

    @Test
    fun returnsNullForTruncatedIpv4() {
        assertNull(IpPacketParser.parse(ByteArray(19))) // < 20-byte header
    }

    @Test
    fun returnsNullWhenIhlExceedsData() {
        // version 4 with IHL=15 (60 bytes) but only 20 bytes present
        val pkt = ByteArray(20)
        pkt[0] = 0x4F
        assertNull(IpPacketParser.parse(pkt))
    }

    @Test
    fun returnsNullForUnsupportedVersion() {
        val pkt = NetworkTestVectors.ipv4Tcp("10.0.0.1", "10.0.0.2", 1, 2, 0, 0, ByteArray(0))
        pkt[0] = 0x45 // keep; test a non 4/6 nibble
        pkt[0] = ((pkt[0].toInt() and 0x0F) or 0x70).toByte() // version 7
        assertNull(IpPacketParser.parse(pkt))
    }

    @Test
    fun returnsNullForTruncatedIpv6() {
        assertNull(IpPacketParser.parse(ByteArray(39)))
    }

    @Test
    fun tcpPayloadExcludesOptions() {
        // build a SYN-ACK-like packet with a 4-byte option and confirm payload empty
        val pkt = NetworkTestVectors.ipv4Tcp("10.0.0.1", "10.0.0.2", 1, 2, 0, 0, ByteArray(0))
        // make data offset 6 (options) without changing length: craft by hand
        val srcB = java.net.InetAddress.getByName("10.0.0.1").address
        val dstB = java.net.InetAddress.getByName("10.0.0.2").address
        val total = 20 + 24 // 4 bytes of options
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total ushr 8).toByte(); p[3] = (total and 0xFF).toByte()
        p[9] = 6
        System.arraycopy(srcB, 0, p, 12, 4)
        System.arraycopy(dstB, 0, p, 16, 4)
        val o = 20
        p[o + 12] = 0x60 // data offset 6
        p[o + 13] = (TcpFlags.SYN or TcpFlags.ACK).toByte()
        p[o + 20] = 0x02.toByte(); p[o + 21] = 0x04.toByte(); p[o + 22] = 0x05.toByte(); p[o + 23] = 0xB4.toByte() // MSS option
        val tcp = IpPacketParser.parse(p)!!.tcpHeader!!
        assertEquals(24, tcp.dataOffset)
        assertEquals(0, tcp.payload.size)
    }
}
