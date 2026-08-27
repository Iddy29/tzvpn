package com.vpntz.app.config

/**
 * Port for the build-provisioned device AES key used by the legacy
 * `vpntz-enc://` single-profile envelope (framing `[0x01|iv12|ct‖tag]`).
 *
 * The key itself is provisioned outside this repository (build secret) and
 * obtained natively at runtime; production binds it to the existing native
 * accessor, tests inject a standalone implementation.
 *
 * VPN-TZ original interface — implementations must never persist or log the key.
 */
interface DeviceKeyCipher {
    fun decrypt(payload: ByteArray): String
    fun encrypt(plaintext: String): ByteArray

    companion object {
        /** Adapter that keeps plaintext-only contract using external symmetric impls. */
        fun of(
            onEncrypt: (String) -> ByteArray,
            onDecrypt: (ByteArray) -> String
        ): DeviceKeyCipher = object : DeviceKeyCipher {
            override fun decrypt(payload: ByteArray): String = onDecrypt(payload)
            override fun encrypt(plaintext: String): ByteArray = onEncrypt(plaintext)
        }
    }
}
