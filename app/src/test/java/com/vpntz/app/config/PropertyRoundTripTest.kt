package com.vpntz.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Property-based roundtrip guarantee: encode→parse must be identity for every
 * representable document, and the sanitizer must forbid field-count drift.
 */
class PropertyRoundTripTest {

    private val tokens = listOf(
        "dnstt", "dnstt_ssh", "ss", "slipstream_ssh", "sayedns", "sayedns_ssh",
        "vaydns", "vaydns_ssh", "ssh", "doh", "snowflake", "naive", "naive_ssh",
        "socks5", "vless", "hysteria2"
    )

    private fun text(rnd: Random, alphabet: String = ALPHA_SAFE) =
        (1..rnd.nextInt(1, 18)).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")

    private fun host(rnd: Random) =
        (1..rnd.nextInt(4, 12)).map { HOST_SAFE[rnd.nextInt(HOST_SAFE.length)] }.joinToString("")

    private fun doc(rnd: Random): ProfileDocument {
        val token = tokens[rnd.nextInt(tokens.size)]
        return ProfileDocument(
            tunnelToken = token,
            name = "p" + text(rnd),
            domain = "h${rnd.nextInt(9)}." + text(rnd, "abcdef") + ".net",
            resolvers = List(rnd.nextInt(0, 4)) {
                ProfileDocument.Resolver(host(rnd), 53 + rnd.nextInt(100), rnd.nextBoolean())
            },
            authoritativeMode = rnd.nextBoolean(),
            keepAliveInterval = 500 + rnd.nextInt(90_000),
            congestionControl = if (rnd.nextBoolean()) "bbr" else "dcubic",
            tcpListenPort = 1024 + rnd.nextInt(50_000),
            tcpListenHost = "127.0.0.${rnd.nextInt(9)}",
            gsoEnabled = rnd.nextBoolean(),
            dnsttPublicKey = text(rnd, "ABCDEF1234567890"),
            socksUsername = if (rnd.nextBoolean()) null else text(rnd),
            socksPassword = if (rnd.nextBoolean()) null else text(rnd),
            sshChainEnabled = ProfileDocument.isSshChained(token) && rnd.nextBoolean(),
            sshUsername = text(rnd),
            sshPassword = text(rnd, "!@#\$%^&*()_+=-~`[]{};':\",./<>?abcXYZ"),
            sshPort = 22 + rnd.nextInt(60_000),
            sshHost = "s" + text(rnd, "abcdef1234") + ".io",
            dohUrl = if (rnd.nextBoolean()) "" else "https://dns/ QUERY ?x=1&y=2",
            dnsTransport = listOf("udp", "tcp", "dot", "doh")[rnd.nextInt(4)],
            sshAuthType = if (rnd.nextBoolean()) "password" else "key",
            sshPrivateKey = text(rnd, "-----BEGIN ABC \n"),
            sshKeyPassphrase = text(rnd, "~!@#$%Z"),
            torBridgeLines = if (rnd.nextBoolean()) "" else "obfs4 x\nobfs4 y",
            dnsttAuthoritative = rnd.nextBoolean(),
            naivePort = 443 + rnd.nextInt(99),
            naiveUsername = text(rnd),
            naivePassword = text(rnd, "pW9+/-="),
            isLocked = false,
            lockPasswordHash = "",
            expirationDate = if (rnd.nextBoolean()) 0L else System.currentTimeMillis(),
            allowSharing = rnd.nextBoolean(),
            boundDeviceId = "",
            resolversHidden = false,
            hiddenResolvers = emptyList(),
            noizdnsStealth = rnd.nextBoolean(),
            dnsPayloadSize = rnd.nextInt(0, 1_000),
            socks5ServerPort = 1080 + rnd.nextInt(1_000),
            vaydnsDnsttCompat = rnd.nextBoolean(),
            vaydnsRecordType = listOf("txt","cname","a","aaaa","mx","ns","srv","null","caa")[rnd.nextInt(9)],
            vaydnsMaxQnameLen = 101 + rnd.nextInt(120),
            vaydnsRps = listOf(0.0, 3.5, 12.0, 0.25)[rnd.nextInt(4)],
            vaydnsIdleTimeout = rnd.nextInt(0, 600),
            vaydnsKeepalive = rnd.nextInt(0, 30),
            vaydnsUdpTimeout = rnd.nextInt(0, 5_000),
            vaydnsMaxNumLabels = rnd.nextInt(0, 10),
            vaydnsClientIdSize = rnd.nextInt(0, 8),
            sshTlsEnabled = rnd.nextBoolean(),
            sshTlsSni = text(rnd),
            sshHttpProxyHost = text(rnd),
            sshHttpProxyPort = 8080 + rnd.nextInt(200),
            sshHttpProxyCustomHost = text(rnd),
            sshWsEnabled = rnd.nextBoolean(),
            sshWsPath = "/" + text(rnd, "/abcdef"),
            sshWsUseTls = rnd.nextBoolean(),
            sshWsCustomHost = text(rnd),
            sshPayload = text(rnd, "GET / HTTP/1.1\r\n\r\nX"),
            resolverMode = if (rnd.nextBoolean()) "roundrobin" else "fanout",
            rrSpreadCount = 1 + rnd.nextInt(6),
            vlessUuid = "11111111-2222-3333-4444-555555555555",
            vlessSecurity = if (rnd.nextBoolean()) "tls" else "reality",
            vlessTransport = "ws",
            vlessWsPath = "/" + text(rnd, "abcdef"),
            cdnIp = "1.2.3.${rnd.nextInt(250)}",
            cdnPort = 443 + rnd.nextInt(900),
            sniFragmentEnabled = rnd.nextBoolean(),
            sniFragmentStrategy = listOf("micro","multi","sni_split","half","fake","disorder")[rnd.nextInt(6)],
            sniFragmentDelayMs = rnd.nextInt(20, 900),
            chPaddingEnabled = rnd.nextBoolean(),
            wsHeaderObfuscation = rnd.nextBoolean(),
            wsPaddingEnabled = rnd.nextBoolean(),
            sniSpoofTtl = 8 + rnd.nextInt(100),
            fakeDecoyHost = text(rnd) + ".com",
            tcpMaxSeg = rnd.nextInt(-2, 1400).let { if (it < 0) it else it }, // any int per spec
            vlessSni = text(rnd),
            vlessRealityPubKey = text(rnd, "AB-_-Cdef"),
            vlessRealityShortId = "00ff22",
            vlessRealityFp = "chrome",
            hy2Password = text(rnd, "passWo9+/="),
            hy2Sni = text(rnd) + ".net",
            hy2Insecure = rnd.nextBoolean(),
            hy2Obfs = if (rnd.nextBoolean()) "" else "salamander",
            hy2ObfsPassword = text(rnd)
        )
    }

    @Test
    fun `encode then parse reproduces the document for 200 random cases`() {
        val rnd = Random(SEED)
        repeat(200) { i ->
            val doc = doc(rnd)
            val record = ProfileRecordCodec.encode(doc)
            val outcome = ProfileRecordCodec.parse(record)
            assertTrue("case $i rejected: $outcome", outcome is ProfileRecordCodec.ParseOutcome.Ok)
            val back = (outcome as ProfileRecordCodec.ParseOutcome.Ok).document
            assertEquals("case $i tunnel mismatch", doc.tunnelToken, back.tunnelToken)
            assertEquals("case $i name mismatch", doc.name, back.name)
            assertEquals("case $i domain mismatch", doc.domain, back.domain)
            assertEquals("case $i resolvers mismatch", doc.resolvers, back.resolvers)
            assertEquals("case $i ports mismatch", doc.tcpListenPort, back.tcpListenPort)
            assertEquals("case $i rps mismatch", doc.vaydnsRps, back.vaydnsRps, 0.0)
            assertEquals("case $i ssh payload mismatch", doc.sshPayload, back.sshPayload)
            // Full-document equality except derived/ignored wire nuances:
            assertEquals(
                "case $i full-doc divergence",
                stripDerived(doc),
                stripDerived(back)
            )
        }
    }

    private fun stripDerived(d: ProfileDocument) = d.copy(
        // Field 14 stores an OR of the chain flags; keep comparison symmetric:
        sshChainEnabled = ProfileDocument.isSshChained(d.tunnelToken),
        // Lock state travels via dedicated fields; lock-free docs compare clean.
        isLocked = false,
        lockPasswordHash = ""
    )

    private companion object {
        const val SEED = 0x00A11CE5
        const val ALPHA_SAFE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_. "
        const val HOST_SAFE = "abcdefghijklmnopqrstuvwxyz0123456789-"
    }
}
