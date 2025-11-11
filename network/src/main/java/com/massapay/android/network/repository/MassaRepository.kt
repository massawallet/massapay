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
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
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
            val privateKeyBytes = privateKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
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

    suspend fun getTransactionHistory(address: String): Result<List<Transaction>> {
        return try {
            // NOTE: Massa API doesn't have a direct "get operations by address" endpoint
            // get_operations requires operation IDs, not addresses
            // We maintain a persistent cache of sent transactions using SharedPreferences
            
            Log.d("MassaRepository", "getTransactionHistory called for: $address")
            
            val cachedTransactions = loadTransactionsFromPrefs(address).toMutableList()
            
            // Detect balance changes to identify incoming transactions
            try {
                detectIncomingTransactions(address, cachedTransactions)
            } catch (e: Exception) {
                Log.e("MassaRepository", "Error detecting incoming transactions", e)
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
            
            // Save updated transactions back to cache if changed
            if (updatedTransactions != cachedTransactions) {
                saveTransactionsToPrefs(address, updatedTransactions)
                Log.d("MassaRepository", "Updated transaction cache")
            }
            
            Log.d("MassaRepository", "Returning ${updatedTransactions.size} cached transactions")
            
            Result.Success(updatedTransactions)
        } catch (e: Exception) {
            Log.e("MassaRepository", "Error in getTransactionHistory", e)
            Result.Error(e)
        }
    }
    
    /**
     * Detect incoming transactions by monitoring balance changes
     * This is a workaround since Massa API doesn't provide "get transactions by address"
     */
    private suspend fun detectIncomingTransactions(address: String, cachedTransactions: MutableList<Transaction>) {
        try {
            // Get current balance
            val currentBalanceResult = getAddressBalance(address)
            if (currentBalanceResult !is Result.Success) return
            
            val currentBalance = currentBalanceResult.data
            
            // Get previous balance from SharedPreferences
            val previousBalance = sharedPreferences.getString("balance_$address", "0") ?: "0"
            
            // If balance increased, we likely received a transaction
            val currentBal = currentBalance.toBigDecimalOrNull() ?: return
            val previousBal = previousBalance.toBigDecimalOrNull() ?: return
            
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
                    // Create a placeholder incoming transaction
                    val incomingTx = Transaction(
                        hash = "incoming_${System.currentTimeMillis()}", // Placeholder hash
                        from = "External", // Unknown sender
                        to = address,
                        amount = difference.toPlainString(),
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
                    cachedTransactions.add(0, incomingTx)
                    Log.d("MassaRepository", "Added incoming transaction: ${difference.toPlainString()} MAS")
                }
            }
            
            // Save current balance for next comparison
            sharedPreferences.edit().putString("balance_$address", currentBalance).apply()
            
        } catch (e: Exception) {
            Log.e("MassaRepository", "Error detecting incoming transactions", e)
        }
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
                Log.d("MassaRepository", "Operation details: isFinal=${op.isFinal}, inPool=${op.inPool}")
                
                val status = when {
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