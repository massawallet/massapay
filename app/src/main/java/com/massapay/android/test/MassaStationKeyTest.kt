package com.massapay.android.test

import android.util.Log
import com.massapay.android.security.wallet.WalletManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quick test utility to validate Massa Station imported keys
 * Can be called from any debug screen or startup
 */
@Singleton
class MassaStationKeyTest @Inject constructor(
    private val walletManager: WalletManager
) {
    
    /**
     * Test importing keys from Massa Station
     * P: P11FHwL29KgDuwqn9ojfQ99wspqYAEYqhXywL4G5eqGNPK72uQ5
     * S1: S12FjradPXbF8CAQanNzJcTi4rFUSnJQQA9kzN4xYfH65LPvFPnB
     */
    fun testMassaStationKeys() {
        val publicKeyMassaStation = "P11FHwL29KgDuwqn9ojfQ99wspqYAEYqhXywL4G5eqGNPK72uQ5"
        val privateKeyMassaStation = "S12FjradPXbF8CAQanNzJcTi4rFUSnJQQA9kzN4xYfH65LPvFPnB"
        
        Log.d("MassaStationKeyTest", "=== TESTING MASSA STATION KEYS ===")
        
        try {
            // Test 1: Import from S1 and derive address
            val importedAddr = walletManager.importFromS1PrivateKey(privateKeyMassaStation)
            Log.d("MassaStationKeyTest", "✅ Imported from S1")
            Log.d("MassaStationKeyTest", "   Address: ${importedAddr.address}")
            Log.d("MassaStationKeyTest", "   Public Key: ${importedAddr.publicKey}")
            
            // Test 2: Verify that derived public key matches provided P key
            val rawPub = walletManager.decodePPublicKey(publicKeyMassaStation)
            val derivedPub = walletManager.decodePPublicKey(importedAddr.publicKey)
            val pubKeyMatch = rawPub.contentEquals(derivedPub)
            Log.d("MassaStationKeyTest", "Public key match: $pubKeyMatch")
            
            if (!pubKeyMatch) {
                Log.e("MassaStationKeyTest", "❌ Provided P and derived P do NOT match!")
                Log.e("MassaStationKeyTest", "   Provided P raw: ${rawPub.joinToString("") { "%02x".format(it) }}")
                Log.e("MassaStationKeyTest", "   Derived P raw: ${derivedPub.joinToString("") { "%02x".format(it) }}")
            } else {
                Log.d("MassaStationKeyTest", "✅ Public keys match perfectly")
            }
            
            // Test 3: Get private key bytes for signing
            val privKeyBytes = walletManager.getPrivateKeyFromS1(privateKeyMassaStation)
            Log.d("MassaStationKeyTest", "Private key for signing (hex): ${privKeyBytes.joinToString("") { "%02x".format(it) }}")
            
            Log.d("MassaStationKeyTest", "=== TEST COMPLETE ===")
            Log.d("MassaStationKeyTest", "NEXT STEP: Use importedAddr.address and privKeyBytes.toHex() for transactions")
            
        } catch (e: Exception) {
            Log.e("MassaStationKeyTest", "❌ Test failed: ${e.message}", e)
        }
    }
    
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
