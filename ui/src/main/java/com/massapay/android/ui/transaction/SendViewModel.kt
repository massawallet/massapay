package com.massapay.android.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.core.model.Token
import com.massapay.android.core.util.Constants
import com.massapay.android.core.util.Result
import com.massapay.android.network.repository.MassaRepository
import com.massapay.android.price.repository.PriceRepository
import com.massapay.android.security.storage.SecureStorage
import com.massapay.android.security.wallet.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class SendViewModel @Inject constructor(
    private val massaRepository: MassaRepository,
    private val priceRepository: PriceRepository,
    private val secureStorage: SecureStorage,
    private val walletManager: WalletManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendState())
    val uiState: StateFlow<SendState> = _uiState
    
    private val _biometricRequest = MutableSharedFlow<Unit>()
    val biometricRequest: SharedFlow<Unit> = _biometricRequest
    
    fun getBiometricEnabled(): Boolean = secureStorage.isBiometricEnabled()
    
    fun verifyPin(pin: String): Boolean = secureStorage.getPin() == pin
    
    fun requestBiometric() {
        viewModelScope.launch {
            _biometricRequest.emit(Unit)
        }
    }

    init {
        viewModelScope.launch {
            try {
                // Get current wallet balance
                val address = secureStorage.getActiveWallet()
                address?.let { loadBalance(it) }

                // Start USD price updates
                priceRepository.getPrice("massa").collect { result ->
                    when (result) {
                        is com.massapay.android.core.util.Result.Success -> {
                            val price = result.data
                            _uiState.update { it.copy(
                                usdPrice = price,
                                usdAmount = calculateUsdAmount(it.amount, price)
                            ) }
                        }
                        else -> { /* ignore errors/loading */ }
                    }
                }
            } catch (e: Exception) {
                // Fail gracefully if price service is unavailable
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadBalance(address: String) {
        when (val balRes = massaRepository.getAddressBalance(address)) {
            is com.massapay.android.core.util.Result.Success -> {
                _uiState.update { it.copy(
                    availableBalance = balRes.data,
                    isLoading = false
                ) }
            }
            else -> { /* ignore errors for now */ }
        }
    }

    fun updateAmount(amount: String) {
        try {
            val currentState = _uiState.value
            
            if (currentState.isUsdMode) {
                // User is entering USD, convert to MAS
                val usdAmount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val masAmount = if (currentState.usdPrice > 0 && usdAmount > BigDecimal.ZERO) {
                    usdAmount.divide(BigDecimal.valueOf(currentState.usdPrice), 8, BigDecimal.ROUND_HALF_UP)
                } else {
                    BigDecimal.ZERO
                }
                
                _uiState.update { it.copy(
                    usdAmount = amount,
                    amount = if (masAmount > BigDecimal.ZERO) masAmount.toPlainString() else "",
                    isValidAmount = masAmount > BigDecimal.ZERO && masAmount <= it.availableBalance.toBigDecimal()
                ) }
                
                if (masAmount > BigDecimal.ZERO) {
                    estimateFee(masAmount.toPlainString())
                }
            } else {
                // User is entering MAS
                val numericAmount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                _uiState.update { it.copy(
                    amount = amount,
                    usdAmount = calculateUsdAmount(amount, it.usdPrice),
                    isValidAmount = numericAmount <= it.availableBalance.toBigDecimal()
                ) }
                
                if (numericAmount > BigDecimal.ZERO) {
                    estimateFee(amount)
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(
                amount = if (!it.isUsdMode) amount else it.amount,
                usdAmount = if (it.isUsdMode) amount else it.usdAmount,
                isValidAmount = false
            ) }
        }
    }
    
    fun toggleCurrency() {
        _uiState.update { it.copy(
            isUsdMode = !it.isUsdMode,
            amount = "",
            usdAmount = ""
        ) }
    }
    
    fun setMaxAmount() {
        val availableBalance = _uiState.value.availableBalance.toBigDecimalOrNull() ?: BigDecimal.ZERO
        
        // Base fee for simple transfer: 0.01 MAS
        // Additional safety margin for potential account creation: 0.01 MAS
        val baseFee = BigDecimal("0.01")
        val safetyMargin = BigDecimal("0.01")
        val totalReserve = baseFee + safetyMargin
        
        // Calculate max amount (balance - fees - safety margin)
        val maxAmount = (availableBalance - totalReserve).max(BigDecimal.ZERO)
        
        // If in USD mode, convert to USD
        if (_uiState.value.isUsdMode) {
            val maxUsd = maxAmount * BigDecimal.valueOf(_uiState.value.usdPrice)
            updateAmount(maxUsd.toPlainString())
        } else {
            updateAmount(maxAmount.toPlainString())
        }
    }

    fun updateAddress(address: String) {
        _uiState.update { it.copy(
            recipientAddress = address,
            isValidAddress = massaRepository.validateMassaAddress(address)
        ) }
    }

    private fun estimateFee(amount: String) {
        viewModelScope.launch {
            when (val feeResult = massaRepository.estimateTransactionFee(amount)) {
                is Result.Success -> {
                    // Convert nanoMAS to MAS (divide by 1e9)
                    val feeNano = feeResult.data.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val feeMas = feeNano.divide(BigDecimal.valueOf(1000000000))
                    _uiState.update { it.copy(estimatedFee = feeMas.toString()) }
                }
                else -> {
                    // Use fallback fee
                    _uiState.update { it.copy(estimatedFee = "0.1") }
                }
            }
        }
    }

    fun requestAuthentication() {
        _uiState.update { it.copy(showAuthDialog = true) }
    }
    
    fun onAuthenticationSuccess() {
        _uiState.update { it.copy(showAuthDialog = false) }
        sendTransaction()
    }
    
    fun onAuthenticationCancelled() {
        _uiState.update { it.copy(showAuthDialog = false) }
    }
    
    private fun sendTransaction() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val fromAddress = secureStorage.getActiveWallet() ?: return@launch
            
            // Get the mnemonic to derive keys
            val mnemonic = secureStorage.getMnemonic("default_wallet")
            if (mnemonic == null) {
                _uiState.update { it.copy(
                    error = "No wallet found",
                    isLoading = false,
                    showFailureScreen = true
                ) }
                return@launch
            }
            
            // Derive the address to get public key
            val address = walletManager.deriveAddress(mnemonic)
            val publicKey = address.publicKey
            
            // Get private key bytes and convert to hex
            val privateKeyBytes = walletManager.getPrivateKey(mnemonic)
            val privateKey = privateKeyBytes.joinToString("") { "%02x".format(it) }
            
            val sendRes = massaRepository.sendTransaction(
                from = fromAddress,
                to = _uiState.value.recipientAddress,
                amount = _uiState.value.amount,
                fee = _uiState.value.estimatedFee,
                privateKey = privateKey,
                publicKey = publicKey
            )

            when (sendRes) {
                is com.massapay.android.core.util.Result.Success -> {
                    _uiState.update { it.copy(
                        transactionHash = sendRes.data,
                        isSuccess = true,
                        isLoading = false,
                        showSuccessScreen = true
                    ) }
                }
                is com.massapay.android.core.util.Result.Error -> {
                    _uiState.update { it.copy(
                        error = sendRes.exception.message,
                        isLoading = false,
                        showFailureScreen = true
                    ) }
                }
                else -> { /* ignore */ }
            }
        }
    }

    fun parseQrCode(qrData: String) {
        try {
            if (qrData.startsWith(Constants.MASSA_QR_SCHEME)) {
                // Parse massa:address?amount=... format
                val uri = android.net.Uri.parse(qrData)
                val address = uri.schemeSpecificPart
                val amount = uri.getQueryParameter(Constants.QR_PARAM_AMOUNT)
                
                _uiState.update { it.copy(
                    recipientAddress = address,
                    amount = amount ?: it.amount,
                    isValidAddress = massaRepository.validateMassaAddress(address)
                ) }
            } else {
                // Treat as plain address
                val address = qrData.trim()
                _uiState.update { it.copy(
                    recipientAddress = address,
                    isValidAddress = massaRepository.validateMassaAddress(address)
                ) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(
                error = "Invalid QR code: ${e.message}"
            ) }
        }
    }
    
    fun dismissResultScreen() {
        _uiState.update { it.copy(
            showSuccessScreen = false,
            showFailureScreen = false
        ) }
    }

    private fun calculateUsdAmount(amount: String, usdPrice: Double): String {
        return try {
            val masaAmount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
            (masaAmount * BigDecimal.valueOf(usdPrice)).toString()
        } catch (e: Exception) {
            "0"
        }
    }
}

data class SendState(
    val amount: String = "",
    val usdAmount: String = "0",
    val usdPrice: Double = 0.0,
    val recipientAddress: String = "",
    val availableBalance: String = "0",
    val estimatedFee: String = "0.1",
    val isValidAmount: Boolean = true,
    val isValidAddress: Boolean = true,
    val isLoading: Boolean = true,
    val isSuccess: Boolean = false,
    val showAuthDialog: Boolean = false,
    val transactionHash: String? = null,
    val error: String? = null,
    val isUsdMode: Boolean = false,  // Toggle between MAS and USD input
    val showSuccessScreen: Boolean = false,
    val showFailureScreen: Boolean = false
)