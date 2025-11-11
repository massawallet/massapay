package com.massapay.android.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.security.biometric.BiometricManager
import com.massapay.android.security.storage.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val biometricManager: BiometricManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockState())
    val uiState: StateFlow<LockState> = _uiState

    init {
        _uiState.update { 
            it.copy(biometricAvailable = secureStorage.isBiometricEnabled())
        }
    }

    fun verifyPin(pin: String) {
        val storedPin = secureStorage.getPin()
        if (storedPin == pin) {
            _uiState.update { it.copy(isUnlocked = true, error = null) }
        } else {
            _uiState.update { it.copy(error = "Incorrect PIN", isUnlocked = false) }
        }
    }

    fun authenticateWithBiometric() {
        _uiState.update { it.copy(showBiometricPrompt = true, isLoading = true) }
    }

    fun resetBiometricPrompt() {
        _uiState.update { it.copy(showBiometricPrompt = false) }
    }

    fun onBiometricSuccess() {
        _uiState.update { it.copy(error = null, showBiometricPrompt = false, isLoading = true) }
        viewModelScope.launch {
            delay(5000)
            _uiState.update { it.copy(isUnlocked = true, isLoading = false) }
        }
    }

    fun onBiometricError(error: String) {
        _uiState.update { it.copy(error = error, isUnlocked = false, showBiometricPrompt = false, isLoading = false) }
    }

    fun resetWallet() {
        secureStorage.clear()
        _uiState.update { it.copy(walletReset = true) }
    }

    fun showForgotPasswordDialog(show: Boolean) {
        _uiState.update { it.copy(showForgotPasswordDialog = show) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class LockState(
    val isUnlocked: Boolean = false,
    val biometricAvailable: Boolean = false,
    val showBiometricPrompt: Boolean = false,
    val error: String? = null,
    val showForgotPasswordDialog: Boolean = false,
    val walletReset: Boolean = false,
    val isLoading: Boolean = false
)
