package com.massapay.android.ui.portfolio

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.core.util.Result
import com.massapay.android.network.repository.MassaRepository
import com.massapay.android.price.repository.PriceRepository
import com.massapay.android.security.storage.SecureStorage
import com.massapay.android.security.wallet.AccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

// Token data for portfolio
data class PortfolioToken(
    val symbol: String,
    val name: String,
    val contractAddress: String,
    val decimals: Int,
    val balance: BigDecimal,
    val balanceFormatted: String,
    val usdValue: BigDecimal,
    val usdPrice: BigDecimal,
    val percentage: Double,
    val color: Color
)

data class PortfolioState(
    val tokens: List<PortfolioToken> = emptyList(),
    val totalUsdValue: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true,
    val error: String? = null
)

// Token definitions with contract addresses
private data class TokenInfo(
    val symbol: String,
    val name: String,
    val contractAddress: String,
    val decimals: Int,
    val color: Color,
    val defaultPrice: BigDecimal // Fallback price if we can't get real price
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val massaRepository: MassaRepository,
    private val priceRepository: PriceRepository,
    private val secureStorage: SecureStorage,
    private val accountManager: AccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioState())
    val uiState: StateFlow<PortfolioState> = _uiState

    // HTTP client for token balance queries
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // All supported tokens with their info
    private val supportedTokens = listOf(
        TokenInfo(
            symbol = "MAS",
            name = "Massa",
            contractAddress = "", // Native token
            decimals = 9,
            color = Color(0xFF1A1A2E),
            defaultPrice = BigDecimal("0.003") // Fallback only
        ),
        TokenInfo(
            symbol = "WMAS",
            name = "Wrapped Massa",
            contractAddress = "AS12U4TZfNK7qoLyEERBBRDMu8nm5MKoRzPXDXans4v9wdATZedz9",
            decimals = 9,
            color = Color(0xFF4A90D9),
            defaultPrice = BigDecimal("0.003") // Same as MAS
        ),
        TokenInfo(
            symbol = "USDC.e",
            name = "USD Coin (Bridged)",
            contractAddress = "AS1hCJXjndR4c9vekLWsXGnrdigp4AaZ7uYG3UKFzzKnWVsrNLPJ",
            decimals = 6,
            color = Color(0xFF2775CA),
            defaultPrice = BigDecimal("1.0") // Stablecoin
        ),
        TokenInfo(
            symbol = "WETH.e",
            name = "Wrapped Ether (Bridged)",
            contractAddress = "AS124vf3YfAJCSCQVYKczzuWWpXrximFpbTmX4rheLs5uNSftiiRY",
            decimals = 18,
            color = Color(0xFF627EEA),
            defaultPrice = BigDecimal("4000") // Approximate ETH price
        ),
        TokenInfo(
            symbol = "DAI.e",
            name = "Dai Stablecoin (Bridged)",
            contractAddress = "AS1ZGF1upwp9kPRvDKLxFAKRebgg7b3RWDnhgV7VvdZkZsUL7Nuv",
            decimals = 18,
            color = Color(0xFFF5AC37),
            defaultPrice = BigDecimal("1.0") // Stablecoin
        ),
        TokenInfo(
            symbol = "DUSA",
            name = "Dusa Token",
            contractAddress = "AS12HT1JQUne9nkHevS9Q7HcsoAaYLXWPNgoWPuruV7Gw6Mb92ACL",
            decimals = 18,
            color = Color(0xFF00BFA5),
            defaultPrice = BigDecimal("0.01") // Will get real price from DEX
        ),
        TokenInfo(
            symbol = "PUR",
            name = "Purrfect Universe",
            contractAddress = "AS133eqPPaPttJ6hJnk3sfoG5cjFFqBDi1VGxdo2wzWkq8AfZnan",
            decimals = 9,
            color = Color(0xFF9C27B0),
            defaultPrice = BigDecimal("0.0001")
        ),
        TokenInfo(
            symbol = "MC",
            name = "MassaConnect",
            contractAddress = "AS12XfRvGn4A8QZBmLSHsD2EBqpBVEsdF1CcbvJRTpqtXMYGhH6FH",
            decimals = 18,
            color = Color(0xFFFFD700), // Gold color for official token
            defaultPrice = BigDecimal("0.00004") // Will get real price from DEX
        )
    )

    // Cached real prices (fetched from CoinPaprika and DEX)
    private var realPrices = mutableMapOf<String, BigDecimal>()
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
                loadPortfolio(address)
            }
        }
    }

    fun loadPortfolio() {
        val address = accountManager.activeAccount.value?.address
            ?: secureStorage.getActiveWallet()
        if (address == null) {
            _uiState.update { it.copy(isLoading = false, error = "No wallet found") }
            return
        }
        currentAddress = address
        loadPortfolio(address)
    }

    private fun loadPortfolio(address: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // First, fetch real prices
                fetchRealPrices()
                
                val portfolioTokens = mutableListOf<PortfolioToken>()
                var totalUsd = BigDecimal.ZERO

                for (tokenInfo in supportedTokens) {
                    val balance = if (tokenInfo.contractAddress.isEmpty()) {
                        // Native MAS token
                        getMasBalance(address)
                    } else {
                        // ERC20 token
                        getTokenBalance(address, tokenInfo.contractAddress, tokenInfo.decimals)
                    }

                    if (balance > BigDecimal.ZERO) {
                        // Get real price if available, otherwise use default
                        val usdPrice = realPrices[tokenInfo.symbol] ?: tokenInfo.defaultPrice
                        val usdValue = balance.multiply(usdPrice)
                        totalUsd = totalUsd.add(usdValue)

                        portfolioTokens.add(
                            PortfolioToken(
                                symbol = tokenInfo.symbol,
                                name = tokenInfo.name,
                                contractAddress = tokenInfo.contractAddress,
                                decimals = tokenInfo.decimals,
                                balance = balance,
                                balanceFormatted = formatBalance(balance, tokenInfo.decimals),
                                usdValue = usdValue,
                                usdPrice = usdPrice,
                                percentage = 0.0, // Will calculate after we have total
                                color = tokenInfo.color
                            )
                        )
                    }
                }

                // Calculate percentages
                val tokensWithPercentage = portfolioTokens.map { token ->
                    val percentage = if (totalUsd > BigDecimal.ZERO) {
                        token.usdValue.divide(totalUsd, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal("100"))
                            .toDouble()
                    } else {
                        0.0
                    }
                    token.copy(percentage = percentage)
                }.sortedByDescending { it.usdValue }

                _uiState.update {
                    if (currentAddress != address) {
                        it
                    } else {
                        it.copy(
                            tokens = tokensWithPercentage,
                            totalUsdValue = totalUsd,
                            isLoading = false
                        )
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("PortfolioVM", "Error loading portfolio", e)
                _uiState.update {
                    if (currentAddress != address) {
                        it
                    } else {
                        it.copy(isLoading = false, error = "Failed to load portfolio")
                    }
                }
            }
        }
    }
    
    /**
     * Fetch real token prices from CoinPaprika (for MAS) and calculate others relative to USDC
     */
    private suspend fun fetchRealPrices() {
        try {
            // Get MAS price from CoinPaprika via our PriceRepository
            // Use firstOrNull with catch to avoid AbortFlowException
            val result = priceRepository.getMassaStats()
                .catch { e -> 
                    android.util.Log.e("PortfolioVM", "Flow error: ${e.message}")
                    // Don't emit, just log and flow completes empty
                }
                .firstOrNull()
            if (result is Result.Success) {
                val masPrice = BigDecimal(result.data.price.toString())
                realPrices["MAS"] = masPrice
                realPrices["WMAS"] = masPrice // Same as MAS
                android.util.Log.d("PortfolioVM", "Real MAS price from CoinPaprika: $masPrice")
            }
            
            // Stablecoins are fixed at $1
            realPrices["USDC.e"] = BigDecimal("1.0")
            realPrices["DAI.e"] = BigDecimal("1.0")
            
            // For WETH, fetch from a public API or use a reasonable estimate
            fetchEthPrice()?.let { ethPrice ->
                realPrices["WETH.e"] = ethPrice
                android.util.Log.d("PortfolioVM", "Real ETH price: $ethPrice")
            }
            
            // For DUSA, we could query the DEX for DUSA/USDC price
            // For now, use default (DEX price query would require significant additional code)
            
            // For MC token, calculate price based on MC-MAS pool
            fetchMCPrice()?.let { mcPrice ->
                realPrices["MC"] = mcPrice
                android.util.Log.d("PortfolioVM", "Real MC price: $mcPrice")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("PortfolioVM", "Error fetching real prices: ${e.message}")
            // Prices will fall back to defaults
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
            android.util.Log.e("PortfolioVM", "Error fetching ETH price: ${e.message}")
            null
        }
    }
    
    /**
     * Fetch MC token price from DUSA DEX
     * MC price = (MAS in pool / MC in pool) * MAS price
     */
    private suspend fun fetchMCPrice(): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            val masPrice = realPrices["MAS"] ?: return@withContext null
            
            // MC-WMAS pool address
            val poolAddress = "AS1tnZc9iWKrE77on7sVqZ5FqaHw81jVXjLChVT8vnmyxBKRRE93"
            
            // Query pool reserves using getReserves function
            val requestBody = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "execute_read_only_call")
                put("params", JSONArray().apply {
                    put(JSONArray().apply {
                        put(JSONObject().apply {
                            put("max_gas", 2100000)
                            put("target_address", poolAddress)
                            put("target_function", "getReserves")
                            put("parameter", JSONArray()) // No parameters needed
                        })
                    })
                })
            }.toString()
            
            val request = Request.Builder()
                .url("https://mainnet.massa.net/api/v2")
                .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val json = JSONObject(body)
                    val result = json.optJSONArray("result")?.optJSONObject(0) ?: return@withContext null
                    val resultData = result.optJSONObject("result")?.optJSONArray("Ok") ?: return@withContext null
                    
                    // Parse reserves - typically returns two U256 values (reserveX, reserveY)
                    // Each U256 is 32 bytes
                    if (resultData.length() >= 64) {
                        val bytes = ByteArray(resultData.length()) { resultData.getInt(it).toByte() }
                        
                        // First 32 bytes = reserveX (WMAS), next 32 bytes = reserveY (MC)
                        val reserveXBytes = bytes.sliceArray(0 until 32)
                        val reserveYBytes = bytes.sliceArray(32 until 64)
                        
                        // Convert little-endian bytes to BigInteger
                        val reserveX = BigInteger(1, reserveXBytes.reversedArray())
                        val reserveY = BigInteger(1, reserveYBytes.reversedArray())
                        
                        if (reserveY > BigInteger.ZERO && reserveX > BigInteger.ZERO) {
                            // WMAS has 9 decimals, MC has 18 decimals
                            val wmasReserve = BigDecimal(reserveX).divide(BigDecimal("1000000000"), 18, RoundingMode.HALF_UP)
                            val mcReserve = BigDecimal(reserveY).divide(BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP)
                            
                            // MC price = (WMAS reserve / MC reserve) * MAS price
                            val mcPrice = wmasReserve.divide(mcReserve, 18, RoundingMode.HALF_UP).multiply(masPrice)
                            android.util.Log.d("PortfolioVM", "MC Pool - WMAS: $wmasReserve, MC: $mcReserve, MC Price: $mcPrice")
                            return@withContext mcPrice
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("PortfolioVM", "Error fetching MC price: ${e.message}")
            null
        }
    }

    private suspend fun getMasBalance(address: String): BigDecimal {
        return when (val result = massaRepository.getAddressBalance(address)) {
            is Result.Success -> {
                result.data?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            }
            else -> BigDecimal.ZERO
        }
    }

    private suspend fun getTokenBalance(
        userAddress: String,
        contractAddress: String,
        decimals: Int
    ): BigDecimal = withContext(Dispatchers.IO) {
        try {
            // Build parameter for balanceOf: string(address)
            val buffer = java.io.ByteArrayOutputStream()
            val addrBytes = userAddress.toByteArray(Charsets.UTF_8)
            val lenBytes = java.nio.ByteBuffer.allocate(4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
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
                                    val balanceUnits = decodeU256FromJsonArray(okArray)
                                    val balance = balanceUnits.toBigDecimal()
                                        .divide(BigDecimal.TEN.pow(decimals), 18, RoundingMode.HALF_UP)
                                    return@withContext balance
                                }
                            }
                        }
                    }
                }
            }
            BigDecimal.ZERO
        } catch (e: Exception) {
            android.util.Log.e("PortfolioVM", "Error getting token balance for $contractAddress", e)
            BigDecimal.ZERO
        }
    }

    private fun decodeU256FromJsonArray(jsonArray: JSONArray): BigInteger {
        if (jsonArray.length() < 32) {
            // Try to decode as smaller int
            val bytes = ByteArray(jsonArray.length())
            for (i in 0 until jsonArray.length()) {
                bytes[i] = (jsonArray.getInt(i) and 0xFF).toByte()
            }
            return BigInteger(1, bytes.reversedArray())
        }
        
        val bytes = ByteArray(32)
        for (i in 0 until minOf(32, jsonArray.length())) {
            bytes[i] = (jsonArray.getInt(i) and 0xFF).toByte()
        }
        return BigInteger(1, bytes.reversedArray())
    }

    private fun formatBalance(balance: BigDecimal, decimals: Int): String {
        return when {
            balance >= BigDecimal("1000000") -> {
                String.format("%.2fM", balance.divide(BigDecimal("1000000")).toDouble())
            }
            balance >= BigDecimal("1000") -> {
                String.format("%,.2f", balance.toDouble())
            }
            balance >= BigDecimal("1") -> {
                String.format("%.4f", balance.toDouble())
            }
            balance > BigDecimal.ZERO -> {
                String.format("%.6f", balance.toDouble())
            }
            else -> "0"
        }
    }

    fun refresh() {
        loadPortfolio()
    }
}
