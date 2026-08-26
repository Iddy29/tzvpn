package com.vpntz.app.domain.repository

import com.vpntz.app.domain.model.ProfileChain
import kotlinx.coroutines.flow.Flow

interface ChainRepository {
    fun getAllChains(): Flow<List<ProfileChain>>
    fun getActiveChain(): Flow<ProfileChain?>
    suspend fun getChainById(id: Long): ProfileChain?
    suspend fun saveChain(chain: ProfileChain): Long
    suspend fun deleteChain(id: Long)
    suspend fun setActiveChain(id: Long)
    suspend fun clearActiveChain()
    suspend fun updateChainOrder(orderedIds: List<Long>)
}
