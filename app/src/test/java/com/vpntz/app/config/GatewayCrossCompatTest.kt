package com.vpntz.app.config

import com.vpntz.app.config.crypto.VaultCrypto
import com.vpntz.app.domain.model.ServerProfile
import com.vpntz.app.domain.model.TunnelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cross-implementation compatibility suite: proves the new layer accepts what
 * the rest of the ecosystem produces and that its own exports loop cleanly.
 * Fixture corpus (when present) is shared with the TzGate Go tests:
 * docs/provenance/fixtures/record_v43_full.json / record_v17_short.json.
 */
class GatewayCrossCompatTest {

    private val gateway = ConfigGateway(
        deviceKeyCipher = DeviceKeyCipher.of(
            onEncrypt = { plain -> /* deterministic test envelope */ byteArrayOf(0x01) + WireBase64.encode(plain).toByteArray() },
            onDecrypt = { payload ->
                check(payload[0] == 0x01.toByte())
                String(payload.copyOfRange(1, payload.size), Charsets.UTF_8)
            }
        )
    )

    @Volatile private var lastDecrypted: String = "<nothing>"

    private val debugGateway = ConfigGateway(
        deviceKeyCipher = DeviceKeyCipher.of(
            onEncrypt = { plain -> VaultCrypto.seal(testAesKey, plain.toByteArray(Charsets.UTF_8)) },
            onDecrypt = { payload ->
                lastDecrypted = String(
                    VaultCrypto.open(testAesKey, payload), Charsets.UTF_8)
                lastDecrypted
            }
        )
    )

    private companion object {
        val testAesKey by lazy {
            com.vpntz.app.config.crypto.VaultCrypto.derivePasswordKey(
                "phase1-test".toCharArray(), ByteArray(16) { 3 }, 1)
        }
    }

    // ---- Legacy scheme acceptance ---------------------------------------------

    @Test
    fun `tzvpn legacy scheme aliases import identically`() {
        val doc = ProfileDocument(tunnelToken = "dnstt", name = "old", domain = "d.x",
            resolvers = listOf(ProfileDocument.Resolver("1.1.1.1")))
        val modern = ConfigGateway.Schemes.PLAIN + WireBase64.encode(ProfileRecordCodec.encode(doc))
        val legacy = modern.replaceFirst("vpntz://", "tzvpn://")

        val modernResult = gateway.parseAndImport(modern, "device-x")
        val legacyResult = gateway.parseAndImport(legacy, "device-x")
        // createdAt/updatedAt are stamped at parse time — ignore them here.
        assertEquals(
            one(modernResult).copy(createdAt = 0, updatedAt = 0),
            one(legacyResult).copy(createdAt = 0, updatedAt = 0)
        )
        assertEquals(emptyList<String>(), warningsOrEmpty(legacyResult))
    }

    @Test
    fun `device-bound foreign profile is skipped with warning`() {
        val doc = ProfileDocument(tunnelToken = "ssh", name = "bound", domain = "s.x",
            boundDeviceId = "someone-else")
        val uri = ConfigGateway.Schemes.PLAIN + WireBase64.encode(ProfileRecordCodec.encode(doc))
        val result = gateway.parseAndImport(uri, "me")
        assertTrue(result is ConfigGateway.ImportResult.Success)
        result as ConfigGateway.ImportResult.Success
        assertEquals(0, result.profiles.size)
        assertTrue(result.warnings.single().contains("different device"))
    }

    @Test
    fun `own exports no longer trigger newer-version warning`() {
        val profile = sampleProfile()
        val uri = gateway.exportSingleProfile(profile)
        val result = gateway.parseAndImport(uri, "")
        result as ConfigGateway.ImportResult.Success
        assertEquals(listOf<String>(), result.warnings.filter { it.contains("newer app version") })
    }

    // ---- Encrypted containers --------------------------------------------------

    @Test
    fun `password bundle roundtrip including enforced inner locks`() {
        val p1 = sampleProfile(name = "a")
        val p2 = sampleProfile(name = "b")
        val bundle = gateway.exportAllProfilesEncrypted(
            listOf(p1, p2), bundlePassword = "Bundle!pw",
            profilePassword = "", expirationDate = 42L,
            allowSharing = false, boundDeviceId = "",
            hideResolvers = true
        )
        // Enforced ⇒ inner profiles locked with bundle password hash.
        val firstPass = gateway.parseAndImport(bundle)
        assertTrue(firstPass is ConfigGateway.ImportResult.NeedsPassword)

        val wrongPw = gateway.parseAndImport(bundle, localDeviceId = "", bundlePassword = "wrong")
        assertTrue(wrongPw is ConfigGateway.ImportResult.Error)
        assertTrue((wrongPw as ConfigGateway.ImportResult.Error).message.contains("Failed to decrypt"))

        val good = gateway.parseAndImport(bundle, localDeviceId = "", bundlePassword = "Bundle!pw")
        assertTrue(
            "bundle import failed: $good",
            good is ConfigGateway.ImportResult.Success
        )
        good as ConfigGateway.ImportResult.Success
        assertEquals(
            "warnings=${good.warnings}",
            2, good.profiles.size
        )
        assertTrue(good.profiles.all { it.isLocked && it.expirationDate == 42L })
        assertTrue(good.warnings.isEmpty())
    }

    @Test
    fun `native vault container opt-in writes new scheme yet imports here`() {
        val bundle = gateway.exportAllProfilesEncrypted(
            listOf(sampleProfile()), bundlePassword = "pw", useNativeVaultContainer = true)
        assertTrue(bundle.startsWith(ConfigGateway.Schemes.VAULT))
        val back = gateway.parseAndImport(bundle, localDeviceId = "", bundlePassword = "pw")
        assertTrue("vault import failed: $back", back is ConfigGateway.ImportResult.Success)
        back as ConfigGateway.ImportResult.Success
        assertEquals("warnings=${back.warnings}", 1, back.profiles.size)
        assertTrue(back.warnings.isEmpty())
    }

    @Test
    fun `single encrypted export imports through device-key port`() {
        val lockedProfile = sampleProfile(name = "locked-one").copy(isLocked = false)
        val uri = debugGateway.exportSingleProfileLocked(
            lockedProfile, password = "inner-secret", allowSharing = true)
        assertTrue(uri.startsWith(ConfigGateway.Schemes.ENCRYPTED))
        val imported = debugGateway.parseAndImport(uri, localDeviceId = "")
        assertTrue(
            "import failed: $imported / decryptedHead=${lastDecrypted.take(40)}",
            imported is ConfigGateway.ImportResult.Success
        )
        imported as ConfigGateway.ImportResult.Success
        assertEquals(
            "profiles=${imported.profiles} warnings=${imported.warnings}",
            1, imported.profiles.size
        )
        assertTrue(imported.profiles[0].isLocked)
        assertTrue(VaultCrypto.verifyLockPassword("inner-secret", imported.profiles[0].lockPasswordHash))
        assertTrue(imported.warnings.isEmpty())
    }

    @Test
    fun `export rejection messages match baseline`() {
        val locked = sampleProfile().copy(isLocked = true, lockPasswordHash = "x:y")
        try {
            gateway.exportSingleProfile(locked); throw AssertionError("expected throw")
        } catch (e: IllegalStateException) {
            assertEquals("Cannot export a locked profile", e.message)
        }
        try {
            gateway.reExportLockedProfile(sampleProfile().copy(isLocked = false)); throw AssertionError()
        } catch (e: IllegalStateException) {
            assertEquals("Profile is not locked", e.message)
        }
        try {
            gateway.reExportLockedProfile(sampleProfile().copy(isLocked = true)); throw AssertionError()
        } catch (e: IllegalStateException) {
            assertEquals("Profile does not allow re-sharing", e.message)
        }
    }

    // ---- Tunnel token mapping on domain boundary -------------------------------

    @Test
    fun `sayedns alias lands on NOIZDNS enum`() {
        val doc = ProfileDocument(tunnelToken = "sayedns", name = "n", domain = "n.x")
        val record = ProfileRecordCodec.encode(doc)
        val result = gateway.parseAndImport(
            ConfigGateway.Schemes.PLAIN + WireBase64.encode(record), localDeviceId = "")
        result as ConfigGateway.ImportResult.Success
        assertEquals(TunnelType.NOIZDNS, result.profiles[0].tunnelType)
    }

    // ---- Shared Go fixtures ------------------------------------------------------

    private val stringInArray = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

    @Test
    fun `fixture corpus from repository decodes exactly`() {
        val file = File("../docs/provenance/fixtures/record_v43_full.json")
        org.junit.Assume.assumeTrue(file.exists())
        val fields = readFields(file)
        assertEquals(87, fields.size)
        val record = fields.joinToString("|")
        val ok = ProfileRecordCodec.parse(record) as ProfileRecordCodec.ParseOutcome.Ok
        // The full fixture re-encodes to identical field sequence:
        val out = ProfileRecordCodec.encode(ok.document).split("|")
        assertEquals(fields, out)
    }

    @Test
    fun `short fixture (Go v17 generator shape) accepts with defaults`() {
        val f = File("../docs/provenance/fixtures/record_v17_short.json")
        org.junit.Assume.assumeTrue(f.exists())
        val fields = readFields(f)
        assertEquals(60, fields.size)
        val outcome = ProfileRecordCodec.parse(fields.joinToString("|"))
        assertTrue(outcome is ProfileRecordCodec.ParseOutcome.Ok)
    }

    private fun readFields(f: File): List<String> =
        stringInArray.findAll(f.readText()).map { it.groupValues[1] }.toList()

    private fun one(result: ConfigGateway.ImportResult): ServerProfile =
        when (result) {
            is ConfigGateway.ImportResult.Success -> result.profiles.single()
            else -> throw AssertionError("expected success")
        }

    private fun warningsOrEmpty(result: ConfigGateway.ImportResult): List<String> =
        (result as? ConfigGateway.ImportResult.Success)?.warnings ?: emptyList()

    private fun sampleProfile(name: String = "sample"): ServerProfile =
        ServerProfile(
            name = name,
            domain = "vpn.example.com",
            resolvers = listOf(
                com.vpntz.app.domain.model.DnsResolver("9.9.9.9", 53, true),
                com.vpntz.app.domain.model.DnsResolver("1.0.0.1", 53, false)
            ),
            keepAliveInterval = 6000,
            tcpListenPort = 1085
        )
}
