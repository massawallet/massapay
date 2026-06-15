package com.massapay.android.ui.staking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.core.model.BuyRollsRequest
import com.massapay.android.core.model.SellRollsRequest
import com.massapay.android.core.model.StakingInfo
import com.massapay.android.core.util.Result
import com.massapay.android.network.repository.StakingRepository
import com.massapay.android.security.storage.SecureStorage
import com.massapay.android.security.wallet.AccountManager
import com.massapay.android.security.wallet.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StakingViewModel @Inject constructor(
    private val stakingRepository: StakingRepository,
    private val secureStorage: SecureStorage,
    private val accountManager: AccountManager,
    private val walletManager: WalletManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(StakingUiState())
    val uiState: StateFlow<StakingUiState> = _uiState.asStateFlow()

    private var currentAddress: String? = null
    
    init {
        observeActiveAccount()
    }

    private fun observeActiveAccount() {
        viewModelScope.launch {
            accountManager.activeAccount.collectLatest { account ->
                val address = account?.address ?: secureStorage.getActiveWallet()
                if (address.isNullOrBlank() || address == currentAddress) {
                    return@collectLatest
                }

                currentAddress = address
                loadStakingInfo(address)
            }
        }
    }
    
    fun loadStakingInfo() {
        val address = getActiveAddress() ?: return
        currentAddress = address
        loadStakingInfo(address)
    }

    private fun loadStakingInfo(address: String) {
        viewModelScope.launch {
            stakingRepository.getStakingInfo(address).collect { result ->
                _uiState.update { state ->
                    if (currentAddress != address) {
                        return@update state
                    }
                    when (result) {
                        is Result.Loading -> state.copy(isLoading = true, error = null)
                        is Result.Success -> state.copy(
                            isLoading = false,
                            stakingInfo = result.data,
                            error = null
                        )
                        is Result.Error -> state.copy(
                            isLoading = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }
    
    private fun getActiveAddress(): String? {
        return accountManager.activeAccount.value?.address ?: secureStorage.getActiveWallet()
    }
    
    private fun getCredentials(): Triple<String, String, String>? {
        try {
            val activeAccount = accountManager.activeAccount.value
            val address = activeAccount?.address ?: secureStorage.getActiveWallet()
                ?: return null
            
            // Get private key
            val privateKeyBytes = if (activeAccount != null) {
                accountManager.getPrivateKeyForAccount(activeAccount.id)
            } else {
                val mnemonic = secureStorage.getMnemonic("default_wallet")
                mnemonic?.let { walletManager.getPrivateKey(it) }
            } ?: return null
            
            val privateKey = privateKeyBytes.joinToString("") { "%02x".format(it) }
            val publicKeyBytes = walletManager.derivePublicKey(privateKeyBytes)
            val publicKey = walletManager.encodePublicKeyP1(publicKeyBytes)
            
            return Triple(address, privateKey, publicKey)
        } catch (e: Exception) {
            android.util.Log.e("StakingVM", "Error getting credentials", e)
            return null
        }
    }
    
    fun buyRolls(rollCount: Int) {
        viewModelScope.launch {
            val credentials = getCredentials()
            if (credentials == null) {
                _uiState.update { it.copy(error = "Unable to access wallet credentials") }
                return@launch
            }
            
            val (address, privateKey, publicKey) = credentials
            
            _uiState.update { it.copy(isProcessing = true, error = null) }
            
            android.util.Log.d("StakingVM", "Buying $rollCount rolls for address $address")
            
            val request = BuyRollsRequest(rollCount = rollCount)
            val result = stakingRepository.buyRolls(
                address = address,
                request = request,
                privateKey = privateKey,
                publicKey = publicKey
            )
            
            _uiState.update { state ->
                if (result.success) {
                    state.copy(
                        isProcessing = false,
                        successMessage = "Successfully purchased $rollCount roll(s)!",
                        lastOperationId = result.operationId
                    )
                } else {
                    state.copy(
                        isProcessing = false,
                        error = result.error
                    )
                }
            }
            
            // Reload staking info after operation
            if (result.success) {
                kotlinx.coroutines.delay(2000) // Wait for network to process
                loadStakingInfo()
            }
        }
    }
    
    fun sellRolls(rollCount: Int) {
        viewModelScope.launch {
            val credentials = getCredentials()
            if (credentials == null) {
                _uiState.update { it.copy(error = "Unable to access wallet credentials") }
                return@launch
            }
            
            val (address, privateKey, publicKey) = credentials
            
            _uiState.update { it.copy(isProcessing = true, error = null) }
            
            android.util.Log.d("StakingVM", "Selling $rollCount rolls for address $address")
            
            val request = SellRollsRequest(rollCount = rollCount)
            val result = stakingRepository.sellRolls(
                address = address,
                request = request,
                privateKey = privateKey,
                publicKey = publicKey
            )
            
            _uiState.update { state ->
                if (result.success) {
                    state.copy(
                        isProcessing = false,
                        successMessage = "Successfully sold $rollCount roll(s)! MAS will be available in ~3 cycles.",
                        lastOperationId = result.operationId
                    )
                } else {
                    state.copy(
                        isProcessing = false,
                        error = result.error
                    )
                }
            }
            
            // Reload staking info after operation
            if (result.success) {
                kotlinx.coroutines.delay(2000)
                loadStakingInfo()
            }
        }
    }
    
    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, error = null) }
    }
    
    fun getMaxRollsToBuy(): Int {
        val balance = _uiState.value.stakingInfo?.balance?.toDoubleOrNull() ?: 0.0
        // Reserve 0.1 MAS for fee
        val availableForRolls = balance - 0.1
        return if (availableForRolls > 0) {
            (availableForRolls / StakingInfo.ROLL_PRICE_MAS).toInt()
        } else {
            0
        }
    }
    
    fun getMaxRollsToSell(): Int {
        return _uiState.value.stakingInfo?.finalRolls ?: 0
    }
}

data class StakingUiState(
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val stakingInfo: StakingInfo? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val lastOperationId: String? = null
)
