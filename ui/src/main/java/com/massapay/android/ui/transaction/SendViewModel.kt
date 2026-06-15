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
    private val walletManager: WalletManager,
    private val accountManager: com.massapay.android.security.wallet.AccountManager
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

    private var currentAccountAddress: String? = null

    init {
        observeActiveAccount()
        observePrice()
    }

    private fun observeActiveAccount() {
        viewModelScope.launch {
            accountManager.activeAccount.collectLatest { account ->
                val address = account?.address ?: secureStorage.getActiveWallet()
                if (address.isNullOrBlank() || address == currentAccountAddress) {
                    return@collectLatest
                }

                currentAccountAddress = address
                _uiState.update {
                    it.copy(
                        availableBalance = "0",
                        isLoading = true,
                        isValidAmount = it.amount.isBlank(),
                        activeAccountAddress = address,
                        activeAccountName = account?.name,
                        error = null,
                        showAuthDialog = false,
                        showSuccessScreen = false,
                        showFailureScreen = false
                    )
                }
                loadBalance(address)
            }
        }
    }

    private fun observePrice() {
        viewModelScope.launch {
            try {
                priceRepository.getPrice("massa").collect { result ->
                    if (result is com.massapay.android.core.util.Result.Success) {
                        val price = result.data
                        _uiState.update {
                            it.copy(
                                usdPrice = price,
                                usdAmount = calculateUsdAmount(it.amount, price)
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadBalance(address: String) {
        when (val balRes = massaRepository.getAddressBalance(address)) {
            is com.massapay.android.core.util.Result.Success -> {
                _uiState.update {
                    it.copy(
                        availableBalance = balRes.data,
                        isLoading = false,
                        isValidAmount = isAmountValid(it.amount, balRes.data)
                    )
                }
            }
            is com.massapay.android.core.util.Result.Error -> {
                _uiState.update {
                    it.copy(
                        availableBalance = "0",
                        isLoading = false,
                        isValidAmount = it.amount.isBlank()
                    )
                }
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
                    isValidAmount = isAmountValid(
                        if (masAmount > BigDecimal.ZERO) masAmount.toPlainString() else "",
                        it.availableBalance
                    )
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
                    isValidAmount = numericAmount > BigDecimal.ZERO &&
                        numericAmount <= (it.availableBalance.toBigDecimalOrNull() ?: BigDecimal.ZERO)
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
            usdAmount = "",
            isValidAmount = true
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
        val normalizedAddress = address.trim()
        _uiState.update { it.copy(
            recipientAddress = normalizedAddress,
            isValidAddress = isStrictMassaAddress(normalizedAddress)
        ) }
    }

    private fun isStrictMassaAddress(address: String): Boolean {
        val base58Alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        return (address.length == 52 || address.length == 53) &&
            address.startsWith("AU1") &&
            address.drop(2).all { it in base58Alphabet } &&
            massaRepository.validateMassaAddress(address)
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
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val activeAccount = accountManager.activeAccount.value
            val fromAddress = activeAccount?.address ?: secureStorage.getActiveWallet() ?: return@launch
            val amountToSend = _uiState.value.amount.toBigDecimalOrNull()
            if (amountToSend == null || amountToSend <= BigDecimal.ZERO) {
                _uiState.update { it.copy(
                    error = "Enter a valid amount",
                    isLoading = false
                ) }
                return@launch
            }

            when (val balanceResult = massaRepository.getAddressBalance(fromAddress)) {
                is com.massapay.android.core.util.Result.Success -> {
                    val liveBalance = balanceResult.data.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    _uiState.update {
                        it.copy(
                            availableBalance = balanceResult.data,
                            isValidAmount = amountToSend <= liveBalance
                        )
                    }
                    if (amountToSend > liveBalance) {
                        _uiState.update { it.copy(
                            error = "Insufficient balance for the selected account",
                            isLoading = false
                        ) }
                        return@launch
                    }
                }
                is com.massapay.android.core.util.Result.Error -> {
                    _uiState.update { it.copy(
                        error = balanceResult.exception.message ?: "Could not verify selected account balance",
                        isLoading = false
                    ) }
                    return@launch
                }
                else -> Unit
            }
            
            // Get private key from AccountManager
            val privateKeyBytes = if (activeAccount != null) {
                accountManager.getPrivateKeyForAccount(activeAccount.id)
            } else {
                // Fallback for legacy single account
                val mnemonic = secureStorage.getMnemonic("default_wallet")
                if (mnemonic != null) {
                    walletManager.getPrivateKey(mnemonic)
                } else {
                    null
                }
            }

            if (privateKeyBytes == null) {
                _uiState.update { it.copy(
                    error = "Could not retrieve private key",
                    isLoading = false,
                    showFailureScreen = true
                ) }
                return@launch
            }
            
            val privateKey = privateKeyBytes.joinToString("") { "%02x".format(it) }
            
            // Derive public key using WalletManager (assuming same curve Ed25519)
            // We use WalletManager here as a utility since AccountManager doesn't expose public key directly yet
            val publicKeyBytes = walletManager.derivePublicKey(privateKeyBytes)
            val publicKey = walletManager.encodePublicKeyP1(publicKeyBytes)
            
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
                    usdAmount = calculateUsdAmount(amount ?: it.amount, it.usdPrice),
                    isValidAmount = isAmountValid(amount ?: it.amount, it.availableBalance),
                    isValidAddress = isStrictMassaAddress(address)
                ) }
            } else {
                // Treat as plain address
                val address = qrData.trim()
                _uiState.update { it.copy(
                    recipientAddress = address,
                    isValidAddress = isStrictMassaAddress(address)
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

    private fun isAmountValid(amount: String, balance: String): Boolean {
        if (amount.isBlank()) return true
        val numericAmount = amount.toBigDecimalOrNull() ?: return false
        val availableBalance = balance.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return numericAmount > BigDecimal.ZERO && numericAmount <= availableBalance
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
    val activeAccountAddress: String? = null,
    val activeAccountName: String? = null,
    val isUsdMode: Boolean = false,  // Toggle between MAS and USD input
    val showSuccessScreen: Boolean = false,
    val showFailureScreen: Boolean = false
)
