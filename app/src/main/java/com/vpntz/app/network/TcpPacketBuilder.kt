package com.vpntz.app.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Builds TCP/IP segments for injection into the TUN device. Pure JVM and
 * deterministic: for the same inputs it always emits the same bytes, including
 * the internet (RFC 791) and transport (RFC 793) checksums.
 */
object TcpPacketBuilder {

    private const val TCP_HEADER_LEN = 20
    private const val MSS_HEADER_LEN = 24
    private const val IPV4_HDR_LEN = 20
    private const val IPV6_HDR_LEN = 40
    private const val MSS_VALUE = 1460

    /**
     * SYN-ACK to complete the handshake, with an MSS option.
     */
    fun buildSynAck(
        srcAddr: InetAddress,
        srcPort: Int,
        dstAddr: InetAddress,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long
    ): ByteArray? {
        return build(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum,
            TcpFlags.SYN or TcpFlags.ACK, ByteArray(0), includeMss = true)
    }

    /**
     * Data segment with PSH+ACK.
     */
    fun buildDataPacket(
        srcAddr: InetAddress,
        srcPort: Int,
        dstAddr: InetAddress,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        payload: ByteArray
    ): ByteArray? {
        return build(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum,
            TcpFlags.PSH or TcpFlags.ACK, payload, includeMss = false)
    }

    /**
     * FIN-ACK to close the connection.
     */
    fun buildFinAck(
        srcAddr: InetAddress,
        srcPort: Int,
        dstAddr: InetAddress,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long
    ): ByteArray? {
        return build(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum,
            TcpFlags.FIN or TcpFlags.ACK, ByteArray(0), includeMss = false)
    }

    /**
     * Bare ACK (no payload).
     */
    fun buildAck(
        srcAddr: InetAddress,
        srcPort: Int,
        dstAddr: InetAddress,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long
    ): ByteArray? {
        return build(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum,
            TcpFlags.ACK, ByteArray(0), includeMss = false)
    }

    /**
     * RST+ACK to reset the connection.
     */
    fun buildRst(
        srcAddr: InetAddress,
        srcPort: Int,
        dstAddr: InetAddress,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long
    ): ByteArray? {
        return build(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum,
            TcpFlags.RST or TcpFlags.ACK, ByteArray(0), includeMss = false)
    }

    private fun build(
        srcAddr: InetAddress,
        srcPort: Int,
        dstAddr: InetAddress,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        flags: Int,
        payload: ByteArray,
        includeMss: Boolean
    ): ByteArray? {
        return when {
            srcAddr is Inet4Address && dstAddr is Inet4Address ->
                buildV4(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum, flags, payload, includeMss)
            srcAddr is Inet6Address && dstAddr is Inet6Address ->
                buildV6(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum, flags, payload, includeMss)
            else -> null
        }
    }

    private fun buildV4(
        srcAddr: Inet4Address,
        srcPort: Int,
        dstAddr: Inet4Address,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        flags: Int,
        payload: ByteArray,
        includeMss: Boolean
    ): ByteArray {
        val tcpHeaderLen = if (includeMss) MSS_HEADER_LEN else TCP_HEADER_LEN
        val totalLen = IPV4_HDR_LEN + tcpHeaderLen + payload.size
        val buffer = ByteBuffer.allocate(totalLen)

        // IPv4 header
        buffer.put(0x45.toByte())
        buffer.put(0x00.toByte())
        buffer.putShort(totalLen.toShort())
        buffer.putShort(0x0000.toShort())
        buffer.putShort(0x4000.toShort()) // Don't Fragment
        buffer.put(64.toByte()) // TTL
        buffer.put(Protocol.TCP.toByte())
        buffer.putShort(0x0000.toShort()) // header checksum placeholder
        buffer.put(srcAddr.address)
        buffer.put(dstAddr.address)

        val ipHeader = buffer.array().copyOfRange(0, IPV4_HDR_LEN)
        buffer.putShort(10, checksum(ipHeader).toShort())

        val tcpStart = buffer.position()
        writeTcpHeader(buffer, srcPort, dstPort, seqNum, ackNum, tcpHeaderLen, flags, includeMss)
        if (payload.isNotEmpty()) buffer.put(payload)

        val tcpSegment = buffer.array().copyOfRange(tcpStart, totalLen)
        buffer.putShort(tcpStart + 16, tcpChecksumV4(srcAddr, dstAddr, tcpSegment).toShort())

        return buffer.array()
    }

    private fun buildV6(
        srcAddr: Inet6Address,
        srcPort: Int,
        dstAddr: Inet6Address,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        flags: Int,
        payload: ByteArray,
        includeMss: Boolean
    ): ByteArray {
        val tcpHeaderLen = if (includeMss) MSS_HEADER_LEN else TCP_HEADER_LEN
        val tcpLen = tcpHeaderLen + payload.size
        val totalLen = IPV6_HDR_LEN + tcpLen
        val buffer = ByteBuffer.allocate(totalLen)

        // IPv6 header
        buffer.put(0x60.toByte())
        buffer.put(0x00.toByte())
        buffer.putShort(0x0000.toShort()) // flow label
        buffer.putShort(tcpLen.toShort()) // payload length
        buffer.put(Protocol.TCP.toByte()) // next header
        buffer.put(64.toByte()) // hop limit
        buffer.put(srcAddr.address)
        buffer.put(dstAddr.address)

        val tcpStart = buffer.position()
        writeTcpHeader(buffer, srcPort, dstPort, seqNum, ackNum, tcpHeaderLen, flags, includeMss)
        if (payload.isNotEmpty()) buffer.put(payload)

        val tcpSegment = buffer.array().copyOfRange(tcpStart, totalLen)
        buffer.putShort(tcpStart + 16, tcpChecksumV6(srcAddr, dstAddr, tcpSegment).toShort())

        return buffer.array()
    }

    private fun writeTcpHeader(
        buffer: ByteBuffer,
        srcPort: Int,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        tcpHeaderLen: Int,
        flags: Int,
        includeMss: Boolean
    ) {
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putInt(seqNum.toInt())
        buffer.putInt(ackNum.toInt())
        buffer.put(((tcpHeaderLen / 4) shl 4).toByte()) // data offset
        buffer.put(flags.toByte())
        buffer.putShort(0xFFFF.toShort()) // window
        buffer.putShort(0x0000.toShort()) // checksum placeholder
        buffer.putShort(0x0000.toShort()) // urgent pointer
        if (includeMss) {
            buffer.put(0x02.toByte()) // kind: MSS
            buffer.put(0x04.toByte()) // length: 4
            buffer.putShort(MSS_VALUE.toShort())
        }
    }

    /**
     * Internet checksum (RFC 1071) over an even-length header. The checksum
     * field is treated as zero by callers that leave it unset.
     */
    internal fun checksum(header: ByteArray): Int {
        var sum = 0L
        var i = 0
        while (i < header.size) {
            val word = if (i + 1 < header.size) {
                ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
            } else {
                (header[i].toInt() and 0xFF) shl 8
            }
            sum += word
            i += 2
        }
        return fold(sum)
    }

    /**
     * TCP checksum for IPv4: pseudo-header (src, dst, protocol, length) plus
     * the segment. The segment is supplied with the checksum field already
     * zeroed (the builder writes the placeholder before computing).
     */
    private fun tcpChecksumV4(
        srcAddr: Inet4Address,
        dstAddr: Inet4Address,
        tcpSegment: ByteArray
    ): Int {
        var sum = 0L
        val srcBytes = srcAddr.address
        val dstBytes = dstAddr.address
        sum += ((srcBytes[0].toInt() and 0xFF) shl 8) or (srcBytes[1].toInt() and 0xFF)
        sum += ((srcBytes[2].toInt() and 0xFF) shl 8) or (srcBytes[3].toInt() and 0xFF)
        sum += ((dstBytes[0].toInt() and 0xFF) shl 8) or (dstBytes[1].toInt() and 0xFF)
        sum += ((dstBytes[2].toInt() and 0xFF) shl 8) or (dstBytes[3].toInt() and 0xFF)
        sum += Protocol.TCP
        sum += tcpSegment.size
        sum += wordsWithSkippedChecksum(tcpSegment)
        return fold(sum)
    }

    /**
     * TCP checksum for IPv6: pseudo-header (src 16B, dst 16B, length 32-bit,
     * next header) plus the segment.
     */
    private fun tcpChecksumV6(
        srcAddr: Inet6Address,
        dstAddr: Inet6Address,
        tcpSegment: ByteArray
    ): Int {
        var sum = 0L
        val srcBytes = srcAddr.address
        val dstBytes = dstAddr.address
        for (i in 0 until srcBytes.size step 2) {
            sum += ((srcBytes[i].toInt() and 0xFF) shl 8) or (srcBytes[i + 1].toInt() and 0xFF)
        }
        for (i in 0 until dstBytes.size step 2) {
            sum += ((dstBytes[i].toInt() and 0xFF) shl 8) or (dstBytes[i + 1].toInt() and 0xFF)
        }
        sum += tcpSegment.size
        sum += Protocol.TCP
        sum += wordsWithSkippedChecksum(tcpSegment)
        return fold(sum)
    }

    /** One's-complement sum of a bytes array, skipping the checksum field. */
    private fun wordsWithSkippedChecksum(bytes: ByteArray): Long {
        var sum = 0L
        var i = 0
        while (i < bytes.size) {
            val word = if (i + 1 < bytes.size) {
                ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            } else {
                (bytes[i].toInt() and 0xFF) shl 8
            }
            if (i != 16) sum += word
            i += 2
        }
        return sum
    }

    private fun fold(sum: Long): Int {
        var acc = sum
        while (acc > 0xFFFF) {
            acc = (acc and 0xFFFF) + (acc shr 16)
        }
        return (acc.inv() and 0xFFFF).toInt()
    }
}
