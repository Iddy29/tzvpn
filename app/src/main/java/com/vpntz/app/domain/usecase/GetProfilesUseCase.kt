package com.vpntz.app.domain.usecase

import com.vpntz.app.domain.model.ServerProfile
import com.vpntz.app.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetProfilesUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    operator fun invoke(): Flow<List<ServerProfile>> {
        return profileRepository.getAllProfiles()
    }
}
