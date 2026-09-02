package com.vpntz.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStateTest {

    @Test
    fun `only Connected reports connected`() {
        assertTrue(ConnectionState.Connected(ServerProfile(name = "p")).isConnected)
        assertFalse(ConnectionState.Disconnected.isConnected)
        assertFalse(ConnectionState.Connecting.isConnected)
        assertFalse(ConnectionState.Disconnecting.isConnected)
        assertFalse(ConnectionState.Error("x").isConnected)
    }

    @Test
    fun `only Connecting reports connecting`() {
        assertTrue(ConnectionState.Connecting.isConnecting)
        assertFalse(ConnectionState.Connected(ServerProfile(name = "p")).isConnecting)
        assertFalse(ConnectionState.Disconnected.isConnecting)
    }

    @Test
    fun `Disconnected and Error count as disconnected`() {
        assertTrue(ConnectionState.Disconnected.isDisconnected)
        assertTrue(ConnectionState.Error("boom").isDisconnected)
        assertFalse(ConnectionState.Connecting.isDisconnected)
        assertFalse(ConnectionState.Disconnecting.isDisconnected)
        assertFalse(ConnectionState.Connected(ServerProfile(name = "p")).isDisconnected)
    }

    @Test
    fun `display name mirrors the variant`() {
        assertEquals("Disconnected", ConnectionState.Disconnected.displayName)
        assertEquals("Connecting", ConnectionState.Connecting.displayName)
        assertEquals("Connected", ConnectionState.Connected(ServerProfile(name = "p")).displayName)
        assertEquals("Disconnecting", ConnectionState.Disconnecting.displayName)
        assertEquals("Error", ConnectionState.Error("x").displayName)
    }

    @Test
    fun `connected state carries profile and chain metadata`() {
        val profile = ServerProfile(name = "p")
        val state = ConnectionState.Connected(profile = profile, chainId = 7, chainName = "my chain")
        assertEquals(profile, state.profile)
        assertEquals(7L, state.chainId)
        assertEquals("my chain", state.chainName)
    }

    @Test
    fun `error state preserves the cause`() {
        val cause = IllegalStateException("root")
        val state = ConnectionState.Error("failed", cause)
        assertEquals("failed", state.message)
        assertEquals(cause, state.cause)
    }
}
