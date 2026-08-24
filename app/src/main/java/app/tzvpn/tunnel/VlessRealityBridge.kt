package app.tzvpn.tunnel

import android.util.Base64
import vlessreality.Client
import vlessreality.Vlessreality
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * VLESS over REALITY tunnel bridge.
 *
 * Wraps the vlessreality Go mobile client (gomobile AAR) which provides a
 * local SOCKS5 server tunneling through a REALITY-authenticated TLS session
 * carrying the VLESS protocol (raw TCP transport):
 *
 * App -> hev-socks5-tunnel -> VlessRealityBridge SOCKS5 (listenPort)
 *   -> REALITY (uTLS fingerprint + auth) -> VLESS -> Server -> Internet
 */
object VlessRealityBridge {
    private const val TAG = "VlessRealityBridge"

    private var client: Client? = null
    private val running = AtomicBoolean(false)
    private val tunnelTxBytes = AtomicLong(0)
    private val tunnelRxBytes = AtomicLong(0)

    fun start(
        listenPort: Int,
        listenHost: String = "127.0.0.1",
        serverHost: String,
        serverPort: Int,
        uuid: String,
        sni: String,
        publicKey: String,
        shortId: String,
        fingerprint: String
    ): Result<Unit> {
        app.tzvpn.util.AppLog.i(TAG, "Starting VLESS+REALITY bridge on $listenHost:$listenPort " +
            "server=$serverHost:$serverPort sni=$sni fp=$fingerprint")

        stop()

        return try {
            // Validate public key early (base64url raw 32 bytes)
            val pkBytes = try {
                Base64.decode(publicKey.replace('-', '+').replace('_', '/'), Base64.NO_WRAP)
            } catch (_: Exception) {
                byteArrayOf()
            }
            if (pkBytes.size != 32) {
                return Result.failure(IllegalArgumentException("REALITY public key must be 32 bytes base64url"))
            }

            val c: Client = Vlessreality.newClient(
                "$listenHost:$listenPort",
                "$serverHost:$serverPort",
                uuid,
                sni,
                publicKey,
                shortId,
                fingerprint.ifBlank { "chrome" }
            )
            c.start()
            client = c
            running.set(true)
            app.tzvpn.util.AppLog.i(TAG, "VLESS+REALITY bridge started")
            Result.success(Unit)
        } catch (e: Exception) {
            app.tzvpn.util.AppLog.e(TAG, "Failed to start VLESS+REALITY bridge: ${e.message}", e)
            stop()
            Result.failure(e)
        }
    }

    fun stop() {
        running.set(false)
        try {
            client?.stop()
        } catch (_: Exception) {}
        client = null
    }

    fun isRunning(): Boolean = running.get() && client?.isRunning() == true
    fun isClientHealthy(): Boolean = isRunning()
    fun getTunnelTxBytes(): Long = tunnelTxBytes.get()
    fun getTunnelRxBytes(): Long = tunnelRxBytes.get()

    fun resetTrafficStats() {
        tunnelTxBytes.set(0)
        tunnelRxBytes.set(0)
    }
}
