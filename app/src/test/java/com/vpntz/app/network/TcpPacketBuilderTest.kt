package com.vpntz.app.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Build-side unit tests. Wire bytes are validated structurally and via a
 * one's-complement checksum property, and round-tripped through the parser.
 */
class TcpPacketBuilderTest {

    private val v4: Inet4Address = InetAddress.getByName("10.0.0.1") as Inet4Address
    private val v4b: Inet4Address = InetAddress.getByName("10.0.0.2") as Inet4Address
    private val v6: Inet6Address = InetAddress.getByName("2001:db8::1") as Inet6Address
    private val v6b: Inet6Address = InetAddress.getByName("2001:db8::2") as Inet6Address

    @Test
    fun synAckV4IsValid() {
        val pkt = TcpPacketBuilder.buildSynAck(v4, 443, v4b, 5001, 0, 1)!!
        assertEquals(20 + 24, pkt.size) // IP + TCP(with MSS)
        assertEquals(0x45, pkt[0].toInt() and 0xFF)
        assertEquals(Protocol.TCP, pkt[9].toInt() and 0xFF)
        val tcp = IpPacketParser.parse(pkt)!!.tcpHeader!!
        assertEquals(443, tcp.srcPort)
        assertEquals(5001, tcp.dstPort)
        assertTrue(tcp.isSyn)
        assertTrue(tcp.isAck)
        assertEquals(24, tcp.dataOffset) // MSS option present
    }

    @Test
    fun dataPacketRoundTripsThroughParserV4() {
        val payload = "hello".toByteArray()
        val pkt = TcpPacketBuilder.buildDataPacket(v4, 123, v4b, 443, 100, 200, payload)!!
        assertEquals(20 + 20 + payload.size, pkt.size)
        val tcp = IpPacketParser.parse(pkt)!!.tcpHeader!!
        assertArrayEquals(payload, tcp.payload)
        assertEquals(123, tcp.srcPort)
        assertEquals(443, tcp.dstPort)
        assertTrue(tcp.isPsh)
        assertTrue(tcp.isAck)
    }

    @Test
    fun dataPacketRoundTripsThroughParserV6() {
        val payload = "v6".toByteArray()
        val pkt = TcpPacketBuilder.buildDataPacket(v6, 100, v6b, 200, 10, 20, payload)!!
        assertEquals(40 + 20 + payload.size, pkt.size)
        val tcp = IpPacketParser.parse(pkt)!!.tcpHeader!!
        assertArrayEquals(payload, tcp.payload)
    }

    @Test
    fun emptyPayloadDataPacketHasNoMss() {
        val pkt = TcpPacketBuilder.buildDataPacket(v4, 1, v4b, 2, 0, 0, ByteArray(0))!!
        assertEquals(20 + 20, pkt.size)
        assertEquals(20, IpPacketParser.parse(pkt)!!.tcpHeader!!.dataOffset)
    }

    @Test
    fun ipv4HeaderChecksumIsValid() {
        val pkt = TcpPacketBuilder.buildSynAck(v4, 1, v4b, 2, 0, 0)!!
        val ipHeader = pkt.copyOfRange(0, 20)
        // Recomputing the checksum over the header that already carries a checksum
        // field must yield 0 (the one's-complement of a valid header's fold).
        assertEquals(0, onesComplementFold(ipHeader))
    }

    @Test
    fun tcpChecksumIsValidV4() {
        val pkt = TcpPacketBuilder.buildDataPacket(v4, 123, v4b, 443, 100, 200, "abc".toByteArray())!!
        val src = pkt.copyOfRange(12, 16)
        val dst = pkt.copyOfRange(16, 20)
        val seg = pkt.copyOfRange(20, pkt.size)
        assertEquals(0, pseudoHeaderTcpFold(src, dst, seg))
    }

    @Test
    fun tcpChecksumIsValidV6() {
        val pkt = TcpPacketBuilder.buildDataPacket(v6, 123, v6b, 443, 100, 200, "abc".toByteArray())!!
        val src = pkt.copyOfRange(8, 24)
        val dst = pkt.copyOfRange(24, 40)
        val seg = pkt.copyOfRange(40, pkt.size)
        assertEquals(0, pseudoHeaderTcpFoldV6(src, dst, seg))
    }

    @Test
    fun deterministicAcrossCalls() {
        assertArrayEquals(
            TcpPacketBuilder.buildAck(v4, 1, v4b, 2, 3, 4)!!,
            TcpPacketBuilder.buildAck(v4, 1, v4b, 2, 3, 4)!!
        )
    }

    @Test
    fun mismatchedFamiliesReturnNull() {
        assertNull(TcpPacketBuilder.buildSynAck(v4, 1, v6, 2, 0, 0))
        assertNull(TcpPacketBuilder.buildDataPacket(v6, 1, v4, 2, 0, 0, ByteArray(0)))
    }

    @Test
    fun finPacketHasFinAndAck() {
        val pkt = TcpPacketBuilder.buildFinAck(v4, 1, v4b, 2, 5, 6)!!
        val tcp = IpPacketParser.parse(pkt)!!.tcpHeader!!
        assertTrue(tcp.isFin)
        assertTrue(tcp.isAck)
    }

    @Test
    fun rstPacketHasRstAndAck() {
        val pkt = TcpPacketBuilder.buildRst(v4, 1, v4b, 2, 5, 6)!!
        val tcp = IpPacketParser.parse(pkt)!!.tcpHeader!!
        assertTrue(tcp.isRst)
        assertTrue(tcp.isAck)
    }

    // ---- checksum helper: sum 16-bit words, fold, complement ----------------------------------

    private fun onesComplementFold(bytes: ByteArray): Int {
        var sum = 0L
        var i = 0
        while (i < bytes.size) {
            val word = if (i + 1 < bytes.size) {
                ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            } else {
                (bytes[i].toInt() and 0xFF) shl 8
            }
            sum += word
            i += 2
        }
        while (sum > 0xFFFF) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun pseudoHeaderTcpFold(src: ByteArray, dst: ByteArray, seg: ByteArray): Int {
        val pseudo = ByteArray(12 + seg.size)
        System.arraycopy(src, 0, pseudo, 0, 4)
        System.arraycopy(dst, 0, pseudo, 4, 4)
        pseudo[8] = 0
        pseudo[9] = Protocol.TCP.toByte()
        pseudo[10] = ((seg.size shr 8) and 0xFF).toByte()
        pseudo[11] = (seg.size and 0xFF).toByte()
        System.arraycopy(seg, 0, pseudo, 12, seg.size)
        return onesComplementFold(pseudo)
    }

    private fun pseudoHeaderTcpFoldV6(src: ByteArray, dst: ByteArray, seg: ByteArray): Int {
        // RFC 8200 pseudo-header: src(16) + dst(16) + length(4) + zero(3) + next(1) = 40 bytes
        val pseudo = ByteArray(40 + seg.size)
        System.arraycopy(src, 0, pseudo, 0, 16)
        System.arraycopy(dst, 0, pseudo, 16, 16)
        pseudo[32] = ((seg.size ushr 24) and 0xFF).toByte()
        pseudo[33] = ((seg.size ushr 16) and 0xFF).toByte()
        pseudo[34] = ((seg.size ushr 8) and 0xFF).toByte()
        pseudo[35] = (seg.size and 0xFF).toByte()
        // bytes 36..38 are zero
        pseudo[39] = Protocol.TCP.toByte()
        System.arraycopy(seg, 0, pseudo, 40, seg.size)
        return onesComplementFold(pseudo)
    }
}
