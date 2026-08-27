package com.vpntz.app.config.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic primitives for the VPN-TZ configuration layer.
 * VPN-TZ original implementation — only standard platform APIs are used:
 *
 *  - key derivation: PBKDF2-HMAC-SHA256
 *  - authenticated encryption: AES-256-GCM (96-bit IV, 128-bit tag)
 *  - password verification hashing: SHA-256 with random prefix salt
 *
 * No custom primitives, no key material hard-coded anywhere in this module.
 */
object VaultCrypto {

    const val GCM_TAG_BITS = 128
    const val GCM_IV_BYTES = 12

    class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val random = SecureRandom()

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { random.nextBytes(it) }

    // ---- Key derivation ------------------------------------------------------

    /**
     * Derives an AES-256 key from [password] and [salt]. The iteration count is
     * a parameter so legacy (600k) and native (700k) formats stay distinct.
     */
    fun derivePasswordKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        require(iterations > 0) { "iteration count must be positive" }
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    // ---- AES-GCM -------------------------------------------------------------

    /** Encrypts [plaintext]; output layout: `[iv][ciphertext‖tag]`. */
    fun seal(key: SecretKeySpec, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val iv = randomBytes(GCM_IV_BYTES)
        val sealed = seal(key, plaintext, iv, aad)
        return iv + sealed
    }

    /** Encrypts with a caller-chosen IV — test vector support. */
    fun seal(key: SecretKeySpec, plaintext: ByteArray, iv: ByteArray, aad: ByteArray?): ByteArray {
        require(iv.size == GCM_IV_BYTES) { "IV must be ${GCM_IV_BYTES} bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(plaintext)
    }

    /**
     * Decrypts `[iv][ciphertext‖tag]`. Throws [CryptoException] on tag mismatch,
     * wrong keys or truncated input — callers must treat every failure as
     * "wrong key or tampered data".
     */
    fun open(key: SecretKeySpec, sealed: ByteArray, aad: ByteArray? = null): ByteArray {
        if (sealed.size <= GCM_IV_BYTES + GCM_TAG_BITS / 8) {
            throw CryptoException("sealed payload too short")
        }
        val iv = sealed.copyOfRange(0, GCM_IV_BYTES)
        val body = sealed.copyOfRange(GCM_IV_BYTES, sealed.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            aad?.let { cipher.updateAAD(it) }
            cipher.doFinal(body)
        } catch (e: Exception) {
            throw CryptoException("authentication failed", e)
        }
    }

    // ---- Legacy lock-hash format (field 32 of the pipe record) ---------------
    // Fixed wire format shared across app versions; hex(salt16):hex(sha256(salt||pw)).

    private fun hexLower(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun hashLockPassword(password: String): String {
        val salt = randomBytes(16)
        return "${hexLower(salt)}:${hashLockPassword(password, salt)}"
    }

    fun hashLockPassword(password: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return hexLower(digest.digest(password.toByteArray(Charsets.UTF_8)))
    }

    fun verifyLockPassword(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2 || parts[0].length != 32 || parts[1].length != 64) return false
        val salt = parts[0].chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        if (salt.size != 16) return false
        // Constant-time comparison over the hash bytes.
        return MessageDigest.isEqual(
            parts[1].toByteArray(Charsets.US_ASCII),
            hashLockPassword(password, salt).toByteArray(Charsets.US_ASCII)
        )
    }
}
