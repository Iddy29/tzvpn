package com.vpntz.app.domain.repository

import com.vpntz.app.domain.model.ConnectionState
import com.vpntz.app.domain.model.ServerProfile
import com.vpntz.app.domain.model.TrafficStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface VpnRepository {
    val connectionState: StateFlow<ConnectionState>
    val trafficStats: StateFlow<TrafficStats>

    suspend fun connect(profile: ServerProfile): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    fun isConnected(): Boolean
    fun getConnectedProfile(): ServerProfile?
}
