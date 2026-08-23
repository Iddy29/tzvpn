package app.tzvpn.di

import app.tzvpn.data.repository.ChainRepositoryImpl
import app.tzvpn.data.repository.ProfileRepositoryImpl
import app.tzvpn.data.repository.ResolverScannerRepositoryImpl
import app.tzvpn.data.repository.VpnRepositoryImpl
import app.tzvpn.domain.repository.ChainRepository
import app.tzvpn.domain.repository.ProfileRepository
import app.tzvpn.domain.repository.ResolverScannerRepository
import app.tzvpn.domain.repository.VpnRepository
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
