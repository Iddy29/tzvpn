package com.vpntz.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReferenceArray

data class LogEntry(val id: Long, val raw: String, val level: Char)

/**
 * VPN-TZ original logging facade.
 *
 * Forwards every call to the platform logger and mirrors it into a fixed-slot
 * concurrent ring that the in-app debug console can observe without shelling
 * out to `logcat` (which trips Google Play Protect).
 *
 * Storage design: a pre-allocated [AtomicReferenceArray] of [SLOTS] entries
 * addressed by a monotonically increasing sequence number. Writers never block
 * readers and vice versa; a snapshot walks the last [SLOTS] sequence numbers
 * and skips any that were overwritten mid-scan.
 *
 * Usage: `import com.vpntz.app.util.AppLog as Log` — drop-in for android.util.Log.
 */
object AppLog {
    private val slots = AtomicReferenceArray<LogEntry>(SLOTS)
    private val sequence = AtomicLong(0)

    /** When true, sensitive config details are withheld from the in-app buffer. */
    @Volatile var redactSensitive = false

    private val _lines = MutableStateFlow<List<LogEntry>>(emptyList())
    val lines: StateFlow<List<LogEntry>> = _lines.asStateFlow()

    @Volatile var observerCount = 0
        private set

    private val dirty = AtomicBoolean(false)

    private val timestamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC)

    private fun append(level: Char, tag: String, msg: String) {
        val id = sequence.getAndIncrement()
        val rendered = if (observerCount > 0) {
            val ts = timestamp.format(Instant.now())
            "$ts $level/$tag: $msg"
        } else {
            "$level/$tag: $msg"
        }
        slots.set((id % SLOTS).toInt(), LogEntry(id, rendered, level))
        if (observerCount > 0) dirty.set(true)
    }

    /** Publishes a snapshot when new entries exist. Polled by the debug console. */
    fun flushIfDirty() {
        if (dirty.compareAndSet(true, false)) {
            _lines.value = snapshot()
        }
    }

    /** Call from the debug console onStart to begin receiving snapshots. */
    fun addObserver() {
        observerCount++
        _lines.value = snapshot()
    }

    fun removeObserver() {
        observerCount = (observerCount - 1).coerceAtLeast(0)
    }

    private fun snapshot(): List<LogEntry> {
        val end = sequence.get()
        val start = (end - SLOTS).coerceAtLeast(0)
        val out = ArrayList<LogEntry>(SLOTS)
        for (seq in start until end) {
            val entry = slots.get((seq % SLOTS).toInt()) ?: continue
            if (entry.id == seq) out.add(entry)
        }
        return out
    }

    /** Tags whose messages may contain hosts, ports, or credentials. */
    private val SENSITIVE_TAGS = setOf(
        "HevSocks5Tunnel",
        "SlipstreamSocksBridge",
        "DnsttSocksBridge",
        "SshTunnelBridge",
        "VpnTzService",
        "KotlinTunnelManager",
        "NaiveSocksBridge",
        "TorSocksBridge",
        "SlipstreamBridge",
        "DnsttBridge",
        "NaiveBridge",
        "VpnRepositoryImpl",
        "VaydnsBridge",
        "DnsResolverProber",
        "DohBridge",
        "HttpProxyServer",
        "ProxyHttpConnect",
        "ProxyWebSocket",
        "TlsSocketFactory",
        "NaiveSocksProxy",
        "PayloadSocketFactory",
        "DomainRouter",
        "DnsDoHProxy"
    )

    /** Prefixes for dynamic tags (e.g. SshTunnel[default], Socks5Proxy[0]). */
    private val SENSITIVE_TAG_PREFIXES = arrayOf("SshTunnel[", "Socks5Proxy[")

    private fun shouldRedact(tag: String): Boolean {
        if (!redactSensitive) return false
        if (tag in SENSITIVE_TAGS) return true
        return SENSITIVE_TAG_PREFIXES.any { tag.startsWith(it) }
    }

    fun v(tag: String, msg: String): Int {
        if (!shouldRedact(tag)) append('V', tag, msg)
        return android.util.Log.v(tag, msg)
    }

    fun d(tag: String, msg: String): Int {
        if (!shouldRedact(tag)) append('D', tag, msg)
        return android.util.Log.d(tag, msg)
    }

    fun i(tag: String, msg: String): Int {
        if (!shouldRedact(tag)) append('I', tag, msg)
        return android.util.Log.i(tag, msg)
    }

    fun w(tag: String, msg: String): Int {
        if (!shouldRedact(tag)) append('W', tag, msg)
        return android.util.Log.w(tag, msg)
    }

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable?): Int {
        if (!shouldRedact(tag)) append('W', tag, if (tr != null) "$msg\n${tr.stackTraceToString()}" else msg)
        return android.util.Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String): Int {
        if (!shouldRedact(tag)) append('E', tag, msg)
        return android.util.Log.e(tag, msg)
    }

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable?): Int {
        if (!shouldRedact(tag)) append('E', tag, if (tr != null) "$msg\n${tr.stackTraceToString()}" else msg)
        return android.util.Log.e(tag, msg, tr)
    }

    fun clear() {
        for (i in 0 until SLOTS) slots.set(i, null)
        _lines.value = emptyList()
    }

    private const val SLOTS = 1024
}
