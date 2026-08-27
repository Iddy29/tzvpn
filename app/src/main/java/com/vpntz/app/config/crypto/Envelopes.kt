package com.vpntz.app.config.crypto

import java.io.ByteArrayOutputStream

/**
 * Encrypted containers of the VPN-TZ configuration layer.
 * VPN-TZ original implementation — framing specified in WIRE_FORMAT.md.
 *
 *  container "vault" (new, VPN-TZ native):
 *      magic "VTZ1" | salt16 | [iv12 | ct‖tag]   ; PBKDF2 700_000 iters
 *      AAD binds: "vpntz-vault\u0001"
 *
 *  legacy password envelope (read support only; ver byte 0x01):
 *      0x01 | salt16 | iv12 | ct‖tag             ; PBKDF2 600_000 iters
 *
 *  legacy device-key envelope (read/write via injected cipher; ver byte 0x01):
 *      0x01 | iv12 | ct‖tag                      ; key = native device key
 */
object Envelopes {

    // ---- New vault container -------------------------------------------------

    val VAULT_MAGIC = "VTZ1".toByteArray(Charsets.US_ASCII)
    const val VAULT_SALT_BYTES = 16
    const val VAULT_ITERATIONS = 700_000
    private val VAULT_AAD = "vpntz-vault\u0001".toByteArray(Charsets.UTF_8)

    fun sealVault(plaintext: String, password: CharArray): ByteArray {
        require(password.isNotEmpty()) { "password must not be empty" }
        val salt = VaultCrypto.randomBytes(VAULT_SALT_BYTES)
        val key = VaultCrypto.derivePasswordKey(password, salt, VAULT_ITERATIONS)
        val body = VaultCrypto.seal(key, plaintext.toByteArray(Charsets.UTF_8), VAULT_AAD)
        return ByteArrayOutputStream().apply {
            write(VAULT_MAGIC); write(salt); write(body)
        }.toByteArray()
    }

    fun openVault(container: ByteArray, password: CharArray): String {
        val minLen = VAULT_MAGIC.size + VAULT_SALT_BYTES + VaultCrypto.GCM_IV_BYTES + VaultCrypto.GCM_TAG_BITS / 8
        if (container.size < minLen ||
            !container.copyOfRange(0, VAULT_MAGIC.size).contentEquals(VAULT_MAGIC)) {
            throw VaultCrypto.CryptoException("not a VPN-TZ vault container")
        }
        val salt = container.copyOfRange(VAULT_MAGIC.size, VAULT_MAGIC.size + VAULT_SALT_BYTES)
        val body = container.copyOfRange(VAULT_MAGIC.size + VAULT_SALT_BYTES, container.size)
        val key = VaultCrypto.derivePasswordKey(password, salt, VAULT_ITERATIONS)
        return String(VaultCrypto.open(key, body, VAULT_AAD), Charsets.UTF_8)
    }

    // ---- Legacy password envelope (0x01|salt|iv|ct‖tag) ------------------------

    private const val LEGACY_HEADER = 1 + 16 + 12
    const val LEGACY_ITERATIONS = 600_000

    fun openLegacyPasswordEnvelope(payload: ByteArray, password: CharArray): String {
        if (payload.size < LEGACY_HEADER + VaultCrypto.GCM_TAG_BITS / 8) {
            throw VaultCrypto.CryptoException("legacy envelope truncated")
        }
        if (payload[0] != 0x01.toByte()) {
            throw VaultCrypto.CryptoException("unsupported legacy envelope version")
        }
        val salt = payload.copyOfRange(1, 17)
        val key = VaultCrypto.derivePasswordKey(password, salt, LEGACY_ITERATIONS)
        // Body = [iv12 | ct‖tag] — starts AFTER the salt (offset 17).
        val text = try {
            String(VaultCrypto.open(key, payload.copyOfRange(17, payload.size)), Charsets.UTF_8)
        } catch (e: VaultCrypto.CryptoException) {
            throw VaultCrypto.CryptoException("wrong password or corrupted bundle", e)
        }
        return text
    }

    fun sealLegacyPasswordEnvelope(plaintext: String, password: CharArray): ByteArray =
        sealLegacyPasswordEnvelope(
            plaintext, password,
            VaultCrypto.randomBytes(16), VaultCrypto.randomBytes(VaultCrypto.GCM_IV_BYTES)
        )

    /** Deterministic variant for test vectors. */
    fun sealLegacyPasswordEnvelope(
        plaintext: String,
        password: CharArray,
        salt: ByteArray,
        iv: ByteArray
    ): ByteArray {
        val key = VaultCrypto.derivePasswordKey(password, salt, LEGACY_ITERATIONS)
        // Wire layout requires the IV between salt and ciphertext.
        val body = VaultCrypto.seal(key, plaintext.toByteArray(Charsets.UTF_8), iv, null)
        return byteArrayOf(0x01) + salt + iv + body
    }
}
