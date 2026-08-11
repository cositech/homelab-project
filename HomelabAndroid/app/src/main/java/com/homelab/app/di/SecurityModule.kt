package com.homelab.app.di

import com.homelab.app.domain.action.ControlledActionCoordinator
import com.homelab.app.security.AndroidKeystoreCredentialStore
import com.homelab.app.security.SecureCredentialStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    @Singleton
    abstract fun bindSecureCredentialStore(
        implementation: AndroidKeystoreCredentialStore
    ): SecureCredentialStore

    companion object {
        @Provides
        @Singleton
        fun provideControlledActionCoordinator(): ControlledActionCoordinator =
            ControlledActionCoordinator()
    }
}
