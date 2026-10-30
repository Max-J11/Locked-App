package com.max.locked.di

import android.content.Context
import androidx.room.Room
import com.max.locked.data.LockedDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** App-wide scope that outlives screens and services. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LockedDatabase =
        // No destructive fallback: every schema change must ship a migration (see LockedDatabase),
        // so an update can never wipe her modes, pucks or schedules.
        Room.databaseBuilder(context, LockedDatabase::class.java, "locked.db")
            .build()
}
