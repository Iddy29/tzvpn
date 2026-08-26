package com.vpntz.app.di

import com.vpntz.app.data.repository.ChainRepositoryImpl
import com.vpntz.app.data.repository.ProfileRepositoryImpl
import com.vpntz.app.data.repository.ResolverScannerRepositoryImpl
import com.vpntz.app.data.repository.VpnRepositoryImpl
import com.vpntz.app.domain.repository.ChainRepository
import com.vpntz.app.domain.repository.ProfileRepository
import com.vpntz.app.domain.repository.ResolverScannerRepository
import com.vpntz.app.domain.repository.VpnRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        profileRepositoryImpl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindVpnRepository(
        vpnRepositoryImpl: VpnRepositoryImpl
    ): VpnRepository

    @Binds
    @Singleton
    abstract fun bindResolverScannerRepository(
        resolverScannerRepositoryImpl: ResolverScannerRepositoryImpl
    ): ResolverScannerRepository

    @Binds
    @Singleton
    abstract fun bindChainRepository(
        chainRepositoryImpl: ChainRepositoryImpl
    ): ChainRepository
}
