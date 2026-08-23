package app.tzvpn.domain.usecase

import app.tzvpn.domain.model.ServerProfile
import app.tzvpn.domain.repository.VpnRepository
import javax.inject.Inject

class ConnectVpnUseCase @Inject constructor(
    private val vpnRepository: VpnRepository
) {
    suspend operator fun invoke(profile: ServerProfile): Result<Unit> {
        return vpnRepository.connect(profile)
    }
}
