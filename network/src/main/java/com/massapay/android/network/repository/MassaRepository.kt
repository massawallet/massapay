package com.massapay.android.network.repository

import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.massapay.android.core.model.Token
import com.massapay.android.core.model.Transaction
import com.massapay.android.core.model.WalletAddress
import com.massapay.android.core.util.Result
import com.massapay.android.network.api.MassaApi
import com.massapay.android.network.model.JsonRpcRequest
import com.massapay.android.network.model.OperationResponse
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MassaRepository @Inject constructor(
    private val massaApi: MassaApi,
    private val sharedPreferences: SharedPreferences,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val gson = Gson()
    private val MAX_CACHED_TRANSACTIONS = 50
    
    companion object {
        private const val PREF_KEY_PREFIX = "transactions_"
        private const val NANO_MAS_DECIMALS = 9 // 1 MAS = 10^9 nanoMAS
    }

    suspend fun getOperation(operationId: String): Result<OperationResponse?> {
        return try {
            val request = JsonRpcRequest(
                method = "get_operations",
                params = listOf(listOf(operationId))
            )
            val response = massaApi.getOperations(request)
            response.error?.let { return Result.Error(Exception(it.message)) }
            Result.Success(response.result?.firstOrNull())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun waitForOperationExecution(
        operationId: String,
        timeoutMs: Long = 45_000L,
        pollMs: Long = 1_500L
    ): Result<OperationResponse?> {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            when (val res = getOperation(operationId)) {
                is Result.Success -> {
                    val op = res.data
                    if (op?.opExecStatus != null) return Result.Success(op)
                }
                is Result.Error -> return res
                is Result.Loading -> {
                    // ignore
                }
            }
            delay(pollMs)
        }
        return Result.Success(null)
    }
    
    /**
     * Convert nanoMAS to MAS for display
     * 
     * DApps like EagleFi send values in nanoMAS (1 MAS = 10^9 nanoMAS)
     * We detect this by checking if the value is unreasonably large for MAS
     * 
     * Examples:
     * - 100000000 nanoMAS = 0.1 MAS
     * - 1000000000 nanoMAS = 1 MAS  
     * - 133000000000 nanoMAS = 133 MAS
     * 
     * Threshold: If value > 10000 (assuming no one sends more than 10K MAS in a single tx),
     * then it's likely in nanoMAS
     */
    private fun normalizeAmountForDisplay(amount: String): String {
        return try {
            val value = amount.toBigDecimalOrNull() ?: return amount
            // If value is greater than 10000, it's likely in nanoMAS
            // 10000 MAS would be 10000000000000 nanoMAS, so this is a safe threshold
            if (value > java.math.BigDecimal("10000")) {
                val masValue = value.divide(java.math.BigDecimal("1000000000"))
                masValue.stripTrailingZeros().toPlainString()
            } else {
                amount
            }
        } catch (e: Exception) {
            amount
        }
    }
    suspend fun getAddressBalance(address: String): Result<String> {
        return try {
            val request = JsonRpcRequest(
                method = "get_addresses",
                params = listOf(listOf(address))
            )
            val response = massaApi.getAddresses(request)
            response.error?.let {
                return Result.Error(Exception(it.message))
            }
            response.result?.firstOrNull()?.let { addressInfo ->
                // Use finalBalance (confirmed) or candidateBalance (pending) from Massa API v2
                val balance = addressInfo.finalBalance 
                    ?: addressInfo.candidateBalance 
                    ?: addressInfo.balance 
                    ?: "0"
                Result.Success(balance)
            } ?: Result.Error(Exception("No balance found"))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getNetworkStatus(): Result<String> {
        return try {
            val request = JsonRpcRequest(
                method = "get_status",
                params = listOf()
            )
            val response = massaApi.getStatus(request)
            response.error?.let {
                return Result.Error(Exception(it.message))
            }
            response.result?.let {
                Result.Success(it.chainId)
            } ?: Result.Error(Exception("Network status unavailable"))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun sendTransaction(
        from: String,
        to: String,
        amount: String,
        fee: String,
        privateKey: String,
        publicKey: String
    ): Result<String> {
        return try {
            android.util.Log.d("MassaRepository", "=== Starting transaction ===")
            android.util.Log.d("MassaRepository", "From: $from, To: $to, Amount: $amount, Fee: $fee")
            
            val statusRequest = JsonRpcRequest(
                method = "get_status",
                params = emptyList<Any>()
            )
            val statusResponse = massaApi.getStatus(statusRequest)
            statusResponse.error?.let {
                android.util.Log.e("MassaRepository", "Status error: ${it.message}")
                return Result.Error(Exception("Failed to fetch network status: ${it.message}"))
            }

            val chainId = statusResponse.result?.chainId?.toLongOrNull()
                ?: return Result.Error(Exception("Network status missing chain id"))

            val nextPeriod = statusResponse.result?.nextSlot?.period
            val expirePeriod = nextPeriod?.plus(10)
                ?: return Result.Error(Exception("Network status missing slot information"))

            android.util.Log.d("MassaRepository", "ChainId: $chainId, ExpirePeriod: $expirePeriod")

            // 1. Serialize Operation (spec-compliant ordering), with legacy fallback if needed
            // Spec ordering per Massa docs:
            // expiration_period (varint) + fee (varint) + type (varint=0) + amount (varint) + destination_hash (32 bytes)
            var operationBytes = serializeMassaOperationSpec(expirePeriod, fee, to, amount)
            android.util.Log.d("MassaRepository", "1. Operation serialized [spec] (${operationBytes.size} bytes): ${operationBytes.joinToString("") { "%02x".format(it) }}")

            // 2. Derive public key from private key
            // Support both hex format and S1 (base58) format
            val privateKeyBytes = if (privateKey.startsWith("S")) {
                decodeBase58PrivateKey(privateKey)
            } else {
                privateKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            val privateKeyParams = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
            val publicKeyRaw = privateKeyParams.generatePublicKey().encoded
            val publicKeyBase58 = encodePublicKeyBase58(publicKeyRaw)
            android.util.Log.d("MassaRepository", "2. Public key derived: $publicKeyBase58")

            // Helper to sign and send once
            suspend fun signAndSend(opBytes: ByteArray): Result<String> {
                // Build message to hash: chainId (u64 BE) + publicKey (versioned) + serialized_content
                // Per massa-web3 canonicalize(): [...networkId, ...publicKeyBytes, ...data]
                // PublicKey.toBytes() includes version byte (0) + raw key (32 bytes) = 33 bytes
                // ChainId is Big Endian (view.setBigUint64(0, chainId, false))
                val chainIdBytesBE = java.nio.ByteBuffer.allocate(8)
                    .order(java.nio.ByteOrder.BIG_ENDIAN)  // Changed to BIG_ENDIAN
                    .putLong(chainId)
                    .array()

                // Create versioned public key: version (1 byte) + raw key (32 bytes)
                val publicKeyVersioned = ByteArray(33)
                publicKeyVersioned[0] = 0  // Version 0
                System.arraycopy(publicKeyRaw, 0, publicKeyVersioned, 1, 32)

                val messageToHash = java.io.ByteArrayOutputStream()
                messageToHash.write(chainIdBytesBE)
                messageToHash.write(publicKeyVersioned)  // Add versioned public key to message
                messageToHash.write(opBytes)
                val fullMessage = messageToHash.toByteArray()

                android.util.Log.d("MassaRepository", "3. Message to hash (${fullMessage.size} bytes):")
                android.util.Log.d("MassaRepository", "   ChainId BE: ${chainIdBytesBE.joinToString("") { "%02x".format(it) }}")
                android.util.Log.d("MassaRepository", "   PublicKey (versioned): ${publicKeyVersioned.joinToString("") { "%02x".format(it) }}")
                android.util.Log.d("MassaRepository", "   Full message: ${fullMessage.joinToString("") { "%02x".format(it) }}")

                // Hash and sign
                val blake3Hash = hashWithBlake3(fullMessage)
                android.util.Log.d("MassaRepository", "4. BLAKE3 hash (32 bytes): ${blake3Hash.joinToString("") { "%02x".format(it) }}")

                val signer = Ed25519Signer()
                signer.init(true, privateKeyParams)
                signer.update(blake3Hash, 0, blake3Hash.size)
                val signature = signer.generateSignature()
                android.util.Log.d("MassaRepository", "5. Ed25519 signature (${signature.size} bytes): ${signature.joinToString("") { "%02x".format(it) }}")

                val signatureBase58 = encodeSignatureBase58(signature)
                android.util.Log.d("MassaRepository", "6. Signature Base58: $signatureBase58")

                val serializedContent = opBytes.map { it.toInt() and 0xFF }
                android.util.Log.d("MassaRepository", "7. Serialized content array length: ${serializedContent.size}")

                val operation = mapOf(
                    "creator_public_key" to publicKeyBase58,
                    "signature" to signatureBase58,
                    "serialized_content" to serializedContent
                )

                android.util.Log.d("MassaRepository", "8. Sending operation to Massa node...")

                val request = JsonRpcRequest(
                    method = "send_operations",
                    params = listOf(listOf(operation))
                )
                val response = massaApi.sendOperation(request)

                android.util.Log.d("MassaRepository", "9. Response: error=${response.error}, result=${response.result}")

                response.error?.let {
                    return Result.Error(Exception(it.message))
                }
                response.result?.let { operationIds ->
                    // The API returns a list of operation IDs
                    val operationId = operationIds.firstOrNull() ?: return Result.Error(Exception("No operation ID returned"))
                    android.util.Log.i("MassaRepository", "✅ Transaction successful! Operation ID: $operationId")
                    return Result.Success(operationId)
                }
                return Result.Error(Exception("Transaction failed"))
            }

            // Try spec ordering first
            var result: Result<String> = signAndSend(operationBytes)
            if (result is Result.Error) {
                android.util.Log.w("MassaRepository", "Spec serialization failed: ${result.exception.message}. Retrying with legacy ordering...")
                val legacyBytes = serializeMassaOperationLegacy(fee, expirePeriod, to, amount)
                android.util.Log.d("MassaRepository", "1b. Operation serialized [legacy] (${legacyBytes.size} bytes): ${legacyBytes.joinToString("") { "%02x".format(it) }}")
                result = signAndSend(legacyBytes)
            }
            if (result is Result.Success) {
                android.util.Log.d("MassaRepository", "✅ Transaction SUCCESS: ${result.data}")
                
                // Add transaction to cache for history display
                val transaction = Transaction(
                    hash = result.data,
                    from = from,
                    to = to,
                    amount = amount,
                    token = Token(
                        address = "",
                        symbol = "MAS",
                        decimals = 18,
                        name = "Massa"
                    ),
                    timestamp = System.currentTimeMillis(),
                    status = com.massapay.android.core.model.TransactionStatus.PENDING,
                    fee = fee
                )
                addTransactionToCache(from, transaction)
                android.util.Log.d("MassaRepository", "Transaction added to cache for address: $from")
                
                return Result.Success(result.data)
            } else if (result is Result.Error) {
                android.util.Log.e("MassaRepository", "Transaction error: ${result.exception.message}")
                return Result.Error(result.exception)
            } else {
                return Result.Error(Exception("Unexpected result state"))
            }
        } catch (e: Exception) {
            android.util.Log.e("MassaRepository", "Transaction exception: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Call a smart contract (execute a function on a contract)
     * Used for DeFi operations like swaps, staking, etc.
     * 
     * Operation type 4 = CALL_SC in Massa protocol
     */
    suspend fun callSmartContract(
        from: String,
        targetAddress: String,
        functionName: String,
        parameter: String?,
        coins: String,
        fee: String,
        maxGas: String?,
        privateKey: String,
        publicKey: String
    ): Result<String> {
        return try {
            android.util.Log.d("MassaRepository", "=== Starting Smart Contract Call ===")
            android.util.Log.d("MassaRepository", "From: $from, Target: $targetAddress, Function: $functionName")
            android.util.Log.d("MassaRepository", "Coins: $coins, Fee: $fee, MaxGas: $maxGas")
            
            // Get network status for chain ID and expiration period
            val statusRequest = JsonRpcRequest(
                method = "get_status",
                params = emptyList<Any>()
            )
            val statusResponse = massaApi.getStatus(statusRequest)
            statusResponse.error?.let {
                android.util.Log.e("MassaRepository", "Status error: ${it.message}")
                return Result.Error(Exception("Failed to fetch network status: ${it.message}"))
            }

            val chainId = statusResponse.result?.chainId?.toLongOrNull()
                ?: return Result.Error(Exception("Network status missing chain id"))

            val nextPeriod = statusResponse.result?.nextSlot?.period
            val expirePeriod = nextPeriod?.plus(10)
                ?: return Result.Error(Exception("Network status missing slot information"))

            android.util.Log.d("MassaRepository", "ChainId: $chainId, ExpirePeriod: $expirePeriod")

            // Serialize the CallSC operation
            val operationBytes = serializeCallSCOperation(
                expirePeriod = expirePeriod,
                fee = fee,
                targetAddress = targetAddress,
                functionName = functionName,
                parameter = parameter,
                coins = coins,
                maxGas = maxGas ?: "100000000" // Default 100M gas
            )
            android.util.Log.d("MassaRepository", "CallSC serialized (${operationBytes.size} bytes)")

            // Derive public key from private key
            // Support both hex format and S1 (base58) format
            val privateKeyBytes = if (privateKey.startsWith("S")) {
                decodeBase58PrivateKey(privateKey)
            } else {
                privateKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            val privateKeyParams = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
            val publicKeyRaw = privateKeyParams.generatePublicKey().encoded
            val publicKeyBase58 = encodePublicKeyBase58(publicKeyRaw)
            android.util.Log.d("MassaRepository", "Public key derived: $publicKeyBase58")

            // Build message to hash: chainId (u64 BE) + publicKey (versioned) + serialized_content
            val chainIdBytesBE = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.BIG_ENDIAN)
                .putLong(chainId)
                .array()

            // Create versioned public key: version (1 byte) + raw key (32 bytes)
            val publicKeyVersioned = ByteArray(33)
            publicKeyVersioned[0] = 0  // Version 0
            System.arraycopy(publicKeyRaw, 0, publicKeyVersioned, 1, 32)

            val messageToHash = java.io.ByteArrayOutputStream()
            messageToHash.write(chainIdBytesBE)
            messageToHash.write(publicKeyVersioned)
            messageToHash.write(operationBytes)
            val fullMessage = messageToHash.toByteArray()

            // Hash and sign
            val blake3Hash = hashWithBlake3(fullMessage)
            android.util.Log.d("MassaRepository", "BLAKE3 hash computed")

            val signer = Ed25519Signer()
            signer.init(true, privateKeyParams)
            signer.update(blake3Hash, 0, blake3Hash.size)
            val signature = signer.generateSignature()
            android.util.Log.d("MassaRepository", "Signature generated")

            val signatureBase58 = encodeSignatureBase58(signature)
            val serializedContent = operationBytes.map { it.toInt() and 0xFF }

            val operation = mapOf(
                "creator_public_key" to publicKeyBase58,
                "signature" to signatureBase58,
                "serialized_content" to serializedContent
            )

            android.util.Log.d("MassaRepository", "Sending CallSC operation to Massa node...")

            val request = JsonRpcRequest(
                method = "send_operations",
                params = listOf(listOf(operation))
            )
            val response = massaApi.sendOperation(request)

            android.util.Log.d("MassaRepository", "Response: error=${response.error}, result=${response.result}")

            response.error?.let {
                android.util.Log.e("MassaRepository", "CallSC error: ${it.message}")
                return Result.Error(Exception(it.message))
            }
            
            response.result?.let { operationIds ->
                val operationId = operationIds.firstOrNull() ?: return Result.Error(Exception("No operation ID returned"))
                android.util.Log.i("MassaRepository", "✅ CallSC successful! Operation ID: $operationId")
                
                // Add to transaction cache (normalize amount for display)
                val transaction = Transaction(
                    hash = operationId,
                    from = from,
                    to = targetAddress,
                    amount = normalizeAmountForDisplay(coins),
                    token = Token(
                        address = "",
                        symbol = "MAS",
                        decimals = 18,
                        name = "Massa"
                    ),
                    timestamp = System.currentTimeMillis(),
                    status = com.massapay.android.core.model.TransactionStatus.PENDING,
                    fee = normalizeAmountForDisplay(fee)
                )
                addTransactionToCache(from, transaction)
                
                return Result.Success(operationId)
            }
            
            Result.Error(Exception("Smart contract call failed"))
        } catch (e: Exception) {
            android.util.Log.e("MassaRepository", "CallSC exception: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Buy Rolls for staking
     * Operation type 1 = RollBuy in Massa protocol
     * 1 Roll = 100 MAS
     */
    suspend fun buyRolls(
        from: String,
        rollCount: Int,
        fee: String,
        privateKey: String,
        publicKey: String
    ): Result<String> {
        return try {
            android.util.Log.d("MassaRepository", "=== Starting Buy Rolls ===")
            android.util.Log.d("MassaRepository", "From: $from, RollCount: $rollCount, Fee: $fee")

            // Get network status for chain ID and expiration period
            val statusRequest = JsonRpcRequest(
                method = "get_status",
                params = emptyList<Any>()
            )
            val statusResponse = massaApi.getStatus(statusRequest)
            statusResponse.error?.let {
                android.util.Log.e("MassaRepository", "Status error: ${it.message}")
                return Result.Error(Exception("Failed to fetch network status: ${it.message}"))
            }

            val chainId = statusResponse.result?.chainId?.toLongOrNull()
                ?: return Result.Error(Exception("Network status missing chain id"))

            val nextPeriod = statusResponse.result?.nextSlot?.period
            val expirePeriod = nextPeriod?.plus(10)
                ?: return Result.Error(Exception("Network status missing slot information"))

            android.util.Log.d("MassaRepository", "ChainId: $chainId, ExpirePeriod: $expirePeriod")

            // Serialize the RollBuy operation
            val operationBytes = serializeRollBuyOperation(
                expirePeriod = expirePeriod,
                fee = fee,
                rollCount = rollCount
            )
            android.util.Log.d("MassaRepository", "RollBuy serialized (${operationBytes.size} bytes)")

            // Derive public key from private key
            val privateKeyBytes = if (privateKey.startsWith("S")) {
                decodeBase58PrivateKey(privateKey)
            } else {
                privateKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            val privateKeyParams = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
            val publicKeyRaw = privateKeyParams.generatePublicKey().encoded
            val publicKeyBase58 = encodePublicKeyBase58(publicKeyRaw)
            android.util.Log.d("MassaRepository", "Public key derived: $publicKeyBase58")

            // Build message to hash: chainId (u64 BE) + publicKey (versioned) + serialized_content
            val chainIdBytesBE = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.BIG_ENDIAN)
                .putLong(chainId)
                .array()

            // Create versioned public key: version (1 byte) + raw key (32 bytes)
            val publicKeyVersioned = ByteArray(33)
            publicKeyVersioned[0] = 0  // Version 0
            System.arraycopy(publicKeyRaw, 0, publicKeyVersioned, 1, 32)

            val messageToHash = java.io.ByteArrayOutputStream()
            messageToHash.write(chainIdBytesBE)
            messageToHash.write(publicKeyVersioned)
            messageToHash.write(operationBytes)
            val fullMessage = messageToHash.toByteArray()

            // Hash and sign
            val blake3Hash = hashWithBlake3(fullMessage)
            android.util.Log.d("MassaRepository", "BLAKE3 hash computed")

            val signer = Ed25519Signer()
            signer.init(true, privateKeyParams)
            signer.update(blake3Hash, 0, blake3Hash.size)
            val signature = signer.generateSignature()
            android.util.Log.d("MassaRepository", "Signature generated")

            val signatureBase58 = encodeSignatureBase58(signature)
            val serializedContent = operationBytes.map { it.toInt() and 0xFF }

            val operation = mapOf(
                "creator_public_key" to publicKeyBase58,
                "signature" to signatureBase58,
                "serialized_content" to serializedContent
            )

            android.util.Log.d("MassaRepository", "Sending RollBuy operation to Massa node...")

            val request = JsonRpcRequest(
                method = "send_operations",
                params = listOf(listOf(operation))
            )
            val response = massaApi.sendOperation(request)

            android.util.Log.d("MassaRepository", "Response: error=${response.error}, result=${response.result}")

            response.error?.let {
                android.util.Log.e("MassaRepository", "RollBuy error: ${it.message}")
                return Result.Error(Exception(it.message))
            }

            response.result?.let { operationIds ->
                val operationId = operationIds.firstOrNull() ?: return Result.Error(Exception("No operation ID returned"))
                android.util.Log.i("MassaRepository", "✅ RollBuy successful! Operation ID: $operationId")
                return Result.Success(operationId)
            }

            Result.Error(Exception("Buy Rolls failed"))
        } catch (e: Exception) {
            android.util.Log.e("MassaRepository", "RollBuy exception: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Sell Rolls to get MAS back
     * Operation type 2 = RollSell in Massa protocol
     * Note: There's a delay of ~3 cycles before MAS is available (deferred credits)
     */
    suspend fun sellRolls(
        from: String,
        rollCount: Int,
        fee: String,
        privateKey: String,
        publicKey: String
    ): Result<String> {
        return try {
            android.util.Log.d("MassaRepository", "=== Starting Sell Rolls ===")
            android.util.Log.d("MassaRepository", "From: $from, RollCount: $rollCount, Fee: $fee")

            // Get network status for chain ID and expiration period
            val statusRequest = JsonRpcRequest(
                method = "get_status",
                params = emptyList<Any>()
            )
            val statusResponse = massaApi.getStatus(statusRequest)
            statusResponse.error?.let {
                android.util.Log.e("MassaRepository", "Status error: ${it.message}")
                return Result.Error(Exception("Failed to fetch network status: ${it.message}"))
            }

            val chainId = statusResponse.result?.chainId?.toLongOrNull()
                ?: return Result.Error(Exception("Network status missing chain id"))

            val nextPeriod = statusResponse.result?.nextSlot?.period
            val expirePeriod = nextPeriod?.plus(10)
                ?: return Result.Error(Exception("Network status missing slot information"))

            android.util.Log.d("MassaRepository", "ChainId: $chainId, ExpirePeriod: $expirePeriod")

            // Serialize the RollSell operation
            val operationBytes = serializeRollSellOperation(
                expirePeriod = expirePeriod,
                fee = fee,
                rollCount = rollCount
            )
            android.util.Log.d("MassaRepository", "RollSell serialized (${operationBytes.size} bytes)")

            // Derive public key from private key
            val privateKeyBytes = if (privateKey.startsWith("S")) {
                decodeBase58PrivateKey(privateKey)
            } else {
                privateKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            val privateKeyParams = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
            val publicKeyRaw = privateKeyParams.generatePublicKey().encoded
            val publicKeyBase58 = encodePublicKeyBase58(publicKeyRaw)
            android.util.Log.d("MassaRepository", "Public key derived: $publicKeyBase58")

            // Build message to hash: chainId (u64 BE) + publicKey (versioned) + serialized_content
            val chainIdBytesBE = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.BIG_ENDIAN)
                .putLong(chainId)
                .array()

            // Create versioned public key: version (1 byte) + raw key (32 bytes)
            val publicKeyVersioned = ByteArray(33)
            publicKeyVersioned[0] = 0  // Version 0
            System.arraycopy(publicKeyRaw, 0, publicKeyVersioned, 1, 32)

            val messageToHash = java.io.ByteArrayOutputStream()
            messageToHash.write(chainIdBytesBE)
            messageToHash.write(publicKeyVersioned)
            messageToHash.write(operationBytes)
            val fullMessage = messageToHash.toByteArray()

            // Hash and sign
            val blake3Hash = hashWithBlake3(fullMessage)
            android.util.Log.d("MassaRepository", "BLAKE3 hash computed")

            val signer = Ed25519Signer()
            signer.init(true, privateKeyParams)
            signer.update(blake3Hash, 0, blake3Hash.size)
            val signature = signer.generateSignature()
            android.util.Log.d("MassaRepository", "Signature generated")

            val signatureBase58 = encodeSignatureBase58(signature)
            val serializedContent = operationBytes.map { it.toInt() and 0xFF }

            val operation = mapOf(
                "creator_public_key" to publicKeyBase58,
                "signature" to signatureBase58,
                "serialized_content" to serializedContent
            )

            android.util.Log.d("MassaRepository", "Sending RollSell operation to Massa node...")

            val request = JsonRpcRequest(
                method = "send_operations",
                params = listOf(listOf(operation))
            )
            val response = massaApi.sendOperation(request)

            android.util.Log.d("MassaRepository", "Response: error=${response.error}, result=${response.result}")

            response.error?.let {
                android.util.Log.e("MassaRepository", "RollSell error: ${it.message}")
                return Result.Error(Exception(it.message))
            }

            response.result?.let { operationIds ->
                val operationId = operationIds.firstOrNull() ?: return Result.Error(Exception("No operation ID returned"))
                android.util.Log.i("MassaRepository", "✅ RollSell successful! Operation ID: $operationId")
                return Result.Success(operationId)
            }

            Result.Error(Exception("Sell Rolls failed"))
        } catch (e: Exception) {
            android.util.Log.e("MassaRepository", "RollSell exception: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Execute bytecode on the Massa blockchain
     * Operation type 3 = EXECUTE_SC in Massa protocol
     * 
     * This is used by DApps like EagleFi for complex operations like swaps
     * where pre-compiled bytecode needs to be executed directly.
     */
    suspend fun executeBytecode(
        from: String,
        bytecode: String,
        datastore: String?,
        coins: String,
        fee: String,
        maxGas: String?,
        privateKey: String,
        publicKey: String
    ): Result<String> {
        return try {
            android.util.Log.d("MassaRepository", "=== Starting Execute Bytecode ===")
            android.util.Log.d("MassaRepository", "From: $from, Bytecode length: ${bytecode.length}")
            android.util.Log.d("MassaRepository", "Coins: $coins, Fee: $fee, MaxGas: $maxGas")

            // Get network status for chain ID and expiration period
            val statusRequest = JsonRpcRequest(
                method = "get_status",
                params = emptyList<Any>()
            )
            val statusResponse = massaApi.getStatus(statusRequest)
            statusResponse.error?.let {
                android.util.Log.e("MassaRepository", "Status error: ${it.message}")
                return Result.Error(Exception("Failed to fetch network status: ${it.message}"))
            }

            val chainId = statusResponse.result?.chainId?.toLongOrNull()
                ?: return Result.Error(Exception("Network status missing chain id"))

            val nextPeriod = statusResponse.result?.nextSlot?.period
            val expirePeriod = nextPeriod?.plus(10)
                ?: return Result.Error(Exception("Network status missing slot information"))

            android.util.Log.d("MassaRepository", "ChainId: $chainId, ExpirePeriod: $expirePeriod")

            // Serialize the ExecuteSC operation
            val operationBytes = serializeExecuteSCOperation(
                expirePeriod = expirePeriod,
                fee = fee,
                bytecode = bytecode,
                datastore = datastore,
                coins = coins,
                maxGas = maxGas ?: "500000000" // Default 500M gas for bytecode execution
            )
            android.util.Log.d("MassaRepository", "ExecuteSC serialized (${operationBytes.size} bytes)")

            // Derive public key from private key
            val privateKeyBytes = if (privateKey.startsWith("S")) {
                decodeBase58PrivateKey(privateKey)
            } else {
                privateKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            val privateKeyParams = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
            val publicKeyRaw = privateKeyParams.generatePublicKey().encoded
            val publicKeyBase58 = encodePublicKeyBase58(publicKeyRaw)
            android.util.Log.d("MassaRepository", "Public key derived: $publicKeyBase58")

            // Build message to hash: chainId (u64 BE) + publicKey (versioned) + serialized_content
            val chainIdBytesBE = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.BIG_ENDIAN)
                .putLong(chainId)
                .array()

            // Create versioned public key: version (1 byte) + raw key (32 bytes)
            val publicKeyVersioned = ByteArray(33)
            publicKeyVersioned[0] = 0  // Version 0
            System.arraycopy(publicKeyRaw, 0, publicKeyVersioned, 1, 32)

            val messageToHash = java.io.ByteArrayOutputStream()
            messageToHash.write(chainIdBytesBE)
            messageToHash.write(publicKeyVersioned)
            messageToHash.write(operationBytes)
            val fullMessage = messageToHash.toByteArray()

            // Hash and sign
            val blake3Hash = hashWithBlake3(fullMessage)
            android.util.Log.d("MassaRepository", "BLAKE3 hash computed")

            val signer = Ed25519Signer()
            signer.init(true, privateKeyParams)
            signer.update(blake3Hash, 0, blake3Hash.size)
            val signature = signer.generateSignature()
            android.util.Log.d("MassaRepository", "Signature generated")

            val signatureBase58 = encodeSignatureBase58(signature)
            val serializedContent = operationBytes.map { it.toInt() and 0xFF }

            val operation = mapOf(
                "creator_public_key" to publicKeyBase58,
                "signature" to signatureBase58,
                "serialized_content" to serializedContent
            )

            android.util.Log.d("MassaRepository", "Sending ExecuteSC operation to Massa node...")

            val request = JsonRpcRequest(
                method = "send_operations",
                params = listOf(listOf(operation))
            )
            val response = massaApi.sendOperation(request)

            android.util.Log.d("MassaRepository", "Response: error=${response.error}, result=${response.result}")

            response.error?.let {
                android.util.Log.e("MassaRepository", "ExecuteSC error: ${it.message}")
                return Result.Error(Exception(it.message))
            }
            
            response.result?.let { operationIds ->
                val operationId = operationIds.firstOrNull() ?: return Result.Error(Exception("No operation ID returned"))
                android.util.Log.i("MassaRepository", "✅ ExecuteSC successful! Operation ID: $operationId")
                
                // Add to transaction cache (normalize amount for display)
                val transaction = Transaction(
                    hash = operationId,
                    from = from,
                    to = "Bytecode Execution",
                    amount = normalizeAmountForDisplay(coins),
                    token = Token(
                        address = "",
                        symbol = "MAS",
                        decimals = 18,
                        name = "Massa"
                    ),
                    timestamp = System.currentTimeMillis(),
                    status = com.massapay.android.core.model.TransactionStatus.PENDING,
                    fee = normalizeAmountForDisplay(fee)
                )
                addTransactionToCache(from, transaction)
                
                return Result.Success(operationId)
            }
            
            Result.Error(Exception("Execute bytecode failed"))
        } catch (e: Exception) {
            android.util.Log.e("MassaRepository", "ExecuteSC exception: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Serialize an ExecuteSC (Execute Smart Contract bytecode) operation
     * 
     * Operation format:
     * - fee (varint): fee in nanoMAS
     * - expire_period (varint): when the operation expires
     * - op_type (varint): 3 for ExecuteSC
     * - max_gas (varint): maximum gas for execution
     * - bytecode_length (varint): length of bytecode
     * - bytecode (bytes): compiled bytecode
     * - datastore_length (varint): number of datastore entries
     * - datastore entries: key-value pairs
     */
    private fun serializeExecuteSCOperation(
        expirePeriod: Long,
        fee: String,
        bytecode: String,
        datastore: String?,
        coins: String,
        maxGas: String
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()

        // fee (already in nanoMAS)
        val feeNanos = fee.toLongOrNull() ?: 10000000L
        writeVarint(output, feeNanos)

        // expiration_period
        writeVarint(output, expirePeriod)

        // type = 3 (ExecuteSC)
        writeVarint(output, 3L)

        // max_gas
        val maxGasValue = maxGas.toLongOrNull() ?: 500000000L
        writeVarint(output, maxGasValue)

        // coins (in nanoMAS)
        val coinsNanos = coins.toLongOrNull() ?: 0L
        writeVarint(output, coinsNanos)

        // Bytecode - convert from hex/base64 to bytes
        val bytecodeBytes = try {
            if (bytecode.startsWith("[") && bytecode.endsWith("]")) {
                // JSON array format: [1,2,3,...]
                val jsonArray = org.json.JSONArray(bytecode)
                ByteArray(jsonArray.length()) { i ->
                    (jsonArray.getInt(i) and 0xFF).toByte()
                }
            } else if (bytecode.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                // Hex format
                bytecode.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } else {
                // Try Base64
                android.util.Base64.decode(bytecode, android.util.Base64.DEFAULT)
            }
        } catch (e: Exception) {
            android.util.Log.e("MassaRepository", "Failed to decode bytecode: ${e.message}")
            bytecode.toByteArray(Charsets.UTF_8)
        }
        
        writeVarint(output, bytecodeBytes.size.toLong())
        output.write(bytecodeBytes)

        // Datastore entries
        if (datastore != null && datastore.isNotEmpty() && datastore != "null" && datastore != "[]") {
            try {
                val datastoreArray = org.json.JSONArray(datastore)
                writeVarint(output, datastoreArray.length().toLong())
                for (i in 0 until datastoreArray.length()) {
                    val entry = datastoreArray.getJSONObject(i)
                    val key = entry.optString("key", "")
                    val value = entry.optString("value", "")
                    
                    val keyBytes = if (key.startsWith("[")) {
                        val arr = org.json.JSONArray(key)
                        ByteArray(arr.length()) { j -> (arr.getInt(j) and 0xFF).toByte() }
                    } else {
                        key.toByteArray(Charsets.UTF_8)
                    }
                    
                    val valueBytes = if (value.startsWith("[")) {
                        val arr = org.json.JSONArray(value)
                        ByteArray(arr.length()) { j -> (arr.getInt(j) and 0xFF).toByte() }
                    } else {
                        value.toByteArray(Charsets.UTF_8)
                    }
                    
                    writeVarint(output, keyBytes.size.toLong())
                    output.write(keyBytes)
                    writeVarint(output, valueBytes.size.toLong())
                    output.write(valueBytes)
                }
            } catch (e: Exception) {
                android.util.Log.e("MassaRepository", "Failed to parse datastore: ${e.message}")
                writeVarint(output, 0L) // Empty datastore
            }
        } else {
            writeVarint(output, 0L) // Empty datastore
        }

        return output.toByteArray()
    }

    /**
     * Serialize a CallSC (Call Smart Contract) operation
     * 
     * Operation format:
     * - fee (varint): fee in nanoMAS
     * - expire_period (varint): when the operation expires
     * - op_type (varint): 4 for CallSC
     * - max_gas (varint): maximum gas for execution
     * - coins (varint): MAS to send with the call
     * - target_address (34 bytes): contract address
     * - function_name_length (varint): length of function name
     * - function_name (bytes): function name UTF-8
     * - param_length (varint): length of parameter
     * - param (bytes): parameter data
     */
    private fun serializeCallSCOperation(
        expirePeriod: Long,
        fee: String,
        targetAddress: String,
        functionName: String,
        parameter: String?,
        coins: String,
        maxGas: String
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()

        // fee (already in nanoMAS from DApp/Bearby)
        val feeNanos = fee.toLongOrNull() ?: 10000000L
        writeVarint(output, feeNanos)

        // expiration_period
        writeVarint(output, expirePeriod)

        // type = 4 (CallSC)
        writeVarint(output, 4L)

        // max_gas
        val maxGasValue = maxGas.toLongOrNull() ?: 100000000L
        writeVarint(output, maxGasValue)

        // coins (already in nanoMAS from DApp/Bearby)
        // DApps like Dusa send coins already in nanoMAS format
        val coinsNanos = coins.toLongOrNull() ?: 0L
        writeVarint(output, coinsNanos)

        // Target address: 34 bytes (addressType + version + hash)
        val targetBytes = decodeBase58Address(targetAddress)
        require(targetBytes.size == 34) { "Target address must be 34 bytes" }
        output.write(targetBytes)

        // Function name (length-prefixed)
        val functionNameBytes = functionName.toByteArray(Charsets.UTF_8)
        writeVarint(output, functionNameBytes.size.toLong())
        output.write(functionNameBytes)

        // Parameter (length-prefixed)
        val paramBytes = if (parameter != null && parameter.isNotEmpty()) {
            // Parameter can be:
            // 1. JSON object with numeric keys like {"0":1,"1":2,...} (unsafeParameters from Bearby)
            // 2. JSON array like [1,2,3,...]
            // 3. Base64 encoded
            // 4. Hex encoded
            // 5. Raw UTF-8
            try {
                // First try to parse as JSON object with numeric keys (unsafeParameters format)
                if (parameter.startsWith("{") && parameter.contains(":")) {
                    val jsonObj = org.json.JSONObject(parameter)
                    val keys = jsonObj.keys().asSequence().toList()
                    if (keys.isNotEmpty() && keys.all { it.toIntOrNull() != null }) {
                        // It's a JSON object with numeric keys - convert to byte array
                        val maxIndex = keys.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: -1
                        val bytes = ByteArray(maxIndex + 1)
                        for (key in keys) {
                            val index = key.toIntOrNull() ?: continue
                            bytes[index] = jsonObj.getInt(key).toByte()
                        }
                        android.util.Log.d("MassaRepository", "Parameter parsed as JSON object with ${bytes.size} bytes")
                        bytes
                    } else {
                        // Regular JSON object - encode as UTF-8
                        parameter.toByteArray(Charsets.UTF_8)
                    }
                }
                // Try as JSON array
                else if (parameter.startsWith("[")) {
                    val jsonArray = org.json.JSONArray(parameter)
                    val bytes = ByteArray(jsonArray.length())
                    for (i in 0 until jsonArray.length()) {
                        bytes[i] = jsonArray.getInt(i).toByte()
                    }
                    android.util.Log.d("MassaRepository", "Parameter parsed as JSON array with ${bytes.size} bytes")
                    bytes
                }
                // Try as Base64
                else {
                    try {
                        val decoded = android.util.Base64.decode(parameter, android.util.Base64.DEFAULT)
                        android.util.Log.d("MassaRepository", "Parameter parsed as Base64 with ${decoded.size} bytes")
                        decoded
                    } catch (e: Exception) {
                        // Try as hex
                        try {
                            val decoded = parameter.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            android.util.Log.d("MassaRepository", "Parameter parsed as Hex with ${decoded.size} bytes")
                            decoded
                        } catch (e2: Exception) {
                            // Use as raw UTF-8
                            android.util.Log.d("MassaRepository", "Parameter parsed as UTF-8")
                            parameter.toByteArray(Charsets.UTF_8)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MassaRepository", "Error parsing parameter: ${e.message}")
                ByteArray(0)
            }
        } else {
            ByteArray(0)
        }
        android.util.Log.d("MassaRepository", "Final parameter bytes: ${paramBytes.size} bytes")
        writeVarint(output, paramBytes.size.toLong())
        output.write(paramBytes)

        return output.toByteArray()
    }

    /**
     * Serialize a RollBuy operation
     * 
     * Operation format:
     * - fee (varint): fee in nanoMAS
     * - expire_period (varint): when the operation expires
     * - op_type (varint): 1 for RollBuy
     * - roll_count (varint): number of rolls to buy
     */
    private fun serializeRollBuyOperation(
        expirePeriod: Long,
        fee: String,
        rollCount: Int
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()

        // fee (in nanoMAS)
        val feeNanos = masToNano(fee)
        writeVarint(output, feeNanos)

        // expiration_period
        writeVarint(output, expirePeriod)

        // type = 1 (RollBuy)
        writeVarint(output, 1L)

        // roll_count
        writeVarint(output, rollCount.toLong())

        return output.toByteArray()
    }

    /**
     * Serialize a RollSell operation
     * 
     * Operation format:
     * - fee (varint): fee in nanoMAS
     * - expire_period (varint): when the operation expires
     * - op_type (varint): 2 for RollSell
     * - roll_count (varint): number of rolls to sell
     */
    private fun serializeRollSellOperation(
        expirePeriod: Long,
        fee: String,
        rollCount: Int
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()

        // fee (in nanoMAS)
        val feeNanos = masToNano(fee)
        writeVarint(output, feeNanos)

        // expiration_period
        writeVarint(output, expirePeriod)

        // type = 2 (RollSell)
        writeVarint(output, 2L)

        // roll_count
        writeVarint(output, rollCount.toLong())

        return output.toByteArray()
    }

    suspend fun getTransactionHistory(address: String): Result<List<Transaction>> {
        return try {
            // NOTE: Massa API doesn't have a direct "get operations by address" endpoint
            // get_operations requires operation IDs, not addresses
            // We maintain a persistent cache of sent transactions using SharedPreferences
            
            Log.d("MassaRepository", "getTransactionHistory called for: $address")
            
            val cachedTransactions = loadTransactionsFromPrefs(address).toMutableList()
            
            // Detect balance changes to identify incoming transactions
            val incomingDetected = try {
                detectIncomingTransactions(address, cachedTransactions)
            } catch (e: Exception) {
                Log.e("MassaRepository", "Error detecting incoming transactions", e)
                false
            }
            
            // Update status of pending transactions
            val updatedTransactions = cachedTransactions.map { transaction ->
                if (transaction.status == com.massapay.android.core.model.TransactionStatus.PENDING) {
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val status = getOperationStatus(transaction.hash, transaction.timestamp)
                            if (status != transaction.status) {
                                Log.d("MassaRepository", "Transaction ${transaction.hash} status changed: ${transaction.status} -> $status")
                            }
                            transaction.copy(status = status)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Log.w("MassaRepository", "Status check cancelled for ${transaction.hash}, keeping current status")
                        transaction // Keep current status if cancelled
                    } catch (e: Exception) {
                        Log.e("MassaRepository", "Error updating status for ${transaction.hash}", e)
                        transaction // Return unchanged on error
                    }
                } else {
                    transaction
                }
            }
            
            val sortedTransactions = updatedTransactions
                .distinctBy { it.hash }
                .sortedByDescending { it.timestamp }
                .take(MAX_CACHED_TRANSACTIONS)

            // Save updated transactions back to cache if changed, including newly detected incoming txs.
            if (incomingDetected || sortedTransactions != loadTransactionsFromPrefs(address)) {
                saveTransactionsToPrefs(address, sortedTransactions)
                Log.d("MassaRepository", "Updated transaction cache")
            }
            
            Log.d("MassaRepository", "Returning ${sortedTransactions.size} cached transactions")
            
            Result.Success(sortedTransactions)
        } catch (e: Exception) {
            Log.e("MassaRepository", "Error in getTransactionHistory", e)
            Result.Error(e)
        }
    }
    
    /**
     * Detect incoming transactions by monitoring balance changes
     * This is a workaround since Massa API doesn't provide "get transactions by address"
     */
    private suspend fun detectIncomingTransactions(address: String, cachedTransactions: MutableList<Transaction>): Boolean {
        return try {
            // Get current balance
            val currentBalanceResult = getAddressBalance(address)
            if (currentBalanceResult !is Result.Success) return false
            
            val currentBalance = currentBalanceResult.data
            
            // Get previous balance from SharedPreferences. If this is the first read,
            // store the baseline and avoid creating a fake incoming transaction.
            val previousBalance = sharedPreferences.getString("balance_$address", null)
            if (previousBalance == null) {
                sharedPreferences.edit().putString("balance_$address", currentBalance).apply()
                Log.d("MassaRepository", "Stored initial balance baseline for $address: $currentBalance")
                return false
            }
            
            var addedIncoming = false

            // If balance increased, we likely received a transaction
            val currentBal = currentBalance.toBigDecimalOrNull() ?: return false
            val previousBal = previousBalance.toBigDecimalOrNull() ?: return false
            
            if (currentBal > previousBal) {
                val difference = currentBal.subtract(previousBal)
                Log.d("MassaRepository", "Balance increased by $difference. Previous: $previousBal, Current: $currentBal")
                
                // Check if we already have a recent incoming transaction with this amount
                val recentIncoming = cachedTransactions.find { tx ->
                    tx.to == address && 
                    tx.amount == difference.toPlainString() &&
                    System.currentTimeMillis() - tx.timestamp < 3600000 // Within last hour
                }
                
                if (recentIncoming == null) {
                    val incomingTx = createIncomingTransaction(address, difference.toPlainString())
                    cachedTransactions.add(0, incomingTx)
                    addedIncoming = true
                    Log.d("MassaRepository", "Added incoming transaction: ${difference.toPlainString()} MAS")
                }
            }
            
            // Save current balance for next comparison
            sharedPreferences.edit().putString("balance_$address", currentBalance).apply()
            addedIncoming
        } catch (e: Exception) {
            Log.e("MassaRepository", "Error detecting incoming transactions", e)
            false
        }
    }

    fun recordIncomingTransaction(address: String, amount: String): Transaction? {
        val transactions = loadTransactionsFromPrefs(address).toMutableList()
        val alreadyExists = transactions.any { tx ->
            tx.to == address &&
                tx.amount == amount &&
                tx.hash.startsWith("incoming_") &&
                System.currentTimeMillis() - tx.timestamp < 3600000
        }

        if (alreadyExists) return null

        val transaction = createIncomingTransaction(address, amount)
        transactions.add(0, transaction)
        val updatedTransactions = transactions
            .distinctBy { it.hash }
            .sortedByDescending { it.timestamp }
            .take(MAX_CACHED_TRANSACTIONS)
        saveTransactionsToPrefs(address, updatedTransactions)
        Log.d("MassaRepository", "Recorded incoming transaction from UI balance delta: $amount MAS")
        return transaction
    }

    private fun createIncomingTransaction(address: String, amount: String): Transaction {
        return Transaction(
            hash = "incoming_${System.currentTimeMillis()}",
            from = "External",
            to = address,
            amount = amount,
            token = Token(
                address = "",
                symbol = "MAS",
                decimals = 18,
                name = "Massa"
            ),
            timestamp = System.currentTimeMillis(),
            status = com.massapay.android.core.model.TransactionStatus.CONFIRMED,
            fee = "0"
        )
    }
    
    /**
     * Get the status of an operation from Massa blockchain
     * 
     * IMPORTANT: Massa API's get_operations returns operations with different states:
     * - isFinal=true: Operation is finalized with enough confirmations → CONFIRMED
     * - inPool=true: Operation is in mempool waiting to be included → PENDING  
     * - isFinal=false, inPool=false: Operation is in a block but not yet final → PENDING (awaiting confirmations)
     * - Empty result: Old operation no longer in API memory → Infer from timestamp
     */
    private suspend fun getOperationStatus(operationId: String, timestamp: Long): com.massapay.android.core.model.TransactionStatus {
        return try {
            Log.d("MassaRepository", "Checking status for operation: $operationId")
            
            val request = JsonRpcRequest(
                method = "get_operations",
                params = listOf(listOf(operationId))
            )
            val response = massaApi.getOperations(request)
            
            Log.d("MassaRepository", "Operation status response: error=${response.error}, result size=${response.result?.size}")
            
            // Check for API errors
            if (response.error != null) {
                Log.w("MassaRepository", "API error for operation $operationId: ${response.error.message}")
                return com.massapay.android.core.model.TransactionStatus.FAILED
            }
            
            response.result?.firstOrNull()?.let { op ->
                Log.d(
                    "MassaRepository",
                    "Operation details: isFinal=${op.isFinal}, inPool=${op.inPool}, op_exec_status=${op.opExecStatus}, op_exec_error=${op.opExecError}"
                )
                
                val status = when {
                    op.isFinal && op.opExecStatus == false -> {
                        Log.w("MassaRepository", "Operation $operationId is FAILED (finalized, execution failed): ${op.opExecError}")
                        com.massapay.android.core.model.TransactionStatus.FAILED
                    }
                    op.isFinal -> {
                        Log.d("MassaRepository", "Operation $operationId is CONFIRMED (isFinal=true)")
                        com.massapay.android.core.model.TransactionStatus.CONFIRMED
                    }
                    op.inPool -> {
                        Log.d("MassaRepository", "Operation $operationId is PENDING (inPool=true)")
                        com.massapay.android.core.model.TransactionStatus.PENDING
                    }
                    // Operation exists in response but is not final and not in pool
                    // This means it's in a block but waiting for finalization
                    else -> {
                        Log.d("MassaRepository", "Operation $operationId is PENDING (in block, awaiting finality)")
                        com.massapay.android.core.model.TransactionStatus.PENDING
                    }
                }
                status
            } ?: run {
                // API returned empty array - this happens for old confirmed operations
                // Massa transactions finalize quickly, so 1 minute is enough
                val ageMinutes = (System.currentTimeMillis() - timestamp) / 60000
                
                if (ageMinutes > 1) {
                    Log.d("MassaRepository", "Operation $operationId not in API (age: ${ageMinutes}min). Assuming CONFIRMED (old transaction)")
                    com.massapay.android.core.model.TransactionStatus.CONFIRMED
                } else {
                    Log.w("MassaRepository", "Operation $operationId not found in API (age: ${ageMinutes}min). Marking as FAILED")
                    com.massapay.android.core.model.TransactionStatus.FAILED
                }
            }
        } catch (e: Exception) {
            Log.e("MassaRepository", "Error getting operation status for $operationId: ${e.message}", e)
            // If there's an exception but transaction is old, assume confirmed
            val ageMinutes = (System.currentTimeMillis() - timestamp) / 60000
            if (ageMinutes > 1) {
                com.massapay.android.core.model.TransactionStatus.CONFIRMED
            } else {
                com.massapay.android.core.model.TransactionStatus.FAILED
            }
        }
    }
    
    /**
     * Load transactions from SharedPreferences
     */
    private fun loadTransactionsFromPrefs(address: String): List<Transaction> {
        return try {
            val key = PREF_KEY_PREFIX + address
            val json = sharedPreferences.getString(key, null) ?: return emptyList()
            val type = object : TypeToken<List<Transaction>>() {}.type
            gson.fromJson<List<Transaction>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("MassaRepository", "Error loading transactions from prefs", e)
            emptyList()
        }
    }
    
    /**
     * Save transactions to SharedPreferences
     */
    private fun saveTransactionsToPrefs(address: String, transactions: List<Transaction>) {
        try {
            val key = PREF_KEY_PREFIX + address
            val json = gson.toJson(transactions)
            sharedPreferences.edit().putString(key, json).apply()
            Log.d("MassaRepository", "Saved ${transactions.size} transactions to SharedPreferences")
        } catch (e: Exception) {
            Log.e("MassaRepository", "Error saving transactions to prefs", e)
        }
    }
    
    /**
     * Add a transaction to the persistent cache
     */
    private fun addTransactionToCache(address: String, transaction: Transaction) {
        val transactions = loadTransactionsFromPrefs(address).toMutableList()
        
        // Add to beginning (most recent first)
        transactions.add(0, transaction)
        
        // Keep only the most recent transactions
        val trimmedTransactions = transactions.take(MAX_CACHED_TRANSACTIONS)
        
        // Save back to SharedPreferences
        saveTransactionsToPrefs(address, trimmedTransactions)
        
        Log.d("MassaRepository", "Added transaction to cache. Total: ${trimmedTransactions.size}")
    }

    /**
     * Calculate estimated transaction fee based on network conditions
     * Returns fee in nanoMAS (1 MAS = 1e9 nanoMAS)
     */
    suspend fun estimateTransactionFee(amount: String): Result<String> {
        return try {
            // Get current network status for fee estimation
            val statusRequest = JsonRpcRequest(
                method = "get_status",
                params = listOf()
            )
            val statusResponse = massaApi.getStatus(statusRequest)
            
            statusResponse.error?.let {
                // Fallback to base fee if status unavailable
                return Result.Success("100000000") // 0.1 MAS
            }
            
            // Base fee: 0.01 MAS (10000000 nanoMAS)
            // Add dynamic component based on amount (0.001% of amount)
            val baseFee = 10000000L
            val amountNano = amount.toBigDecimalOrNull()?.multiply(1000000000.toBigDecimal())?.toLong() ?: 0L
            val dynamicFee = (amountNano * 0.00001).toLong() // 0.001% of amount
            
            val totalFee = baseFee + dynamicFee
            Result.Success(totalFee.toString())
        } catch (e: Exception) {
            // Fallback to safe base fee
            Result.Success("100000000") // 0.1 MAS
        }
    }

    /**
     * Validate Massa address format and checksum
     * Massa addresses start with "AU" followed by Base58 encoded data
     */
    fun validateMassaAddress(address: String): Boolean {
        if (!address.startsWith("AU") || address.length < 40 || address.length > 55) {
            return false
        }
        
        return try {
            // Extract Base58 portion
            val base58Part = address.substring(2)
            
            // Verify it only contains valid Base58 characters
            val base58Alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
            base58Part.all { it in base58Alphabet }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encode bytes to Base58 (Bitcoin-style alphabet)
     */
    private fun encodeBase58(input: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger(1, input)
        val base = java.math.BigInteger.valueOf(58)
        val result = StringBuilder()
        
        while (num > java.math.BigInteger.ZERO) {
            val remainder = num.mod(base).toInt()
            result.insert(0, alphabet[remainder])
            num = num.divide(base)
        }
        
        // Add leading 1s for leading zero bytes
        for (b in input) {
            if (b.toInt() == 0) result.insert(0, '1')
            else break
        }
        
        return result.toString()
    }

    /**
     * Serialize Massa operation (legacy ordering kept for fallback)
     * Legacy Format: fee (varint) + expire_period (varint) + op_type (varint) + recipient (bytes, version+hash 33) + amount (varint)
     */
    private fun serializeMassaOperationLegacy(
        fee: String,
        expirePeriod: Long,
        recipient: String,
        amount: String
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        
        // fee (in nanoMAS)
        val feeNanos = masToNano(fee)
        writeVarint(output, feeNanos)
        
        // expiration_period
        writeVarint(output, expirePeriod)
        
        // type = 0 (Transaction)
        writeVarint(output, 0L)
        
        // Recipient address: 34 bytes (addressType + version + hash)
        val recipientBytes = decodeBase58Address(recipient)
        require(recipientBytes.size == 34) { "Recipient address must be 34 bytes (addressType + version + hash)" }
        output.write(recipientBytes)
        
        // amount (in nanoMAS)
        val amountNanos = masToNano(amount)
        writeVarint(output, amountNanos)
        
        return output.toByteArray()
    }

    /**
     * Massa operation serialization matching Bearby's PaymentBuild.bytes():
     * fee (varint) + expirePeriod (varint) + type (varint=0) + recipient (33 bytes) + amount (varint)
     */
    private fun serializeMassaOperationSpec(
        expirePeriod: Long,
        fee: String,
        recipient: String,
        amount: String
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()

        // fee (in nanoMAS)
        val feeNanos = masToNano(fee)
        writeVarint(output, feeNanos)
        
        // expiration_period
        writeVarint(output, expirePeriod)

        // type = 0 (Transaction)
        writeVarint(output, 0L)

        // Recipient address: 34 bytes (addressType + version + hash) matching massa-web3 Address.toBytes()
        val recipientBytes = decodeBase58Address(recipient)
        require(recipientBytes.size == 34) { "Recipient address must be 34 bytes (addressType + version + hash)" }
        output.write(recipientBytes)

        // amount (in nanoMAS)
        val amountNanos = masToNano(amount)
        writeVarint(output, amountNanos)

        return output.toByteArray()
    }

    /**
     * Serialize complete operation including signature and public key
     * This is what gets sent in serialized_content field
     */
    private fun serializeCompleteOperation(
        publicKey: String,
        fee: String,
        expirePeriod: Long,
        recipient: String,
        amount: String,
        signature: ByteArray
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        
        // Decode public key from Base58 (remove P prefix)
        val pubKeyBytes = decodeBase58PublicKey(publicKey)
        
        // Write public key (32 bytes)
        output.write(pubKeyBytes)
        
        // Write signature (64 bytes)
        output.write(signature)
        
        // Write operation data
    val feeNanos = masToNano(fee)
    writeVarint(output, feeNanos)
        
    writeVarint(output, expirePeriod) // expire_period
        
        val recipientBytes = decodeBase58Address(recipient)
        output.write(recipientBytes)
        
    val amountNanos = masToNano(amount)
    writeVarint(output, amountNanos)
        
        return output.toByteArray()
    }

    private fun decodeBase58PublicKey(publicKey: String): ByteArray {
        require(publicKey.startsWith("P")) { "Invalid public key prefix" }
        val base58Part = publicKey.substring(1)
        val decoded = decodeBase58(base58Part)
        require(decoded.size >= 33) { "Invalid decoded public key length" }

        // Skip version byte (first byte) and checksum (last 4 bytes)
        // Return only the 32-byte Ed25519 public key
        return decoded.copyOfRange(1, 33)
    }

    /**
     * Decode a Massa private key in S1 format (base58 encoded)
     * Format: S + base58(version + raw_32_bytes + checksum)
     * Returns the raw 32-byte Ed25519 private key seed
     */
    private fun decodeBase58PrivateKey(privateKeyS1: String): ByteArray {
        require(privateKeyS1.startsWith("S")) { "Invalid private key prefix (expected 'S')" }
        val base58Part = privateKeyS1.substring(1)
        val decoded = decodeBase58(base58Part)
        require(decoded.size >= 37) { "Invalid decoded private key length" }

        // decoded format: version(1) + raw_key(32) + checksum(4) = 37 bytes
        // Skip version byte (first byte) and checksum (last 4 bytes)
        return decoded.copyOfRange(1, 33)
    }

    private fun encodePublicKeyBase58(raw32: ByteArray): String {
        require(raw32.size == 32) { "Raw public key must be 32 bytes" }
        val version: Byte = 0x00
        val payload = ByteArray(1 + 32)
        payload[0] = version
        System.arraycopy(raw32, 0, payload, 1, 32)
        val sha = java.security.MessageDigest.getInstance("SHA-256")
        val h1 = sha.digest(payload)
        val h2 = sha.digest(h1)
        val checksum = h2.copyOfRange(0,4)
        val full = ByteArray(payload.size + 4)
        System.arraycopy(payload,0,full,0,payload.size)
        System.arraycopy(checksum,0,full,payload.size,4)
        return "P" + encodeBase58(full)
    }

    /**
     * Hash data with BLAKE3 (32-byte output)
     * Used for signing Massa operations
     */
    private fun hashWithBlake3(data: ByteArray): ByteArray {
        val digest = org.bouncycastle.crypto.digests.Blake3Digest()
        digest.update(data, 0, data.size)
        val hash = ByteArray(32)
        digest.doFinal(hash, 0)
        return hash
    }

    /**
     * Encode Ed25519 signature to Base58 with Massa format
     * Format: Base58(version (0x00) + signature (64 bytes) + checksum (4 bytes))
     * NO prefix "S" - that's only for display/storage
     */
    private fun encodeSignatureBase58(signature: ByteArray): String {
        require(signature.size == 64) { "Signature must be 64 bytes" }
        val version: Byte = 0x00
        val payload = ByteArray(65)
        payload[0] = version
        System.arraycopy(signature, 0, payload, 1, 64)
        
        val sha = java.security.MessageDigest.getInstance("SHA-256")
        val h1 = sha.digest(payload)
        val h2 = sha.digest(h1)
        val checksum = h2.copyOfRange(0, 4)
        
        val full = ByteArray(69)
        System.arraycopy(payload, 0, full, 0, 65)
        System.arraycopy(checksum, 0, full, 65, 4)
        
        return encodeBase58(full)
    }

    private fun decodeBase58(base58: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger.ZERO
        val base = java.math.BigInteger.valueOf(58)
        
        for (char in base58) {
            val digit = alphabet.indexOf(char)
            if (digit < 0) throw IllegalArgumentException("Invalid Base58 character: $char")
            num = num.multiply(base).add(java.math.BigInteger.valueOf(digit.toLong()))
        }
        
        var bytes = num.toByteArray()
        
        // Remove leading zero byte if present
        if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        
        // Add leading zeros for leading '1' characters
        val leadingOnes = base58.takeWhile { it == '1' }.length
        if (leadingOnes > 0) {
            val zeros = ByteArray(leadingOnes)
            bytes = zeros + bytes
        }
        
        return bytes
    }

    private fun writeVarint(output: java.io.ByteArrayOutputStream, value: Long) {
        var v = value
        while (v >= 0x80) {
            output.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        output.write(v.toInt())
    }

    private fun masToNano(value: String): Long {
        val decimals = java.math.BigDecimal(value)
        val nanos = decimals.multiply(java.math.BigDecimal("1000000000"))
        val nanosExact = nanos.stripTrailingZeros()
        require(nanosExact.scale() <= 0) { "Amount precision exceeds nanoMAS" }
        val asBigInt = nanosExact.toBigIntegerExact()
        require(asBigInt.bitLength() <= 63) { "Amount too large" }
        return asBigInt.longValueExact()
    }

    /**
     * Convert Massa address to bytes matching massa-web3 Address.toBytes() format.
     * 
     * Format: [addressType (1 byte)] + [version (1 byte)] + [hash (32 bytes)] = 34 bytes
     * - addressType: 0 for EOA (user addresses starting with AU), 1 for contracts (AS)
     * - version: 0 (current version)
     * - hash: 32-byte hash
     */
    private fun decodeBase58Address(address: String): ByteArray {
        require(address.length >= 3 && address.startsWith("A")) {
            "Invalid Massa address prefix"
        }

        val isEOA = address.startsWith("AU")
        val addressType: Byte = if (isEOA) 0 else 1

        val base58Part = address.substring(2)
        val decoded = decodeBase58(base58Part)
        require(decoded.size >= 37) { "Invalid decoded address length" }

        // decoded format: version(1) + hash(32) + checksum(4) = 37 bytes
        val version = decoded[0]  // Should be 0
        val hash = decoded.copyOfRange(1, 33)  // 32 bytes

        // Build format matching massa-web3 Address.toBytes():
        // [addressType] + [version] + [hash]
        val result = ByteArray(34)
        result[0] = addressType
        result[1] = version
        System.arraycopy(hash, 0, result, 2, 32)

        return result
    }

    /**
     * Export transaction history to JSON file
     * Returns the file path where the backup was saved
     * 
     * Format: {
     *   "address": "AU...",
     *   "exportedAt": 1699564800000,
     *   "transactions": [...]
     * }
     */
    suspend fun exportTransactionHistory(address: String): Result<String> {
        return try {
            val transactions = loadTransactionsFromPrefs(address)
            
            if (transactions.isEmpty()) {
                return Result.Error(Exception("No transaction history to export"))
            }
            
            // Create export data structure
            val exportData = mapOf(
                "address" to address,
                "exportedAt" to System.currentTimeMillis(),
                "transactionCount" to transactions.size,
                "transactions" to transactions
            )
            
            val json = gson.toJson(exportData)
            
            // Generate filename with timestamp
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val fileName = "massapay_backup_${address.take(8)}_$timestamp.json"
            
            // Save to app-specific external storage (no permissions needed)
            val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            
            file.writeText(json)
            
            Log.i("MassaRepository", "✅ Exported ${transactions.size} transactions to: ${file.absolutePath}")
            
            Result.Success(file.absolutePath)
        } catch (e: Exception) {
            Log.e("MassaRepository", "Error exporting transaction history", e)
            Result.Error(e)
        }
    }

    /**
     * Import transaction history from JSON file
     * Validates address match and merges without duplicates
     */
    suspend fun importTransactionHistory(currentAddress: String, jsonFilePath: String): Result<Int> {
        return try {
            val file = java.io.File(jsonFilePath)
            
            if (!file.exists()) {
                return Result.Error(Exception("Backup file not found"))
            }
            
            val json = file.readText()
            val importData = gson.fromJson(json, Map::class.java)
            
            // Validate address match
            val exportedAddress = importData["address"] as? String
            if (exportedAddress != currentAddress) {
                return Result.Error(Exception("Backup address mismatch. Expected: $currentAddress, Found: $exportedAddress"))
            }
            
            // Parse transactions
            val transactionsJson = gson.toJson(importData["transactions"])
            val type = object : TypeToken<List<Transaction>>() {}.type
            val importedTransactions = gson.fromJson<List<Transaction>>(transactionsJson, type)
                ?: return Result.Error(Exception("Invalid transaction data"))
            
            // Load existing transactions
            val existingTransactions = loadTransactionsFromPrefs(currentAddress).toMutableList()
            
            // Merge without duplicates (compare by hash)
            val existingHashes = existingTransactions.map { it.hash }.toSet()
            val newTransactions = importedTransactions.filter { it.hash !in existingHashes }
            
            // Combine and sort by timestamp (newest first)
            val mergedTransactions = (existingTransactions + newTransactions)
                .sortedByDescending { it.timestamp }
                .take(MAX_CACHED_TRANSACTIONS)
            
            // Save merged history
            saveTransactionsToPrefs(currentAddress, mergedTransactions)
            
            Log.i("MassaRepository", "✅ Imported ${newTransactions.size} new transactions (${importedTransactions.size} total in backup)")
            
            Result.Success(newTransactions.size)
        } catch (e: Exception) {
            Log.e("MassaRepository", "Error importing transaction history", e)
            Result.Error(e)
        }
    }
}
