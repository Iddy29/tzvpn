package com.vpntz.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden conformance of the pipe record against docs/provenance/WIRE_FORMAT.md.
 * The canonical 88-field vector below is transcribed directly from the spec
 * table; both decode-side field assertions and encode-side byte equality are
 * enforced so any layout drift fails loudly.
 */
class SpecConformanceTest {

    private val lockHash = "a".repeat(16) + ":" + "b".repeat(32)

    /** Authored once from the WIRE_FORMAT.md table — positions are the contract. */
    private val golden88 = listOf(
        "43",                          // 0 version
        "dnstt",                       // 1 tunnel token
        "goldy",                       // 2 name
        "t.example.com",               // 3 domain
        "9.9.9.9:53:1,1.0.0.1:53:0",   // 4 resolvers CSV
        "1",                           // 5 authoritative mode
        "7000",                        // 6 keep alive
        "dcubic",                      // 7 congestion control
        "1090",                        // 8 tcp listen port
        "127.0.0.2",                   // 9 tcp listen host
        "1",                           // 10 gso
        "PUBKEY123",                   // 11 dnstt public key
        "suser",                       // 12 socks username
        "spass",                       // 13 socks password
        "0",                           // 14 ssh chain enabled
        "ruser",                       // 15 ssh username
        "rpass",                       // 16 ssh password
        "2222",                        // 17 ssh port
        "0",                           // 18 deprecated fwd dns
        "rhost",                       // 19 ssh host
        "0",                           // 20 removed useServerDns
        "https://doh.test/dns-query",  // 21 doh url (not sanitized)
        "dot",                         // 22 dns transport
        "key",                         // 23 ssh auth type
        WireBase64.encode("priv"),     // 24 ssh private key b64
        WireBase64.encode("pass"),     // 25 ssh key passphrase b64
        WireBase64.encode("bridge\nlines"), // 26 tor bridges b64
        "1",                           // 27 dnstt authoritative
        "1443",                        // 28 naive port
        "nuser",                       // 29 naive username
        WireBase64.encode("naive"),    // 30 naive password b64
        "1",                           // 31 is locked
        lockHash,                      // 32 lock password hash
        "1710000000000",               // 33 expiration date
        "1",                           // 34 allow sharing
        "dev-abc",                     // 35 bound device id
        "1",                           // 36 resolvers hidden
        "",                            // 37 hidden resolvers (empty)
        "0",                           // 38 noizdns stealth
        "512",                         // 39 dns payload size
        "1081",                        // 40 socks5 server port
        "1",                           // 41 vaydns compat
        "cname",                       // 42 vaydns record type
        "150",                         // 43 vaydns qname len
        "3.5",                         // 44 vaydns rps (double!)
        "11",                          // 45 vaydns idle timeout
        "3",                           // 46 vaydns keepalive
        "240",                         // 47 vaydns udp timeout
        "7",                           // 48 vaydns max num labels
        "4",                           // 49 vaydns client id size
        "0",                           // 50 ssh tls enabled
        "tls.sni.example",             // 51 ssh tls sni
        "mp.host.net",                 // 52 http proxy host
        "9080",                        // 53 http proxy port
        "x-host.example",              // 54 http proxy custom host
        "1",                           // 55 ssh ws enabled
        "/wsconn",                     // 56 ws path
        "0",                           // 57 ws use tls
        "wshost",                      // 58 ws custom host
        WireBase64.encode("Injected"), // 59 ssh payload b64
        "fanout",                      // 60 resolver mode
        "5",                           // 61 rr spread
        "11111111-2222-3333-4444-555555555555", // 62 vless uuid slot
        "reality",                     // 63 vless security
        "grpc",                        // 64 vless transport
        "/gs",                         // 65 vless ws path
        "1.2.3.4",                     // 66 cdn ip
        "8443",                        // 67 cdn port
        "0",                           // 68 sni fragment enabled
        "multi",                       // 69 fragment strategy
        "120",                         // 70 fragment delay ms
        "",                            // 71 legacy vless SNI (frozen)
        "1",                           // 72 ch padding
        "0",                           // 73 ws header obfuscation
        "1",                           // 74 ws padding
        "15",                          // 75 spoof ttl
        "decoy.host.io",               // 76 fake decoy host
        "1316",                        // 77 tcp max seg
        "cdn-sni.example",             // 78 vless sni
        "FAKEPK",                      // 79 reality pubkey
        "00112233",                    // 80 reality short id
        "firefox",                     // 81 reality fp
        "hypass",                      // 82 hy2 password
        "hy.sni",                      // 83 hy2 sni
        "1",                           // 84 hy2 insecure
        "salamander",                  // 85 hy2 obfs
        "obfspass"                     // 86 hy2 obfs password
    )

    @Test
    fun `golden wire vector decodes to exactly the spec values`() {
        val record = golden88.joinToString(VpnTzField.DELIMITER)
        val outcome = ProfileRecordCodec.parse(record)
        assertTrue("decode failed: ${(outcome as? ProfileRecordCodec.ParseOutcome.Bad)?.reason}", outcome is ProfileRecordCodec.ParseOutcome.Ok)
        outcome as ProfileRecordCodec.ParseOutcome.Ok

        val d = outcome.document
        assertEquals("dnstt", d.tunnelToken)
        assertEquals("goldy", d.name)
        assertEquals("t.example.com", d.domain)
        assertEquals(
            listOf(
                ProfileDocument.Resolver("9.9.9.9", 53, true),
                ProfileDocument.Resolver("1.0.0.1", 53, false)
            ), d.resolvers)
        assertEquals(true, d.authoritativeMode)
        assertEquals(7000, d.keepAliveInterval)
        assertEquals("dcubic", d.congestionControl)
        assertEquals(1090, d.tcpListenPort)
        assertEquals("127.0.0.2", d.tcpListenHost)
        assertEquals(true, d.gsoEnabled)
        assertEquals("PUBKEY123", d.dnsttPublicKey)
        assertEquals("suser", d.socksUsername)
        assertEquals("spass", d.socksPassword)
        assertFalse(d.sshChainEnabled)
        assertEquals("ruser", d.sshUsername)
        assertEquals("2222", d.sshPort.toString())
        assertEquals("rhost", d.sshHost)
        assertEquals("https://doh.test/dns-query", d.dohUrl)
        assertEquals("dot", d.dnsTransport)
        assertEquals("key", d.sshAuthType)
        assertEquals("priv", d.sshPrivateKey)
        assertEquals("pass", d.sshKeyPassphrase)
        assertEquals("bridge\nlines", d.torBridgeLines)
        assertTrue(d.dnsttAuthoritative)
        assertEquals(1443, d.naivePort)
        assertEquals("naive", d.naivePassword)
        assertTrue(d.isLocked)
        assertEquals(lockHash, d.lockPasswordHash)
        assertEquals(1710000000000L, d.expirationDate)
        assertTrue(d.allowSharing)
        assertEquals("dev-abc", d.boundDeviceId)
        assertTrue(d.resolversHidden)
        assertEquals(0, d.hiddenResolvers.size)
        assertEquals(512, d.dnsPayloadSize)
        assertEquals(1081, d.socks5ServerPort)
        assertTrue(d.vaydnsDnsttCompat)
        assertEquals("cname", d.vaydnsRecordType)
        assertEquals(150, d.vaydnsMaxQnameLen)
        assertEquals(3.5, d.vaydnsRps, 0.0)
        assertEquals(11, d.vaydnsIdleTimeout)
        assertEquals(3, d.vaydnsKeepalive)
        assertEquals(240, d.vaydnsUdpTimeout)
        assertEquals(7, d.vaydnsMaxNumLabels)
        assertEquals(4, d.vaydnsClientIdSize)
        assertFalse(d.sshTlsEnabled)
        assertEquals("tls.sni.example", d.sshTlsSni)
        assertEquals("mp.host.net", d.sshHttpProxyHost)
        assertEquals(9080, d.sshHttpProxyPort)
        assertEquals("x-host.example", d.sshHttpProxyCustomHost)
        assertTrue(d.sshWsEnabled)
        assertEquals("/wsconn", d.sshWsPath)
        assertFalse(d.sshWsUseTls)
        assertEquals("wshost", d.sshWsCustomHost)
        assertEquals("Injected", d.sshPayload)
        assertEquals("fanout", d.resolverMode)
        assertEquals(5, d.rrSpreadCount)
        assertEquals("11111111-2222-3333-4444-555555555555", d.vlessUuid)
        assertEquals("reality", d.vlessSecurity)
        assertEquals("grpc", d.vlessTransport)
        assertEquals("/gs", d.vlessWsPath)
        assertEquals("1.2.3.4", d.cdnIp)
        assertEquals(8443, d.cdnPort)
        assertFalse(d.sniFragmentEnabled)
        assertEquals("multi", d.sniFragmentStrategy)
        assertEquals(120, d.sniFragmentDelayMs)
        assertEquals("cdn-sni.example", d.vlessSni)
        assertTrue(d.chPaddingEnabled)
        assertFalse(d.wsHeaderObfuscation)
        assertTrue(d.wsPaddingEnabled)
        assertEquals(15, d.sniSpoofTtl)
        assertEquals("decoy.host.io", d.fakeDecoyHost)
        assertEquals(1316, d.tcpMaxSeg)
        assertEquals("FAKEPK", d.vlessRealityPubKey)
        assertEquals("00112233", d.vlessRealityShortId)
        assertEquals("firefox", d.vlessRealityFp)
        assertEquals("hypass", d.hy2Password)
        assertEquals("hy.sni", d.hy2Sni)
        assertTrue(d.hy2Insecure)
        assertEquals("salamander", d.hy2Obfs)
        assertEquals("obfspass", d.hy2ObfsPassword)
    }

    @Test
    fun `encoding the reconstructed document reproduces the golden bytes`() {
        val record = golden88.joinToString(VpnTzField.DELIMITER)
        val ok = ProfileRecordCodec.parse(record) as ProfileRecordCodec.ParseOutcome.Ok
        val reencoded = ProfileRecordCodec.encode(ok.document)
        val expected = WireBase64.encode(record)
        assertEquals(expected, WireBase64.encode(reencoded))
        assertEquals(golden88, reencoded.split(VpnTzField.DELIMITER))
    }

    @Test
    fun `wire length is exactly 87 segments today`() {
        val record = golden88.joinToString(VpnTzField.DELIMITER)
        assertEquals(87, Record.split(record).size)
        assertEquals(VpnTzField.CURRENT_LENGTH, 87)
    }

    @Test
    fun `short 60-field legacy records get spec defaults for missing tail`() {
        val head = listOf(
            "43", "vaydns", "legacy", "l.example.com",
            "1.1.1.1:53:0", "0", "5000", "bbr", "1080", "127.0.0.1", "0"
            /* tail absent — like TzGate's 60-field generator */
        )
        val short = (head + List(60 - head.size) { "" }).joinToString("|")
        val outcome = ProfileRecordCodec.parse(short)
        assertTrue(
            "short record rejected: $outcome / record=$short",
            outcome is ProfileRecordCodec.ParseOutcome.Ok
        )
        val ok = outcome as ProfileRecordCodec.ParseOutcome.Ok
        val d = ok.document
        assertFalse(d.isLocked)
        assertEquals(101, d.vaydnsMaxQnameLen)
        assertEquals(0.0, d.vaydnsRps, 0.0)
        // Quirks (WIRE_FORMAT §Observed quirks): BLANK-in-range fields keep the
        // strict baseline semantics; only fields BEYOND the record end default.
        assertEquals("sni_split", d.sniFragmentStrategy)   // blank string ⇒ default
        assertEquals(100, d.sniFragmentDelayMs)            // blank int ⇒ 100
        assertTrue(d.sniFragmentEnabled)                   // F68 beyond 60-field record ⇒ true
        assertFalse(d.sshWsUseTls)                         // F57 present-but-blank ⇒ strict false
        assertTrue(d.wsHeaderObfuscation)                  // blank text-flag ⇒ "1"
        // String-tail defaults from the table:
        assertEquals("chrome", d.vlessRealityFp)
        assertEquals("/", d.sshWsPath)
        assertFalse(ok.newerThanKnown)

        // A record that genuinely ENDS before those fields uses absent-defaults:
        val truncated = head + List(57 - head.size) { "" }
        val d57 = (ProfileRecordCodec.parse(truncated.joinToString("|"))
            as ProfileRecordCodec.ParseOutcome.Ok).document
        assertTrue(d57.sshWsUseTls)                        // absent ⇒ true
        assertTrue(d57.sniFragmentEnabled)                 // absent ⇒ true
    }

    @Test
    fun `forward versions warn through outcome flag`() {
        val forward = listOf("99", "dnstt", "fut", "d.x") .joinToString("|")
        val ok = ProfileRecordCodec.parse(forward) as ProfileRecordCodec.ParseOutcome.Ok
        assertTrue(ok.newerThanKnown)
        val current = listOf("43", "dnstt", "cur", "d.x").joinToString("|")
        assertFalse((ProfileRecordCodec.parse(current) as ProfileRecordCodec.ParseOutcome.Ok).newerThanKnown)
    }

    @Test
    fun `garbage first field rejects cleanly`() {
        assertTrue(ProfileRecordCodec.parse("") is ProfileRecordCodec.ParseOutcome.Bad)
        assertTrue(ProfileRecordCodec.parse("hello world") is ProfileRecordCodec.ParseOutcome.Bad)
        val badToken = listOf("43", "martian", "n", "d").joinToString("|")
        val out = ProfileRecordCodec.parse(badToken)
        assertTrue(out is ProfileRecordCodec.ParseOutcome.Bad && out.reason.contains("tunnel"))
    }

    @Test
    fun `resolver csv tolerates ipv6 literals`() {
        val csv = "[2001:db8::1]:53:1,dns.example:5353:0,host:53"
        val parsed = ProfileRecordCodec.parseResolvers(csv)
        assertEquals(3, parsed.size)
        assertEquals(ProfileDocument.Resolver("[2001:db8::1]", 53, true), parsed[0])
        assertEquals(ProfileDocument.Resolver("dns.example", 5353, false), parsed[1])
        assertEquals(ProfileDocument.Resolver("host", 53, false), parsed[2])
    }

    @Test
    fun `field delimiter cannot be smuggled through user text`() {
        val malicious = ProfileDocument(tunnelToken = "ssh", name = "ev|il|name")
        val encoded = ProfileRecordCodec.encode(malicious)
        val parts = encoded.split("|")
        // Name must not have opened extra columns: positions stay fixed.
        assertEquals(VpnTzField.CURRENT_LENGTH, parts.size)
        assertEquals(87, parts.size)
    }
}
