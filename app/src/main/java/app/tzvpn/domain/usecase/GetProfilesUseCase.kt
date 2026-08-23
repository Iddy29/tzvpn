package app.tzvpn.domain.usecase

import app.tzvpn.domain.model.ServerProfile
import app.tzvpn.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetProfilesUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    operator fun invoke(): Flow<List<ServerProfile>> {
        return profileRepository.getAllProfiles()
    }
}
