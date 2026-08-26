package com.vpntz.app.util

import java.util.Locale

/**
 * Human-readable formatting for traffic counters and throughput.
 * VPN-TZ original implementation.
 *
 * VPN-TZ displays transfer sizes in binary units (KiB/MiB/GiB) and rates in
 * kilobits per second, matching what users see in their ISP tools.
 */
object TrafficFormatter {

    private val BINARY_UNITS = arrayOf("B", "KiB", "MiB", "GiB", "TiB")

    /** Formats a byte count as a compact size string, e.g. 1536 -> "1.5 KiB". */
    fun bytes(totalBytes: Long): String {
        if (totalBytes < 0) return "0 B"
        var value = totalBytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < BINARY_UNITS.lastIndex) {
            value /= 1024.0
            unit++
        }
        return when {
            unit == 0 -> "$totalBytes B"
            value >= 100 -> String.format(Locale.US, "%.0f %s", value, BINARY_UNITS[unit])
            value >= 10 -> String.format(Locale.US, "%.1f %s", value, BINARY_UNITS[unit])
            else -> String.format(Locale.US, "%.2f %s", value, BINARY_UNITS[unit])
        }
    }

    /** Formats bytes elapsed over one second as a rate, e.g. "2.4 Mbps". */
    fun rate(bytesPerSecond: Long): String {
        if (bytesPerSecond < 0) return "0 Kbps"
        val megabits = bytesPerSecond * 8.0 / (1000.0 * 1000.0)
        return when {
            megabits >= 100 -> String.format(Locale.US, "%.0f Mbps", megabits)
            megabits >= 10 -> String.format(Locale.US, "%.1f Mbps", megabits)
            megabits >= 1 -> String.format(Locale.US, "%.2f Mbps", megabits)
            else -> {
                val kilobits = bytesPerSecond * 8.0 / 1000.0
                String.format(Locale.US, "%.0f Kbps", kilobits)
            }
        }
    }
}
