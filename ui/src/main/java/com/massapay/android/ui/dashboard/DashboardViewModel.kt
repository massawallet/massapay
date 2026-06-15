package com.massapay.android.ui.dashboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.core.model.Token
import com.massapay.android.core.model.Transaction
import com.massapay.android.network.repository.MassaRepository
import com.massapay.android.price.repository.PriceRepository
import com.massapay.android.price.model.MassaStats
import com.massapay.android.security.storage.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// Token info for portfolio calculation
private data class TokenInfo(
    val symbol: String,
    val contractAddress: String,
    val decimals: Int,
    val defaultPrice: BigDecimal
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val massaRepository: MassaRepository,
    private val priceRepository: PriceRepository,
    private val secureStorage: SecureStorage,
    private val accountManager: com.massapay.android.security.wallet.AccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var pollingJob: Job? = null
    private var polledAddress: String? = null
    private var initialTransactionLoadComplete = false
    
    // All supported tokens for portfolio calculation
    private val supportedTokens = listOf(
        TokenInfo("WMAS", "AS12U4TZfNK7qoLyEERBBRDMu8nm5MKoRzPXDXans4v9wdATZedz9", 9, BigDecimal("0.003")),
        TokenInfo("USDC.e", "AS1hCJXjndR4c9vekLWsXGnrdigp4AaZ7uYG3UKFzzKnWVsrNLPJ", 6, BigDecimal("1.0")),
        TokenInfo("WETH.e", "AS124vf3YfAJCSCQVYKczzuWWpXrximFpbTmX4rheLs5uNSftiiRY", 18, BigDecimal("4000")),
        TokenInfo("DAI.e", "AS1ZGF1upwp9kPRvDKLxFAKRebgg7b3RWDnhgV7VvdZkZsUL7Nuv", 18, BigDecimal("1.0")),
        TokenInfo("DUSA", "AS12HT1JQUne9nkHevS9Q7HcsoAaYLXWPNgoWPuruV7Gw6Mb92ACL", 18, BigDecimal("0.01")),
        TokenInfo("PUR", "AS133eqPPaPttJ6hJnk3sfoG5cjFFqBDi1VGxdo2wzWkq8AfZnan", 9, BigDecimal("0.0001"))
    )

    init {
        viewModelScope.launch {
            // Observe active account changes from AccountManager
            accountManager.activeAccount.collect { account ->
                account?.let {
                    // store active wallet and account info in state
                    _uiState.update { state -> state.copy(
                        activeWallet = it.address,
                        activeAccountName = it.name,
                        activeAccountColor = it.color.hex,
                        accountCount = accountManager.accounts.value.size
                    ) }
                    if (polledAddress != it.address) {
                        initialTransactionLoadComplete = false
                    }
                    loadWalletData(it.address)
                    startWalletPolling(it.address)
                } ?: run {
                    // Fallback to secureStorage if AccountManager hasn't loaded yet
                    val address = secureStorage.getActiveWallet()
                    address?.let {
                        _uiState.update { state -> state.copy(activeWallet = it) }
                        if (polledAddress != it) {
                            initialTransactionLoadComplete = false
                        }
                        loadWalletData(it)
                        startWalletPolling(it)
                    }
                }
            }
        }
    }

    private fun startWalletPolling(address: String) {
        if (polledAddress == address && pollingJob?.isActive == true) return
        polledAddress = address
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                loadWalletData(address, silent = true)
            }
        }
    }

    private fun loadWalletData(address: String, silent: Boolean = false) {
        viewModelScope.launch {
            try {
                // Get MAS balance - si falla, mostrar 0
                when (val balRes = massaRepository.getAddressBalance(address)) {
                    is com.massapay.android.core.util.Result.Success -> {
                        val balance = balRes.data ?: "0"
                        val previousBalance = _uiState.value.balance.toBigDecimalOrNull()
                        val currentBalance = balance.toBigDecimalOrNull()
                        val incomingFromBalanceDelta = if (
                            initialTransactionLoadComplete &&
                            previousBalance != null &&
                            currentBalance != null &&
                            currentBalance > previousBalance
                        ) {
                            val amount = currentBalance.subtract(previousBalance)
                                .stripTrailingZeros()
                                .toPlainString()
                            massaRepository.recordIncomingTransaction(address, amount)
                        } else {
                            null
                        }

                        _uiState.update { it.copy(
                            balance = balance,
                            recentTransactions = incomingFromBalanceDelta
                                ?.let { incoming -> mergeRecentTransaction(incoming, it.recentTransactions) }
                                ?: it.recentTransactions,
                            isLoading = false
                        ) }
                        incomingFromBalanceDelta?.let { showIncomingTransactionNotification(it) }
                        
                        // Update account balance in AccountManager
                        accountManager.activeAccount.value?.let { account ->
                            if (account.address == address) {
                                accountManager.updateAccountBalance(account.id, balance)
                            }
                        }

                        // Get USD value and stats
                        try {
                            android.util.Log.d("DashboardVM", "Requesting Massa stats...")
                            priceRepository.getMassaStats()
                                .catch { e -> 
                                    android.util.Log.e("DashboardVM", "Flow error: ${e.message}")
                                    // Don't emit, just log and flow completes
                                }
                                .collect { result ->
                                    android.util.Log.d("DashboardVM", "Stats result: $result")
                                    when (result) {
                                        is com.massapay.android.core.util.Result.Success -> {
                                            val stats = result.data
                                            android.util.Log.d("DashboardVM", "Stats received: price=${stats.price}, rank=${stats.rank}")
                                            val masUsdValue = balance.toBigDecimal().multiply(stats.price.toBigDecimal())
                                            
                                            _uiState.update { state ->
                                                state.copy(
                                                    usdValue = masUsdValue.toString(),
                                                    currentPrice = stats.price,
                                                    priceChange24h = stats.percentChange24h,
                                                    massaStats = stats
                                                )
                                            }
                                            
                                            // Now calculate total portfolio value including other tokens
                                            calculateTotalPortfolioValue(address, masUsdValue, stats.price)
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
                            isLoading = if (silent) it.isLoading else false,
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
                    isLoading = if (silent) it.isLoading else false,
                    error = null
                ) }
            }

            // Get recent transactions - si falla, continuar sin TXs
            try {
                when (val txRes = massaRepository.getTransactionHistory(address)) {
                    is com.massapay.android.core.util.Result.Success -> {
                        android.util.Log.d("DashboardVM", "Loaded ${txRes.data.size} transactions")
                        val existingHashes = _uiState.value.recentTransactions.map { it.hash }.toSet()
                        val newIncoming = txRes.data.firstOrNull { transaction ->
                            initialTransactionLoadComplete &&
                                transaction.to == address &&
                                transaction.hash.startsWith("incoming_") &&
                                transaction.hash !in existingHashes
                        }
                        _uiState.update { it.copy(
                            recentTransactions = txRes.data.take(5),
                            isLoading = if (silent) it.isLoading else false
                        ) }
                        if (newIncoming != null) {
                            showIncomingTransactionNotification(newIncoming)
                        }
                        initialTransactionLoadComplete = true
                    }
                    else -> { 
                        android.util.Log.d("DashboardVM", "No transactions or error")
                        _uiState.update { it.copy(isLoading = if (silent) it.isLoading else false) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Failed to get transactions", e)
                _uiState.update { it.copy(isLoading = if (silent) it.isLoading else false) }
            }
        }
    }

    fun refreshData() {
        _uiState.update { it.copy(isLoading = true) }
        val address = accountManager.activeAccount.value?.address ?: secureStorage.getActiveWallet()
        address?.let { loadWalletData(it) }
    }

    private fun showIncomingTransactionNotification(transaction: Transaction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val channelId = "incoming_transactions"
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Incoming transactions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when your Massa wallet receives balance"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.let { launchIntent ->
                PendingIntent.getActivity(
                    appContext,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

        val notification = android.app.Notification.Builder(appContext, channelId)
            .setSmallIcon(com.massapay.android.ui.R.drawable.brand_logo)
            .setContentTitle("Balance received")
            .setContentText("You received ${transaction.amount} MAS")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(transaction.hash.hashCode(), notification)
    }

    private fun mergeRecentTransaction(
        transaction: Transaction,
        currentTransactions: List<Transaction>
    ): List<Transaction> {
        return (listOf(transaction) + currentTransactions)
            .distinctBy { it.hash }
            .sortedByDescending { it.timestamp }
            .take(5)
    }

    fun toggleUsdDisplay() {
        _uiState.update { it.copy(showUsdValue = !it.showUsdValue) }
    }
    
    fun toggleBalanceVisibility() {
        _uiState.update { it.copy(isBalanceHidden = !it.isBalanceHidden) }
    }
    
    /**
     * Calculate total portfolio value by fetching all token balances
     */
    private fun calculateTotalPortfolioValue(address: String, masUsdValue: BigDecimal, masPrice: Double) {
        viewModelScope.launch {
            try {
                var totalValue = masUsdValue
                
                // Fetch ETH price for WETH.e
                val ethPrice = fetchEthPrice() ?: BigDecimal("4000")
                
                // Calculate value of each token
                for (token in supportedTokens) {
                    val balance = getTokenBalance(address, token.contractAddress, token.decimals)
                    if (balance > BigDecimal.ZERO) {
                        val price = when (token.symbol) {
                            "WMAS" -> BigDecimal(masPrice.toString())
                            "USDC.e", "DAI.e" -> BigDecimal("1.0")
                            "WETH.e" -> ethPrice
                            else -> token.defaultPrice
                        }
                        val tokenValue = balance.multiply(price)
                        totalValue = totalValue.add(tokenValue)
                        android.util.Log.d("DashboardVM", "${token.symbol}: $balance * $price = $tokenValue")
                    }
                }
                
                android.util.Log.d("DashboardVM", "Total portfolio value: $totalValue")
                _uiState.update { it.copy(totalPortfolioValue = totalValue) }
                
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Error calculating portfolio value", e)
                // If calculation fails, just use MAS value
                _uiState.update { it.copy(totalPortfolioValue = masUsdValue) }
            }
        }
    }
    
    /**
     * Fetch ETH price from CoinPaprika
     */
    private suspend fun fetchEthPrice(): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.coinpaprika.com/v1/tickers/eth-ethereum")
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val json = JSONObject(body)
                    val quotes = json.optJSONObject("quotes")
                    val usd = quotes?.optJSONObject("USD")
                    val price = usd?.optDouble("price") ?: return@withContext null
                    return@withContext BigDecimal(price.toString())
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("DashboardVM", "Error fetching ETH price: ${e.message}")
            null
        }
    }
    
    /**
     * Get balance of an ERC20 token
     */
    private suspend fun getTokenBalance(
        userAddress: String,
        contractAddress: String,
        decimals: Int
    ): BigDecimal = withContext(Dispatchers.IO) {
        try {
            // Build parameter for balanceOf: string(address)
            val buffer = java.io.ByteArrayOutputStream()
            val addrBytes = userAddress.toByteArray(Charsets.UTF_8)
            val lenBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(addrBytes.size)
                .array()
            buffer.write(lenBytes)
            buffer.write(addrBytes)
            
            val bytes = buffer.toByteArray()
            val parameter = JSONArray().apply {
                bytes.forEach { put(it.toInt() and 0xFF) }
            }
            
            val requestBody = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "execute_read_only_call")
                put("params", JSONArray().apply {
                    put(JSONArray().apply {
                        put(JSONObject().apply {
                            put("target_address", contractAddress)
                            put("target_function", "balanceOf")
                            put("parameter", parameter)
                            put("max_gas", 100000000)
                            put("caller_address", JSONObject.NULL)
                            put("coins", JSONObject.NULL)
                            put("fee", JSONObject.NULL)
                        })
                    })
                })
            }.toString()
            
            val request = Request.Builder()
                .url("https://mainnet.massa.net/api/v2")
                .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful && responseBody.contains("\"result\"")) {
                    val json = JSONObject(responseBody)
                    
                    if (json.has("result")) {
                        val resultArray = json.optJSONArray("result")
                        if (resultArray != null && resultArray.length() > 0) {
                            val firstResult = resultArray.getJSONObject(0)
                            val resultObj = firstResult.optJSONObject("result")
                            
                            if (resultObj != null && resultObj.has("Ok")) {
                                val okArray = resultObj.optJSONArray("Ok")
                                if (okArray != null && okArray.length() > 0) {
                                    // Parse u256 balance (little-endian)
                                    val balanceBytes = ByteArray(minOf(okArray.length(), 32))
                                    for (i in balanceBytes.indices) {
                                        balanceBytes[i] = (okArray.getInt(i) and 0xFF).toByte()
                                    }
                                    
                                    // Convert from little-endian to BigInteger
                                    val reversed = balanceBytes.reversedArray()
                                    val rawBalance = BigInteger(1, reversed)
                                    
                                    // Convert to decimal
                                    return@withContext BigDecimal(rawBalance)
                                        .divide(BigDecimal.TEN.pow(decimals), decimals, RoundingMode.DOWN)
                                }
                            }
                        }
                    }
                }
            }
            BigDecimal.ZERO
        } catch (e: Exception) {
            android.util.Log.e("DashboardVM", "Error getting token balance for $contractAddress: ${e.message}")
            BigDecimal.ZERO
        }
    }
}

data class DashboardState(
    val balance: String = "0",
    val usdValue: String = "0",
    val totalPortfolioValue: BigDecimal = BigDecimal.ZERO, // Total USD value of all tokens
    val currentPrice: Double = 0.0,
    val priceChange24h: Double = 0.0,
    val massaStats: MassaStats? = null,
    val showUsdValue: Boolean = true,
    val isBalanceHidden: Boolean = false,
    val recentTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val activeWallet: String? = null,
    val activeAccountName: String = "Main Account",
    val activeAccountColor: String = "#2196F3",
    val accountCount: Int = 1
)
