package app.tzvpn.domain.usecase

import app.tzvpn.domain.repository.ProfileRepository
import javax.inject.Inject

class SetActiveProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(id: Long) {
        profileRepository.setActiveProfile(id)
    }
}
