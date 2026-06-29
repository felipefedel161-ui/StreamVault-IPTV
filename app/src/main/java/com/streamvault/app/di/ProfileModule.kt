package com.streamvault.app.di

import com.streamvault.app.profiles.UserProfileActiveProvider
import com.streamvault.domain.repository.ActiveProfileProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {
    @Binds
    @Singleton
    abstract fun bindActiveProfileProvider(
        impl: UserProfileActiveProvider
    ): ActiveProfileProvider
}
