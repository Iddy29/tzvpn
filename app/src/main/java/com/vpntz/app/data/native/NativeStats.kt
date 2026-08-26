package com.vpntz.app.data.native

import com.vpntz.app.domain.model.TrafficStats
import com.vpntz.app.util.TrafficFormatter

/**
 * Traffic statistics from the native tunnel.
 */
data class NativeStats(
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val activeConnections: Long = 0,
    val rttMs: Long = 0
) {
    companion object {
        val EMPTY = NativeStats()
    }

    /**
     * Convert to domain TrafficStats.
     */
    fun toTrafficStats() = TrafficStats(
        bytesSent = bytesSent,
        bytesReceived = bytesReceived,
        packetsSent = packetsSent,
        packetsReceived = packetsReceived,
        rttMs = rttMs
    )

    /**
     * Format bytes as human-readable string.
     */
    fun formatBytesSent(): String = formatBytes(bytesSent)
    fun formatBytesReceived(): String = formatBytes(bytesReceived)

    /**
     * Get total bytes transferred.
     */
    val totalBytes: Long get() = bytesSent + bytesReceived

    /**
     * Format total bytes as human-readable string.
     */
    fun formatTotalBytes(): String = formatBytes(totalBytes)

    private fun formatBytes(bytes: Long): String = TrafficFormatter.bytes(bytes)
}
