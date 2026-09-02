package com.vpntz.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverScanResultTest {

    // --- DnsTunnelTestResult scoring invariants ---

    @Test
    fun `score counts the number of passed checks`() {
        val result = DnsTunnelTestResult(nsSupport = true, txtSupport = true)
        assertEquals(2, result.score)
    }

    @Test
    fun `all checks passing means fully compatible`() {
        val result = DnsTunnelTestResult(
            nsSupport = true, txtSupport = true, randomSubdomain = true,
            tunnelRealism = true, edns0Support = true, nxdomainCorrect = true
        )
        assertEquals(6, result.maxScore)
        assertEquals(6, result.score)
        assertTrue(result.isCompatible)
    }

    @Test
    fun `any failed check breaks compatibility`() {
        val result = DnsTunnelTestResult(
            nsSupport = true, txtSupport = true, randomSubdomain = true,
            tunnelRealism = true, edns0Support = true, nxdomainCorrect = false
        )
        assertFalse(result.isCompatible)
    }

    @Test
    fun `details render check marks per failed and passed probe`() {
        val result = DnsTunnelTestResult(
            nsSupport = true, txtSupport = false, randomSubdomain = true,
            tunnelRealism = false, edns0Support = true, ednsMaxPayload = 1232, nxdomainCorrect = false
        )
        val details = result.details
        assertTrue(details.contains("NS→A✓"))
        assertTrue(details.contains("TXT✗"))
        assertTrue(details.contains("RND✓"))
        assertTrue(details.contains("DPI✗"))
        assertTrue(details.contains("EDNS✓(1232)"))
        assertTrue(details.contains("NXD✗"))
    }

    // --- ScannerState progress invariant ---

    @Test
    fun `progress is zero when there is nothing to scan`() {
        assertEquals(0f, ScannerState().progress, 0.0001f)
        assertEquals(0f, ScannerState(totalCount = 0).progress, 0.0001f)
    }

    @Test
    fun `progress is scanned over total including focus range`() {
        val state = ScannerState(totalCount = 90, focusRangeCount = 10, scannedCount = 50)
        assertEquals(0.5f, state.progress, 0.0001f)
    }

    // --- ResolverScanResult defaults ---

    @Test
    fun `scan result defaults to pending with no tunnel verdict`() {
        val result = ResolverScanResult(host = "1.2.3.4")
        assertEquals("1.2.3.4", result.host)
        assertEquals(ResolverStatus.PENDING, result.status)
        assertEquals(53, result.port)
        assertEquals(null, result.tunnelTestResult)
        assertEquals(null, result.prismVerified)
        assertEquals(null, result.udpWorking)
        assertEquals(null, result.tcpWorking)
    }

    // --- E2E state models ---

    @Test
    fun `simple mode e2e state starts empty`() {
        val state = SimpleModeE2eState()
        assertFalse(state.isRunning)
        assertEquals(0, state.queuedCount)
        assertTrue(state.activeResolvers.isEmpty())
    }

    @Test
    fun `e2e test result carries phase and failure info`() {
        val failed = E2eTestResult(errorMessage = "timeout", phase = E2eTestPhase.QUIC_HANDSHAKE)
        assertFalse(failed.success)
        assertEquals(E2eTestPhase.QUIC_HANDSHAKE, failed.phase)
        val ok = E2eTestResult(success = true, httpStatusCode = 200)
        assertEquals(200, ok.httpStatusCode)
    }
}
