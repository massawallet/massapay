package com.massapay.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.security.storage.SecureStorage
import com.massapay.android.security.wallet.AccountManager
import com.massapay.android.core.preferences.AdvancedFeatureManager
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
    private val advancedFeatureManager: AdvancedFeatureManager,
    private val walletManager: com.massapay.android.security.wallet.WalletManager,
    private val massaRepository: com.massapay.android.network.repository.MassaRepository,
    private val accountManager: AccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState

    init {
        loadSettings()
        observeAdvancedFeatures()
        observeActiveAccount()
    }

    private fun observeAdvancedFeatures() {
        viewModelScope.launch {
            advancedFeatureManager.featureState.collect { featureState ->
                _uiState.update {
                    it.copy(
                        nftEnabled = featureState.nftEnabled,
                        swapEnabled = featureState.swapEnabled,
                        stakingEnabled = featureState.stakingEnabled
                    )
                }
            }
        }
    }

    private fun observeActiveAccount() {
        viewModelScope.launch {
            accountManager.activeAccount.collect { account ->
                account?.let {
                    _uiState.update { state ->
                        state.copy(
                            activeWallet = account.address,
                            activeAccountName = account.name
                        )
                    }
                }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            themeManager.themeMode.collect { themeMode ->
                val activeAccount = accountManager.activeAccount.value
                val activeWallet = activeAccount?.address ?: secureStorage.getActiveWallet()
                
                // Check if active account has mnemonic or was imported from S1
                val hasMnemonic = !activeAccount?.mnemonic.isNullOrBlank()
                val isS1Import = !hasMnemonic
                
                _uiState.update {
                    it.copy(
                        biometricEnabled = secureStorage.isBiometricEnabled(),
                        activeWallet = activeWallet,
                        activeAccountName = activeAccount?.name ?: "Main Account",
                        seedPhrase = if (hasMnemonic) activeAccount?.mnemonic else null,
                        s1PrivateKey = if (isS1Import) activeAccount?.privateKeyS1 else null,
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

    fun enableBiometric(activity: androidx.fragment.app.FragmentActivity, onError: (String) -> Unit) {
        val biometricManager = androidx.biometric.BiometricManager.from(activity)
        val canAuthenticate = biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        when (canAuthenticate) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                val biometricPrompt = androidx.biometric.BiometricPrompt(
                    activity,
                    executor,
                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                            toggleBiometric(true)
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            onError("Authentication error: $errString")
                        }
                        override fun onAuthenticationFailed() {
                            onError("Authentication failed")
                        }
                    }
                )
                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Enable Biometric Authentication")
                    .setSubtitle("Authenticate to enable biometric unlock")
                    .setNegativeButtonText("Cancel")
                    .build()
                biometricPrompt.authenticate(promptInfo)
            }
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                onError("No biometric hardware available")
            }
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                onError("Biometric hardware unavailable")
            }
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                onError("No biometric credentials enrolled. Please set up biometric in device settings.")
            }
            else -> {
                onError("Biometric authentication not available")
            }
        }
    }

    fun disableBiometric() {
        toggleBiometric(false)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeManager.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    fun setNftEnabled(enabled: Boolean) {
        viewModelScope.launch {
            advancedFeatureManager.setNftEnabled(enabled)
        }
    }

    fun setSwapEnabled(enabled: Boolean) {
        viewModelScope.launch {
            advancedFeatureManager.setSwapEnabled(enabled)
        }
    }

    fun setStakingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            advancedFeatureManager.setStakingEnabled(enabled)
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
        // Get seed phrase from active account
        val activeAccount = accountManager.activeAccount.value
        val mnemonic = activeAccount?.mnemonic
        val hasMnemonic = !mnemonic.isNullOrBlank()
        
        _uiState.update { 
            it.copy(
                seedPhrase = if (hasMnemonic) mnemonic else null,
                s1PrivateKey = if (!hasMnemonic) activeAccount?.privateKeyS1 else null,
                isS1Import = !hasMnemonic
            ) 
        }
    }

    fun loadPrivateKey() {
        viewModelScope.launch {
            try {
                // Get keys from active account
                val activeAccount = accountManager.activeAccount.value
                
                if (activeAccount != null) {
                    _uiState.update { it.copy(
                        privateKeyS1 = activeAccount.privateKeyS1,
                        publicKeyP1 = activeAccount.publicKey,
                        isS1Import = activeAccount.mnemonic.isBlank()
                    ) }
                } else {
                    // Fallback to old method for migration
                    val mnemonic = secureStorage.getMnemonic("default_wallet")
                    val s1Key = secureStorage.getMnemonic("s1_private_key")
                    val isS1Import = s1Key != null && mnemonic == null
                    
                    if (isS1Import && s1Key != null) {
                        val privateKeyBytes = walletManager.decodeS1PrivateKey(s1Key)
                        val publicKeyBytes = walletManager.derivePublicKey(privateKeyBytes)
                        val publicKeyP1 = walletManager.encodePublicKeyP1(publicKeyBytes)
                        
                        _uiState.update { it.copy(
                            privateKeyS1 = s1Key,
                            publicKeyP1 = publicKeyP1,
                            isS1Import = true
                        ) }
                    } else if (mnemonic != null) {
                        val privateKeyBytes = walletManager.getPrivateKey(mnemonic)
                        val privateKeyS1 = walletManager.getPrivateKeyS1(mnemonic)
                        val publicKeyBytes = walletManager.derivePublicKey(privateKeyBytes)
                        val publicKeyP1 = walletManager.encodePublicKeyP1(publicKeyBytes)
                        
                        _uiState.update { it.copy(
                            privateKeyS1 = privateKeyS1,
                            publicKeyP1 = publicKeyP1,
                            isS1Import = false
                        ) }
                    } else {
                        _uiState.update { it.copy(error = "No wallet found") }
                    }
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
                val address = accountManager.activeAccount.value?.address
                    ?: secureStorage.getActiveWallet()
                    ?: run {
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
                val address = accountManager.activeAccount.value?.address
                    ?: secureStorage.getActiveWallet()
                    ?: run {
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
    val activeAccountName: String = "Main Account",
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val seedPhrase: String? = null,
    val s1PrivateKey: String? = null,
    val isS1Import: Boolean = false,
    val privateKeyS1: String? = null,  // S1 format (for import/export)
    val publicKeyP1: String? = null,   // P1 format (for verification/sharing)
    val error: String? = null,
    val pinChangeSuccess: Boolean = false,
    val nftEnabled: Boolean = false,
    val swapEnabled: Boolean = false,
    val stakingEnabled: Boolean = false
)
