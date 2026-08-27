package com.vpntz.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral tables for community URIs, mirroring observable baseline semantics
 * documented in WIRE_FORMAT.md §Community URIs.
 */
class CommunityCodecTest {

    // ------------------------------------------------------------------ VLESS --

    @Test
    fun `minimal vless ws link`() {
        val uri = "vless://11112222333344445555666677778888@cdn.example.com:443" +
            "?type=ws&security=tls&path=%2Fws&host=origin.example.com#My%20Node"
        val r = CommunityUriCodec.parseVless(uri)
        assertTrue(r is CommunityUriCodec.Result.Ok)
        val d = (r as CommunityUriCodec.Result.Ok).document
        assertEquals("vless", d.tunnelToken)
        assertEquals("11112222333344445555666677778888", d.vlessUuid)
        assertEquals("origin.example.com", d.domain)          // WS Host wins
        assertEquals(443, d.cdnPort)
        assertEquals("ws", d.vlessTransport)
        assertEquals("/ws", d.vlessWsPath)
        assertEquals("tls", d.vlessSecurity)
        assertEquals("", d.vlessSni)                          // sni==host ⇒ empty slot
        assertEquals(true, d.sniFragmentEnabled)
        assertEquals("micro", d.sniFragmentStrategy)
        assertEquals(300, d.sniFragmentDelayMs)
        assertEquals("My Node", d.name)
        assertEquals("cdn.example.com", d.cdnIp)              // cdn defaults to server
    }

    @Test
    fun `vless explicit sni and cdn override`() {
        val uri = "vless://11112222333344445555666677778888@edge.io:2053?type=ws&security=tls" +
            "&sni=cert.example.com&host=route.example.com&cdn=203.0.113.9&cdn-port=2096#/x"
        val d = (CommunityUriCodec.parseVless(uri) as CommunityUriCodec.Result.Ok).document
        assertEquals("route.example.com", d.domain)
        assertEquals("cert.example.com", d.vlessSni)          // sni differs ⇒ stored
        assertEquals("203.0.113.9", d.cdnIp)
        assertEquals(2096, d.cdnPort)
    }

    @Test
    fun `vless fragment parameter parsing`() {
        fun doc(query: String) =
            (CommunityUriCodec.parseVless(
                "vless://11112222333344445555666677778888@a.io:443?$query")
                as CommunityUriCodec.Result.Ok).document

        val explicit = doc("type=ws&fragment=150,fake")
        assertEquals(150, explicit.sniFragmentDelayMs)
        assertEquals("fake", explicit.sniFragmentStrategy)
        assertTrue(explicit.sniFragmentEnabled)

        val disabled = doc("type=ws&fragment=")
        org.junit.Assert.assertFalse(disabled.sniFragmentEnabled)

        val defaulted = doc("type=ws")
        assertTrue(defaulted.sniFragmentEnabled)
        assertEquals("micro", defaulted.sniFragmentStrategy)
        assertEquals(300, defaulted.sniFragmentDelayMs)

        // Unknown strategies fall back to micro.
        val unknown = doc("type=ws&fragment=99,blackmagic")
        assertEquals("micro", unknown.sniFragmentStrategy)
    }

    @Test
    fun `vless reality requires pbk and disables fragmentation`() {
        val missing = CommunityUriCodec.parseVless(
            "vless://11112222333344445555666677778888@r.io:443?security=reality&type=raw")
        assertTrue(missing is CommunityUriCodec.Result.Reject)

        val full = CommunityUriCodec.parseVless(
            "vless://11112222333344445555666677778888@r.io:443?security=reality" +
                "&pbk=SomeBase64urlKey&sid=abcd1234&fp=firefox&type=raw")
        val d = (full as CommunityUriCodec.Result.Ok).document
        assertEquals("reality", d.vlessSecurity)
        assertEquals("SomeBase64urlKey", d.vlessRealityPubKey)
        assertEquals("abcd1234", d.vlessRealityShortId)
        assertEquals("firefox", d.vlessRealityFp)
        assertEquals(false, d.sniFragmentEnabled)
    }

    @Test
    fun `vless transports beyond websocket soft-reject except reality-tcp`() {
        val grpc = CommunityUriCodec.parseVless(
            "vless://11112222333344445555666677778888@g.io:443?type=grpc")
        assertTrue(grpc is CommunityUriCodec.Result.SoftReject)
        val tcpTls = CommunityUriCodec.parseVless(
            "vless://11112222333344445555666677778888@g.io:443?type=tcp&security=tls")
        assertTrue(tcpTls is CommunityUriCodec.Result.SoftReject)
        val rawReality = CommunityUriCodec.parseVless(
            "vless://11112222333344445555666677778888@g.io:443?type=tcp&security=reality&pbk=k")
        assertTrue(rawReality is CommunityUriCodec.Result.Ok)
    }

    @Test
    fun `vless uuid validation rejects non-hex or wrong length`() {
        // Baseline checks transport first, so plain tcp links surface a SoftReject;
        // the UUID gate must never let these through as Ok either way.
        val bad = listOf(
            "vless://notauuid@ws.io:443?type=ws",
            "vless://11112222333344445555666677778zzz@ws.io:443?type=ws",
            "vless://1111222233334444@ws.io:443?type=ws"
        )
        bad.forEach {
            val r = CommunityUriCodec.parseVless(it)
            assertTrue("$it → $r", r !is CommunityUriCodec.Result.Ok)
        }
    }

    @Test
    fun `vless ipv6 bracketed authority`() {
        val d = (CommunityUriCodec.parseVless(
            "vless://11112222333344445555666677778888@[2001:db8::7]:8443?type=ws")
            as CommunityUriCodec.Result.Ok).document
        assertEquals("2001:db8::7", d.cdnIp)     // server default feeds cdnIp
        assertEquals("2001:db8::7", d.domain)    // wsHost falls back to server
        assertEquals(8443, d.cdnPort)
    }

    @Test
    fun `vless emit parses back to same essentials`() {
        val original = ProfileDocument(
            tunnelToken = "vless",
            name = "Round Trip",
            vlessUuid = "11112222333344445555666677778888",
            domain = "route.example.com",
            cdnPort = 2096,
            vlessSecurity = "tls",
            vlessTransport = "ws",
            vlessWsPath = "/p a th",
            vlessSni = "cert.example.com",
            sniFragmentDelayMs = 300
        )
        val uri = CommunityUriCodec.emitVless(original)
        val parsed = (CommunityUriCodec.parseVless(uri) as CommunityUriCodec.Result.Ok).document
        assertEquals(original.vlessUuid, parsed.vlessUuid)
        assertEquals(original.domain, parsed.domain)
        assertEquals(original.cdnPort, parsed.cdnPort)
        assertEquals(original.vlessSni, parsed.vlessSni)
        assertEquals(original.vlessWsPath, parsed.vlessWsPath)
    }

    // -------------------------------------------------------------- HYSTERIA2 --

    @Test
    fun `hy2 full mapping`() {
        val d = (CommunityUriCodec.parseHysteria2(
            "hysteria2://secret@h.example.com:36712?sni=s.example.com&insecure=1" +
                "&obfs=salamander&obfs-password=obpw#Hy2%20Name")
            as CommunityUriCodec.Result.Ok).document
        assertEquals("hysteria2", d.tunnelToken)
        assertEquals("secret", d.hy2Password)
        assertEquals("h.example.com", d.domain)
        assertEquals(36712, d.cdnPort)
        assertEquals("s.example.com", d.hy2Sni)
        assertTrue(d.hy2Insecure)
        assertEquals("salamander", d.hy2Obfs)
        assertEquals("obpw", d.hy2ObfsPassword)
        assertEquals("Hy2 Name", d.name)
    }

    @Test
    fun `hy2 insecure accepts exactly 1_true_yes`() {
        for (flag in listOf("1", "true", "yes")) {
            val d = (CommunityUriCodec.parseHysteria2("hysteria2://p@h:443?insecure=$flag")
                as CommunityUriCodec.Result.Ok).document
            org.junit.Assert.assertTrue(flag, d.hy2Insecure)
        }
        for (flag in listOf("0", "false", "")) {
            val uri = if (flag.isEmpty()) "hysteria2://p@h:443" else "hysteria2://p@h:443?insecure=$flag"
            val d = (CommunityUriCodec.parseHysteria2(uri) as CommunityUriCodec.Result.Ok).document
            org.junit.Assert.assertFalse("'$flag'", d.hy2Insecure)
        }
    }

    @Test
    fun `hy2 obfs none means absent and unknown obfs soft-rejects`() {
        val none = (CommunityUriCodec.parseHysteria2("hysteria2://p@h?obfs=none")
            as CommunityUriCodec.Result.Ok).document
        assertEquals("", none.hy2Obfs)
        val weird = CommunityUriCodec.parseHysteria2("hysteria2://p@h?obfs=httpupgrade")
        assertTrue(weird is CommunityUriCodec.Result.SoftReject)
    }

    @Test
    fun `hy2 blank password and blank host hard-reject`() {
        assertTrue(CommunityUriCodec.parseHysteria2("hysteria2://@h:443") is CommunityUriCodec.Result.Reject)
        assertTrue(CommunityUriCodec.parseHysteria2("hysteria2://p@") is CommunityUriCodec.Result.Reject)
    }

    @Test
    fun `hy2 extra params are silently ignored`() {
        val d = (CommunityUriCodec.parseHysteria2(
            "hysteria2://p@h:443?m=3-7&pinSHA256=ABCD&unknownZz=1")
            as CommunityUriCodec.Result.Ok).document
        assertEquals("hysteria2", d.tunnelToken)
    }

    @Test
    fun `hy2 ipv6 literal host`() {
        val d = (CommunityUriCodec.parseHysteria2("hysteria2://p@[2001:db8::9]:9993")
            as CommunityUriCodec.Result.Ok).document
        assertEquals("2001:db8::9", d.domain)
        assertEquals(9993, d.cdnPort)
    }

    @Test
    fun `hy2 emit roundtrip preserves credentials`() {
        val original = ProfileDocument(
            tunnelToken = "hysteria2",
            name = "Safe / name",
            domain = "h.example.com",
            cdnPort = 36712,
            hy2Password = "sec/ret:12+34",
            hy2Obfs = "salamander",
            hy2ObfsPassword = "opw&=%",
            hy2Insecure = true,
            hy2Sni = "s.example.com"
        )
        val uri = CommunityUriCodec.emitHysteria2(original)
        assertTrue(uri.startsWith("hysteria2://"))
        val back = (CommunityUriCodec.parseHysteria2(uri) as CommunityUriCodec.Result.Ok).document
        assertEquals(original.hy2Password, back.hy2Password)
        assertEquals(original.hy2ObfsPassword, back.hy2ObfsPassword)
        assertEquals(original.hy2Sni, back.hy2Sni)
        assertEquals(original.hy2Insecure, back.hy2Insecure)
        assertEquals(original.cdnPort, back.cdnPort)
        assertEquals(original.domain, back.domain)
    }
}
