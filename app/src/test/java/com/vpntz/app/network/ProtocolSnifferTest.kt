package com.vpntz.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ProtocolSnifferTest {

    @Test
    fun extractsSniFromTlsClientHello() {
        val rec = NetworkTestVectors.tlsClientHello("www.example.com")
        val result = ProtocolSniffer.sniff(ByteArrayInputStream(rec))
        assertEquals("www.example.com", result.domain)
        assertEquals(rec.size, result.bufferedLength)
    }

    @Test
    fun sniIsLowercased() {
        val rec = NetworkTestVectors.tlsClientHello("MixedCase.Example.COM")
        assertEquals("mixedcase.example.com", ProtocolSniffer.sniff(ByteArrayInputStream(rec)).domain)
    }

    @Test
    fun missingSniReturnsNull() {
        val rec = NetworkTestVectors.tlsClientHello("nope.example.com", withSni = false)
        assertNull(ProtocolSniffer.sniff(ByteArrayInputStream(rec)).domain)
    }

    @Test
    fun nonHandshakeRecordReturnsNull() {
        val bytes = ByteArray(64).also { it[0] = 0x17 } // application_data content type
        assertNull(ProtocolSniffer.sniff(ByteArrayInputStream(bytes)).domain)
    }

    @Test
    fun handshakeRecordWithoutClientHelloReturnsNull() {
        val bytes = NetworkTestVectors.tlsClientHello("x.com")
        bytes[5] = 0x0C // not a ClientHello
        assertNull(ProtocolSniffer.sniff(ByteArrayInputStream(bytes)).domain)
    }

    @Test
    fun truncatedTlsRecordReturnsNull() {
        val rec = NetworkTestVectors.tlsClientHello("www.example.com")
        val truncated = rec.copyOfRange(0, 20)
        assertNull(ProtocolSniffer.sniff(ByteArrayInputStream(truncated)).domain)
    }

    @Test
    fun extractsHttpHostAndStripsPort() {
        val http = "GET /p HTTP/1.1\r\nHost: cdn.example.com:8080\r\nAccept: */*\r\n\r\n".toByteArray()
        assertEquals("cdn.example.com", ProtocolSniffer.sniff(ByteArrayInputStream(http)).domain)
    }

    @Test
    fun httpHostWithoutPort() {
        val http = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n".toByteArray()
        assertEquals("example.com", ProtocolSniffer.sniff(ByteArrayInputStream(http)).domain)
    }

    @Test
    fun nonHttpDataReturnsNull() {
        val data = "NOTGETHERE, just some bytes".toByteArray() +
                byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertNull(ProtocolSniffer.sniff(ByteArrayInputStream(data)).domain)
    }

    @Test
    fun tooShortHttpReturnsNull() {
        assertNull(ProtocolSniffer.sniff(ByteArrayInputStream("GET".toByteArray())).domain)
    }

    @Test
    fun emptyStreamReturnsNoDomainAndZeroLen() {
        val result = ProtocolSniffer.sniff(ByteArrayInputStream(ByteArray(0)))
        assertNull(result.domain)
        assertEquals(0, result.bufferedLength)
    }
}
