package com.vpntz.app.domain.usecase

import com.vpntz.app.domain.model.ServerProfile
import com.vpntz.app.domain.repository.ProfileRepository
import javax.inject.Inject

class GetProfileByIdUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(id: Long): ServerProfile? {
        return profileRepository.getProfileById(id)
    }
}
