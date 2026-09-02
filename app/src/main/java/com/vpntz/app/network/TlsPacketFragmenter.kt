package com.vpntz.app.network

import java.util.Random

/**
 * The deterministic, byte-level core of TLS ClientHello fragmentation used for
 * DPI bypass. `SniFragmentForwarder` owns socket/threading and delegates the
 * packet math here.
 *
 * All functions are pure JVM and deterministic given a supplied [Random]
 * (the caller passes a [java.security.SecureRandom] in production and a seeded
 * [java.util.Random] in tests). Offsets are relative to the handshake
 * payload (the TLS record body, i.e. everything after the 5-byte record
 * header), matching the legacy forwarder's convention.
 */
object TlsPacketFragmenter {

    const val STRATEGY_SNI_SPLIT = "sni_split"
    const val STRATEGY_HALF = "half"
    const val STRATEGY_MULTI = "multi"
    const val STRATEGY_MICRO = "micro"

    private const val MULTI_CHUNK_MIN = 16
    private const val MULTI_CHUNK_MAX = 40

    /** True when [data] begins with a TLS Handshake/ClientHello record. */
    fun isClientHello(data: ByteArray): Boolean =
        data.size > 5 &&
                data[0] == 0x16.toByte() &&
                data[5] == 0x01.toByte()

    /**
     * Wrap a slice of the handshake payload in a TLS record with its own 5-byte
     * header. The returned record is a legitimate per-RFC 8446 fragment.
     */
    fun buildTlsRecord(
        contentType: Byte,
        versionMajor: Byte,
        versionMinor: Byte,
        payload: ByteArray,
        offset: Int,
        length: Int
    ): ByteArray {
        val record = ByteArray(5 + length)
        record[0] = contentType
        record[1] = versionMajor
        record[2] = versionMinor
        record[3] = ((length shr 8) and 0xFF).toByte()
        record[4] = (length and 0xFF).toByte()
        System.arraycopy(payload, offset, record, 5, length)
        return record
    }

    /**
     * Offset where the SNI hostname bytes begin inside the handshake payload,
     * or -1 when the payload lacks a usable SNI extension.
     */
    fun findSniHostnameOffset(payload: ByteArray): Int {
        if (payload.size < 39) return -1

        var pos = 4 // handshake header
        pos += 2 // client version
        pos += 32 // random

        if (pos >= payload.size) return -1
        val sessionIdLen = payload[pos].toInt() and 0xFF
        pos += 1 + sessionIdLen

        if (pos + 2 > payload.size) return -1
        val cipherSuitesLen = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
        pos += 2 + cipherSuitesLen

        if (pos + 1 > payload.size) return -1
        val compMethodsLen = payload[pos].toInt() and 0xFF
        pos += 1 + compMethodsLen

        if (pos + 2 > payload.size) return -1
        val extensionsLen = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
        pos += 2
        val extensionsEnd = pos + extensionsLen

        while (pos + 4 <= extensionsEnd && pos + 4 <= payload.size) {
            val extType = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
            val extLen = ((payload[pos + 2].toInt() and 0xFF) shl 8) or (payload[pos + 3].toInt() and 0xFF)
            pos += 4

            if (extType == 0x0000 && extLen > 0) {
                if (pos + 5 <= payload.size) {
                    val hostnameStart = pos + 5
                    if (hostnameStart < payload.size) return hostnameStart
                }
            }
            pos += extLen
        }
        return -1
    }

    /** Length of the SNI hostname (2-byte prefix immediately before [hostStart]). */
    fun sniHostnameLength(payload: ByteArray, hostStart: Int): Int =
        if (hostStart >= 2) {
            ((payload[hostStart - 2].toInt() and 0xFF) shl 8) or (payload[hostStart - 1].toInt() and 0xFF)
        } else 0

    /**
     * Compute the split points for a handshake payload under [strategy].
     * Each split point is an offset *intended to be the start of a new record
     * within the payload* (the record preceding it spans up to that offset).
     */
    fun computeSplitPoints(payload: ByteArray, strategy: String, random: Random): List<Int> =
        when (strategy) {
            STRATEGY_HALF -> halfSplitPoints(payload)
            STRATEGY_MULTI -> multiSplitPoints(payload, random)
            STRATEGY_MICRO -> microSplitPoints(payload)
            else -> sniSplitPoints(payload, random)
        }

    private fun sniSplitPoints(payload: ByteArray, random: Random): List<Int> {
        val sniOffset = findSniHostnameOffset(payload)
        if (sniOffset > 0 && sniOffset < payload.size - 1) {
            val hostnameLen = sniHostnameLength(payload, sniOffset)
            val mid = if (hostnameLen > 2) {
                sniOffset + 1 + random.nextInt(hostnameLen - 1) // random point inside the hostname
            } else {
                sniOffset + (payload.size - sniOffset) / 2
            }
            val splitPoint = mid.coerceIn(2, payload.size - 1)
            // 1-byte lead fragment to defeat DPI that inspects only the first packet.
            return listOf(1, splitPoint)
        }
        return halfSplitPoints(payload)
    }

    private fun halfSplitPoints(payload: ByteArray): List<Int> {
        val mid = 1 + (payload.size - 1) / 2
        return listOf(1, mid)
    }

    private fun multiSplitPoints(payload: ByteArray, random: Random): List<Int> {
        val points = mutableListOf<Int>()
        var pos = 1
        points.add(pos)
        while (pos < payload.size) {
            val chunkSize = MULTI_CHUNK_MIN + random.nextInt(MULTI_CHUNK_MAX - MULTI_CHUNK_MIN + 1)
            pos += chunkSize
            if (pos < payload.size) points.add(pos)
        }
        return points
    }

    private fun microSplitPoints(payload: ByteArray): List<Int> =
        (1 until payload.size).toList()

    /**
     * Build a decoy ClientHello by overwriting [real]'s SNI with [decoy],
     * truncated/padded to the original hostname length so the record's byte
     * offsets stay identical. Returns null when the SNI cannot be located or
     * the hostname is malformed.
     */
    fun buildFakeClientHello(real: ByteArray, decoy: String): ByteArray? {
        if (real.size < 6) return null
        val payload = real.copyOfRange(5, real.size)
        val sniHostOff = findSniHostnameOffset(payload)
        if (sniHostOff < 0) return null
        val hostLen = sniHostnameLength(payload, sniHostOff)
        if (hostLen <= 0 || sniHostOff + hostLen > payload.size) return null

        val decoyBytes = decoy.toByteArray(Charsets.US_ASCII)
        val replacement = ByteArray(hostLen) { ' '.code.toByte() }
        val copyLen = minOf(decoyBytes.size, hostLen)
        System.arraycopy(decoyBytes, 0, replacement, 0, copyLen)

        val fake = real.copyOf()
        System.arraycopy(replacement, 0, fake, 5 + sniHostOff, hostLen)
        return fake
    }
}
