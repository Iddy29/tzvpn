package app.tzvpn.domain.usecase

import app.tzvpn.domain.model.ServerProfile
import app.tzvpn.domain.repository.ProfileRepository
import javax.inject.Inject

class GetProfileByIdUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(id: Long): ServerProfile? {
        return profileRepository.getProfileById(id)
    }
}
