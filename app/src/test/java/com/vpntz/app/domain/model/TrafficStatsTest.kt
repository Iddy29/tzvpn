package com.vpntz.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficStatsTest {

    @Test
    fun `totals are the sum of directional counters`() {
        val stats = TrafficStats(bytesSent = 10, bytesReceived = 5, packetsSent = 7, packetsReceived = 3)
        assertEquals(15L, stats.totalBytes)
        assertEquals(10L, stats.totalPackets)
    }

    @Test
    fun `bytes stay in bytes below 1 KiB`() {
        assertEquals("512 B", TrafficStats.formatBytes(512))
        assertEquals("0 B", TrafficStats.formatBytes(0))
    }

    @Test
    fun `bytes scale through binary units`() {
        assertEquals("1.0 KB", TrafficStats.formatBytes(1024))
        assertEquals("1.5 KB", TrafficStats.formatBytes(1536))
        assertEquals("1.0 MB", TrafficStats.formatBytes(1024L * 1024))
        assertEquals("1.00 GB", TrafficStats.formatBytes(1024L * 1024 * 1024))
    }

    @Test
    fun `speed appends per-second suffix`() {
        assertEquals("512 B/s", TrafficStats.formatSpeed(512))
        assertEquals("1.0 KB/s", TrafficStats.formatSpeed(1024))
    }

    @Test
    fun `instance formatters delegate to the static rules`() {
        val stats = TrafficStats(bytesSent = 1024, bytesReceived = 2048)
        assertEquals(TrafficStats.formatBytes(1024), stats.formatBytesSent())
        assertEquals(TrafficStats.formatBytes(2048), stats.formatBytesReceived())
        assertEquals(TrafficStats.formatBytes(3072), stats.formatTotalBytes())
    }

    @Test
    fun `empty constant is all zeros`() {
        assertEquals(TrafficStats(), TrafficStats.EMPTY)
    }
}
