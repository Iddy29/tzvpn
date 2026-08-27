package com.vpntz.app.config

import com.vpntz.app.config.crypto.Envelopes
import com.vpntz.app.config.crypto.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoAndEnvelopeTest {

    private val key = VaultCrypto.derivePasswordKey("pw".toCharArray(), ByteArray(16) { 1 }, 1)

    // ---- AES-GCM primitives -----------------------------------------------------

    @Test
    fun `seal open roundtrip with aad`() {
        val aad = "ctx".toByteArray()
        val sealed = VaultCrypto.seal(key, "hello vpn-tz".toByteArray(), aad)
        assertArrayEquals("hello vpn-tz".toByteArray(), VaultCrypto.open(key, sealed, aad))
    }

    @Test(expected = VaultCrypto.CryptoException::class)
    fun `tampered ciphertext is rejected`() {
        val sealed = VaultCrypto.seal(key, "payload".toByteArray())
        sealed[sealed.size - 3] = (sealed[sealed.size - 3].toInt() xor 0x40).toByte()
        VaultCrypto.open(key, sealed)
    }

    @Test(expected = VaultCrypto.CryptoException::class)
    fun `aad mismatch is rejected`() {
        val sealed = VaultCrypto.seal(key, "p".toByteArray(), byteArrayOf(1))
        VaultCrypto.open(key, sealed, byteArrayOf(2))
    }

    @Test
    fun `iv uniqueness across seals`() {
        val a = VaultCrypto.seal(key, "same".toByteArray())
        val b = VaultCrypto.seal(key, "same".toByteArray())
        assertTrue(!a.copyOfRange(0, 12).contentEquals(b.copyOfRange(0, 12)))
    }

    @Test
    fun `oversized plaintext still roundtrips`() {
        val blob = ByteArray(8 * 1024 * 1024) { (it % 251).toByte() }
        assertArrayEquals(blob, VaultCrypto.open(key, VaultCrypto.seal(key, blob)))
    }

    // ---- Lock-hash format --------------------------------------------------------

    @Test
    fun `lock hash verifies and rejects`() {
        val stored = VaultCrypto.hashLockPassword("s3cret")
        assertTrue(VaultCrypto.verifyLockPassword("s3cret", stored))
        assertEquals(false, VaultCrypto.verifyLockPassword("wrong", stored))
        assertNotEquals(stored, VaultCrypto.hashLockPassword("s3cret")) // unique salts
        assertTrue(!VaultCrypto.verifyLockPassword("x", "not-a-valid-layout"))
    }

    // ---- New vault container ------------------------------------------------------

    @Test
    fun `vault container roundtrip and framing`() {
        val c = Envelopes.sealVault("line1\nline2", "pw123".toCharArray())
        assertEquals("VTZ1", String(c.copyOfRange(0, 4), Charsets.US_ASCII))
        assertTrue("container shorter than minimum framing", c.size >= 4 + 16 + 12 + 16)
        assertEquals("line1\nline2", Envelopes.openVault(c, "pw123".toCharArray()))
    }

    @Test(expected = VaultCrypto.CryptoException::class)
    fun `vault wrong password rejected`() {
        val c = Envelopes.sealVault("secret bundle", "right".toCharArray())
        Envelopes.openVault(c, "wrong".toCharArray())
    }

    @Test(expected = VaultCrypto.CryptoException::class)
    fun `vault truncated container rejected`() {
        Envelopes.openVault(ByteArray(20), "x".toCharArray())
    }

    @Test(expected = VaultCrypto.CryptoException::class)
    fun `vault tampered body rejected`() {
        val c = Envelopes.sealVault("data", "pw".toCharArray()).also { it[it.size - 5]++ }
        Envelopes.openVault(c, "pw".toCharArray())
    }

    @Test
    fun `vault iteration count stays distinct from legacy format`() {
        assertEquals(700_000, Envelopes.VAULT_ITERATIONS)
        assertEquals(600_000, Envelopes.LEGACY_ITERATIONS)
    }

    // ---- Legacy password envelope (deterministic vectors) --------------------------

    @Test
    fun `legacy envelope framing matches spec layout`() {
        val salt = ByteArray(16) { 7 }
        val iv = ByteArray(12) { 9 }
        val payload = Envelopes.sealLegacyPasswordEnvelope("round", "key".toCharArray(), salt, iv)
        val hex = payload.joinToString("") { "%02x".format(it) }
        assertEquals(0x01.toByte(), payload[0])
        assertArrayEquals(
            "salt slice wrong; hex=$hex",
            salt, payload.copyOfRange(1, 17))
        assertArrayEquals(
            "iv slice wrong; hex=$hex",
            iv, payload.copyOfRange(17, 29))
        // Decrypt via independent re-derivation of the documented KDF:
        val kdfKey = VaultCrypto.derivePasswordKey("key".toCharArray(), salt, 600_000)
        assertArrayEquals(
            "round".toByteArray(),
            VaultCrypto.open(kdfKey, payload.copyOfRange(17, payload.size)))
    }

    @Test(expected = VaultCrypto.CryptoException::class)
    fun `legacy envelope version byte must be 0x01`() {
        val bad = Envelopes.sealLegacyPasswordEnvelope("t", "k".toCharArray()).also {
            it[0] = 0x02
        }
        Envelopes.openLegacyPasswordEnvelope(bad, "k".toCharArray())
    }

    @Test(expected = VaultCrypto.CryptoException::class)
    fun `legacy envelope truncation rejected`() {
        Envelopes.openLegacyPasswordEnvelope(byteArrayOf(0x01, 1, 2), "k".toCharArray())
    }

    // ---- Device-key envelope port ---------------------------------------------------

    @Test
    fun `device-key cipher port roundtrips without leaking key handling`() {
        // Stand-in for the production native bridge (JVM-testable equivalent).
        val cipher = DeviceKeyCipher.of(
            onEncrypt = { VaultCrypto.seal(key, it.toByteArray(Charsets.UTF_8)) },
            onDecrypt = { String(VaultCrypto.open(key, it), Charsets.UTF_8) }
        )
        val frame = cipher.encrypt("43|dnstt|name|d.x")
        assertTrue(frame.isNotEmpty())
        assertEquals("43|dnstt|name|d.x", cipher.decrypt(frame))
    }
}
