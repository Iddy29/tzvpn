package com.vpntz.app.domain.usecase

import com.vpntz.app.domain.model.ServerProfile
import com.vpntz.app.domain.repository.VpnRepository
import javax.inject.Inject

class ConnectVpnUseCase @Inject constructor(
    private val vpnRepository: VpnRepository
) {
    suspend operator fun invoke(profile: ServerProfile): Result<Unit> {
        return vpnRepository.connect(profile)
    }
}
