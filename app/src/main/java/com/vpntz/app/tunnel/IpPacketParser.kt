package com.vpntz.app.tunnel

import java.net.InetAddress

// Re-exports of the independent byte-level codec in com.vpntz.app.network.
// This file exists so the existing tunnel-package call sites (KotlinTunnelManager,
// TunnelConnection, the SOCKS bridges, etc.) continue to compile unchanged while
// all packet logic now lives in one place.

typealias Protocol = com.vpntz.app.network.Protocol
typealias TcpFlags = com.vpntz.app.network.TcpFlags
typealias TcpHeader = com.vpntz.app.network.TcpHeader
typealias IpPacket = com.vpntz.app.network.IpPacket
typealias ConnectionKey = com.vpntz.app.network.ConnectionKey

object IpPacketParser {
    fun parse(data: ByteArray): IpPacket? = com.vpntz.app.network.IpPacketParser.parse(data)

    fun extractConnectionKey(packet: IpPacket): ConnectionKey? =
        com.vpntz.app.network.IpPacketParser.extractConnectionKey(packet)
}
