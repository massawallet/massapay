package com.massapay.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.security.wallet.MnemonicManager
import com.massapay.android.security.storage.SecureStorage
import com.massapay.android.security.crypto.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val mnemonicManager: MnemonicManager,
    private val secureStorage: SecureStorage,
    private val keystoreManager: KeystoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingState())
    val uiState: StateFlow<OnboardingState> = _uiState

    fun generateNewWallet() {
        viewModelScope.launch {
            try {
                val mnemonic = mnemonicManager.generateMnemonic()
                _uiState.update { it.copy(
                    mnemonic = mnemonic.split(" "),
                    currentStep = OnboardingStep.SHOW_MNEMONIC
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun verifyMnemonicWord(index: Int, word: String) {
        val currentMnemonic = _uiState.value.mnemonic
        if (currentMnemonic.getOrNull(index) == word) {
            val verifiedWords = _uiState.value.verifiedWords.toMutableSet()
            verifiedWords.add(index)
            _uiState.update { it.copy(
                verifiedWords = verifiedWords,
                error = null
            ) }
            
            if (verifiedWords.size == 3) { // All words verified
                _uiState.update { it.copy(currentStep = OnboardingStep.SET_PIN) }
            }
        } else {
            _uiState.update { it.copy(error = "Incorrect word") }
        }
    }

    fun setPin(pin: String) {
        if (pin.length == 6) {
            _uiState.update { it.copy(
                pin = pin,
                currentStep = OnboardingStep.CONFIRM_PIN
            ) }
        }
    }

    fun confirmPin(pin: String) {
        if (pin == _uiState.value.pin) {
            viewModelScope.launch {
                try {
                    // Create master key for the wallet
                    keystoreManager.createMasterKey("wallet_key")
                    
                    // Check if this is a private key import or mnemonic
                    val mnemonicList = _uiState.value.mnemonic
                    val isPrivateKeyImport = mnemonicList.firstOrNull() == "IMPORTED_FROM_PRIVATE_KEY"
                    
                    if (isPrivateKeyImport) {
                        // Store the S1 private key securely
                        val s1Key = mnemonicList[1]  // Second element is the S1 key
                        val (encrypted, iv) = keystoreManager.encrypt(
                            s1Key.toByteArray(),
                            "wallet_key"
                        )
                        
                        // Store with a special marker
                        secureStorage.storeMnemonic("imported_s1_key", encrypted.toString())
                    } else {
                        // Standard mnemonic storage
                        val mnemonicString = mnemonicList.joinToString(" ")
                        val (encrypted, iv) = keystoreManager.encrypt(
                            mnemonicString.toByteArray(),
                            "wallet_key"
                        )
                        
                        secureStorage.storeMnemonic("default_wallet", encrypted.toString())
                    }
                    
                    // Store PIN securely
                    secureStorage.storePin(_uiState.value.pin)
                    
                    // Set a placeholder wallet address (will be replaced with actual derivation)
                    secureStorage.setActiveWallet("AU12... placeholder")
                    
                    // Move to biometric setup
                    _uiState.update { it.copy(
                        currentStep = OnboardingStep.SETUP_BIOMETRIC,
                        error = null
                    ) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message) }
                }
            }
        } else {
            _uiState.update { it.copy(error = "PINs do not match") }
        }
    }
    
    fun enableBiometric(enabled: Boolean) {
        secureStorage.setBiometricEnabled(enabled)
        // Mark onboarding as completed
        secureStorage.setOnboardingCompleted(true)
        _uiState.update { it.copy(currentStep = OnboardingStep.COMPLETE) }
    }
    
    fun skipBiometric() {
        secureStorage.setBiometricEnabled(false)
        // Mark onboarding as completed
        secureStorage.setOnboardingCompleted(true)
        _uiState.update { it.copy(currentStep = OnboardingStep.COMPLETE) }
    }

    fun importWallet(mnemonic: String) {
        if (mnemonicManager.validateMnemonic(mnemonic)) {
            _uiState.update { it.copy(
                mnemonic = mnemonic.split(" "),
                currentStep = OnboardingStep.SET_PIN,
                error = null
            ) }
        } else {
            _uiState.update { it.copy(error = "Invalid mnemonic") }
        }
    }

    /**
     * Import wallet from S1 private key (Massa Station/Bearby format)
     * This bypasses mnemonic verification and goes straight to PIN setup
     */
    fun importFromPrivateKey(privateKey: String) {
        viewModelScope.launch {
            try {
                // Validate S1 format
                if (!privateKey.startsWith("S1")) {
                    _uiState.update { it.copy(error = "Invalid private key format. Must start with S1") }
                    return@launch
                }
                
                // Store the S1 key temporarily (will be re-encrypted with PIN later)
                // For now, we'll use a placeholder mnemonic flag
                _uiState.update { it.copy(
                    mnemonic = listOf("IMPORTED_FROM_PRIVATE_KEY", privateKey), // Special marker
                    currentStep = OnboardingStep.SET_PIN,
                    error = null
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to import private key: ${e.message}") }
            }
        }
    }

    /**
     * Move one step backwards in the onboarding flow.
     * Used by UI back button to allow returning to the previous step.
     */
    fun goBack() {
        _uiState.update { state ->
            val prev = when (state.currentStep) {
                OnboardingStep.WELCOME -> OnboardingStep.WELCOME
                OnboardingStep.SHOW_MNEMONIC -> OnboardingStep.WELCOME
                OnboardingStep.VERIFY_MNEMONIC -> OnboardingStep.SHOW_MNEMONIC
                OnboardingStep.SET_PIN -> OnboardingStep.VERIFY_MNEMONIC
                OnboardingStep.CONFIRM_PIN -> OnboardingStep.SET_PIN
                OnboardingStep.SETUP_BIOMETRIC -> OnboardingStep.CONFIRM_PIN
                OnboardingStep.COMPLETE -> OnboardingStep.WELCOME
            }
            state.copy(currentStep = prev, error = null)
        }
    }
}

data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val mnemonic: List<String> = emptyList(),
    val verifiedWords: Set<Int> = emptySet(),
    val pin: String = "",
    val error: String? = null
)

enum class OnboardingStep {
    WELCOME,
    SHOW_MNEMONIC,
    VERIFY_MNEMONIC,
    SET_PIN,
    CONFIRM_PIN,
    SETUP_BIOMETRIC,
    COMPLETE
}