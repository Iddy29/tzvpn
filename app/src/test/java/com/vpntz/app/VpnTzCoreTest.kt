package com.vpntz.app

import com.vpntz.app.tunnel.RateLimiter
import com.vpntz.app.util.DeviceIdUtil
import com.vpntz.app.util.TrafficFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun `zero rate is unlimited and returns immediately`() {
        val limiter = RateLimiter(0)
        val start = System.nanoTime()
        limiter.acquire(10 * 1024 * 1024)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 50)
    }

    @Test
    fun `empty acquire is a no-op`() {
        val limiter = RateLimiter(1024)
        limiter.acquire(0)
    }

    @Test
    fun `first burst up to tolerance passes without sleep`() {
        val limiter = RateLimiter(64 * 1024) // 64 KB/s
        val start = System.nanoTime()
        limiter.acquire(64 * 1024) // exactly one second of rate — within burst tolerance
        assertTrue((System.nanoTime() - start) / 1_000_000 < 100)
    }

    @Test
    fun `sustained reads are paced near the configured rate`() {
        val rateBytes = 256 * 1024L // 256 KB/s
        val limiter = RateLimiter(rateBytes)
        val chunk = 32 * 1024
        val start = System.nanoTime()
        repeat(16) { limiter.acquire(chunk) } // 512 KB = 2s of traffic; 1s burst tolerance absorbed, rest paced
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // Generous bounds — the scheduler must slow us down but not stall.
        assertTrue("expected pacing, elapsed=${elapsedMs}ms", elapsedMs > 400)
        assertTrue("over-throttled, elapsed=${elapsedMs}ms", elapsedMs < 4000)
    }

    @Test
    fun `raising the limit mid-flight removes the wait`() {
        val limiter = RateLimiter(8 * 1024) // very slow
        limiter.acquire(8 * 1024)
        limiter.bytesPerSecond = 8 * 1024 * 1024 // very fast
        val start = System.nanoTime()
        limiter.acquire(512 * 1024)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 500)
    }
}

class DeviceIdUtilTest {

    @Test
    fun `empty android id yields empty fingerprint`() {
        assertEquals("", DeviceIdUtil.fingerprint(""))
    }

    @Test
    fun `fingerprint is 16 lowercase hex characters`() {
        val fp = DeviceIdUtil.fingerprint("9774d56d682e549c")
        assertTrue(fp.matches(Regex("^[0-9a-f]{16}$")))
    }

    @Test
    fun `fingerprint is deterministic`() {
        assertEquals(
            DeviceIdUtil.fingerprint("input-123"),
            DeviceIdUtil.fingerprint("input-123")
        )
    }

    @Test
    fun `different inputs produce different fingerprints`() {
        assertNotEquals(
            DeviceIdUtil.fingerprint("device-one"),
            DeviceIdUtil.fingerprint("device-two")
        )
    }

    @Test
    fun `fingerprint never equals the raw android id`() {
        val raw = "9774d56d682e549c"
        assertNotEquals(raw, DeviceIdUtil.fingerprint(raw))
    }
}

class TrafficFormatterTest {

    @Test
    fun `bytes below 1 KiB stay in bytes`() {
        assertEquals("512 B", TrafficFormatter.bytes(512))
    }

    @Test
    fun `bytes scale through binary units`() {
        assertEquals("1.00 KiB", TrafficFormatter.bytes(1024))
        assertEquals("1.50 MiB", TrafficFormatter.bytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.00 GiB", TrafficFormatter.bytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `negative byte count is clamped to zero`() {
        assertEquals("0 B", TrafficFormatter.bytes(-5))
    }

    @Test
    fun `rate renders kilobits for slow links`() {
        assertEquals("64 Kbps", TrafficFormatter.rate(8_000))
    }

    @Test
    fun `rate renders megabits for fast links`() {
        assertEquals("8.00 Mbps", TrafficFormatter.rate(1_000_000))
        assertEquals("125 Mbps", TrafficFormatter.rate(15_625_000))
    }

    @Test
    fun `negative rate is clamped`() {
        assertEquals("0 Kbps", TrafficFormatter.rate(-1))
    }
}
