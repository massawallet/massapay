package com.massapay.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.security.wallet.MnemonicManager
import com.massapay.android.security.wallet.WalletManager
import com.massapay.android.security.storage.SecureStorage
import com.massapay.android.security.crypto.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModelNew @Inject constructor(
    private val mnemonicManager: MnemonicManager,
    private val walletManager: WalletManager,
    private val secureStorage: SecureStorage,
    private val keystoreManager: KeystoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingStateNew())
    val uiState: StateFlow<OnboardingStateNew> = _uiState

    fun startCreateWallet() {
        viewModelScope.launch {
            try {
                val mnemonic = mnemonicManager.generateMnemonic()
                _uiState.update {
                    it.copy(
                        seedWords = mnemonic.split(" "),
                        currentStep = OnboardingStepNew.GENERATE_SEED,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to generate seed: ${e.message}")
                }
            }
        }
    }

    fun startImportWallet(seedPhrase: String) {
        viewModelScope.launch {
            try {
                // Check if this is an S1 private key import
                if (seedPhrase.startsWith("S1_IMPORT:")) {
                    val s1Key = seedPhrase.substring("S1_IMPORT:".length).trim()
                    
                    // Store with special marker
                    _uiState.update {
                        it.copy(
                            seedWords = listOf("S1_PRIVATE_KEY", s1Key), // Special marker
                            currentStep = OnboardingStepNew.SETUP_PIN,
                            error = null
                        )
                    }
                } else {
                    // Standard mnemonic import
                    if (mnemonicManager.validateMnemonic(seedPhrase.trim())) {
                        _uiState.update {
                            it.copy(
                                seedWords = seedPhrase.trim().split(" "),
                                currentStep = OnboardingStepNew.SETUP_PIN,
                                error = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(error = "Invalid seed phrase")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Import failed: ${e.message}")
                }
            }
        }
    }

    fun moveToVerifySeed() {
        _uiState.update {
            it.copy(currentStep = OnboardingStepNew.VERIFY_SEED)
        }
    }

    fun onSeedVerified() {
        _uiState.update {
            it.copy(currentStep = OnboardingStepNew.SETUP_PIN)
        }
    }

    fun onPinCreated(pin: String, biometricEnabled: Boolean) {
        viewModelScope.launch {
            try {
                // Store PIN
                secureStorage.storePin(pin)
                
                // Store biometric preference
                secureStorage.setBiometricEnabled(biometricEnabled)
                
                // Check if this is an S1 private key import
                val seedWords = _uiState.value.seedWords
                val isS1Import = seedWords.firstOrNull() == "S1_PRIVATE_KEY"
                
                val address = if (isS1Import) {
                    // Import from S1 private key
                    val s1Key = seedWords[1]
                    
                    // Store the S1 key
                    secureStorage.storeMnemonic("s1_private_key", s1Key)
                    
                    // Generate address from S1
                    val massaAddress = walletManager.importFromS1PrivateKey(s1Key)
                    massaAddress.address
                } else {
                    // Standard mnemonic import/generation
                    val mnemonicString = seedWords.joinToString(" ")
                    
                    // Store mnemonic
                    secureStorage.storeMnemonic("default_wallet", mnemonicString)
                    
                    // Derive address from mnemonic
                    val massaAddress = walletManager.deriveAddress(
                        mnemonic = mnemonicString,
                        passphrase = "",
                        accountIndex = 0,
                        addressIndex = 0
                    )
                    massaAddress.address
                }
                
                secureStorage.setActiveWallet(address)
                
                // Mark onboarding as completed
                secureStorage.setOnboardingCompleted(true)
                
                _uiState.update {
                    it.copy(
                        currentStep = OnboardingStepNew.COMPLETED,
                        error = null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(error = "Setup failed: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    fun goBack() {
        _uiState.update { state ->
            val previousStep = when (state.currentStep) {
                OnboardingStepNew.WELCOME -> OnboardingStepNew.WELCOME
                OnboardingStepNew.GENERATE_SEED -> OnboardingStepNew.WELCOME
                OnboardingStepNew.VERIFY_SEED -> OnboardingStepNew.GENERATE_SEED
                OnboardingStepNew.SETUP_PIN -> {
                    if (state.seedWords.isNotEmpty()) OnboardingStepNew.VERIFY_SEED
                    else OnboardingStepNew.WELCOME
                }
                OnboardingStepNew.COMPLETED -> OnboardingStepNew.WELCOME
            }
            state.copy(currentStep = previousStep, error = null)
        }
    }

    private fun generateRandomString(length: Int): String {
        val chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }
}

data class OnboardingStateNew(
    val currentStep: OnboardingStepNew = OnboardingStepNew.WELCOME,
    val seedWords: List<String> = emptyList(),
    val error: String? = null
)

enum class OnboardingStepNew {
    WELCOME,
    GENERATE_SEED,
    VERIFY_SEED,
    SETUP_PIN,
    COMPLETED
}
