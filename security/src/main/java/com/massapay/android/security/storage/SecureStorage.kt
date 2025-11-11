package com.massapay.android.security.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.massapay.android.core.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        Constants.PREF_ENCRYPTED_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun storeMnemonic(alias: String, encryptedMnemonic: String) {
        prefs.edit().putString("mnemonic_$alias", encryptedMnemonic).apply()
    }
    
    fun getMnemonic(alias: String): String? {
        return prefs.getString("mnemonic_$alias", null)
    }
    
    fun storePrivateKey(alias: String, encryptedKey: String) {
        prefs.edit().putString("private_key_$alias", encryptedKey).apply()
    }
    
    fun getPrivateKey(alias: String): String? {
        return prefs.getString("private_key_$alias", null)
    }
    
    fun storePublicKey(alias: String, publicKey: String) {
        prefs.edit().putString("public_key_$alias", publicKey).apply()
    }
    
    fun getPublicKey(alias: String): String? {
        return prefs.getString("public_key_$alias", null)
    }
    
    fun setActiveWallet(address: String) {
        prefs.edit().putString(Constants.PREF_KEY_ACTIVE_WALLET, address).apply()
    }
    
    fun getActiveWallet(): String? {
        return prefs.getString(Constants.PREF_KEY_ACTIVE_WALLET, null)
    }
    
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
    }
    
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }
    
    fun storePin(pin: String) {
        prefs.edit().putString("user_pin", pin).apply()
    }
    
    fun getPin(): String? {
        return prefs.getString("user_pin", null)
    }
    
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }
    
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean("biometric_enabled", false)
    }
    
    fun hasWallet(): Boolean {
        val active = getActiveWallet()
        
        if (active == null) {
            return false
        }
        
        // Consider both mnemonic-based wallets and S1 private-key imports
        val hasDefaultMnemonic = getMnemonic("default_wallet") != null
        val hasS1Import = getMnemonic("s1_private_key") != null || getMnemonic("imported_s1_key") != null
        // Also consider if a private key was stored explicitly
        val hasStoredPrivateKey = getPrivateKey("default_wallet") != null
        
        return hasDefaultMnemonic || hasS1Import || hasStoredPrivateKey
    }
    
    fun clear() {
        prefs.edit().clear().apply()
    }
}