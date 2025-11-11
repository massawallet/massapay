package com.massapay.android.security.di

import android.content.Context
import com.massapay.android.security.biometric.BiometricManager
import com.massapay.android.security.crypto.KeystoreManager
import com.massapay.android.security.storage.SecureStorage
import com.massapay.android.security.storage.SecureStorageManager
import com.massapay.android.security.wallet.MnemonicManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager {
        return KeystoreManager()
    }

    @Provides
    @Singleton
    fun provideMnemonicManager(): MnemonicManager {
        return MnemonicManager()
    }

    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage {
        return SecureStorage(context)
    }

    @Provides
    @Singleton
    fun provideSecureStorageManager(): SecureStorageManager {
        return SecureStorageManager()
    }
}