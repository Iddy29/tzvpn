package com.vpntz.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsUtilsTest {

    @Test
    fun detectsAaaaQuery() {
        assertTrue(DnsUtils.isAAAAQuery(NetworkTestVectors.dnsQuery(28)))
    }

    @Test
    fun rejectsAnAQuery() {
        assertFalse(DnsUtils.isAAAAQuery(NetworkTestVectors.dnsQuery(1)))
    }

    @Test
    fun rejectsOtherQTypes() {
        assertFalse(DnsUtils.isAAAAQuery(NetworkTestVectors.dnsQuery(15))) // MX
        assertFalse(DnsUtils.isAAAAQuery(NetworkTestVectors.dnsQuery(255))) // ANY
    }

    @Test
    fun rejectsTruncatedPayload() {
        assertFalse(DnsUtils.isAAAAQuery(ByteArray(12)))
        assertFalse(DnsUtils.isAAAAQuery(ByteArray(5)))
        assertFalse(DnsUtils.isAAAAQuery(ByteArray(0)))
    }

    @Test
    fun rejectsMalformedLabelOverrun() {
        // header + label length larger than remaining bytes
        val q = ByteArray(12 + 1 + 60)
        q[5] = 0x01
        q[12] = 60 // claims a 60-byte label but only 60 bytes follow the len byte
        assertFalse(DnsUtils.isAAAAQuery(q))
    }

    @Test
    fun nodataResponseFlippingIsCorrect() {
        val query = NetworkTestVectors.dnsQuery(28)
        val resp = DnsUtils.buildAAAANoDataResponse(query)!!
        assertEquals(query.size, resp.size)
        assertTrue("QR bit set", (resp[2].toInt() and 0x80) != 0)
        assertTrue("RA bit set", (resp[3].toInt() and 0x80) != 0)
        assertEquals(0, ((resp[6].toInt() and 0xFF) shl 8) or (resp[7].toInt() and 0xFF)) // ANCOUNT
        assertEquals(0, ((resp[8].toInt() and 0xFF) shl 8) or (resp[9].toInt() and 0xFF)) // NSCOUNT
        assertEquals(0, ((resp[10].toInt() and 0xFF) shl 8) or (resp[11].toInt() and 0xFF)) // ARCOUNT
        assertEquals("QDCOUNT preserved", 1, ((resp[4].toInt() and 0xFF) shl 8) or (resp[5].toInt() and 0xFF))
    }

    @Test
    fun nodataResponseNullWhenTooShort() {
        assertNull(DnsUtils.buildAAAANoDataResponse(ByteArray(11)))
        assertNull(DnsUtils.buildAAAANoDataResponse(ByteArray(0)))
    }

    @Test
    fun normalizeDomainLowercasesAndTakesTrailingDot() {
        assertEquals("example.com", DnsUtils.normalizeDomain("Example.COM."))
        assertEquals("example.com", DnsUtils.normalizeDomain("  example.com "))
        assertNull(DnsUtils.normalizeDomain(""))
        assertNull(DnsUtils.normalizeDomain("..."))
        assertNull(DnsUtils.normalizeDomain("   "))
    }
}
