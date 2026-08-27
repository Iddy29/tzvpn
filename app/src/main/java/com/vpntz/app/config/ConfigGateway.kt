package com.vpntz.app.config

import com.vpntz.app.config.crypto.Envelopes
import com.vpntz.app.config.crypto.VaultCrypto
import com.vpntz.app.domain.model.ServerProfile

/**
 * Facade of the independent VPN-TZ configuration layer (Phase 1).
 * VPN-TZ original implementation.
 *
 * Public surface intentionally mirrors the observable contract consumed by the
 * application so the swap-in is behaviour-compatible; internals are new:
 *
 *  - tolerant multi-format importer (vpntz://, legacy tzvpn:// family,
 *    vless://, hysteria2://, encrypted envelopes)
 *  - strict v43 record emitter with per-field sanitization
 *  - password containers: legacy envelope read + `vpntz-vault://` writer
 */
class ConfigGateway(
    /** Decrypt/encrypt bridge for device-key (`vpntz-enc://`) payloads. */
    private val deviceKeyCipher: DeviceKeyCipher?
) {

    sealed interface ImportResult {
        data class Success(val profiles: List<ServerProfile>, val warnings: List<String>) : ImportResult
        data class Error(val message: String) : ImportResult
        data object NeedsPassword : ImportResult
    }

    fun parseAndImport(
        input: String,
        localDeviceId: String = "",
        bundlePassword: String? = null
    ): ImportResult {
        // 1. Accept historical scheme spellings transparently.
        val normalized = input
            .replace("tzvpn-bundle-enc://", Schemes.BUNDLE_ENCRYPTED, ignoreCase = true)
            .replace("tzvpn-enc://", Schemes.ENCRYPTED, ignoreCase = true)
            .replace("tzvpn://", Schemes.PLAIN, ignoreCase = true)

        val trimmed = normalized.trim()
        if (trimmed.isEmpty()) return ImportResult.Error("No profiles found in input")

        var text = trimmed
        if (trimmed.startsWith(Schemes.BUNDLE_ENCRYPTED, ignoreCase = true)) {
            val payload = try {
                WireBase64.decodeStrict(trimmed.substring(Schemes.BUNDLE_ENCRYPTED.length))
            } catch (_: Exception) {
                return ImportResult.Error("Failed to decode encrypted bundle")
            }
            val key = bundlePassword?.takeIf { it.isNotEmpty() }
                ?: return ImportResult.NeedsPassword
            text = try {
                Envelopes.openLegacyPasswordEnvelope(payload, key.toCharArray())
            } catch (e: Exception) {
                return ImportResult.Error("Failed to decrypt bundle")
            }
        }

        // New native container: unwrap BEFORE line processing so its inner
        // lines flow through the exact same scheme-aware loop as bundles.
        if (trimmed.startsWith(Schemes.VAULT, ignoreCase = true)) {
            val key = bundlePassword?.takeIf { it.isNotEmpty() }
                ?: return ImportResult.NeedsPassword
            val inner = try {
                Envelopes.openVault(
                    WireBase64.decodeStrict(trimmed.substring(Schemes.VAULT.length)),
                    key.toCharArray())
            } catch (e: Exception) {
                return ImportResult.Error("Failed to decrypt bundle")
            }
            text = inner
        }

        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ImportResult.Error("No profiles found in input")

        val profiles = mutableListOf<ServerProfile>()
        val warnings = mutableListOf<String>()

        for ((index0, rawLine) in lines.withIndex()) {
            val lineNo = index0 + 1
            val line = rawLine.trim()
            when {
                CommunityUriCodec.isVless(line) ->
                    consumeCommunity(CommunityUriCodec.parseVless(line), lineNo, profiles, warnings)

                CommunityUriCodec.isHysteria2(line) ->
                    consumeCommunity(CommunityUriCodec.parseHysteria2(line), lineNo, profiles, warnings)

                line.startsWith(Schemes.PLAIN, ignoreCase = true) ->
                    handleRecord(
                        decodeLineBody(line.removePrefix(Schemes.PLAIN), lineNo, warnings) ?: continue,
                        lineNo, localDeviceId, warnings, profiles)

                line.startsWith(Schemes.ENCRYPTED, ignoreCase = true) -> {
                    val cipher = deviceKeyCipher
                    if (cipher == null) {
                        warnings.add("Line $lineNo: this build cannot decrypt locked profiles")
                        continue
                    }
                    val payload = try {
                        WireBase64.decodeStrict(line.removePrefix(Schemes.ENCRYPTED))
                    } catch (_: Exception) {
                        warnings.add("Line $lineNo: Failed to decode, skipping")
                        continue
                    }
                    val record = try {
                        cipher.decrypt(payload)
                    } catch (_: Exception) {
                        warnings.add("Line $lineNo: Failed to decrypt, skipping")
                        continue
                    }
                    handleRecord(record, lineNo, localDeviceId, warnings, profiles)
                }

                else -> warnings.add("Line $lineNo: Invalid format, skipping")
            }
        }
        return ImportResult.Success(profiles, warnings)
    }

    // ---- scheme vocabulary ------------------------------------------------------

    object Schemes {
        const val PLAIN = "vpntz://"
        const val ENCRYPTED = "vpntz-enc://"
        const val BUNDLE_ENCRYPTED = "vpntz-bundle-enc://"
        const val VAULT = "vpntz-vault://"
    }

    // ---- community routing -------------------------------------------------------

    private fun consumeCommunity(
        outcome: CommunityUriCodec.Result,
        lineNo: Int,
        sink: MutableList<ServerProfile>,
        warnings: MutableList<String>
    ) {
        when (outcome) {
            is CommunityUriCodec.Result.Ok -> sink += ProfileBridge.toDomain(outcome.document)
            is CommunityUriCodec.Result.SoftReject -> warnings.add("Line $lineNo: ${outcome.reason}")
            is CommunityUriCodec.Result.Reject -> warnings.add("Line $lineNo: ${outcome.reason}")
        }
    }

    // ---- helpers -----------------------------------------------------------------

    private fun decodeLineBody(encodedPart: String, lineNo: Int, warnings: MutableList<String>): String? =
        try {
            String(WireBase64.decodeStrict(encodedPart), Charsets.UTF_8)
        } catch (_: Exception) {
            warnings.add("Line $lineNo: Failed to decode, skipping")
            null
        }

    internal fun handleRecord(
        record: String,
        lineNo: Int,
        localDeviceId: String,
        warnings: MutableList<String>,
        sink: MutableList<ServerProfile>
    ) {
        when (val outcome = ProfileRecordCodec.parse(record)) {
            is ProfileRecordCodec.ParseOutcome.Bad ->
                warnings.add("Line $lineNo: ${outcome.reason}, skipping")
            is ProfileRecordCodec.ParseOutcome.Ok -> {
                val doc = outcome.document
                if (doc.boundDeviceId.isNotEmpty() && localDeviceId.isNotEmpty() &&
                    doc.boundDeviceId != localDeviceId
                ) {
                    warnings.add("Line $lineNo: Profile is bound to a different device, skipping")
                } else {
                    sink += ProfileBridge.toDomain(doc)
                    if (outcome.newerThanKnown) {
                        warnings.add("Line $lineNo: Exported from a newer app version - some settings may be missing")
                    }
                }
            }
        }
    }

    // ============================ EXPORT ========================================

    /** Single plain profile. Locked profiles are rejected like before. */
    fun exportSingleProfile(profile: ServerProfile, hideResolvers: Boolean = false): String {
        if (profile.isLocked) throw IllegalStateException("Cannot export a locked profile")
        return encodeUri(ProfileBridge.toDocument(profile), hideResolvers)
    }

    /**
     * Password-locked single profile using the native device-key envelope via
     * [deviceKeyCipher]; requires that the port was provided.
     */
    fun exportSingleProfileLocked(
        profile: ServerProfile,
        password: String,
        expirationDate: Long = 0,
        allowSharing: Boolean = false,
        boundDeviceId: String = "",
        hideResolvers: Boolean = false
    ): String {
        val cipher = deviceKeyCipher
            ?: throw IllegalStateException("device encryption unavailable in this build")
        val hash = VaultCrypto.hashLockPassword(password)
        val doc = ProfileBridge.toDocument(
            profile.copy(
                isLocked = true, lockPasswordHash = hash,
                expirationDate = expirationDate, allowSharing = allowSharing,
                boundDeviceId = boundDeviceId
            )
        )
        return Schemes.ENCRYPTED + WireBase64.encode(cipher.encrypt(ProfileRecordCodec.encode(doc)))
    }

    fun exportAllProfiles(profiles: List<ServerProfile>): String =
        profiles.filter { !it.isLocked }.joinToString("\n") {
            encodeUri(ProfileBridge.toDocument(it), hideResolvers = false)
        }

    fun exportAllProfilesEncrypted(
        profiles: List<ServerProfile>,
        bundlePassword: String,
        profilePassword: String = "",
        expirationDate: Long = 0,
        allowSharing: Boolean = false,
        boundDeviceId: String = "",
        hideResolvers: Boolean = false,
        useNativeVaultContainer: Boolean = false
    ): String {
        require(bundlePassword.isNotEmpty()) { "Bundle password must not be empty" }
        val exportable = profiles.filter { !it.isLocked }
        require(exportable.isNotEmpty()) { "No exportable profiles" }

        val enforce = profilePassword.isNotEmpty() || expirationDate > 0 ||
            allowSharing || boundDeviceId.isNotEmpty() || hideResolvers
        val prepared: List<ProfileDocument> = if (enforce) {
            val lockPassword = if (profilePassword.isNotEmpty()) profilePassword else bundlePassword
            val hash = VaultCrypto.hashLockPassword(lockPassword)
            exportable.map { p ->
                ProfileBridge.toDocument(
                    p.copy(isLocked = true, lockPasswordHash = hash,
                        expirationDate = expirationDate, allowSharing = allowSharing,
                        boundDeviceId = boundDeviceId)
                )
            }
        } else {
            exportable.map { ProfileBridge.toDocument(it) }
        }
        val bundle = prepared.joinToString("\n") { doc ->
            // Each inner line is a complete vpntz:// URI (baseline bundle shape).
            Schemes.PLAIN + WireBase64.encode(ProfileRecordCodec.encode(doc))
        }
        return if (useNativeVaultContainer) {
            Schemes.VAULT + WireBase64.encode(Envelopes.sealVault(bundle, bundlePassword.toCharArray()))
        } else {
            Schemes.BUNDLE_ENCRYPTED +
                WireBase64.encode(Envelopes.sealLegacyPasswordEnvelope(bundle, bundlePassword.toCharArray()))
        }
    }

    /** Re-export an existing unlocked-to-share locked profile (legacy flow). */
    fun reExportLockedProfile(profile: ServerProfile): String {
        if (!profile.isLocked) throw IllegalStateException("Profile is not locked")
        if (!profile.allowSharing) throw IllegalStateException("Profile does not allow re-sharing")
        val cipher = deviceKeyCipher
            ?: throw IllegalStateException("device encryption unavailable in this build")
        return Schemes.ENCRYPTED + WireBase64.encode(cipher.encrypt(ProfileRecordCodec.encode(ProfileBridge.toDocument(profile))))
    }

    private fun encodeUri(doc: ProfileDocument, hideResolvers: Boolean): String =
        Schemes.PLAIN + WireBase64.encode(ProfileRecordCodec.encode(doc))

    companion object {
        /**
         * Compat surface only — verifies lock passwords using the documented
         * legacy hash layout without touching native code.
         */
        @JvmStatic
        fun verifyLock(password: String, storedHash: String): Boolean =
            VaultCrypto.verifyLockPassword(password, storedHash)
    }
}
