package com.whispertype.app.di

import android.content.Context
import com.whispertype.app.api.WhisperApiClient
import com.whispertype.app.auth.FirebaseAuthManager
import com.whispertype.app.billing.BillingManagerFactory
import com.whispertype.app.billing.IBillingManager
import com.whispertype.app.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * RepositoryModule - Hilt module for providing repository and manager dependencies
 * 
 * This module:
 * - Provides singleton instances of managers (FirebaseAuthManager, WhisperApiClient)
 * - Binds repository interfaces to their implementations
 * - Ensures consistent dependency injection throughout the app
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    /**
     * Bind UserRepository interface to implementation
     */
    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
    
    /**
     * Bind AuthRepository interface to implementation
     */
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    
    /**
     * Bind BillingRepository interface to implementation
     */
    @Binds
    @Singleton
    abstract fun bindBillingRepository(impl: BillingRepositoryImpl): BillingRepository
    
    companion object {
        /**
         * Provide WhisperApiClient as a singleton
         */
        @Provides
        @Singleton
        fun provideWhisperApiClient(): WhisperApiClient {
            return WhisperApiClient()
        }
        
        /**
         * Provide FirebaseAuthManager as a singleton
         */
        @Provides
        @Singleton
        fun provideFirebaseAuthManager(): FirebaseAuthManager {
            return FirebaseAuthManager()
        }
        
        /**
         * Provide IBillingManager using the factory
         * The factory automatically selects mock for debug, real for release
         */
        @Provides
        @Singleton
        fun provideBillingManager(
            @ApplicationContext context: Context
        ): IBillingManager {
            return BillingManagerFactory.create(context)
        }
    }
}
