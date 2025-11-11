package com.massapay.android.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.core.util.Constants
import com.massapay.android.price.repository.PriceRepository
import com.massapay.android.security.storage.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val priceRepository: PriceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiveState())
    val uiState: StateFlow<ReceiveState> = _uiState

    init {
        viewModelScope.launch {
            val address = secureStorage.getActiveWallet()
            if (address != null) {
                _uiState.update { it.copy(
                    address = address,
                    qrContent = generateQrContent(address, null)
                ) }
            }

            // Monitor USD price
            priceRepository.getPrice("massa").collect { result ->
                when (result) {
                    is com.massapay.android.core.util.Result.Success -> _uiState.update { it.copy(usdPrice = result.data) }
                    else -> { /* ignore */ }
                }
            }
        }
    }

    fun updateRequestAmount(amount: String) {
        try {
            val numericAmount = amount.toBigDecimalOrNull()
            if (numericAmount != null && numericAmount.signum() >= 0) {
                val currentState = _uiState.value
                val masAmount: String
                val usdAmountStr: String
                
                if (currentState.isUsdMode) {
                    // User is entering USD, convert to MAS
                    usdAmountStr = amount
                    masAmount = if (amount.isNotEmpty() && currentState.usdPrice > 0) {
                        val usdValue = amount.toDoubleOrNull() ?: 0.0
                        val masValue = usdValue / currentState.usdPrice
                        String.format("%.6f", masValue).trimEnd('0').trimEnd('.')
                    } else {
                        ""
                    }
                } else {
                    // User is entering MAS, convert to USD
                    masAmount = amount
                    usdAmountStr = if (amount.isNotEmpty() && currentState.usdPrice > 0) {
                        val masValue = amount.toDoubleOrNull() ?: 0.0
                        val usdValue = masValue * currentState.usdPrice
                        String.format("%.2f", usdValue)
                    } else {
                        ""
                    }
                }
                
                _uiState.update { state ->
                    state.copy(
                        requestAmount = masAmount,
                        usdAmount = usdAmountStr,
                        qrContent = generateQrContent(state.address, masAmount),
                        error = null
                    )
                }
            } else {
                _uiState.update { it.copy(error = "Invalid amount") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Invalid amount") }
        }
    }
    
    fun toggleCurrency() {
        _uiState.update { it.copy(isUsdMode = !it.isUsdMode) }
    }

    private fun generateQrContent(address: String, amount: String?): String {
        return buildString {
            append(Constants.MASSA_QR_SCHEME)
            append(address)
            if (!amount.isNullOrEmpty()) {
                append("?")
                append(Constants.QR_PARAM_AMOUNT)
                append("=")
                append(amount)
            }
        }
    }

    fun clearAmount() {
        _uiState.update { state ->
            state.copy(
                requestAmount = "",
                usdAmount = "",
                qrContent = generateQrContent(state.address, null)
            )
        }
    }
}

data class ReceiveState(
    val address: String = "",
    val requestAmount: String = "",
    val usdAmount: String = "",
    val isUsdMode: Boolean = false,
    val qrContent: String = "",
    val usdPrice: Double = 0.0,
    val error: String? = null
)