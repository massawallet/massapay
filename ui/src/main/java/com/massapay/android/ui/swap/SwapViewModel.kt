package com.massapay.android.ui.swap

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.core.util.Result
import com.massapay.android.network.repository.MassaRepository
import com.massapay.android.security.storage.SecureStorage
import com.massapay.android.security.wallet.WalletManager
import com.massapay.android.security.wallet.AccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

// Tokens available on DUSA Exchange
data class SwapToken(
    val symbol: String,
    val name: String,
    val contractAddress: String,
    val decimals: Int = 9,
    val color: Color
) {
    companion object {
        val MAS = SwapToken(
            symbol = "MAS",
            name = "Massa",
            contractAddress = "", // Native token
            decimals = 9,
            color = Color(0xFF1A1A2E)
        )
        
        val WMAS = SwapToken(
            symbol = "WMAS",
            name = "Wrapped Massa",
            contractAddress = "AS12U4TZfNK7qoLyEERBBRDMu8nm5MKoRzPXDXans4v9wdATZedz9",
            decimals = 9,
            color = Color(0xFF4A90D9)
        )
        
        val USDC = SwapToken(
            symbol = "USDC.e",
            name = "USD Coin (Bridged)",
            contractAddress = "AS1hCJXjndR4c9vekLWsXGnrdigp4AaZ7uYG3UKFzzKnWVsrNLPJ",
            decimals = 6,
            color = Color(0xFF2775CA)
        )
        
        val WETH = SwapToken(
            symbol = "WETH.e",
            name = "Wrapped Ether (Bridged)",
            contractAddress = "AS124vf3YfAJCSCQVYKczzuWWpXrximFpbTmX4rheLs5uNSftiiRY",
            decimals = 18,
            color = Color(0xFF627EEA)
        )
        
        val DAI = SwapToken(
            symbol = "DAI.e",
            name = "Dai Stablecoin (Bridged)",
            contractAddress = "AS1ZGF1upwp9kPRvDKLxFAKRebgg7b3RWDnhgV7VvdZkZsUL7Nuv",
            decimals = 18,
            color = Color(0xFFF5AC37)
        )
        
        val DUSA = SwapToken(
            symbol = "DUSA",
            name = "Dusa Token",
            contractAddress = "AS12HT1JQUne9nkHevS9Q7HcsoAaYLXWPNgoWPuruV7Gw6Mb92ACL", // Official DUSA mainnet address
            decimals = 18,
            color = Color(0xFF00BFA5)
        )
        
        val PUR = SwapToken(
            symbol = "PUR",
            name = "Purrfect Universe",
            contractAddress = "AS133eqPPaPttJ6hJnk3sfoG5cjFFqBDi1VGxdo2wzWkq8AfZnan",
            decimals = 9,
            color = Color(0xFF9C27B0)
        )
        
        val MC = SwapToken(
            symbol = "MC",
            name = "MassaConnect",
            contractAddress = "AS12XfRvGn4A8QZBmLSHsD2EBqpBVEsdF1CcbvJRTpqtXMYGhH6FH",
            decimals = 18,
            color = Color(0xFF2196F3) // Blue color for MassaConnect
        )
        
        // All available tokens for swapping
        val ALL = listOf(MAS, WMAS, USDC, WETH, DAI, DUSA, PUR, MC)
    }
}

// Type of swap operation
enum class SwapOperationType {
    SWAP,       // Normal swap through DEX
    WRAP,       // MAS -> WMAS
    UNWRAP      // WMAS -> MAS
}

data class SwapState(
    val fromToken: SwapToken = SwapToken.MAS,
    val toToken: SwapToken = SwapToken.USDC,
    val fromAmount: String = "",
    val toAmount: String = "",
    val operationType: SwapOperationType = SwapOperationType.SWAP,
    val fromBalance: String = "0",        // Display formatted balance
    val toBalance: String = "0",          // Display formatted balance
    val fromBalanceRaw: BigDecimal = BigDecimal.ZERO,  // Raw balance for calculations
    val toBalanceRaw: BigDecimal = BigDecimal.ZERO,    // Raw balance for calculations
    val exchangeRate: String = "0",
    val priceImpact: Float = 0f,
    val slippage: Float = 3.0f,  // Default 3% slippage for DUSA DEX (low liquidity)
    val showSettings: Boolean = false,
    val isLoading: Boolean = false,
    val isCalculatingQuote: Boolean = false,  // Loading state for quote calculation
    val isConfirmingTx: Boolean = false,      // Waiting for blockchain confirmation
    val canSwap: Boolean = false,
    val error: String? = null,
    val swapSuccess: Boolean = false,
    val txHash: String? = null,
    val availableTokens: List<SwapToken> = SwapToken.ALL,
    val swapStatus: String = "",
    // Confirmation dialog state
    val showConfirmation: Boolean = false,
    val confirmationData: SwapConfirmationData? = null
)

data class SwapConfirmationData(
    val fromToken: SwapToken,
    val toToken: SwapToken,
    val fromAmount: String,
    val toAmount: String,
    val minReceived: String,
    val exchangeRate: String,
    val priceImpact: Float,
    val slippage: Float,
    val estimatedFee: String = "0.01 MAS"
)

// Exception for when no swap route is available
class NoRouteException(message: String) : Exception(message)

// DUSA Router contract
private const val DUSA_ROUTER = "AS1gUwVGA3A5Dnmev8c2BjBR2wC8y9hb7CFZXVzLb1iwASFHUZ1p"
// DUSA Quoter V2 contract for getting accurate quotes
private const val DUSA_QUOTER = "AS1d3DvZeqTo3Uq7mfAAUmNggjFXqEfGGpSUv6uTYvikVVW8EybN"
private const val DEFAULT_FEE = "10000000" // 0.01 MAS in nanoMAS
private const val DEFAULT_MAX_GAS = "100000000" // 100M gas

@HiltViewModel
class SwapViewModel @Inject constructor(
    private val massaRepository: MassaRepository,
    private val secureStorage: SecureStorage,
    private val walletManager: WalletManager,
    private val accountManager: AccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SwapState())
    val uiState: StateFlow<SwapState> = _uiState

    private var userAddress: String? = null
    
    // Cached quote with route info for swap execution
    private var cachedQuote: SwapQuote? = null

    init {
        observeActiveAccount()
    }

    private fun observeActiveAccount() {
        viewModelScope.launch {
            accountManager.activeAccount.collectLatest { account ->
                val address = account?.address ?: secureStorage.getActiveWallet()
                if (address.isNullOrBlank() || address == userAddress) {
                    return@collectLatest
                }

                userAddress = address
                cachedQuote = null
                _uiState.update {
                    it.copy(
                        fromBalance = "0",
                        toBalance = "0",
                        fromBalanceRaw = BigDecimal.ZERO,
                        toBalanceRaw = BigDecimal.ZERO,
                        canSwap = false,
                        error = null,
                        showConfirmation = false,
                        confirmationData = null,
                        swapSuccess = false,
                        txHash = null,
                        swapStatus = ""
                    )
                }
                loadBalances()
                validateSwap()
            }
        }
    }

    private suspend fun loadBalances() {
        userAddress?.let { address ->
            // Load MAS balance
            when (val result = massaRepository.getAddressBalance(address)) {
                is Result.Success -> {
                    val masBalanceStr = result.data ?: "0"
                    val masBalance = masBalanceStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    
                    if (_uiState.value.fromToken == SwapToken.MAS) {
                        _uiState.update { it.copy(
                            fromBalance = formatBalanceDisplay(masBalance),
                            fromBalanceRaw = masBalance
                        ) }
                    }
                    if (_uiState.value.toToken == SwapToken.MAS) {
                        _uiState.update { it.copy(
                            toBalance = formatBalanceDisplay(masBalance),
                            toBalanceRaw = masBalance
                        ) }
                    }
                }
                else -> {}
            }
            
            // Load token balances for selected tokens
            loadTokenBalance(_uiState.value.fromToken, true)
            loadTokenBalance(_uiState.value.toToken, false)
        }
    }

    private suspend fun loadTokenBalance(token: SwapToken, isFrom: Boolean) {
        if (token.contractAddress.isEmpty()) return // Native MAS already loaded
        
        userAddress?.let { address ->
            try {
                val balanceRaw = getTokenBalance(address, token.contractAddress, token.decimals)
                val balanceDisplay = formatBalanceDisplay(balanceRaw)
                if (isFrom) {
                    _uiState.update { it.copy(
                        fromBalance = balanceDisplay,
                        fromBalanceRaw = balanceRaw
                    ) }
                } else {
                    _uiState.update { it.copy(
                        toBalance = balanceDisplay,
                        toBalanceRaw = balanceRaw
                    ) }
                }
            } catch (e: Exception) {
                android.util.Log.e("SwapVM", "Failed to load balance for ${token.symbol}", e)
            }
        }
    }

    private suspend fun getTokenBalance(address: String, contractAddress: String, decimals: Int): BigDecimal = withContext(Dispatchers.IO) {
        try {
            // Build parameter for balanceOf: u32(strLen) + address string bytes
            val addrBytes = address.toByteArray(Charsets.UTF_8)
            val parameter = JSONArray().apply {
                // String length as u32 (little-endian)
                put(addrBytes.size and 0xFF)
                put((addrBytes.size shr 8) and 0xFF)
                put((addrBytes.size shr 16) and 0xFF)
                put((addrBytes.size shr 24) and 0xFF)
                // String bytes
                addrBytes.forEach { put(it.toInt() and 0xFF) }
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
                android.util.Log.d("SwapVM", "Token balance response: $responseBody")
                
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
                                    // Decode u256 balance
                                    val balanceUnits = decodeU256FromJsonArray(okArray)
                                    
                                    // Convert to human readable
                                    val balance = balanceUnits.toBigDecimal()
                                        .divide(BigDecimal.TEN.pow(decimals), 18, RoundingMode.HALF_UP)
                                    
                                    android.util.Log.d("SwapVM", "Token balance: $balance (raw: $balanceUnits)")
                                    return@withContext balance
                                }
                            }
                        }
                    }
                }
            }
            BigDecimal.ZERO
        } catch (e: Exception) {
            android.util.Log.e("SwapVM", "Error getting token balance", e)
            BigDecimal.ZERO
        }
    }

    // Format balance for display (e.g., "1,234.56" or "1.23M")
    private fun formatBalanceDisplay(balance: BigDecimal): String {
        return try {
            when {
                balance >= BigDecimal("1000000") -> {
                    String.format("%.2fM", balance.toDouble() / 1_000_000)
                }
                balance >= BigDecimal("10000") -> {
                    String.format("%.2fK", balance.toDouble() / 1_000)
                }
                balance >= BigDecimal("1000") -> {
                    String.format("%,.2f", balance.toDouble())
                }
                else -> {
                    String.format("%.4f", balance.toDouble())
                }
            }
        } catch (e: Exception) {
            "0"
        }
    }

    fun updateFromAmount(amount: String) {
        // Filter to only allow valid decimal input
        val filtered = amount.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } > 1) return
        
        _uiState.update { state ->
            state.copy(
                fromAmount = filtered,
                error = null
            )
        }
        
        // Calculate output amount based on exchange rate
        calculateOutputAmount(filtered)
        validateSwap()
    }

    private var quoteJob: kotlinx.coroutines.Job? = null

    private fun calculateOutputAmount(inputAmount: String) {
        // Cancel previous quote job if any
        quoteJob?.cancel()
        
        if (inputAmount.isEmpty() || inputAmount == ".") {
            _uiState.update { it.copy(toAmount = "", exchangeRate = "0", priceImpact = 0f, isCalculatingQuote = false) }
            return
        }

        quoteJob = viewModelScope.launch {
            try {
                val input = inputAmount.toBigDecimalOrNull() ?: return@launch
                
                // Debounce - wait a bit before fetching quote to avoid too many requests
                delay(300)
                
                // Show loading state
                _uiState.update { it.copy(isCalculatingQuote = true, error = null) }
                
                // Get quote from DUSA
                val quote = getSwapQuote(
                    fromToken = _uiState.value.fromToken,
                    toToken = _uiState.value.toToken,
                    amountIn = input
                )
                
                // Format output amount with appropriate decimals (show more precision)
                val toToken = _uiState.value.toToken
                val displayDecimals = minOf(toToken.decimals, 6) // Show up to 6 decimals
                // Use HALF_DOWN rounding for more conservative estimate shown to user
                val formattedAmount = formatAmountWithMinDecimals(quote.amountOut, displayDecimals, 2)
                val formattedRate = formatAmountWithMinDecimals(quote.rate, 8, 2)
                
                android.util.Log.d("SwapVM", "Setting toAmount to: $formattedAmount, rate: $formattedRate")
                
                _uiState.update { state ->
                    state.copy(
                        toAmount = formattedAmount,
                        exchangeRate = formattedRate,
                        priceImpact = quote.priceImpact,
                        isCalculatingQuote = false,
                        error = null
                    )
                }
                validateSwap()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Don't treat cancellation as an error - just rethrow
                // This happens when user types fast and a new quote request is made
                android.util.Log.d("SwapVM", "Quote job cancelled (user typing)")
                throw e
            } catch (e: NoRouteException) {
                // No liquidity pool available for this pair
                android.util.Log.w("SwapVM", "No route: ${e.message}")
                _uiState.update { it.copy(
                    toAmount = "",
                    error = e.message ?: "No swap route available",
                    isCalculatingQuote = false,
                    canSwap = false
                ) }
            } catch (e: Exception) {
                android.util.Log.e("SwapVM", "Failed to get quote", e)
                _uiState.update { it.copy(
                    toAmount = "", 
                    error = "Unable to get quote: ${e.message}", 
                    isCalculatingQuote = false
                ) }
            }
        }
    }

    /**
     * Format amount with appropriate decimal places
     * Shows at least minDecimals, removes trailing zeros but keeps precision
     */
    private fun formatAmount(amount: BigDecimal, maxDecimals: Int): String {
        // Set scale to max decimals first
        val scaled = amount.setScale(maxDecimals, RoundingMode.HALF_UP)
        
        // Convert to string and remove unnecessary trailing zeros
        // But keep at least 2 decimal places for readability
        var result = scaled.toPlainString()
        
        if (result.contains(".")) {
            // Remove trailing zeros but keep at least 2 decimals after the point
            val parts = result.split(".")
            val intPart = parts[0]
            var decPart = parts[1].trimEnd('0')
            
            // Ensure at least 2 decimal places for small values
            while (decPart.length < 2) {
                decPart += "0"
            }
            
            result = "$intPart.$decPart"
        }
        
        return result
    }
    
    /**
     * Format amount with max decimals but always show at least minDecimals
     * Example: formatAmountWithMinDecimals(1.1, 6, 2) -> "1.10"
     * Example: formatAmountWithMinDecimals(1.123456, 6, 2) -> "1.123456"
     */
    private fun formatAmountWithMinDecimals(amount: BigDecimal, maxDecimals: Int, minDecimals: Int): String {
        // Round to max decimals
        val scaled = amount.setScale(maxDecimals, RoundingMode.HALF_DOWN)
        var result = scaled.toPlainString()
        
        if (result.contains(".")) {
            val parts = result.split(".")
            val intPart = parts[0]
            var decPart = parts[1].trimEnd('0')
            
            // Ensure at least minDecimals decimal places
            while (decPart.length < minDecimals) {
                decPart += "0"
            }
            
            result = "$intPart.$decPart"
        } else {
            // Add decimal places if none exist
            result = "$result.${"0".repeat(minDecimals)}"
        }
        
        return result
    }

    private data class SwapQuote(
        val amountOut: BigDecimal,
        val rate: BigDecimal,
        val priceImpact: Float,
        val binSteps: List<Long> = emptyList(),
        val isLegacy: List<Boolean> = emptyList(),
        val route: List<String> = emptyList()
    )

    // HTTP client for read_only_call
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun getSwapQuote(
        fromToken: SwapToken,
        toToken: SwapToken,
        amountIn: BigDecimal
    ): SwapQuote = withContext(Dispatchers.IO) {
        // Handle WRAP/UNWRAP specially - 1:1 ratio, no DEX needed
        if ((fromToken == SwapToken.MAS && toToken == SwapToken.WMAS) ||
            (fromToken == SwapToken.WMAS && toToken == SwapToken.MAS)) {
            android.util.Log.d("SwapVM", "WRAP/UNWRAP detected - using 1:1 ratio")
            return@withContext SwapQuote(
                amountOut = amountIn,
                rate = BigDecimal.ONE,
                priceImpact = 0f,
                binSteps = emptyList(),
                isLegacy = emptyList(),
                route = emptyList()
            )
        }
        
        try {
            // Try to get real quote from DUSA router
            val realQuote = getQuoteFromDusa(fromToken, toToken, amountIn)
            if (realQuote != null) {
                // Check if the quote returned a valid amount
                if (realQuote.amountOut <= BigDecimal.ZERO) {
                    android.util.Log.w("SwapVM", "Quote returned zero amount - checking if route exists")
                    // Don't fail immediately - maybe the quote parsing failed but route exists
                    // Let user try with different amounts
                    throw NoRouteException("No liquidity available for this amount. Try a smaller amount.")
                }
                return@withContext realQuote
            } else {
                android.util.Log.w("SwapVM", "Quote returned null - no route found")
                throw NoRouteException("No swap route found for ${fromToken.symbol} → ${toToken.symbol}")
            }
        } catch (e: NoRouteException) {
            // Re-throw NoRouteException to show proper error to user
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SwapVM", "Failed to get DUSA quote: ${e.message}", e)
            // Generic error - still allow user to see what happened
            throw NoRouteException("Failed to get quote: ${e.message}")
        }
    }

    /**
     * Get real quote from DUSA Quoter using findBestPathFromAmountIn function
     * Tries multiple routing strategies if the first one fails
     */
    private suspend fun getQuoteFromDusa(
        fromToken: SwapToken,
        toToken: SwapToken,
        amountIn: BigDecimal
    ): SwapQuote? = withContext(Dispatchers.IO) {
        // Generate multiple possible paths to try
        val pathsToTry = buildAllPossiblePaths(fromToken, toToken)
        
        for (path in pathsToTry) {
            try {
                val quote = tryGetQuoteForPath(path, fromToken, toToken, amountIn)
                if (quote != null) {
                    android.util.Log.d("SwapVM", "Found working path: ${path.joinToString(" -> ")}")
                    return@withContext quote
                }
            } catch (e: Exception) {
                android.util.Log.d("SwapVM", "Path ${path.joinToString(" -> ")} failed: ${e.message}")
                // Continue to next path
            }
        }
        
        android.util.Log.e("SwapVM", "No working path found for ${fromToken.symbol} -> ${toToken.symbol}")
        null
    }
    
    /**
     * Build all possible routing paths between two tokens
     */
    private fun buildAllPossiblePaths(fromToken: SwapToken, toToken: SwapToken): List<List<String>> {
        val fromAddress = if (fromToken == SwapToken.MAS) SwapToken.WMAS.contractAddress else fromToken.contractAddress
        val toAddress = if (toToken == SwapToken.MAS) SwapToken.WMAS.contractAddress else toToken.contractAddress
        
        if (fromAddress == toAddress) {
            return listOf(listOf(fromAddress))
        }
        
        val paths = mutableListOf<List<String>>()
        
        // Path 1: Direct route
        paths.add(listOf(fromAddress, toAddress))
        
        // Path 2: Through WMAS (most liquidity)
        if (fromAddress != SwapToken.WMAS.contractAddress && toAddress != SwapToken.WMAS.contractAddress) {
            paths.add(listOf(fromAddress, SwapToken.WMAS.contractAddress, toAddress))
        }
        
        // Path 3: Through USDC.e (stablecoin hub)
        if (fromAddress != SwapToken.USDC.contractAddress && toAddress != SwapToken.USDC.contractAddress) {
            paths.add(listOf(fromAddress, SwapToken.USDC.contractAddress, toAddress))
        }
        
        // Path 4: Through WMAS then USDC.e (for tokens that only connect to one)
        if (fromAddress != SwapToken.WMAS.contractAddress && 
            toAddress != SwapToken.USDC.contractAddress &&
            fromAddress != SwapToken.USDC.contractAddress) {
            paths.add(listOf(fromAddress, SwapToken.WMAS.contractAddress, SwapToken.USDC.contractAddress, toAddress))
        }
        
        // Path 5: Through USDC.e then WMAS
        if (fromAddress != SwapToken.USDC.contractAddress && 
            toAddress != SwapToken.WMAS.contractAddress &&
            toAddress != SwapToken.USDC.contractAddress) {
            paths.add(listOf(fromAddress, SwapToken.USDC.contractAddress, SwapToken.WMAS.contractAddress, toAddress))
        }
        
        return paths
    }
    
    /**
     * Try to get a quote for a specific path
     */
    private suspend fun tryGetQuoteForPath(
        path: List<String>,
        fromToken: SwapToken,
        toToken: SwapToken,
        amountIn: BigDecimal
    ): SwapQuote? {
        // Convert amount to token units (with proper decimals)
        val amountInUnits = amountIn.multiply(BigDecimal.TEN.pow(fromToken.decimals))
            .setScale(0, RoundingMode.DOWN)
            .toBigInteger()
        
        // Build parameter for findBestPathFromAmountIn: 
        // array<string>(route) + u256(amountIn) + bool(checkLegacy)
        val parameter = buildQuoterParameter(path, amountInUnits)
        
        android.util.Log.d("SwapVM", "Trying path: ${path.joinToString(" -> ")}, amount: $amountInUnits")
        
        // Call DUSA Quoter findBestPathFromAmountIn
        val requestBody = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "execute_read_only_call")
            put("params", JSONArray().apply {
                put(JSONArray().apply {
                    put(JSONObject().apply {
                        put("target_address", DUSA_QUOTER)
                        put("target_function", "findBestPathFromAmountIn")
                        put("parameter", parameter)
                        put("max_gas", 500000000) // 500M gas for complex quote
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
                                // Parse Quote response
                                val quoteData = parseQuoteResponse(okArray, toToken.decimals)
                                
                                if (quoteData != null && quoteData.amountOut > BigDecimal.ZERO) {
                                    val rate = if (amountIn > BigDecimal.ZERO) {
                                        quoteData.amountOut.divide(amountIn, 18, RoundingMode.HALF_UP)
                                    } else {
                                        BigDecimal.ZERO
                                    }
                                    
                                    android.util.Log.d("SwapVM", "Quote success: $amountIn ${fromToken.symbol} = ${quoteData.amountOut} ${toToken.symbol}")
                                    
                                    val swapQuote = SwapQuote(
                                        amountOut = quoteData.amountOut, 
                                        rate = rate, 
                                        priceImpact = quoteData.priceImpact,
                                        binSteps = quoteData.binSteps,
                                        isLegacy = quoteData.isLegacy,
                                        route = quoteData.route
                                    )
                                    
                                    // Cache the quote for swap execution
                                    cachedQuote = swapQuote
                                    
                                    return swapQuote
                                }
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Build optimal swap path between two tokens (kept for backward compatibility)
     */
    private fun buildSwapPath(fromToken: SwapToken, toToken: SwapToken): List<String> {
        val paths = buildAllPossiblePaths(fromToken, toToken)
        return paths.firstOrNull() ?: listOf(
            if (fromToken == SwapToken.MAS) SwapToken.WMAS.contractAddress else fromToken.contractAddress,
            if (toToken == SwapToken.MAS) SwapToken.WMAS.contractAddress else toToken.contractAddress
        )
    }

    private data class QuoteData(
        val amountOut: BigDecimal,
        val priceImpact: Float,
        val binSteps: List<Long>,
        val isLegacy: List<Boolean>,
        val route: List<String>
    )

    /**
     * Parse the Quote response from DUSA Quoter
     * Format based on SDK: route(string[]) + pairs(string[]) + binSteps(u64[]) + amounts(u256[]) + 
     *                      virtualAmountsWithoutSlippage(u256[]) + fees(u256[]) + isLegacy(bool[])
     * 
     * Each array is prefixed with u32 byte length of the serialized content (not element count)
     */
    private fun parseQuoteResponse(okArray: JSONArray, outputDecimals: Int): QuoteData? {
        try {
            val bytes = ByteArray(okArray.length())
            for (i in 0 until okArray.length()) {
                bytes[i] = (okArray.getInt(i) and 0xFF).toByte()
            }
            
            android.util.Log.d("SwapVM", "Quote bytes length: ${bytes.size}")
            
            var offset = 0
            
            // 1. Read route array (string[]) - byte length prefix, then parse actual strings
            val routeBytesLen = readU32LittleEndian(bytes, offset)
            offset += 4
            android.util.Log.d("SwapVM", "Route bytes length: $routeBytesLen, offset: $offset")
            
            // Parse route strings
            val route = mutableListOf<String>()
            val routeEndOffset = offset + routeBytesLen
            while (offset < routeEndOffset) {
                val strLen = readU32LittleEndian(bytes, offset)
                offset += 4
                if (strLen > 0 && offset + strLen <= routeEndOffset) {
                    val str = String(bytes, offset, strLen, Charsets.UTF_8)
                    route.add(str)
                    offset += strLen
                } else {
                    break
                }
            }
            offset = routeEndOffset // ensure we're at the right position
            
            // 2. Read pairs array (string[]) - byte length prefix (skip)
            val pairsBytesLen = readU32LittleEndian(bytes, offset)
            offset += 4
            android.util.Log.d("SwapVM", "Pairs bytes length: $pairsBytesLen, offset: $offset")
            offset += pairsBytesLen // skip pairs content
            
            // 3. Read binSteps array (u64[]) - byte length prefix
            val binStepsBytesLen = readU32LittleEndian(bytes, offset)
            offset += 4
            android.util.Log.d("SwapVM", "BinSteps bytes length: $binStepsBytesLen, offset: $offset")
            
            // Parse binSteps - each u64 is 8 bytes
            val binSteps = mutableListOf<Long>()
            val binStepsCount = binStepsBytesLen / 8
            for (i in 0 until binStepsCount) {
                val binStep = readU64LittleEndian(bytes, offset)
                binSteps.add(binStep)
                android.util.Log.d("SwapVM", "BinStep[$i]: $binStep")
                offset += 8
            }
            
            // 4. Read amounts array (u256[]) - byte length prefix
            val amountsBytesLen = readU32LittleEndian(bytes, offset)
            offset += 4
            android.util.Log.d("SwapVM", "Amounts bytes length: $amountsBytesLen, offset: $offset")
            
            // Parse amounts - each u256 is 32 bytes
            val amountsCount = amountsBytesLen / 32
            var lastAmount = BigInteger.ZERO
            for (i in 0 until amountsCount) {
                lastAmount = readU256LittleEndian(bytes, offset)
                android.util.Log.d("SwapVM", "Amount[$i]: $lastAmount")
                offset += 32
            }
            
            // Convert to human readable
            val amountOut = lastAmount.toBigDecimal()
                .divide(BigDecimal.TEN.pow(outputDecimals), 18, RoundingMode.HALF_UP)
            
            android.util.Log.d("SwapVM", "Final amountOut: $amountOut (outputDecimals: $outputDecimals)")
            
            // 5. Read virtualAmountsWithoutSlippage (u256[]) - for price impact calculation
            val virtualAmountsBytesLen = readU32LittleEndian(bytes, offset)
            offset += 4
            val virtualAmountsCount = virtualAmountsBytesLen / 32
            var lastVirtualAmount = BigInteger.ZERO
            for (i in 0 until virtualAmountsCount) {
                lastVirtualAmount = readU256LittleEndian(bytes, offset)
                offset += 32
            }
            
            // 6. Read fees array (u256[]) - skip
            val feesBytesLen = readU32LittleEndian(bytes, offset)
            offset += 4
            offset += feesBytesLen
            
            // 7. Read isLegacy array (bool[]) - byte length prefix
            val isLegacy = mutableListOf<Boolean>()
            if (offset < bytes.size) {
                val isLegacyBytesLen = readU32LittleEndian(bytes, offset)
                offset += 4
                for (i in 0 until isLegacyBytesLen) {
                    if (offset < bytes.size) {
                        isLegacy.add(bytes[offset].toInt() != 0)
                        offset++
                    }
                }
            }
            android.util.Log.d("SwapVM", "isLegacy: $isLegacy")
            
            // Calculate price impact
            val priceImpact = if (lastVirtualAmount > BigInteger.ZERO && lastAmount > BigInteger.ZERO) {
                val virtualOut = lastVirtualAmount.toBigDecimal()
                val actualOut = lastAmount.toBigDecimal()
                val impact = (virtualOut - actualOut).divide(virtualOut, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("100")).toFloat()
                maxOf(0f, impact)
            } else {
                0.1f
            }
            
            android.util.Log.d("SwapVM", "Parsed route: $route, binSteps: $binSteps, isLegacy: $isLegacy")
            
            return QuoteData(amountOut, priceImpact, binSteps, isLegacy, route)
        } catch (e: Exception) {
            android.util.Log.e("SwapVM", "Error parsing quote response", e)
            return null
        }
    }

    private fun readU32LittleEndian(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
    
    private fun readU64LittleEndian(bytes: ByteArray, offset: Int): Long {
        if (offset + 8 > bytes.size) return 0L
        return (bytes[offset].toLong() and 0xFF) or
               ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
               ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
               ((bytes[offset + 3].toLong() and 0xFF) shl 24) or
               ((bytes[offset + 4].toLong() and 0xFF) shl 32) or
               ((bytes[offset + 5].toLong() and 0xFF) shl 40) or
               ((bytes[offset + 6].toLong() and 0xFF) shl 48) or
               ((bytes[offset + 7].toLong() and 0xFF) shl 56)
    }

    private fun readU256LittleEndian(bytes: ByteArray, offset: Int): BigInteger {
        if (offset + 32 > bytes.size) return BigInteger.ZERO
        val u256Bytes = ByteArray(32)
        for (i in 0 until 32) {
            u256Bytes[i] = bytes[offset + i]
        }
        // Convert from little-endian to big-endian for BigInteger
        return BigInteger(1, u256Bytes.reversedArray())
    }

    /**
     * Build parameter bytes for findBestPathFromAmountIn function
     * Format: u32(routeLength) + [u32(strLen) + string bytes]... + u256(amountIn) + bool(checkLegacy)
     */
    private fun buildQuoterParameter(route: List<String>, amountIn: BigInteger): JSONArray {
        val buffer = java.io.ByteArrayOutputStream()
        
        // Serialize route array content first (to calculate length)
        val routeContentBuffer = java.io.ByteArrayOutputStream()
        for (addr in route) {
            val addrBytes = addr.toByteArray(Charsets.UTF_8)
            // String length as u32 (4 bytes, little-endian)
            val strLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(addrBytes.size).array()
            routeContentBuffer.write(strLenBytes)
            routeContentBuffer.write(addrBytes)
        }
        val routeContent = routeContentBuffer.toByteArray()
        
        // Write array: first the byte length of the content (u32), then the content
        val routeContentLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(routeContent.size).array()
        buffer.write(routeContentLenBytes)
        buffer.write(routeContent)
        
        // amountIn as u256 (32 bytes, little-endian)
        val amountBytes = amountIn.toByteArray()
        val u256 = ByteArray(32)
        // Handle negative sign byte if present
        val srcOffset = if (amountBytes.isNotEmpty() && amountBytes[0] == 0.toByte()) 1 else 0
        val srcLength = amountBytes.size - srcOffset
        // Copy to little-endian format
        for (i in 0 until minOf(srcLength, 32)) {
            u256[i] = amountBytes[amountBytes.size - 1 - i]
        }
        buffer.write(u256)
        
        // checkLegacy as bool (1 byte) - true to include legacy pairs
        buffer.write(1) // true
        
        // Convert to JSON array of bytes
        val bytes = buffer.toByteArray()
        return JSONArray().apply {
            bytes.forEach { put(it.toInt() and 0xFF) }
        }
    }

    /**
     * Decode u256 value from JSON array of bytes
     */
    private fun decodeU256FromJsonArray(jsonArray: JSONArray): BigInteger {
        if (jsonArray.length() < 32) {
            // Try to decode as u64 if smaller
            return decodeU64FromJsonArray(jsonArray)
        }
        
        // Read first 32 bytes as u256 little-endian
        val bytes = ByteArray(32)
        for (i in 0 until minOf(32, jsonArray.length())) {
            bytes[i] = (jsonArray.getInt(i) and 0xFF).toByte()
        }
        
        // Convert from little-endian to BigInteger
        val reversed = bytes.reversedArray()
        return BigInteger(1, reversed)
    }

    /**
     * Decode u64 value from JSON array of bytes
     */
    private fun decodeU64FromJsonArray(jsonArray: JSONArray): BigInteger {
        if (jsonArray.length() < 8) {
            return BigInteger.ZERO
        }
        
        var value = 0L
        for (i in 0 until 8) {
            value = value or ((jsonArray.getInt(i).toLong() and 0xFF) shl (i * 8))
        }
        
        return BigInteger.valueOf(value)
    }

    /**
     * Check if a token pair can be swapped on DUSA (directly or through routing)
     * All main tokens can be swapped - we have routing logic to handle any pair
     */
    private fun hasLikelyLiquidityPool(fromToken: SwapToken, toToken: SwapToken): Boolean {
        // All supported tokens can be swapped through our routing logic
        val supportedTokens = setOf(
            SwapToken.MAS.symbol,
            SwapToken.WMAS.symbol,
            SwapToken.USDC.symbol,
            SwapToken.WETH.symbol,
            SwapToken.DUSA.symbol,
            SwapToken.DAI.symbol,
            SwapToken.PUR.symbol
        )
        
        // Check if both tokens are supported
        val fromSupported = fromToken.symbol in supportedTokens
        val toSupported = toToken.symbol in supportedTokens
        
        // All supported token pairs can be swapped (through direct or multi-hop routes)
        return fromSupported && toSupported
    }

    private fun getApproximateRate(fromToken: SwapToken, toToken: SwapToken): BigDecimal {
        // Approximate USD prices for rate calculation
        val prices = mapOf(
            "MAS" to BigDecimal("0.035"),
            "WMAS" to BigDecimal("0.035"),
            "USDC.e" to BigDecimal("1.0"),
            "WETH.e" to BigDecimal("3500"),
            "DAI.e" to BigDecimal("1.0"),
            "DUSA" to BigDecimal("0.15"),
            "PUR" to BigDecimal("0.001")
        )
        
        val fromPrice = prices[fromToken.symbol] ?: BigDecimal.ONE
        val toPrice = prices[toToken.symbol] ?: BigDecimal.ONE
        
        return fromPrice.divide(toPrice, 18, RoundingMode.HALF_UP)
    }

    private fun validateSwap() {
        val state = _uiState.value
        val fromAmount = state.fromAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val fromBalance = state.fromBalanceRaw
        
        val canSwap = fromAmount > BigDecimal.ZERO && 
                      fromAmount <= fromBalance &&
                      state.toAmount.isNotEmpty() &&
                      state.error == null

        val error = when {
            fromAmount > fromBalance -> "Insufficient balance"
            else -> null
        }
        
        _uiState.update { it.copy(canSwap = canSwap, error = error) }
    }

    fun setMaxAmount() {
        val balance = _uiState.value.fromBalanceRaw
        val fromToken = _uiState.value.fromToken
        val opType = _uiState.value.operationType
        
        // Reserve MAS for gas fees
        val maxAmount = when {
            // For WRAP (MAS -> WMAS), reserve 0.5 MAS to ensure user can do transactions later
            fromToken == SwapToken.MAS && opType == SwapOperationType.WRAP -> {
                (balance - BigDecimal("0.5")).max(BigDecimal.ZERO)
            }
            // For regular swaps from MAS, reserve 0.1 MAS for gas
            fromToken == SwapToken.MAS -> {
                (balance - BigDecimal("0.1")).max(BigDecimal.ZERO)
            }
            // For tokens, use full balance
            else -> balance
        }
        
        // Format with appropriate decimals (at least 2)
        val displayDecimals = minOf(fromToken.decimals, 6)
        val formattedAmount = formatAmountWithMinDecimals(maxAmount, displayDecimals, 2)
        android.util.Log.d("SwapVM", "setMaxAmount: balance=$balance, opType=$opType, maxAmount=$maxAmount, formatted=$formattedAmount")
        updateFromAmount(formattedAmount)
    }

    fun swapTokens() {
        val currentFrom = _uiState.value.fromToken
        val currentTo = _uiState.value.toToken
        val currentFromBalance = _uiState.value.fromBalance
        val currentToBalance = _uiState.value.toBalance
        val currentFromBalanceRaw = _uiState.value.fromBalanceRaw
        val currentToBalanceRaw = _uiState.value.toBalanceRaw
        
        _uiState.update { state ->
            state.copy(
                fromToken = currentTo,
                toToken = currentFrom,
                fromBalance = currentToBalance,
                toBalance = currentFromBalance,
                fromBalanceRaw = currentToBalanceRaw,
                toBalanceRaw = currentFromBalanceRaw,
                fromAmount = "",
                toAmount = ""
            )
        }
        updateOperationType()
    }

    /**
     * Determine the operation type based on selected tokens
     */
    private fun updateOperationType() {
        val from = _uiState.value.fromToken
        val to = _uiState.value.toToken
        
        val opType = when {
            from == SwapToken.MAS && to == SwapToken.WMAS -> SwapOperationType.WRAP
            from == SwapToken.WMAS && to == SwapToken.MAS -> SwapOperationType.UNWRAP
            else -> SwapOperationType.SWAP
        }
        
        _uiState.update { it.copy(operationType = opType) }
    }

    fun selectFromToken(token: SwapToken) {
        if (token == _uiState.value.toToken) {
            // Swap the tokens
            swapTokens()
        } else {
            _uiState.update { it.copy(fromToken = token, fromAmount = "", toAmount = "") }
            viewModelScope.launch { loadTokenBalance(token, true) }
            updateOperationType()
        }
    }

    fun selectToToken(token: SwapToken) {
        if (token == _uiState.value.fromToken) {
            // Swap the tokens
            swapTokens()
        } else {
            _uiState.update { it.copy(toToken = token, toAmount = "") }
            viewModelScope.launch { loadTokenBalance(token, false) }
            updateOperationType()
        }
        
        // Recalculate output
        if (_uiState.value.fromAmount.isNotEmpty()) {
            calculateOutputAmount(_uiState.value.fromAmount)
        }
    }

    fun setSlippage(value: Float) {
        _uiState.update { it.copy(slippage = value) }
    }

    fun toggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings) }
    }

    /**
     * Show swap confirmation dialog with preview data
     */
    fun requestSwapConfirmation() {
        val state = _uiState.value
        if (!state.canSwap) return
        
        // Use the exact toAmount from state (already formatted consistently)
        val toAmountDecimal = state.toAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val toToken = state.toToken
        val displayDecimals = minOf(toToken.decimals, 6)
        
        val minReceived = formatAmountWithMinDecimals(
            toAmountDecimal.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(state.slippage / 100.0))),
            displayDecimals,
            2
        )
        
        val confirmationData = SwapConfirmationData(
            fromToken = state.fromToken,
            toToken = state.toToken,
            fromAmount = state.fromAmount,
            toAmount = state.toAmount,
            minReceived = minReceived,
            exchangeRate = state.exchangeRate,
            priceImpact = state.priceImpact,
            slippage = state.slippage
        )
        
        _uiState.update { it.copy(
            showConfirmation = true,
            confirmationData = confirmationData
        ) }
    }
    
    /**
     * Dismiss the confirmation dialog
     */
    fun dismissConfirmation() {
        _uiState.update { it.copy(showConfirmation = false, confirmationData = null) }
    }
    
    /**
     * Execute the swap after user confirms
     */
    fun confirmAndExecuteSwap() {
        _uiState.update { it.copy(showConfirmation = false) }
        executeSwap()
    }

    private fun executeSwap() {
        val state = _uiState.value
        if (!state.canSwap) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, swapStatus = "Refreshing quote...") }
            
            try {
                val fromAmount = state.fromAmount.toBigDecimal()
                
                // Get a fresh quote just before executing to ensure we have the latest price
                // This prevents failures due to stale quotes when price has moved
                val freshQuote = try {
                    getSwapQuote(
                        fromToken = state.fromToken,
                        toToken = state.toToken,
                        amountIn = fromAmount
                    )
                } catch (e: Exception) {
                    android.util.Log.w("SwapVM", "Failed to get fresh quote, using cached: ${e.message}")
                    null
                }
                
                // Use fresh quote if available, otherwise fall back to displayed amount
                val expectedAmountOut = freshQuote?.amountOut ?: state.toAmount.toBigDecimal()
                
                // Apply slippage tolerance
                val minAmountOut = expectedAmountOut
                    .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(state.slippage / 100.0)))
                
                android.util.Log.d("SwapVM", "Fresh quote amountOut: ${freshQuote?.amountOut}, displayed: ${state.toAmount}, minAmountOut: $minAmountOut, slippage: ${state.slippage}%")
                
                _uiState.update { it.copy(swapStatus = "Preparing swap...") }
                
                // Get credentials
                val activeAccount = accountManager.activeAccount.value
                val address = activeAccount?.address ?: secureStorage.getActiveWallet()
                    ?: throw Exception("No wallet address found")
                
                // Get private key
                val privateKeyBytes = if (activeAccount != null) {
                    accountManager.getPrivateKeyForAccount(activeAccount.id)
                } else {
                    val mnemonic = secureStorage.getMnemonic("default_wallet")
                    mnemonic?.let { walletManager.getPrivateKey(it) }
                } ?: throw Exception("Private key not found")
                
                val privateKey = privateKeyBytes.joinToString("") { "%02x".format(it) }
                val publicKeyBytes = walletManager.derivePublicKey(privateKeyBytes)
                val publicKey = walletManager.encodePublicKeyP1(publicKeyBytes)
                
                android.util.Log.d("SwapVM", "Starting swap: ${state.fromToken.symbol} -> ${state.toToken.symbol}")
                android.util.Log.d("SwapVM", "Amount: $fromAmount, MinOut: $minAmountOut")
                
                _uiState.update { it.copy(swapStatus = "Sending transaction...") }
                
                val txHash = performSwap(
                    fromToken = state.fromToken,
                    toToken = state.toToken,
                    amountIn = fromAmount,
                    minAmountOut = minAmountOut,
                    address = address,
                    privateKey = privateKey,
                    publicKey = publicKey
                )
                
                android.util.Log.d("SwapVM", "Transaction sent! Hash: $txHash")
                
                // Now wait for blockchain confirmation with polling
                _uiState.update { it.copy(
                    isLoading = false,
                    isConfirmingTx = true,
                    txHash = txHash,
                    swapStatus = "Confirming on blockchain..."
                ) }
                
                // Poll for confirmation (60 second timeout, poll every 2 seconds)
                val confirmationResult = massaRepository.waitForOperationExecution(
                    operationId = txHash,
                    timeoutMs = 60_000L,
                    pollMs = 2_000L
                )
                
                when (confirmationResult) {
                    is Result.Success -> {
                        val operation = confirmationResult.data
                        if (operation != null) {
                            val isFinal = operation.isFinal
                            val execStatus = operation.opExecStatus
                            
                            android.util.Log.d("SwapVM", "Operation confirmed! isFinal=$isFinal, opExecStatus=$execStatus")
                            
                            if (execStatus == true) {
                                // SUCCESS - transaction executed successfully
                                _uiState.update { it.copy(
                                    isConfirmingTx = false,
                                    swapSuccess = true,
                                    swapStatus = ""
                                ) }
                                // Refresh both balances after successful swap
                                loadBalances()
                            } else if (execStatus == false) {
                                // FAILED - transaction was included but execution failed (slippage, liquidity, etc)
                                val errorMsg = operation.opExecError ?: "Swap failed - price changed too much (try increasing slippage)"
                                android.util.Log.e("SwapVM", "Swap execution failed: $errorMsg")
                                _uiState.update { it.copy(
                                    isConfirmingTx = false,
                                    error = errorMsg,
                                    swapStatus = ""
                                ) }
                            } else if (isFinal) {
                                // Final but no exec status - assume success for simple transfers
                                _uiState.update { it.copy(
                                    isConfirmingTx = false,
                                    swapSuccess = true,
                                    swapStatus = ""
                                ) }
                                // Refresh both balances after successful swap
                                loadBalances()
                            } else {
                                // Still not final after timeout, but no exec status yet
                                _uiState.update { it.copy(
                                    isConfirmingTx = false,
                                    swapSuccess = true, // Show success since tx was sent
                                    swapStatus = ""
                                ) }
                                // Refresh both balances after successful swap
                                loadBalances()
                            }
                        } else {
                            // No operation data returned (timeout) - show success since tx was sent
                            android.util.Log.w("SwapVM", "Confirmation timeout, but tx was sent")
                            _uiState.update { it.copy(
                                isConfirmingTx = false,
                                swapSuccess = true,
                                swapStatus = ""
                            ) }
                            // Refresh both balances after successful swap
                            loadBalances()
                        }
                    }
                    is Result.Error -> {
                        // Error checking status, but tx was sent - show success
                        android.util.Log.w("SwapVM", "Error checking confirmation: ${confirmationResult.exception.message}")
                        _uiState.update { it.copy(
                            isConfirmingTx = false,
                            swapSuccess = true,
                            swapStatus = ""
                        ) }
                        // Refresh both balances after successful swap
                        loadBalances()
                    }
                    is Result.Loading -> {
                        // Shouldn't happen
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SwapVM", "Swap failed", e)
                _uiState.update { it.copy(
                    isLoading = false,
                    isConfirmingTx = false,
                    error = e.message ?: "Swap failed",
                    swapStatus = ""
                ) }
            }
        }
    }
    
    private suspend fun performSwap(
        fromToken: SwapToken,
        toToken: SwapToken,
        amountIn: BigDecimal,
        minAmountOut: BigDecimal,
        address: String,
        privateKey: String,
        publicKey: String
    ): String {
        // Convert amounts to token units (nanoMAS for MAS tokens)
        val amountInUnits = amountIn.multiply(BigDecimal.TEN.pow(fromToken.decimals))
            .setScale(0, RoundingMode.DOWN)
            .toBigInteger()
            .toString()
        val minAmountOutUnits = minAmountOut.multiply(BigDecimal.TEN.pow(toToken.decimals))
            .setScale(0, RoundingMode.DOWN)
            .toBigInteger()
            .toString()
        
        // Deadline: 20 minutes from now (in milliseconds)
        val deadline = (System.currentTimeMillis() + (20 * 60 * 1000)).toString()
        
        android.util.Log.d("SwapVM", "amountInUnits: $amountInUnits, minAmountOutUnits: $minAmountOutUnits")
        
        return when {
            // WRAP: MAS -> WMAS (direct call to WMAS contract)
            fromToken == SwapToken.MAS && toToken == SwapToken.WMAS -> {
                _uiState.update { it.copy(swapStatus = "Wrapping MAS to WMAS...") }
                wrapMAS(amountInUnits, address, privateKey, publicKey)
            }
            // Swap MAS for Token (swapExactMASForTokens)
            fromToken == SwapToken.MAS -> {
                _uiState.update { it.copy(swapStatus = "Swapping MAS for ${toToken.symbol}...") }
                swapMASForToken(toToken, amountInUnits, minAmountOutUnits, deadline, address, privateKey, publicKey)
            }
            // UNWRAP: WMAS -> MAS (direct call to WMAS contract withdraw - NOT through router!)
            fromToken == SwapToken.WMAS && toToken == SwapToken.MAS -> {
                _uiState.update { it.copy(swapStatus = "Unwrapping WMAS to MAS...") }
                android.util.Log.d("SwapVM", "UNWRAP: Calling WMAS.withdraw directly (not via router)")
                val unwrapOpId = unwrapWMAS(amountInUnits, address, privateKey, publicKey)
                android.util.Log.d("SwapVM", "Unwrap tx sent: $unwrapOpId – polling for on-chain result...")
                
                // Wait for unwrap to be executed on-chain and check success
                val unwrapResult = massaRepository.waitForOperationExecution(unwrapOpId, timeoutMs = 45_000L)
                if (unwrapResult is Result.Success) {
                    val op = unwrapResult.data
                    android.util.Log.d("SwapVM", "Unwrap op_exec_status=${op?.opExecStatus}, op_exec_error=${op?.opExecError}")
                    if (op?.opExecStatus == false) {
                        throw Exception("Unwrap failed on-chain: ${op.opExecError ?: "unknown error"}")
                    }
                }
                unwrapOpId
            }
            // Swap Token for MAS (swapExactTokensForMAS) - for tokens other than WMAS
            toToken == SwapToken.MAS -> {
                // First approve the router to spend tokens
                _uiState.update { it.copy(swapStatus = "Approving ${fromToken.symbol}...") }
                val approveOpId = approveToken(fromToken, amountInUnits, address, privateKey, publicKey)
                android.util.Log.d("SwapVM", "Approval tx sent: $approveOpId – polling for on-chain result...")

                // Wait for approval to be executed on-chain and check success
                val approveResult = massaRepository.waitForOperationExecution(approveOpId, timeoutMs = 45_000L)
                if (approveResult is Result.Success) {
                    val op = approveResult.data
                    android.util.Log.d("SwapVM", "Approval op_exec_status=${op?.opExecStatus}, op_exec_error=${op?.opExecError}")
                    if (op?.opExecStatus == false) {
                        throw Exception("Approval failed on-chain: ${op.opExecError ?: "unknown error"}")
                    }
                }
                
                _uiState.update { it.copy(swapStatus = "Swapping ${fromToken.symbol} for MAS...") }
                val swapOpId = swapTokenForMAS(fromToken, amountInUnits, minAmountOutUnits, deadline, address, privateKey, publicKey)
                android.util.Log.d("SwapVM", "Swap tx sent: $swapOpId – polling for on-chain result...")

                // Wait for swap to be executed on-chain and check success
                val swapResult = massaRepository.waitForOperationExecution(swapOpId, timeoutMs = 45_000L)
                if (swapResult is Result.Success) {
                    val op = swapResult.data
                    android.util.Log.d("SwapVM", "Swap op_exec_status=${op?.opExecStatus}, op_exec_error=${op?.opExecError}")
                    if (op?.opExecStatus == false) {
                        throw Exception("Swap failed on-chain: ${op.opExecError ?: "unknown error"}")
                    }
                }
                swapOpId
            }
            else -> {
                // Swap Token for Token (through WMAS)
                _uiState.update { it.copy(swapStatus = "Approving ${fromToken.symbol}...") }
                approveToken(fromToken, amountInUnits, address, privateKey, publicKey)
                delay(3000)
                
                _uiState.update { it.copy(swapStatus = "Swapping ${fromToken.symbol} for ${toToken.symbol}...") }
                swapTokenForToken(fromToken, toToken, amountInUnits, minAmountOutUnits, deadline, address, privateKey, publicKey)
            }
        }
    }
    
    /**
     * Wrap MAS to WMAS - direct call to WMAS contract deposit function
     */
    private suspend fun wrapMAS(
        amountInUnits: String,
        address: String,
        privateKey: String,
        publicKey: String
    ): String {
        // WMAS contract deposit function takes no parameters
        // The MAS to wrap is sent as coins
        val result = massaRepository.callSmartContract(
            from = address,
            targetAddress = SwapToken.WMAS.contractAddress,
            functionName = "deposit",
            parameter = "{}", // No parameters
            coins = amountInUnits, // Send MAS as coins
            fee = DEFAULT_FEE,
            maxGas = "50000000", // 50M gas should be enough
            privateKey = privateKey,
            publicKey = publicKey
        )
        
        return when (result) {
            is Result.Success -> result.data ?: throw Exception("No operation ID returned")
            is Result.Error -> throw Exception(result.exception.message ?: "Wrap failed")
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }
    
    /**
     * Unwrap WMAS to MAS - call WMAS contract withdraw function
     * Parameters: u64 amount + string recipient (based on massa-web3 SDK)
     */
    private suspend fun unwrapWMAS(
        amountInUnits: String,
        address: String,
        privateKey: String,
        publicKey: String
    ): String {
        val buffer = java.io.ByteArrayOutputStream()
        
        // 1. u64 amount (8 bytes, little-endian)
        val amountLong = BigDecimal(amountInUnits).toLong()
        val amountBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(amountLong).array()
        buffer.write(amountBytes)
        
        // 2. string recipient (length-prefixed)
        val recipientBytes = address.toByteArray(Charsets.UTF_8)
        val recipientLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(recipientBytes.size).array()
        buffer.write(recipientLenBytes)
        buffer.write(recipientBytes)
        
        val bytes = buffer.toByteArray()
        
        // Send as JSONObject with string keys
        val parameter = bytes.indices.associate { it.toString() to (bytes[it].toInt() and 0xFF) }
            .let { org.json.JSONObject(it).toString() }
        
        android.util.Log.d("SwapVM", "unwrapWMAS amount: $amountInUnits, recipient: $address, total bytes: ${bytes.size}")
        
        val result = massaRepository.callSmartContract(
            from = address,
            targetAddress = SwapToken.WMAS.contractAddress,
            functionName = "withdraw",
            parameter = parameter,
            coins = "0",
            fee = DEFAULT_FEE,
            maxGas = "50000000", // 50M gas
            privateKey = privateKey,
            publicKey = publicKey
        )
        
        return when (result) {
            is Result.Success -> result.data ?: throw Exception("No operation ID returned")
            is Result.Error -> throw Exception(result.exception.message ?: "Unwrap failed")
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }
    
    /**
     * Serialize DUSA swap parameters according to MassaLabs Args format
     * DUSA Router V2 format for swapExactMASForTokens:
     * - u256(amountOutMin)
     * - array(pairBinSteps, u64[])
     * - array(isLegacy, bool[]) - only for V2 router
     * - array(tokenPath, string[])
     * - string(to)
     * - u64(deadline)
     * - u64(storageCost) - 0.1 MAS = 100_000_000 nanoMAS
     */
    
    /**
     * Helper function to convert a BigInteger to 32-byte little-endian U256
     */
    private fun bigIntToU256LittleEndian(value: BigInteger): ByteArray {
        val bytesBigEndian = value.toByteArray()
        // Remove leading sign byte if present (BigInteger adds 0x00 for positive numbers with high bit set)
        val bytesUnsigned = if (bytesBigEndian.isNotEmpty() && bytesBigEndian[0] == 0.toByte() && bytesBigEndian.size > 1) {
            bytesBigEndian.copyOfRange(1, bytesBigEndian.size)
        } else {
            bytesBigEndian
        }
        // Convert to 32-byte little-endian
        val u256 = ByteArray(32) { 0 }
        for (i in bytesUnsigned.indices) {
            u256[bytesUnsigned.size - 1 - i] = bytesUnsigned[i]
        }
        return u256
    }
    
    private fun serializeSwapExactMASForTokensParams(
        amountOutMin: String,
        binSteps: List<Long>,
        isLegacy: List<Boolean>,
        tokenPath: List<String>,
        toAddress: String,
        deadline: String
    ): String {
        val buffer = java.io.ByteArrayOutputStream()
        val SWAP_STORAGE_COST = 100_000_000L // 0.1 MAS
        
        // 1. amountOutMin as u256 (32 bytes, little-endian)
        val amountBigInt = BigDecimal(amountOutMin).toBigInteger()
        buffer.write(bigIntToU256LittleEndian(amountBigInt))
        android.util.Log.d("SwapVM", "amountOutMin: $amountOutMin -> bigInt: $amountBigInt")
        
        // 2. binSteps array (u64[]) - BYTE length prefix (u32) + u64 elements
        // Massa serialization uses BYTE LENGTH, not element count!
        val binStepsByteLength = binSteps.size * 8 // 8 bytes per u64
        val binStepsLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(binStepsByteLength).array()
        buffer.write(binStepsLenBytes)
        for (binStep in binSteps) {
            val binStepBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(binStep).array()
            buffer.write(binStepBytes)
        }
        
        // 3. isLegacy array (bool[]) - V2 router requires this
        // BYTE length prefix (1 byte per boolean)
        val isLegacyByteLength = isLegacy.size * 1 // 1 byte per bool
        val isLegacyLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(isLegacyByteLength).array()
        buffer.write(isLegacyLenBytes)
        for (legacy in isLegacy) {
            buffer.write(if (legacy) 1 else 0)
        }
        
        // 4. tokenPath array (string[]) - BYTE length prefix (u32) + serialized strings
        // First serialize all strings to a temporary buffer to get total byte length
        val tokenPathBuffer = java.io.ByteArrayOutputStream()
        for (addr in tokenPath) {
            val addrBytes = addr.toByteArray(Charsets.UTF_8)
            val strLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(addrBytes.size).array()
            tokenPathBuffer.write(strLenBytes)
            tokenPathBuffer.write(addrBytes)
        }
        val tokenPathContent = tokenPathBuffer.toByteArray()
        val pathLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(tokenPathContent.size).array()
        buffer.write(pathLenBytes)
        buffer.write(tokenPathContent)
        
        // 5. to address (string length + string bytes)
        val toBytes = toAddress.toByteArray(Charsets.UTF_8)
        val toLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(toBytes.size).array()
        buffer.write(toLenBytes)
        buffer.write(toBytes)
        
        // 6. deadline as u64 (8 bytes, little-endian)
        val deadlineBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(deadline.toLong()).array()
        buffer.write(deadlineBytes)
        
        // 7. storageCost as u64 (8 bytes, little-endian) - 0.1 MAS
        val storageCostBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(SWAP_STORAGE_COST).array()
        buffer.write(storageCostBytes)
        
        // Return as JSON array of bytes for the parameter field
        val bytes = buffer.toByteArray()
        return bytes.indices.associate { it.toString() to (bytes[it].toInt() and 0xFF) }
            .let { org.json.JSONObject(it).toString() }
    }
    
    /**
     * Serialize swap parameters for DUSA Router V2 (old format for backwards compatibility)
     * Format: u256(amountOut) + u32(pathLength) + address[] + address(to) + u64(deadline)
     */
    private fun serializeSwapParams(
        amountOutMin: String,
        path: List<String>,
        toAddress: String,
        deadline: String
    ): String {
        val buffer = java.io.ByteArrayOutputStream()
        
        // amountOutMin as u256 (32 bytes, little-endian)
        buffer.write(bigIntToU256LittleEndian(BigDecimal(amountOutMin).toBigInteger()))
        
        // path length as u32 (4 bytes, little-endian)
        val pathLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(path.size).array()
        buffer.write(pathLenBytes)
        
        // Each address in path (string length + string bytes)
        for (addr in path) {
            val addrBytes = addr.toByteArray(Charsets.UTF_8)
            // String length as u32
            val strLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(addrBytes.size).array()
            buffer.write(strLenBytes)
            buffer.write(addrBytes)
        }
        
        // to address (string length + string bytes)
        val toBytes = toAddress.toByteArray(Charsets.UTF_8)
        val toLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(toBytes.size).array()
        buffer.write(toLenBytes)
        buffer.write(toBytes)
        
        // deadline as u64 (8 bytes, little-endian)
        val deadlineBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(deadline.toLong()).array()
        buffer.write(deadlineBytes)
        
        // Return as JSON array of bytes for the parameter field
        val bytes = buffer.toByteArray()
        return bytes.indices.associate { it.toString() to (bytes[it].toInt() and 0xFF) }
            .let { org.json.JSONObject(it).toString() }
    }
    
    private suspend fun swapMASForToken(
        toToken: SwapToken,
        amountIn: String,
        minAmountOut: String,
        deadline: String,
        address: String,
        privateKey: String,
        publicKey: String
    ): String {
        // Get route info from cached quote
        val quote = cachedQuote ?: throw Exception("No quote available. Please refresh the quote.")
        
        // Use route from quote, or fallback to default path
        val tokenPath = if (quote.route.isNotEmpty()) {
            quote.route
        } else {
            listOf(SwapToken.WMAS.contractAddress, toToken.contractAddress)
        }
        
        // Use binSteps from quote, or default to [20] for standard pool
        val binSteps = if (quote.binSteps.isNotEmpty()) {
            quote.binSteps
        } else {
            listOf(20L) // Default bin step for WMAS-USDC pool
        }
        
        // Use isLegacy from quote - this is CRITICAL!
        // V2.1 pools (isLegacy=false) use different bin math than V2.0 pools (isLegacy=true)
        // Using wrong value causes TreeMath__ErrorDepthSearch error
        val isLegacy = if (quote.isLegacy.isNotEmpty()) {
            quote.isLegacy
        } else {
            // Default to false for V2.1 pools (most common on DUSA mainnet)
            List(binSteps.size) { false }
        }
        
        android.util.Log.d("SwapVM", "Swap params - tokenPath: $tokenPath, binSteps: $binSteps, isLegacy: $isLegacy")
        android.util.Log.d("SwapVM", "Swap params - minAmountOut: $minAmountOut, deadline: $deadline, address: $address")
        
        val parameter = serializeSwapExactMASForTokensParams(
            amountOutMin = minAmountOut,
            binSteps = binSteps,
            isLegacy = isLegacy,
            tokenPath = tokenPath,
            toAddress = address,
            deadline = deadline
        )
        
        android.util.Log.d("SwapVM", "swapExactMASForTokens parameter: $parameter")
        
        val result = massaRepository.callSmartContract(
            from = address,
            targetAddress = DUSA_ROUTER,
            functionName = "swapExactMASForTokens",
            parameter = parameter,
            coins = amountIn, // Send MAS with the call
            fee = DEFAULT_FEE,
            maxGas = DEFAULT_MAX_GAS,
            privateKey = privateKey,
            publicKey = publicKey
        )
        
        return when (result) {
            is Result.Success -> result.data ?: throw Exception("No operation ID returned")
            is Result.Error -> throw Exception(result.exception.message ?: "Swap failed")
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }
    
    private suspend fun swapTokenForMAS(
        fromToken: SwapToken,
        amountIn: String,
        minAmountOut: String,
        deadline: String,
        address: String,
        privateKey: String,
        publicKey: String
    ): String {
        // For WMAS->MAS (unwrap) we MUST use a single-element path [WMAS] with no hops,
        // regardless of any cached quote (which might be stale or from a different pair).
        val isUnwrap = (fromToken == SwapToken.WMAS)
        
        // Get route info from cached quote for non-unwrap swaps.
        val quote = if (isUnwrap) null else cachedQuote
        if (quote == null && !isUnwrap) {
            throw Exception("No quote available. Please refresh the quote.")
        }
        
        // Use route from quote, or fallback to default path
        val tokenPath = if (isUnwrap) {
            // WMAS->MAS unwrap: single-element path, router will call WMAS.withdraw internally
            listOf(SwapToken.WMAS.contractAddress)
        } else if (quote?.route?.isNotEmpty() == true) {
            quote.route
        } else {
            listOf(fromToken.contractAddress, SwapToken.WMAS.contractAddress)
        }
        
        // Use binSteps from quote, or default
        val binSteps = if (isUnwrap) {
            // No hop needed for WMAS->MAS unwrap via router
            emptyList()
        } else if (quote?.binSteps?.isNotEmpty() == true) {
            quote.binSteps
        } else {
            listOf(20L)
        }
        
        // Use isLegacy from quote - CRITICAL for correct pool type
        val isLegacy = if (isUnwrap) {
            emptyList()
        } else if (quote?.isLegacy?.isNotEmpty() == true) {
            quote.isLegacy
        } else {
            // Default to false for V2.1 pools
            List(binSteps.size) { false }
        }
        
        android.util.Log.d("SwapVM", "swapTokenForMAS params - tokenPath: $tokenPath, binSteps: $binSteps, isLegacy: $isLegacy")
        
        // For swapExactTokensForMAS - DUSA V2 format:
        // amountIn(u256) + amountOutMin(u256) + binSteps(u64[]) + isLegacy(bool[]) + tokenPath(string[]) + to(string) + deadline(u64) + storageCost(u64)
        val buffer = java.io.ByteArrayOutputStream()
        val SWAP_STORAGE_COST = 100_000_000L // 0.1 MAS in nanoMAS
        
        // 1. amountIn as u256 (32 bytes, little-endian)
        buffer.write(bigIntToU256LittleEndian(BigDecimal(amountIn).toBigInteger()))
        
        // 2. amountOutMin as u256
        buffer.write(bigIntToU256LittleEndian(BigDecimal(minAmountOut).toBigInteger()))
        
        // 3. binSteps array (u64[]) - BYTE length prefix
        val binStepsByteLen = binSteps.size * 8
        val binStepsLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(binStepsByteLen).array()
        buffer.write(binStepsLenBytes)
        for (binStep in binSteps) {
            val binStepBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(binStep).array()
            buffer.write(binStepBytes)
        }
        
        // 4. isLegacy array (bool[]) - BYTE length prefix
        val isLegacyByteLen = isLegacy.size * 1
        val isLegacyLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(isLegacyByteLen).array()
        buffer.write(isLegacyLenBytes)
        for (legacy in isLegacy) {
            buffer.write(if (legacy) 1 else 0)
        }
        
        // 5. tokenPath array (string[]) - BYTE length prefix
        val tokenPathBuf = java.io.ByteArrayOutputStream()
        for (addr in tokenPath) {
            val addrBytes = addr.toByteArray(Charsets.UTF_8)
            val strLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(addrBytes.size).array()
            tokenPathBuf.write(strLenBytes)
            tokenPathBuf.write(addrBytes)
        }
        val tokenPathData = tokenPathBuf.toByteArray()
        val pathLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(tokenPathData.size).array()
        buffer.write(pathLenBytes)
        buffer.write(tokenPathData)
        
        // 6. to address
        val toBytes = address.toByteArray(Charsets.UTF_8)
        val toLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(toBytes.size).array()
        buffer.write(toLenBytes)
        buffer.write(toBytes)
        
        // 7. deadline as u64
        val deadlineBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(deadline.toLong()).array()
        buffer.write(deadlineBytes)

        // 8. storageCost as u64
        val storageCostBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(SWAP_STORAGE_COST).array()
        buffer.write(storageCostBytes)
        
        val bytes = buffer.toByteArray()
        val parameter = bytes.indices.associate { it.toString() to (bytes[it].toInt() and 0xFF) }
            .let { org.json.JSONObject(it).toString() }
        
        android.util.Log.d("SwapVM", "swapExactTokensForMAS parameter: $parameter")
        
        val result = massaRepository.callSmartContract(
            from = address,
            targetAddress = DUSA_ROUTER,
            functionName = "swapExactTokensForMAS",
            parameter = parameter,
            coins = "0", // No MAS sent for token->MAS swap
            fee = DEFAULT_FEE,
            maxGas = DEFAULT_MAX_GAS,
            privateKey = privateKey,
            publicKey = publicKey
        )
        
        return when (result) {
            is Result.Success -> result.data ?: throw Exception("No operation ID returned")
            is Result.Error -> throw Exception(result.exception.message ?: "Swap failed")
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }
    
    private suspend fun swapTokenForToken(
        fromToken: SwapToken,
        toToken: SwapToken,
        amountIn: String,
        minAmountOut: String,
        deadline: String,
        address: String,
        privateKey: String,
        publicKey: String
    ): String {
        // Get route info from cached quote
        val quote = cachedQuote ?: throw Exception("No quote available. Please refresh the quote.")
        
        // Use route from quote, or fallback to default path
        val tokenPath = if (quote.route.isNotEmpty()) {
            quote.route
        } else {
            listOf(fromToken.contractAddress, SwapToken.WMAS.contractAddress, toToken.contractAddress)
        }
        
        // Use binSteps from quote, or default
        val binSteps = if (quote.binSteps.isNotEmpty()) {
            quote.binSteps
        } else {
            listOf(20L, 20L) // 2 hops for token-to-token
        }
        
        // Use isLegacy from quote - CRITICAL for correct pool type
        val isLegacy = if (quote.isLegacy.isNotEmpty()) {
            quote.isLegacy
        } else {
            // Default to false for V2.1 pools
            List(binSteps.size) { false }
        }
        
        android.util.Log.d("SwapVM", "swapTokenForToken params - tokenPath: $tokenPath, binSteps: $binSteps, isLegacy: $isLegacy")
        
        // For swapExactTokensForTokens - DUSA V2 format:
        // amountIn(u256) + amountOutMin(u256) + binSteps(u64[]) + isLegacy(bool[]) + tokenPath(string[]) + to(string) + deadline(u64)
        val buffer = java.io.ByteArrayOutputStream()
        
        // 1. amountIn as u256
        buffer.write(bigIntToU256LittleEndian(BigDecimal(amountIn).toBigInteger()))
        
        // 2. amountOutMin as u256
        buffer.write(bigIntToU256LittleEndian(BigDecimal(minAmountOut).toBigInteger()))
        
        // 3. binSteps array (u64[]) - BYTE length prefix
        val binStepsByteLenT = binSteps.size * 8
        val binStepsLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(binStepsByteLenT).array()
        buffer.write(binStepsLenBytes)
        for (binStep in binSteps) {
            val binStepBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(binStep).array()
            buffer.write(binStepBytes)
        }
        
        // 4. isLegacy array (bool[]) - BYTE length prefix
        val isLegacyByteLenT = isLegacy.size * 1
        val isLegacyLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(isLegacyByteLenT).array()
        buffer.write(isLegacyLenBytes)
        for (legacy in isLegacy) {
            buffer.write(if (legacy) 1 else 0)
        }
        
        // 5. tokenPath array (string[]) - BYTE length prefix
        val tokenPathBufT = java.io.ByteArrayOutputStream()
        for (addr in tokenPath) {
            val addrBytes = addr.toByteArray(Charsets.UTF_8)
            val strLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(addrBytes.size).array()
            tokenPathBufT.write(strLenBytes)
            tokenPathBufT.write(addrBytes)
        }
        val tokenPathDataT = tokenPathBufT.toByteArray()
        val pathLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(tokenPathDataT.size).array()
        buffer.write(pathLenBytes)
        buffer.write(tokenPathDataT)
        
        // 6. to address
        val toBytes = address.toByteArray(Charsets.UTF_8)
        val toLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(toBytes.size).array()
        buffer.write(toLenBytes)
        buffer.write(toBytes)
        
        // 7. deadline
        val deadlineBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(deadline.toLong()).array()
        buffer.write(deadlineBytes)
        
        val bytes = buffer.toByteArray()
        val parameter = bytes.indices.associate { it.toString() to (bytes[it].toInt() and 0xFF) }
            .let { org.json.JSONObject(it).toString() }
        
        android.util.Log.d("SwapVM", "swapExactTokensForTokens parameter: $parameter")
        
        val result = massaRepository.callSmartContract(
            from = address,
            targetAddress = DUSA_ROUTER,
            functionName = "swapExactTokensForTokens",
            parameter = parameter,
            coins = "0",
            fee = DEFAULT_FEE,
            maxGas = DEFAULT_MAX_GAS,
            privateKey = privateKey,
            publicKey = publicKey
        )
        
        return when (result) {
            is Result.Success -> result.data ?: throw Exception("No operation ID returned")
            is Result.Error -> throw Exception(result.exception.message ?: "Swap failed")
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }
    
    private suspend fun approveToken(
        token: SwapToken,
        amount: String,
        address: String,
        privateKey: String,
        publicKey: String
    ): String {
        // increaseAllowance(spender: Address, addedValue: u256)
        val buffer = java.io.ByteArrayOutputStream()
        
        // spender address (DUSA_ROUTER)
        val spenderBytes = DUSA_ROUTER.toByteArray(Charsets.UTF_8)
        val spenderLenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(spenderBytes.size).array()
        buffer.write(spenderLenBytes)
        buffer.write(spenderBytes)
        
        // addedValue as u256 (use max uint256 for unlimited approval)
        val maxU256 = ByteArray(32) { 0xFF.toByte() }
        buffer.write(maxU256)
        
        val bytes = buffer.toByteArray()
        val parameter = bytes.indices.associate { it.toString() to (bytes[it].toInt() and 0xFF) }
            .let { org.json.JSONObject(it).toString() }
        
        android.util.Log.d("SwapVM", "increaseAllowance parameter for ${token.symbol}")
        
        val result = massaRepository.callSmartContract(
            from = address,
            targetAddress = token.contractAddress,
            functionName = "increaseAllowance",
            parameter = parameter,
            coins = "0",
            fee = DEFAULT_FEE,
            maxGas = DEFAULT_MAX_GAS,
            privateKey = privateKey,
            publicKey = publicKey
        )
        
        return when (result) {
            is Result.Success -> {
                android.util.Log.d("SwapVM", "Approval successful: ${result.data}")
                result.data ?: throw Exception("No operation ID returned from approval")
            }
            is Result.Error -> {
                throw Exception("Approval failed: ${result.exception.message}")
            }
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }

    fun resetSwap() {
        _uiState.update { state ->
            state.copy(
                fromAmount = "",
                toAmount = "",
                swapSuccess = false,
                isConfirmingTx = false,
                txHash = null,
                error = null,
                swapStatus = ""
            )
        }
        viewModelScope.launch { loadBalances() }
    }
    
    /**
     * Reset swap form and refresh balances - used after successful swap
     * to stay on the swap screen with updated balances
     */
    fun resetSwapAndRefresh() {
        _uiState.update { state ->
            state.copy(
                fromAmount = "",
                toAmount = "",
                exchangeRate = "0",
                priceImpact = 0f,
                swapSuccess = false,
                isConfirmingTx = false,
                txHash = null,
                error = null,
                swapStatus = "",
                canSwap = false,
                isCalculatingQuote = false
            )
        }
        // Clear cached quote
        cachedQuote = null
        // Refresh balances
        viewModelScope.launch { 
            loadBalances() 
        }
    }
}
