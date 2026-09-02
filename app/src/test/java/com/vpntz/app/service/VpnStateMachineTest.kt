package com.vpntz.app.service

import com.vpntz.app.domain.model.ConnectionState
import com.vpntz.app.domain.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnStateMachineTest {

    private val profile = ServerProfile(name = "p", id = 7L)
    private val machine = VpnStateMachine()

    // ── initial state ───────────────────────────────────────────────────────

    @Test
    fun `starts disconnected and can connect`() {
        assertEquals(ConnectionState.Disconnected, machine.state.value)
        assertTrue(machine.canConnect())
        assertFalse(machine.canDisconnect())
        assertTrue(machine.beginConnect())
        assertEquals(ConnectionState.Connecting, machine.state.value)
    }

    // ── successful connect ──────────────────────────────────────────────────

    @Test
    fun `successful connect transitions to connected`() {
        machine.beginConnect()
        val connected = machine.onEstablished(profile)
        assertTrue(connected is ConnectionState.Connected)
        assertEquals(profile, (connected as ConnectionState.Connected).profile)
        assertTrue(machine.isConnected())
        assertFalse(machine.isConnecting())
    }

    @Test
    fun `connected state carries chain info`() {
        machine.beginConnect()
        val connected = machine.onEstablished(profile, chainName = "MyChain", chainId = 42L)
        assertEquals("MyChain", (connected as ConnectionState.Connected).chainName)
        assertEquals(42L, connected.chainId)
    }

    // ── successful disconnect ───────────────────────────────────────────────

    @Test
    fun `successful disconnect goes connecting disconnecting disconnected`() {
        assertTrue(machine.beginConnect())
        machine.onEstablished(profile)
        assertTrue(machine.beginDisconnect())
        assertEquals(ConnectionState.Disconnecting, machine.state.value)
        assertEquals(ConnectionState.Disconnected, machine.onDisconnected())
        assertTrue(machine.canConnect())
    }

    // ── connect failure / error and recovery ────────────────────────────────

    @Test
    fun `connect failure enters error and can recover`() {
        machine.beginConnect()
        val error = machine.onError("boom")
        assertTrue(error is ConnectionState.Error)
        assertEquals("boom", (error as ConnectionState.Error).message)
        // Error is recoverable:
        assertTrue(machine.canConnect())
        assertTrue(machine.beginConnect())
        assertEquals(ConnectionState.Connecting, machine.state.value)
    }

    @Test
    fun `error preserve exact cause`() {
        val cause = IllegalStateException("x")
        val error = machine.onError("failed", cause) as ConnectionState.Error
        assertEquals(cause, error.cause)
    }

    // ── disconnect during connection (cancellation) ─────────────────────────

    @Test
    fun `disconnect while connecting is allowed`() {
        machine.beginConnect()
        assertTrue(machine.canDisconnect())
        assertTrue(machine.beginDisconnect())
        assertEquals(ConnectionState.Disconnecting, machine.state.value)
        assertEquals(ConnectionState.Disconnected, machine.onDisconnected())
    }

    @Test
    fun `cleanup during connecting returns to disconnected`() {
        machine.beginConnect()
        assertEquals(ConnectionState.Disconnected, machine.cleanup())
    }

    // ── repeated connect is rejected ────────────────────────────────────────

    @Test
    fun `repeated connect while connecting or connected is rejected`() {
        assertTrue(machine.beginConnect())
        assertFalse("second connect while Connecting rejected", machine.beginConnect())
        machine.onEstablished(profile)
        assertFalse("connect while Connected rejected", machine.beginConnect())
    }

    @Test
    fun `connect rejected from disconnecting`() {
        machine.beginConnect()
        machine.beginDisconnect()
        assertFalse(machine.beginConnect())
    }

    // ── repeated disconnect is a no-op ──────────────────────────────────────

    @Test
    fun `repeated disconnect from disconnected is a no-op`() {
        assertFalse(machine.beginDisconnect())
        assertEquals(ConnectionState.Disconnected, machine.state.value)
    }

    @Test
    fun `disconnect only once from connected`() {
        machine.beginConnect()
        machine.onEstablished(profile)
        assertTrue(machine.beginDisconnect())
        assertFalse("no second disconnect from Disconnecting", machine.beginDisconnect())
    }

    // ── onDisconnected preserves error ──────────────────────────────────────

    @Test
    fun `disconnected teardown preserves a pending error`() {
        machine.beginConnect()
        machine.onError("failed")
        val result = machine.onDisconnected()
        assertTrue("error preserved through teardown", result is ConnectionState.Error)
        assertEquals("failed", (result as ConnectionState.Error).message)
    }

    // ── state transitions determinism / no invalid transitions ──────────────

    @Test
    fun `onEstablished ignored when not connecting or connected`() {
        val result = machine.onEstablished(profile)
        assertEquals("no transition from Disconnected", ConnectionState.Disconnected, result)
    }

    @Test
    fun `reconnect allowed from any state`() {
        machine.beginConnect()
        machine.onEstablished(profile)
        assertTrue(machine.beginReconnect())
        assertEquals(ConnectionState.Connecting, machine.state.value)
    }

    // ── external sync (mirror of authoritative repository state) ────────────

    @Test
    fun `sync adopts authoritative state`() {
        machine.beginConnect()
        machine.sync(ConnectionState.Connected(profile, connectedAt = 123L))
        val s = machine.state.value as ConnectionState.Connected
        assertEquals(123L, s.connectedAt)
        assertEquals(profile, s.profile)
    }

    @Test
    fun `sync can reflect a direct error that should stick`() {
        machine.beginConnect()
        machine.sync(ConnectionState.Error("dns pool exhausted"))
        assertEquals(
            ConnectionState.Error("dns pool exhausted"),
            machine.onDisconnected() // preserved because it is an Error
        )
    }
}
