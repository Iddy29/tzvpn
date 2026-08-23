package app.tzvpn.di

import android.content.Context
import androidx.room.Room
import app.tzvpn.data.local.database.ChainDao
import app.tzvpn.data.local.database.ProfileDao
import app.tzvpn.data.local.database.TZVPNDatabase
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
    ): TZVPNDatabase {
        return Room.databaseBuilder(
            context,
            TZVPNDatabase::class.java,
            TZVPNDatabase.DATABASE_NAME
        )
            .addMigrations(
                TZVPNDatabase.MIGRATION_5_6,
                TZVPNDatabase.MIGRATION_6_7,
                TZVPNDatabase.MIGRATION_7_8,
                TZVPNDatabase.MIGRATION_8_9,
                TZVPNDatabase.MIGRATION_9_10,
                TZVPNDatabase.MIGRATION_10_11,
                TZVPNDatabase.MIGRATION_11_12,
                TZVPNDatabase.MIGRATION_12_13,
                TZVPNDatabase.MIGRATION_13_14,
                TZVPNDatabase.MIGRATION_14_15,
                TZVPNDatabase.MIGRATION_15_16,
                TZVPNDatabase.MIGRATION_16_17,
                TZVPNDatabase.MIGRATION_17_18,
                TZVPNDatabase.MIGRATION_18_19,
                TZVPNDatabase.MIGRATION_19_20,
                TZVPNDatabase.MIGRATION_20_21,
                TZVPNDatabase.MIGRATION_21_22,
                TZVPNDatabase.MIGRATION_22_23,
                TZVPNDatabase.MIGRATION_23_24,
                TZVPNDatabase.MIGRATION_24_25,
                TZVPNDatabase.MIGRATION_25_26,
                TZVPNDatabase.MIGRATION_26_27,
                TZVPNDatabase.MIGRATION_27_28,
                TZVPNDatabase.MIGRATION_28_29,
                TZVPNDatabase.MIGRATION_29_30,
                TZVPNDatabase.MIGRATION_30_31,
                TZVPNDatabase.MIGRATION_31_32,
                TZVPNDatabase.MIGRATION_32_33,
                TZVPNDatabase.MIGRATION_33_34,
                TZVPNDatabase.MIGRATION_34_35,
                TZVPNDatabase.MIGRATION_35_36,
                TZVPNDatabase.MIGRATION_36_37,
                TZVPNDatabase.MIGRATION_37_38,
                TZVPNDatabase.MIGRATION_38_39,
                TZVPNDatabase.MIGRATION_39_40,
                TZVPNDatabase.MIGRATION_40_41,
                TZVPNDatabase.MIGRATION_41_42
            )
            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideProfileDao(database: TZVPNDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    @Singleton
    fun provideChainDao(database: TZVPNDatabase): ChainDao {
        return database.chainDao()
    }
}
