package com.vpntz.app.tunnel

import java.net.InetAddress

// Delegating facade to the independent TCP/IP builder in com.vpntz.app.network,
// preserving the legacy object name for the existing tunnel-package call sites.
object TcpPacketBuilder {
    fun buildSynAck(
        srcAddr: InetAddress, srcPort: Int, dstAddr: InetAddress, dstPort: Int,
        seqNum: Long, ackNum: Long
    ): ByteArray? =
        com.vpntz.app.network.TcpPacketBuilder.buildSynAck(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum)

    fun buildDataPacket(
        srcAddr: InetAddress, srcPort: Int, dstAddr: InetAddress, dstPort: Int,
        seqNum: Long, ackNum: Long, payload: ByteArray
    ): ByteArray? =
        com.vpntz.app.network.TcpPacketBuilder.buildDataPacket(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum, payload)

    fun buildFinAck(
        srcAddr: InetAddress, srcPort: Int, dstAddr: InetAddress, dstPort: Int,
        seqNum: Long, ackNum: Long
    ): ByteArray? =
        com.vpntz.app.network.TcpPacketBuilder.buildFinAck(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum)

    fun buildAck(
        srcAddr: InetAddress, srcPort: Int, dstAddr: InetAddress, dstPort: Int,
        seqNum: Long, ackNum: Long
    ): ByteArray? =
        com.vpntz.app.network.TcpPacketBuilder.buildAck(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum)

    fun buildRst(
        srcAddr: InetAddress, srcPort: Int, dstAddr: InetAddress, dstPort: Int,
        seqNum: Long, ackNum: Long
    ): ByteArray? =
        com.vpntz.app.network.TcpPacketBuilder.buildRst(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum)
}
