package com.vpntz.app.tunnel.adapter

import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class SnowflakeBridgeArgsTest {

    @Test
    fun `resolves snowflake config to bridge args`() {
        val cfg = TunnelAdapterConfig.Snowflake(
            bridges = "obfs4 1.2.3.4:443 123 cert=..\n",
            listenPort = 1080,
            listenHost = "127.0.0.1",
            snowflakePtPort = 1082,
            torSocksPort = 1081,
            upstreamSocksAddr = InetSocketAddress("10.0.0.1", 1080)
        )
        val a = SnowflakeBridgeArgs.resolve(cfg)
        assertEquals(1082, a.snowflakePort)
        assertEquals(1081, a.torSocksPort)
        assertEquals("127.0.0.1", a.listenHost)
        assertEquals("obfs4 1.2.3.4:443 123 cert=..\n", a.bridgeLines)
        assertEquals("10.0.0.1", a.upstreamSocksAddr!!.hostString)
        assertEquals(1080, a.upstreamSocksAddr!!.port)
    }

    @Test
    fun `upstream socks is null when not chained`() {
        val cfg = TunnelAdapterConfig.Snowflake(
            bridges = "", listenPort = 1080, listenHost = "127.0.0.1",
            snowflakePtPort = 1082, torSocksPort = 1081
        )
        val a = SnowflakeBridgeArgs.resolve(cfg)
        assertEquals(null, a.upstreamSocksAddr)
    }
}
