package app.tzvpn.tunnel

import hysteria2.Client
import hysteria2.Hysteria2
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Hysteria2 tunnel bridge.
 *
 * Wraps the hysteria2 Go mobile client (gomobile AAR) which provides a
 * local SOCKS5 server tunneling through the QUIC-based Hysteria2 protocol
 * with optional Salamander obfuscation:
 *
 * App -> hev-socks5-tunnel -> Hysteria2Bridge SOCKS5 (listenPort)
 *   -> QUIC (Hysteria2, optional Salamander obfs) -> Server -> Internet
 */
object Hysteria2Bridge {
    private const val TAG = "Hysteria2Bridge"

    private var client: Client? = null
    private val running = AtomicBoolean(false)
    private val tunnelTxBytes = AtomicLong(0)
    private val tunnelRxBytes = AtomicLong(0)

    fun start(
        listenPort: Int,
        listenHost: String = "127.0.0.1",
        serverHost: String,
        serverPort: Int,
        password: String,
        sni: String = "",
        insecure: Boolean = false,
        obfs: String = "",
        obfsPassword: String = ""
    ): Result<Unit> {
        app.tzvpn.util.AppLog.i(TAG, "Starting Hysteria2 bridge on $listenHost:$listenPort " +
            "server=$serverHost:$serverPort sni=$sni obfs=$obfs insecure=$insecure")

        stop()

        return try {
            val c: Client = Hysteria2.newClient(
                "$listenHost:$listenPort",
                "$serverHost:$serverPort",
                password,
                sni,
                obfs,
                obfsPassword,
                insecure
            )
            c.Start()
            client = c
            running.set(true)
            app.tzvpn.util.AppLog.i(TAG, "Hysteria2 bridge started")
            Result.success(Unit)
        } catch (e: Exception) {
            app.tzvpn.util.AppLog.e(TAG, "Failed to start Hysteria2 bridge: ${e.message}", e)
            stop()
            Result.failure(e)
        }
    }

    fun stop() {
        running.set(false)
        try {
            client?.Stop()
        } catch (_: Exception) {}
        client = null
    }

    fun isRunning(): Boolean = running.get() && client?.IsRunning() == true
    fun isClientHealthy(): Boolean = isRunning()
    fun getTunnelTxBytes(): Long = tunnelTxBytes.get()
    fun getTunnelRxBytes(): Long = tunnelRxBytes.get()

    fun resetTrafficStats() {
        tunnelTxBytes.set(0)
        tunnelRxBytes.set(0)
    }
}
