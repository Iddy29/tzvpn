package com.vpntz.app.config

import java.util.Base64

/**
 * Canonical base64 helpers of the VPN-TZ config layer.
 * VPN-TZ original implementation.
 *
 * Encoding always uses the standard alphabet without padding or wrapping.
 * Decoding is tolerant: surrounding whitespace is ignored and the URL-safe
 * alphabet is accepted as a fallback (matching community generators).
 */
object WireBase64 {

    fun encode(bytes: ByteArray): String =
        Base64.getEncoder().withoutPadding().encodeToString(bytes)

    fun encode(text: String): String = encode(text.toByteArray(Charsets.UTF_8))

    fun decode(encoded: String): ByteArray {
        val cleaned = encoded.trim().replace("\n", "").replace("\r", "")
        return try {
            Base64.getDecoder().decode(cleaned)
        } catch (e: IllegalArgumentException) {
            Base64.getUrlDecoder().decode(cleaned)
        }
    }

    /** Decode that refuses empty input; used for structured payloads. */
    fun decodeStrict(encoded: String): ByteArray {
        require(encoded.isNotBlank()) { "empty payload" }
        return decode(encoded)
    }
}
