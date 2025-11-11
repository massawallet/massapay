package com.massapay.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.security.storage.SecureStorage
import com.massapay.android.core.preferences.ThemeManager
import com.massapay.android.core.preferences.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val themeManager: ThemeManager,
    private val walletManager: com.massapay.android.security.wallet.WalletManager,
    private val massaRepository: com.massapay.android.network.repository.MassaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            themeManager.themeMode.collect { themeMode ->
                val activeWallet = secureStorage.getActiveWallet()
                
                // Check if wallet was imported from S1 key or mnemonic
                val mnemonic = secureStorage.getMnemonic("default_wallet")
                val s1Key = secureStorage.getMnemonic("s1_private_key")
                val isS1Import = s1Key != null && mnemonic == null
                
                _uiState.update {
                    it.copy(
                        biometricEnabled = secureStorage.isBiometricEnabled(),
                        activeWallet = activeWallet,
                        seedPhrase = if (isS1Import) null else mnemonic,
                        s1PrivateKey = if (isS1Import) s1Key else null,
                        isS1Import = isS1Import,
                        themeMode = themeMode
                    )
                }
            }
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        secureStorage.setBiometricEnabled(enabled)
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeManager.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    fun changePin(oldPin: String, newPin: String) {
        val storedPin = secureStorage.getPin()
        if (storedPin == oldPin) {
            secureStorage.storePin(newPin)
            _uiState.update { it.copy(error = null, pinChangeSuccess = true) }
        } else {
            _uiState.update { it.copy(error = "Incorrect current PIN", pinChangeSuccess = false) }
        }
    }
    
    fun clearPinChangeStatus() {
        _uiState.update { it.copy(error = null, pinChangeSuccess = false) }
    }

    fun verifyPin(pin: String): Boolean {
        val storedPin = secureStorage.getPin()
        return storedPin == pin
    }
    
    fun loadSeedPhrase() {
        // Check if wallet was imported from S1 key or mnemonic
        val mnemonic = secureStorage.getMnemonic("default_wallet")
        val s1Key = secureStorage.getMnemonic("s1_private_key")
        val isS1Import = s1Key != null && mnemonic == null
        
        _uiState.update { 
            it.copy(
                seedPhrase = if (isS1Import) null else mnemonic,
                s1PrivateKey = if (isS1Import) s1Key else null,
                isS1Import = isS1Import
            ) 
        }
    }

    fun loadPrivateKey() {
        viewModelScope.launch {
            try {
                // Check if wallet was imported from S1 key or mnemonic
                val mnemonic = secureStorage.getMnemonic("default_wallet")
                val s1Key = secureStorage.getMnemonic("s1_private_key")
                val isS1Import = s1Key != null && mnemonic == null
                
                if (isS1Import && s1Key != null) {
                    // For S1 imports, derive public key from S1 private key
                    val privateKeyBytes = walletManager.decodeS1PrivateKey(s1Key)
                    val publicKeyBytes = walletManager.derivePublicKey(privateKeyBytes)
                    val publicKeyP1 = walletManager.encodePublicKeyP1(publicKeyBytes)
                    
                    _uiState.update { it.copy(
                        privateKeyS1 = s1Key,  // S1 private key format
                        publicKeyP1 = publicKeyP1,  // P1 public key format
                        isS1Import = true
                    ) }
                } else if (mnemonic != null) {
                    // For mnemonic imports, derive both keys
                    val privateKeyBytes = walletManager.getPrivateKey(mnemonic)
                    val privateKeyS1 = walletManager.getPrivateKeyS1(mnemonic)
                    val publicKeyBytes = walletManager.derivePublicKey(privateKeyBytes)
                    val publicKeyP1 = walletManager.encodePublicKeyP1(publicKeyBytes)
                    
                    _uiState.update { it.copy(
                        privateKeyS1 = privateKeyS1,  // S1 private key format
                        publicKeyP1 = publicKeyP1,  // P1 public key format
                        isS1Import = false
                    ) }
                } else {
                    _uiState.update { it.copy(error = "No wallet found") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load keys: ${e.message}") }
            }
        }
    }
    
    fun clearAllData() {
        secureStorage.clear()
        _uiState.update { SettingsState() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Export transaction history to JSON file
     */
    fun exportTransactionHistory(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val address = secureStorage.getActiveWallet() ?: run {
                    _uiState.update { it.copy(error = "No active wallet found") }
                    return@launch
                }
                
                when (val result = massaRepository.exportTransactionHistory(address)) {
                    is com.massapay.android.core.util.Result.Success -> {
                        onSuccess(result.data)
                    }
                    is com.massapay.android.core.util.Result.Error -> {
                        _uiState.update { it.copy(error = result.exception.message) }
                    }
                    is com.massapay.android.core.util.Result.Loading -> {
                        // Loading state not expected for this operation
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Export failed: ${e.message}") }
            }
        }
    }

    /**
     * Import transaction history from JSON file
     */
    fun importTransactionHistory(filePath: String, onSuccess: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val address = secureStorage.getActiveWallet() ?: run {
                    _uiState.update { it.copy(error = "No active wallet found") }
                    return@launch
                }
                
                when (val result = massaRepository.importTransactionHistory(address, filePath)) {
                    is com.massapay.android.core.util.Result.Success -> {
                        onSuccess(result.data) // Number of new transactions imported
                    }
                    is com.massapay.android.core.util.Result.Error -> {
                        _uiState.update { it.copy(error = result.exception.message) }
                    }
                    is com.massapay.android.core.util.Result.Loading -> {
                        // Loading state not expected for this operation
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Import failed: ${e.message}") }
            }
        }
    }
}

data class SettingsState(
    val biometricEnabled: Boolean = false,
    val activeWallet: String? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val seedPhrase: String? = null,
    val s1PrivateKey: String? = null,
    val isS1Import: Boolean = false,
    val privateKeyS1: String? = null,  // S1 format (for import/export)
    val publicKeyP1: String? = null,   // P1 format (for verification/sharing)
    val error: String? = null,
    val pinChangeSuccess: Boolean = false
)
