package com.massapay.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.core.model.Token
import com.massapay.android.core.model.Transaction
import com.massapay.android.network.repository.MassaRepository
import com.massapay.android.price.repository.PriceRepository
import com.massapay.android.price.model.MassaStats
import com.massapay.android.security.storage.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val massaRepository: MassaRepository,
    private val priceRepository: PriceRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState

    init {
        viewModelScope.launch {
            // Get active wallet address
            val address = secureStorage.getActiveWallet()
            address?.let {
                // store active wallet in state
                _uiState.update { state -> state.copy(activeWallet = it) }
                loadWalletData(it)
            }
        }
    }

    private fun loadWalletData(address: String) {
        viewModelScope.launch {
            try {
                // Get balance - si falla, mostrar 0
                when (val balRes = massaRepository.getAddressBalance(address)) {
                    is com.massapay.android.core.util.Result.Success -> {
                        val balance = balRes.data ?: "0"
                        _uiState.update { it.copy(
                            balance = balance,
                            isLoading = false
                        ) }

                        // Get USD value and stats
                        try {
                            android.util.Log.d("DashboardVM", "Requesting Massa stats...")
                            priceRepository.getMassaStats()
                                .collect { result ->
                                    android.util.Log.d("DashboardVM", "Stats result: $result")
                                    when (result) {
                                        is com.massapay.android.core.util.Result.Success -> {
                                            val stats = result.data
                                            android.util.Log.d("DashboardVM", "Stats received: price=${stats.price}, rank=${stats.rank}")
                                            _uiState.update { state ->
                                                state.copy(
                                                    usdValue = balance.toBigDecimal().multiply(stats.price.toBigDecimal()).toString(),
                                                    currentPrice = stats.price,
                                                    priceChange24h = stats.percentChange24h,
                                                    massaStats = stats
                                                )
                                            }
                                        }
                                        is com.massapay.android.core.util.Result.Error -> {
                                            android.util.Log.e("DashboardVM", "Error getting stats: ${result.exception.message}")
                                        }
                                        is com.massapay.android.core.util.Result.Loading -> {
                                            android.util.Log.d("DashboardVM", "Loading stats...")
                                        }
                                    }
                                }
                        } catch (e: Exception) {
                            // Si falla precio, continuar sin USD
                            android.util.Log.e("DashboardVM", "Failed to get price: ${e.message}", e)
                        }
                    }
                    is com.massapay.android.core.util.Result.Error -> {
                        // Si falla balance, mostrar 0 y continuar
                        android.util.Log.e("DashboardVM", "Failed to get balance", balRes.exception)
                        _uiState.update { it.copy(
                            balance = "0",
                            isLoading = false,
                            error = null // No mostrar error, solo log
                        ) }
                    }
                    is com.massapay.android.core.util.Result.Loading -> {
                        // no-op
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Error loading wallet data", e)
                _uiState.update { it.copy(
                    balance = "0",
                    isLoading = false,
                    error = null
                ) }
            }

            // Get recent transactions - si falla, continuar sin TXs
            try {
                when (val txRes = massaRepository.getTransactionHistory(address)) {
                    is com.massapay.android.core.util.Result.Success -> {
                        android.util.Log.d("DashboardVM", "Loaded ${txRes.data.size} transactions")
                        _uiState.update { it.copy(
                            recentTransactions = txRes.data.take(5),
                            isLoading = false
                        ) }
                    }
                    else -> { 
                        android.util.Log.d("DashboardVM", "No transactions or error")
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Failed to get transactions", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refreshData() {
        _uiState.update { it.copy(isLoading = true) }
        secureStorage.getActiveWallet()?.let { loadWalletData(it) }
    }

    fun toggleUsdDisplay() {
        _uiState.update { it.copy(showUsdValue = !it.showUsdValue) }
    }
}

data class DashboardState(
    val balance: String = "0",
    val usdValue: String = "0",
    val currentPrice: Double = 0.0,
    val priceChange24h: Double = 0.0,
    val massaStats: MassaStats? = null,
    val showUsdValue: Boolean = true,
    val recentTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val activeWallet: String? = null
)