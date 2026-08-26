package com.vpntz.app.domain.usecase

import com.vpntz.app.domain.model.ServerProfile
import com.vpntz.app.domain.repository.ProfileRepository
import javax.inject.Inject

class SaveProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(profile: ServerProfile): Long {
        return if (profile.id == 0L) {
            profileRepository.prepareTopSortOrder()
            profileRepository.saveProfile(profile.copy(sortOrder = 0))
        } else {
            profileRepository.updateProfile(profile.copy(updatedAt = System.currentTimeMillis()))
            profile.id
        }
    }
}
