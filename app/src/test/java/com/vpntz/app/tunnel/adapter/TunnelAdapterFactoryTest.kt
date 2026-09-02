package com.vpntz.app.tunnel.adapter

import com.vpntz.app.domain.model.TunnelType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the adapter seam. A recording fake backend stands in for
 * the real bridge/FFI glue so these validate the lifecycle contract without
 * touching native code or a device. They do NOT claim actual tunnel connectivity.
 */
class TunnelAdapterFactoryTest {

    private val factory = TunnelAdapterFactory()

    private class RecordingBackend : TunnelLifecycleBackend {
        var startedWith: TunnelAdapterConfig? = null
        var startResult: Result<Unit> = Result.success(Unit)
        var stopCount = 0
        var cleanupCount = 0
        var running = false
        var healthy = true
        override suspend fun start(config: TunnelAdapterConfig): Result<Unit> {
            startedWith = config
            running = startResult.isSuccess
            return startResult
        }
        override fun stop() { stopCount++; running = false }
        override fun isRunning() = running
        override fun isHealthy() = healthy
        override fun cleanup() { cleanupCount++; running = false }
    }

    @Test
    fun `factory binds protocol to backend`() {
        val backend = RecordingBackend()
        val adapter = factory.create(TunnelType.DNSTT, backend)
        assertEquals(TunnelType.DNSTT, adapter.protocol)
        assertTrue(adapter is TunnelAdapter)
        // the adapter wraps the backend rather than being it
        assertFalse(adapter is RecordingBackend)
    }

    @Test
    fun `adapter delegates start stop isRunning health cleanup to backend`() = runBlocking {
        val backend = RecordingBackend().apply { healthy = true }
        val adapter = factory.create(TunnelType.VLESS, backend)
        assertEquals(backend.isRunning(), adapter.isRunning())
        assertTrue(adapter.isHealthy())

        val cfg = TunnelAdapterConfig.Vless(
            host = "h", port = 443, uuid = "u", security = "tls", transport = "ws", wsPath = "/",
            sni = "", cdnIp = "", cdnPort = 443, sniFragmentEnabled = true, sniFragmentStrategy = "micro",
            sniFragmentDelayMs = 300, sniSpoofTtl = 8, fakeDecoyHost = "", tcpMaxSeg = 0, chPaddingEnabled = false,
            wsHeaderObfuscation = true, wsPaddingEnabled = false, realityPubKey = "", realityShortId = "",
            realityFp = "chrome", listenPort = 1080, listenHost = "127.0.0.1"
        )
        val result = adapter.start(cfg)
        assertTrue(result.isSuccess)
        assertEquals(cfg, backend.startedWith)
        assertTrue(adapter.isRunning())

        adapter.stop()
        assertFalse(adapter.isRunning())
        assertEquals(1, backend.stopCount)

        adapter.cleanup()
        assertEquals(1, backend.cleanupCount)
    }

    @Test
    fun `failure propagation stays a Result failure`() = runBlocking {
        val backend = RecordingBackend().apply { startResult = Result.failure(RuntimeException("no route")) }
        val adapter = factory.create(TunnelType.DNSTT, backend)
        val result = adapter.start(TunnelAdapterConfig.Dnstt(
            domain = "d", publicKey = "k", authoritative = false, resolvers = emptyList(),
            effectiveDnsServer = "8.8.8.8:53",
            listenPort = 1080, listenHost = "127.0.0.1", maxPayload = 0, resolverMode = "fanout",
            rrSpreadCount = 3, noizdns = false, noizStealth = false,
            socksProxyAddr = null, socksProxyUser = null, socksProxyPass = null
        ))
        assertTrue(result.isFailure)
        assertEquals("no route", result.exceptionOrNull()?.message)
        assertFalse(adapter.isRunning()) // failed start must not leave engine "running"
    }

    @Test
    fun `repeated stop is safe and idempotent`() = runBlocking {
        val backend = RecordingBackend().apply { running = true }
        val adapter = factory.create(TunnelType.SSH, backend)
        adapter.stop()
        adapter.stop()
        adapter.stop()
        assertEquals(3, backend.stopCount)
        assertFalse(adapter.isRunning())
    }

    @Test
    fun `cleanup after failure does not throw`() = runBlocking {
        val backend = RecordingBackend().apply { startResult = Result.failure(Exception("boom")) }
        val adapter = factory.create(TunnelType.DNSTT, backend)
        adapter.start(TunnelAdapterConfig.Dnstt(
            domain = "d", publicKey = "k", authoritative = false, resolvers = emptyList(),
            effectiveDnsServer = "8.8.8.8:53",
            listenPort = 1080, listenHost = "127.0.0.1", maxPayload = 0, resolverMode = "fanout",
            rrSpreadCount = 3, noizdns = false, noizStealth = false,
            socksProxyAddr = null, socksProxyUser = null, socksProxyPass = null
        ))
        adapter.cleanup()
        assertEquals(1, backend.cleanupCount)
    }

    @Test
    fun `socks5 creation is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(TunnelType.SOCKS5, RecordingBackend())
        }
    }

    @Test
    fun `registry describes every adapter protocol`() {
        val specs = factory.specs()
        assertTrue(specs.isNotEmpty())
        // every protocol that maps to a real adapter is described
        val mapped = setOf(
            TunnelType.DNSTT, TunnelType.DNSTT_SSH, TunnelType.NOIZDNS, TunnelType.NOIZDNS_SSH,
            TunnelType.VAYDNS, TunnelType.VAYDNS_SSH, TunnelType.SLIPSTREAM, TunnelType.SLIPSTREAM_SSH,
            TunnelType.NAIVE, TunnelType.NAIVE_SSH, TunnelType.VLESS, TunnelType.SNOWFLAKE,
            TunnelType.HYSTERIA2, TunnelType.SSH, TunnelType.DOH
        )
        assertEquals(mapped, specs.map { it.protocol }.toSet())
        // SSH is the only pure-JVM engine here
        assertFalse(factory.spec(TunnelType.SSH)!!.requiresNativeArtifact)
        assertTrue(factory.spec(TunnelType.DNSTT)!!.requiresNativeArtifact)
        assertNull(factory.spec(TunnelType.SOCKS5))
    }
}
