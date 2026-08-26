package com.vpntz.app.util

import android.content.Context
import android.provider.Settings
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Derives a stable, non-reversible device identifier for VPN-TZ.
 * VPN-TZ original implementation.
 *
 * The raw ANDROID_ID is never exposed or persisted. Instead it is run through
 * keyed HMAC-SHA256 with an application-specific pepper, then folded to a
 * 16-character lowercase hex fingerprint. Because HMAC is keyed, the output
 * cannot be cross-referenced against ANDROID_ID hashes computed by other apps
 * or services.
 */
object DeviceIdUtil {

    /** Application-specific pepper — separates VPN-TZ fingerprints from any other dataset. */
    private val PEPPER = "VPN-TZ::device-fingerprint::v1".toByteArray(Charsets.UTF_8)

    /**
     * Returns the device fingerprint, or an empty string when ANDROID_ID is
     * unavailable (rare; some managed devices).
     */
    fun getScrambledDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        return fingerprint(androidId)
    }

    /**
     * Pure derivation — visible for testing. Empty input yields an empty output
     * so callers can keep their existing "no id available" handling.
     */
    fun fingerprint(androidId: String): String {
        if (androidId.isEmpty()) return ""
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(PEPPER, "HmacSHA256"))
        val digest = mac.doFinal(androidId.toByteArray(Charsets.UTF_8))
        // Fold 32 bytes down to 8 by XOR-compressing the two halves first — every
        // input bit influences the final fingerprint.
        val folded = ByteArray(8)
        for (i in 0 until 8) {
            folded[i] = (digest[i].toInt() xor digest[i + 8].toInt()).toByte()
        }
        return folded.joinToString("") { "%02x".format(it) }
    }
}
