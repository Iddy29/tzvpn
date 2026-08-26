package com.vpntz.app.di

import android.content.Context
import androidx.room.Room
import com.vpntz.app.data.local.database.ChainDao
import com.vpntz.app.data.local.database.ProfileDao
import com.vpntz.app.data.local.database.VpnTzDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): VpnTzDatabase {
        return Room.databaseBuilder(
            context,
            VpnTzDatabase::class.java,
            VpnTzDatabase.DATABASE_NAME
        )
            .addMigrations(
                VpnTzDatabase.MIGRATION_5_6,
                VpnTzDatabase.MIGRATION_6_7,
                VpnTzDatabase.MIGRATION_7_8,
                VpnTzDatabase.MIGRATION_8_9,
                VpnTzDatabase.MIGRATION_9_10,
                VpnTzDatabase.MIGRATION_10_11,
                VpnTzDatabase.MIGRATION_11_12,
                VpnTzDatabase.MIGRATION_12_13,
                VpnTzDatabase.MIGRATION_13_14,
                VpnTzDatabase.MIGRATION_14_15,
                VpnTzDatabase.MIGRATION_15_16,
                VpnTzDatabase.MIGRATION_16_17,
                VpnTzDatabase.MIGRATION_17_18,
                VpnTzDatabase.MIGRATION_18_19,
                VpnTzDatabase.MIGRATION_19_20,
                VpnTzDatabase.MIGRATION_20_21,
                VpnTzDatabase.MIGRATION_21_22,
                VpnTzDatabase.MIGRATION_22_23,
                VpnTzDatabase.MIGRATION_23_24,
                VpnTzDatabase.MIGRATION_24_25,
                VpnTzDatabase.MIGRATION_25_26,
                VpnTzDatabase.MIGRATION_26_27,
                VpnTzDatabase.MIGRATION_27_28,
                VpnTzDatabase.MIGRATION_28_29,
                VpnTzDatabase.MIGRATION_29_30,
                VpnTzDatabase.MIGRATION_30_31,
                VpnTzDatabase.MIGRATION_31_32,
                VpnTzDatabase.MIGRATION_32_33,
                VpnTzDatabase.MIGRATION_33_34,
                VpnTzDatabase.MIGRATION_34_35,
                VpnTzDatabase.MIGRATION_35_36,
                VpnTzDatabase.MIGRATION_36_37,
                VpnTzDatabase.MIGRATION_37_38,
                VpnTzDatabase.MIGRATION_38_39,
                VpnTzDatabase.MIGRATION_39_40,
                VpnTzDatabase.MIGRATION_40_41,
                VpnTzDatabase.MIGRATION_41_42,
            VpnTzDatabase.MIGRATION_42_43
            )
            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideProfileDao(database: VpnTzDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    @Singleton
    fun provideChainDao(database: VpnTzDatabase): ChainDao {
        return database.chainDao()
    }
}
