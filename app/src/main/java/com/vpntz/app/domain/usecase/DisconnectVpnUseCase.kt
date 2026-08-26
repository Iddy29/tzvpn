package com.vpntz.app.domain.usecase

import com.vpntz.app.domain.repository.VpnRepository
import javax.inject.Inject

class DisconnectVpnUseCase @Inject constructor(
    private val vpnRepository: VpnRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return vpnRepository.disconnect()
    }
}
