package com.vpntz.app.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class TlsPacketFragmenterTest {

    private fun payload(host: String): ByteArray =
        NetworkTestVectors.clientHelloPayload(NetworkTestVectors.tlsClientHello(host))

    @Test
    fun detectsClientHello() {
        assertTrue(TlsPacketFragmenter.isClientHello(NetworkTestVectors.tlsClientHello("a.com")))
        assertFalse(TlsPacketFragmenter.isClientHello(ByteArray(6)))
        assertFalse(TlsPacketFragmenter.isClientHello(ByteArray(6).also { it[0] = 0x17 }))
    }

    @Test
    fun buildTlsRecordWrapsCorrectly() {
        val payload = "abcdefgh".toByteArray()
        val rec = TlsPacketFragmenter.buildTlsRecord(0x16, 0x03, 0x03, payload, 2, 3)
        assertEquals(5 + 3, rec.size)
        assertEquals(0x16, rec[0].toInt() and 0xFF)
        assertEquals(0x03, rec[1].toInt() and 0xFF)
        assertEquals(0x03, rec[2].toInt() and 0xFF)
        assertEquals(0, rec[3].toInt() and 0xFF)
        assertEquals(3, rec[4].toInt() and 0xFF)
        assertEquals(payload[2], rec[5])
        assertEquals(payload[3], rec[6])
        assertEquals(payload[4], rec[7])
    }

    @Test
    fun findsSniHostnameOffset() {
        val pl = payload("www.example.com")
        val off = TlsPacketFragmenter.findSniHostnameOffset(pl)
        assertTrue("found SNI at $off", off > 0)
        assertEquals("www.example.com", String(pl, off, "www.example.com".length, Charsets.US_ASCII))
    }

    @Test
    fun sniHostnameLengthMatches() {
        val pl = payload("www.example.com")
        val off = TlsPacketFragmenter.findSniHostnameOffset(pl)
        assertEquals("www.example.com".length, TlsPacketFragmenter.sniHostnameLength(pl, off))
    }

    @Test
    fun negativeOffsetWithoutSni() {
        val noSni = NetworkTestVectors.clientHelloPayload(NetworkTestVectors.tlsClientHello("x.com", withSni = false))
        assertEquals(-1, TlsPacketFragmenter.findSniHostnameOffset(noSni))
    }

    @Test
    fun halfSplitPointsAreDeterministic() {
        val pl = ByteArray(100)
        val points = TlsPacketFragmenter.computeSplitPoints(pl, TlsPacketFragmenter.STRATEGY_HALF, Random(1))
        assertEquals(listOf(1, 50), points)
    }

    @Test
    fun microSplitPointsAreConsecutiveBytes() {
        val pl = ByteArray(10)
        val points = TlsPacketFragmenter.computeSplitPoints(pl, TlsPacketFragmenter.STRATEGY_MICRO, Random(1))
        assertEquals((1 until 10).toList(), points)
    }

    @Test
    fun sniSplitPointsStayWithinPayload() {
        val pl = payload("www.example.com")
        for (seed in 0L until 20L) {
            val points = TlsPacketFragmenter.computeSplitPoints(pl, TlsPacketFragmenter.STRATEGY_SNI_SPLIT, Random(seed))
            // first point is the 1-byte lead, second lands inside the payload
            assertEquals(1, points[0] as Int)
            assertTrue(points[1] in 2 until pl.size)
            assertEquals("exactly two split points", 2, points.size)
        }
    }

    @Test
    fun multiSplitPointsAreDeterministicForSeededRandom() {
        val pl = ByteArray(200)
        val a = TlsPacketFragmenter.computeSplitPoints(pl, TlsPacketFragmenter.STRATEGY_MULTI, Random(42))
        val b = TlsPacketFragmenter.computeSplitPoints(pl, TlsPacketFragmenter.STRATEGY_MULTI, Random(42))
        assertEquals(a, b)
        // every split is a strictly-increasing in-range offset
        assertTrue(a.all { it in 1 until pl.size })
        for (i in 1 until a.size) assertTrue(a[i] > a[i - 1])
    }

    @Test
    fun fakeClientHelloReplacesSniAndKeepsSize() {
        val rec = NetworkTestVectors.tlsClientHello("secret.example.com")
        val fake = TlsPacketFragmenter.buildFakeClientHello(rec, "www.google.com")!!
        assertEquals(rec.size, fake.size)
        // the original host must be gone from the fake's SNI
        assertFalse(String(fake, Charsets.US_ASCII).contains("secret.example.com"))
        // decoy must be findable
        assertTrue(String(fake, Charsets.US_ASCII).contains("www.google.com"))
        // byte offsets preserved: record header + handshake intact
        assertEquals(0x16, fake[0].toInt() and 0xFF)
        assertEquals(rec[5], fake[5])
    }

    @Test
    fun fakeClientHelloNullWhenNoSni() {
        val rec = NetworkTestVectors.tlsClientHello("x.com", withSni = false)
        assertNull(TlsPacketFragmenter.buildFakeClientHello(rec, "www.google.com"))
    }

    @Test
    fun fakeClientHelloNullForTinyInput() {
        assertNull(TlsPacketFragmenter.buildFakeClientHello(ByteArray(3), "x.com"))
    }
}
