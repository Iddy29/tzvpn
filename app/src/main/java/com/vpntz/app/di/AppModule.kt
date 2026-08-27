package com.vpntz.app.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.vpntz.app.config.ConfigGateway
import com.vpntz.app.config.DeviceKeyCipher
import com.vpntz.app.util.LockPasswordUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .create()
    }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * Phase-1 bridge: device-key cipher port backed by the existing native
     * accessor. Key material never leaves the native boundary in plaintext.
     */
    @Provides
    @Singleton
    fun provideDeviceKeyCipher(): DeviceKeyCipher = DeviceKeyCipher.of(
        onEncrypt = { LockPasswordUtil.encryptConfig(it) },
        onDecrypt = { LockPasswordUtil.decryptConfig(it) }
    )

    @Provides
    @Singleton
    fun provideConfigGateway(cipher: DeviceKeyCipher): ConfigGateway =
        ConfigGateway(deviceKeyCipher = cipher)
}
