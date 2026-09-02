package com.vpntz.app.domain.usecase

import com.vpntz.app.domain.model.ConnectionState
import com.vpntz.app.domain.model.ServerProfile
import com.vpntz.app.domain.model.TrafficStats
import com.vpntz.app.domain.repository.ProfileRepository
import com.vpntz.app.domain.repository.VpnRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory ProfileRepository fake recording call order for behavior assertions. */
private class FakeProfileRepository : ProfileRepository {
    val profiles = MutableStateFlow<List<ServerProfile>>(emptyList())
    val events = mutableListOf<String>()
    private val activeId = MutableStateFlow<Long?>(null)
    private var nextId = 1L

    override fun getAllProfiles(): Flow<List<ServerProfile>> = profiles
    override fun getActiveProfile(): Flow<ServerProfile?> =
        MutableStateFlow(profiles.value.firstOrNull { it.id == activeId.value })

    override suspend fun getProfileById(id: Long): ServerProfile? =
        profiles.value.firstOrNull { it.id == id }

    override suspend fun saveProfile(profile: ServerProfile): Long {
        events += "save(sortOrder=${profile.sortOrder})"
        val id = nextId++
        profiles.value = profiles.value + profile.copy(id = id)
        return id
    }

    override suspend fun updateProfile(profile: ServerProfile) {
        events += "update(updatedAt=${profile.updatedAt})"
        profiles.value = profiles.value.map { if (it.id == profile.id) profile else it }
    }

    override suspend fun deleteProfile(id: Long) {
        events += "delete($id)"
        profiles.value = profiles.value.filter { it.id != id }
    }

    override suspend fun setActiveProfile(id: Long) {
        activeId.value = id
    }

    override suspend fun clearActiveProfile() {
        activeId.value = null
    }

    override suspend fun updateLastConnectedAt(id: Long) {
        events += "lastConnected($id)"
    }

    override suspend fun updateProfileOrder(orderedIds: List<Long>) {
        events += "reorder(${orderedIds.size})"
    }

    override suspend fun getMaxSortOrder(): Int = 0

    override suspend fun prepareTopSortOrder() {
        events += "prepareTopSortOrder"
    }

    override suspend fun togglePinned(id: Long) {
        events += "togglePinned($id)"
    }
}

/** In-memory VpnRepository fake. */
private class FakeVpnRepository : VpnRepository {
    val connectedProfile = MutableStateFlow<ServerProfile?>(null)
    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val trafficStats: StateFlow<TrafficStats> = MutableStateFlow(TrafficStats.EMPTY)

    override suspend fun connect(profile: ServerProfile): Result<Unit> {
        connectedProfile.value = profile
        return Result.success(Unit)
    }

    override suspend fun disconnect(): Result<Unit> {
        connectedProfile.value = null
        return Result.success(Unit)
    }

    override fun isConnected(): Boolean = connectedProfile.value != null
    override fun getConnectedProfile(): ServerProfile? = connectedProfile.value
}

class SaveProfileUseCaseTest {

    @Test
    fun `new profile gets top sort order and is inserted with sortOrder zero`() = runBlocking {
        val repo = FakeProfileRepository()
        val useCase = SaveProfileUseCase(repo)

        val id = useCase.invoke(ServerProfile(name = "fresh", sortOrder = 42))

        assertTrue(id > 0)
        assertEquals(
            listOf("prepareTopSortOrder", "save(sortOrder=0)"),
            repo.events
        )
        val saved = repo.getProfileById(id)!!
        assertEquals(0, saved.sortOrder)
        assertEquals("fresh", saved.name)
    }

    @Test
    fun `existing profile is updated with a refreshed updatedAt and keeps its id`() = runBlocking {
        val repo = FakeProfileRepository()
        val useCase = SaveProfileUseCase(repo)
        val originalId = useCase.invoke(ServerProfile(name = "stale"))
        repo.events.clear()

        val stale = repo.getProfileById(originalId)!!.copy(updatedAt = 0L, name = "renamed")
        val resultId = useCase.invoke(stale)

        assertEquals(originalId, resultId)
        assertTrue(repo.events.single().startsWith("update(updatedAt="))
        assertFalse(repo.events.single().startsWith("update(updatedAt=0"))
        assertEquals("renamed", repo.getProfileById(originalId)!!.name)
    }
}

class DeleteProfileUseCaseTest {

    @Test
    fun `deleting the connected profile is rejected`() = runBlocking {
        val profiles = FakeProfileRepository()
        val vpn = FakeVpnRepository()
        val useCase = DeleteProfileUseCase(profiles, vpn)

        val id = useCaseSave(profiles, "connected one")
        vpn.connect(profiles.getProfileById(id)!!)

        val result = useCase.invoke(id)

        assertTrue(result.isFailure)
        assertEquals("Cannot delete profile while connected", result.exceptionOrNull()!!.message)
        assertTrue(profiles.events.none { it.startsWith("delete") })
        assertTrue(profiles.getProfileById(id) != null)
    }

    @Test
    fun `deleting a non-connected profile succeeds and removes it`() = runBlocking {
        val profiles = FakeProfileRepository()
        val vpn = FakeVpnRepository()
        val useCase = DeleteProfileUseCase(profiles, vpn)

        val idA = useCaseSave(profiles, "a")
        val idB = useCaseSave(profiles, "b")
        vpn.connect(profiles.getProfileById(idA)!!)

        val result = useCase.invoke(idB)

        assertTrue(result.isSuccess)
        assertTrue(profiles.events.contains("delete($idB)"))
        assertTrue(profiles.getProfileById(idB) == null)
        assertTrue(profiles.getProfileById(idA) != null)
    }

    private suspend fun useCaseSave(repo: FakeProfileRepository, name: String): Long =
        SaveProfileUseCase(repo).invoke(ServerProfile(name = name))
}

class VpnUseCasesDelegationTest {

    @Test
    fun `connect use case delegates to the repository`() = runBlocking {
        val vpn = FakeVpnRepository()
        val profile = ServerProfile(name = "p")

        val result = ConnectVpnUseCase(vpn).invoke(profile)

        assertTrue(result.isSuccess)
        assertTrue(vpn.isConnected())
        assertEquals(profile, vpn.getConnectedProfile())
    }

    @Test
    fun `disconnect use case delegates to the repository`() = runBlocking {
        val vpn = FakeVpnRepository()
        val profile = ServerProfile(name = "p")
        vpn.connect(profile)

        val result = DisconnectVpnUseCase(vpn).invoke()

        assertTrue(result.isSuccess)
        assertFalse(vpn.isConnected())
    }
}
