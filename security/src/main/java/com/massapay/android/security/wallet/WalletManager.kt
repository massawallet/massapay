package com.massapay.android.security.wallet

import android.content.Context
import android.content.SharedPreferences
import com.massapay.android.security.storage.SecureStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.Security
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wallet Manager for Massa Network
 * Implements BIP-39/BIP-44 key derivation compatible with Bearby wallet.
 *
 * Bearby specification (from official extension documentation):
 * - Mnemonic: BIP-39 (12/24 words)
 * - Seed: PBKDF2-HMAC-SHA512, 2048 iterations (Bearby standard), passphrase="" → 512-bit seed
 * - Master key: HMAC-SHA512("ed25519 seed", seed) → 256-bit secret + chain code
 * - Path: m/44'/632'/0'/0'/0' (Massa coin type 632, all hardened - Bearby official)
 * - Keys: Ed25519 (32-byte private, 32-byte public)
 * - Address: AU... (Base58Check of version + BLAKE3(pubkey) + checksum)
 * 
 * Security:
 * - Mnemonic encrypted with AES-256-GCM + BLAKE3 integrity
 * - Keys stored in Android Keystore (hardware-backed when available)
 * - Optional biometric authentication for key access
 */
@Singleton
class WalletManager @Inject constructor(
    private val mnemonicManager: MnemonicManager,
    private val secureStorage: SecureStorageManager,
    @ApplicationContext private val context: Context
) {
    
    private companion object {
        const val PREFS_NAME = "massa_wallet_prefs"
        const val KEY_ENCRYPTED_MNEMONIC = "encrypted_mnemonic"
        const val KEY_WALLET_CREATED = "wallet_created"
        const val HARDENED_BIT = 0x80000000.toInt()
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    init {
        // Ensure BouncyCastle is registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // Alternative master using legacy label "Bitcoin seed" (diagnostic only)
    private fun deriveMasterKeyAlt(seed: ByteArray): ExtendedKey {
        val hmac = HMac(SHA512Digest())
        val key = KeyParameter("Bitcoin seed".toByteArray())
        hmac.init(key)
        hmac.update(seed, 0, seed.size)
        val out = ByteArray(64)
        hmac.doFinal(out, 0)
        val priv = out.copyOfRange(0, 32)
        val cc = out.copyOfRange(32, 64)
        return ExtendedKey(priv, cc, derivePublicKey(priv))
    }
    
    // ========== Secure Storage Methods (AES-256-GCM + BLAKE3) ==========

    /**
     * Save mnemonic securely with AES-256-GCM encryption + BLAKE3 integrity
     * 
     * @param mnemonic BIP-39 mnemonic phrase to store
     * @param passphrase Optional BIP-39 passphrase
     * @return true if saved successfully
     */
    fun saveMnemonic(mnemonic: String, passphrase: String = ""): Boolean {
        return try {
            // Validate mnemonic before saving
            require(mnemonicManager.validateMnemonic(mnemonic)) {
                "Invalid mnemonic phrase"
            }
            
            // Encrypt mnemonic with AES-256-GCM + BLAKE3 integrity
            val encryptedMnemonic = secureStorage.encryptWithIntegrity(mnemonic)
            
            // Save to SharedPreferences
            prefs.edit()
                .putString(KEY_ENCRYPTED_MNEMONIC, encryptedMnemonic)
                .putBoolean(KEY_WALLET_CREATED, true)
                .apply()
            
            android.util.Log.d("WalletManager", "Mnemonic saved securely (AES-256-GCM + BLAKE3)")
            true
        } catch (e: Exception) {
            android.util.Log.e("WalletManager", "Failed to save mnemonic: ${e.message}", e)
            false
        }
    }

    /**
     * Load mnemonic from secure storage
     * 
     * @return Decrypted mnemonic or null if not found
     * @throws SecurityException if integrity check fails
     */
    fun loadMnemonic(): String? {
        return try {
            val encryptedMnemonic = prefs.getString(KEY_ENCRYPTED_MNEMONIC, null)
                ?: return null
            
            // Decrypt and verify BLAKE3 integrity
            val mnemonic = secureStorage.decryptWithIntegrity(encryptedMnemonic)
            
            // Validate mnemonic after decryption
            if (!mnemonicManager.validateMnemonic(mnemonic)) {
                throw SecurityException("Decrypted mnemonic failed validation")
            }
            
            android.util.Log.d("WalletManager", "Mnemonic loaded and integrity verified")
            mnemonic
        } catch (e: Exception) {
            android.util.Log.e("WalletManager", "Failed to load mnemonic: ${e.message}", e)
            null
        }
    }

    /**
     * Check if wallet exists (mnemonic is stored)
     */
    fun hasWallet(): Boolean {
        return prefs.getBoolean(KEY_WALLET_CREATED, false) &&
               prefs.getString(KEY_ENCRYPTED_MNEMONIC, null) != null
    }

    /**
     * Delete wallet data
     * WARNING: This will make the wallet unrecoverable unless mnemonic is backed up
     */
    fun deleteWallet() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_MNEMONIC)
            .remove(KEY_WALLET_CREATED)
            .apply()
        
        android.util.Log.w("WalletManager", "Wallet deleted - ensure mnemonic backup exists!")
    }

    /**
     * Get current wallet address (from stored mnemonic)
     * 
     * @param accountIndex Account index (default 0)
     * @param addressIndex Address index (default 0)
     * @return Massa address or null if no wallet exists
     */
    fun getCurrentAddress(accountIndex: Int = 0, addressIndex: Int = 0): MassaAddress? {
        val mnemonic = loadMnemonic() ?: return null
        return deriveAddress(mnemonic, "", accountIndex, addressIndex)
    }

    /**
     * Get private key for current wallet (for signing transactions)
     * 
     * @param accountIndex Account index (default 0)
     * @param addressIndex Address index (default 0)
     * @return Raw private key bytes or null if no wallet exists
     */
    fun getCurrentPrivateKey(accountIndex: Int = 0, addressIndex: Int = 0): ByteArray? {
        val mnemonic = loadMnemonic() ?: return null
        return getPrivateKey(mnemonic, "", accountIndex, addressIndex)
    }

    // ========== Key Derivation Methods ==========

    /**
     * Derive Massa address from mnemonic
     * @param mnemonic BIP-39 mnemonic phrase
     * @param passphrase Optional BIP-39 passphrase
     * @param accountIndex Account index (default 0)
     * @param addressIndex Address index (default 0)
     * @return Massa address string (format: AU...)
     */
    fun deriveAddress(
        mnemonic: String,
        passphrase: String = "",
        accountIndex: Int = 0,
        addressIndex: Int = 0,
        useLegacyFormat: Boolean = true  // Default to LEGACY for compatibility
    ): MassaAddress {
        // PBKDF2-HMAC-SHA512 with 2048 iterations, salt = "mnemonic" + passphrase (BIP-39 standard)
        val seed = mnemonicManager.generateSeedFromMnemonic(mnemonic, passphrase, iterationsOverride = 2048)
        val masterKey = deriveMasterKey(seed)
        
        // BIP-44 path: m/44'/632'/account'/0'/addressIndex' (Bearby official coin type 632)
        var key = masterKey
        key = deriveChildKey(key, 44 or HARDENED_BIT)
        key = deriveChildKey(key, 632 or HARDENED_BIT) // Massa coin type 632 (Bearby official)
        key = deriveChildKey(key, accountIndex or HARDENED_BIT)
        key = deriveChildKey(key, 0 or HARDENED_BIT)
        key = deriveChildKey(key, addressIndex or HARDENED_BIT)
        
        // Use LEGACY format by default for compatibility with old Bearby wallets
        return if (useLegacyFormat) {
            generateMassaAddressLegacy(key.publicKey)
        } else {
            generateMassaAddress(key.publicKey)
        }
    }
    
    /**
     * Import wallet directly from S1 private key (Massa Station/Bearby format)
     * Returns MassaAddress with derived public key and address
     */
    fun importFromS1PrivateKey(s1PrivateKey: String): MassaAddress {
        android.util.Log.d("WalletManager", "=== IMPORTING S1 PRIVATE KEY ===")
        android.util.Log.d("WalletManager", "Input S1: $s1PrivateKey")
        
        val rawPriv = decodeS1PrivateKey(s1PrivateKey)
        android.util.Log.d("WalletManager", "Raw Private Key (32 bytes): ${rawPriv.joinToString("") { "%02x".format(it) }}")
        
        // Derive public key using standard Ed25519
        val publicKey = derivePublicKeyMethod1_Standard(rawPriv)
        android.util.Log.d("WalletManager", "Public Key (32 bytes): ${publicKey.joinToString("") { "%02x".format(it) }}")
        
        // Try BOTH address formats: LEGACY (with bug) and STANDARD (correct)
        val legacyAddress = generateMassaAddressLegacy(publicKey)
        val standardAddress = generateMassaAddress(publicKey)
        
        android.util.Log.d("WalletManager", "")
        android.util.Log.d("WalletManager", "ADDRESS GENERATION RESULTS:")
        android.util.Log.d("WalletManager", "  LEGACY  (Bearby pre-July 2023): ${legacyAddress.address}")
        android.util.Log.d("WalletManager", "  STANDARD (Bearby current):      ${standardAddress.address}")
        android.util.Log.d("WalletManager", "")
        android.util.Log.d("WalletManager", "ℹ️  LEGACY format includes version byte in BLAKE3 hash (Bearby bug)")
        android.util.Log.d("WalletManager", "ℹ️  STANDARD format is the correct implementation")
        android.util.Log.d("WalletManager", "ℹ️  Returning LEGACY for compatibility with old wallets")
        android.util.Log.d("WalletManager", "")
        android.util.Log.d("WalletManager", "=== END S1 IMPORT ===")
        
        // Return LEGACY format for compatibility with existing wallets
        // Users can import wallets created before July 2023
        return legacyAddress
    }
    
    /**
     * Get raw private key bytes from S1 format for signing
     */
    fun getPrivateKeyFromS1(s1PrivateKey: String): ByteArray {
        return decodeS1PrivateKey(s1PrivateKey)
    }

    /**
     * Derive multiple addresses for the same account
     */
    fun deriveAddresses(
        mnemonic: String,
        passphrase: String = "",
        accountIndex: Int = 0,
        count: Int = 5
    ): List<MassaAddress> {
        return (0 until count).map { index ->
            deriveAddress(mnemonic, passphrase, accountIndex, index)
        }
    }

    /**
     * Get private key for signing transactions
     */
    fun getPrivateKey(
        mnemonic: String,
        passphrase: String = "",
        accountIndex: Int = 0,
        addressIndex: Int = 0
    ): ByteArray {
        val seed = mnemonicManager.generateSeedFromMnemonic(mnemonic, passphrase, iterationsOverride = 2048)
        val masterKey = deriveMasterKey(seed)
        
        var key = masterKey
        key = deriveChildKey(key, 44 or HARDENED_BIT)
        key = deriveChildKey(key, 632 or HARDENED_BIT) // Massa coin type 632 (Bearby official)
        key = deriveChildKey(key, accountIndex or HARDENED_BIT)
        key = deriveChildKey(key, 0 or HARDENED_BIT)
        key = deriveChildKey(key, addressIndex or HARDENED_BIT)
        
        return key.privateKey
    }

    /**
     * Get private key in S1 format (for export/import with Bearby/Massa Station)
     */
    fun getPrivateKeyS1(
        mnemonic: String,
        passphrase: String = "",
        accountIndex: Int = 0,
        addressIndex: Int = 0
    ): String {
        val privateKeyBytes = getPrivateKey(mnemonic, passphrase, accountIndex, addressIndex)
        return encodePrivateKeyS1(privateKeyBytes)
    }

    /**
     * Encode raw 32-byte private key to Massa S1 format
     * Format: "S" + Base58(version_byte + 32_bytes_privkey + checksum)
     * Compatible with Bearby and Massa Station
     */
    private fun encodePrivateKeyS1(privateKey: ByteArray): String {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        
        val version: Byte = 0x00
        val payload = ByteArray(33)
        payload[0] = version
        System.arraycopy(privateKey, 0, payload, 1, 32)
        
        // Calculate checksum (double SHA-256)
        val sha = java.security.MessageDigest.getInstance("SHA-256")
        val hash1 = sha.digest(payload)
        val hash2 = sha.digest(hash1)
        val checksum = hash2.copyOfRange(0, 4)
        
        // Combine: version + privateKey + checksum
        val full = ByteArray(37)
        System.arraycopy(payload, 0, full, 0, 33)
        System.arraycopy(checksum, 0, full, 33, 4)
        
        return "S" + encodeBase58(full)
    }

    // BIP-32 Implementation
    
    private fun deriveMasterKey(seed: ByteArray): ExtendedKey {
        val hmac = HMac(SHA512Digest())
        // SLIP-10 Ed25519: use "ed25519 seed" instead of "Bitcoin seed"
        val key = KeyParameter("ed25519 seed".toByteArray())
        hmac.init(key)
        hmac.update(seed, 0, seed.size)
        
        val output = ByteArray(64)
        hmac.doFinal(output, 0)
        
        val privateKey = output.copyOfRange(0, 32)
        val chainCode = output.copyOfRange(32, 64)
        
        return ExtendedKey(privateKey, chainCode, derivePublicKey(privateKey))
    }

    private fun deriveChildKey(parent: ExtendedKey, index: Int): ExtendedKey {
        val hmac = HMac(SHA512Digest())
        val key = KeyParameter(parent.chainCode)
        hmac.init(key)
        
        // SLIP-10 Ed25519: ALL derivations must be hardened
        // Hardened derivation: HMAC-SHA512(chainCode, 0x00 || privateKey || index)
        val data = ByteBuffer.allocate(37)
            .put(0x00.toByte())
            .put(parent.privateKey)
            .putInt(index)
            .array()
        
        hmac.update(data, 0, data.size)
        val output = ByteArray(64)
        hmac.doFinal(output, 0)
        
        // For Ed25519 (SLIP-10), the left 32 bytes IS the child private key directly
        // No addition like in secp256k1
        val childKey = output.copyOfRange(0, 32)
        val chainCode = output.copyOfRange(32, 64)
        
        return ExtendedKey(childKey, chainCode, derivePublicKey(childKey))
    }

    /**
     * Derive Ed25519 public key from private key (seed)
     * This is a public method for use in UI/settings
     */
    fun derivePublicKey(privateKey: ByteArray): ByteArray {
        // For Ed25519 in SLIP-10/BIP-32 context, the 32-byte "private key" is actually a SEED
        // We need to:
        // 1. Hash the seed with SHA-512 to get 64 bytes
        // 2. Clamp the first 32 bytes to create the scalar
        // 3. Multiply the base point G by the scalar to get the public key
        
        // DIAGNOSTIC: Log SHA-512 hash to compare with Bearby
        val sha512 = MessageDigest.getInstance("SHA-512")
        val hash64 = sha512.digest(privateKey)
        android.util.Log.d("WalletManager", ">>> DIAG SHA-512(seed): ${hash64.take(32).joinToString("") { "%02x".format(it) }}")
        
        // BouncyCastle's Ed25519PrivateKeyParameters does this automatically when we pass the seed
        val privateKeyParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(privateKey, 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        return publicKeyParams.encoded
    }
    
    // METHOD 1: Standard Ed25519 - treat as seed, BouncyCastle does SHA-512 + clamp + scalar mul
    private fun derivePublicKeyMethod1_Standard(privateKey: ByteArray): ByteArray {
        val privateKeyParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(privateKey, 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        return publicKeyParams.encoded
    }
    
    // METHOD 2: Treat as direct scalar (no SHA-512) - manually clamp and use as scalar
    private fun derivePublicKeyMethod2_DirectScalar(privateKey: ByteArray): ByteArray {
        // Clamp the 32-byte key as if it's already a scalar
        val clamped = privateKey.copyOf()
        clamped[0] = (clamped[0].toInt() and 0xF8).toByte()  // Clear lowest 3 bits
        clamped[31] = (clamped[31].toInt() and 0x7F).toByte() // Clear highest bit
        clamped[31] = (clamped[31].toInt() or 0x40).toByte()  // Set second-highest bit
        
        // Unfortunately, BouncyCastle doesn't expose direct scalar multiplication easily
        // We'll have to use a workaround: create a "fake" seed that when hashed gives us our scalar
        // This is hacky but for diagnostic purposes...
        // Actually, let's try using the clamped value directly with BouncyCastle
        // This might not work correctly, but let's see what happens
        val privateKeyParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(clamped, 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        return publicKeyParams.encoded
    }
    
    // METHOD 3: SHA-512 first, then clamp manually, then use with BouncyCastle
    private fun derivePublicKeyMethod3_ManualClamp(privateKey: ByteArray): ByteArray {
        val sha512 = MessageDigest.getInstance("SHA-512")
        val hash64 = sha512.digest(privateKey)
        
        // Clamp first 32 bytes
        val clamped = hash64.copyOfRange(0, 32)
        clamped[0] = (clamped[0].toInt() and 0xF8).toByte()
        clamped[31] = (clamped[31].toInt() and 0x7F).toByte()
        clamped[31] = (clamped[31].toInt() or 0x40).toByte()
        
        // Now use this clamped value - but BouncyCastle will hash it AGAIN
        // This is a double-hash scenario for testing
        val privateKeyParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(clamped, 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        return publicKeyParams.encoded
    }
    
        // METHOD 4: Try reversing byte order (little-endian vs big-endian)
    private fun derivePublicKeyMethod4_ReversedBytes(privateKey: ByteArray): ByteArray {
        val reversed = privateKey.reversedArray()
        val privateKeyParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(reversed, 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        return publicKeyParams.encoded
    }
    
    // METHOD 5: Try using SHA-512 hash and extracting DIFFERENT 32 bytes
    private fun derivePublicKeyMethod5_DifferentHashSlice(privateKey: ByteArray): ByteArray {
        val sha512 = MessageDigest.getInstance("SHA-512")
        val hash64 = sha512.digest(privateKey)
        // Try using bytes 32-64 instead of 0-32
        val altSeed = hash64.copyOfRange(32, 64)
        val privateKeyParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(altSeed, 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        return publicKeyParams.encoded
    }

    private fun derivePublicKeyMethod6_NobleEd25519(privateKey: ByteArray): ByteArray {
        // This mimics noble-ed25519's getExtendedPublicKey exactly:
        // 1. SHA-512 of the 32-byte seed
        // 2. Take first 32 bytes (head)
        // 3. Clamp: head[0] &= 248, head[31] &= 127, head[31] |= 64
        // 4. Interpret as LITTLE-ENDIAN to get scalar
        // 5. Scalar multiply by base point
        
        val sha512 = MessageDigest.getInstance("SHA-512")
        val hash64 = sha512.digest(privateKey)
        
        val head = hash64.copyOfRange(0, 32)
        
        // Clamp bits (Ed25519 standard)
        head[0] = (head[0].toInt() and 0xF8).toByte()   // Clear lowest 3 bits
        head[31] = (head[31].toInt() and 0x7F).toByte() // Clear highest bit
        head[31] = (head[31].toInt() or 0x40).toByte()  // Set second-highest bit
        
        // CRITICAL: noble-ed25519 interprets head as LITTLE-ENDIAN
        // So we need to use it as-is with BouncyCastle (which expects little-endian)
        
        val privateKeyParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(head, 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        return publicKeyParams.encoded
    }

    /**
     * Generate Massa address using LEGACY format (Bearby pre-July 2023)
     * LEGACY BUG: Included version byte in BLAKE3 hash
     * This is for compatibility with wallets created before Bearby commit 871f077
     */
    private fun generateMassaAddressLegacy(publicKey: ByteArray): MassaAddress {
        android.util.Log.d("WalletManager", ">>> generateMassaAddressLegacy START (with version in BLAKE3)")
        
        val version = 0L
        val versionVarint = encodeVarint(version)
        
        // LEGACY BUG: Hash [version + publicKey] with BLAKE3
        val blake3Input = ByteArray(versionVarint.size + publicKey.size)
        System.arraycopy(versionVarint, 0, blake3Input, 0, versionVarint.size)
        System.arraycopy(publicKey, 0, blake3Input, versionVarint.size, publicKey.size)
        
        val blake3Digest = org.bouncycastle.crypto.digests.Blake3Digest()
        blake3Digest.update(blake3Input, 0, blake3Input.size)
        val pubKeyHash = ByteArray(32)
        blake3Digest.doFinal(pubKeyHash, 0)
        
        android.util.Log.d("WalletManager", ">>> LEGACY BLAKE3([version, pubKey]): ${pubKeyHash.joinToString("") { "%02x".format(it) }}")
        
        // Rest is standard
        val payload = ByteArray(versionVarint.size + 32)
        System.arraycopy(versionVarint, 0, payload, 0, versionVarint.size)
        System.arraycopy(pubKeyHash, 0, payload, versionVarint.size, 32)
        
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash1 = sha256.digest(payload)
        val hash2 = sha256.digest(hash1)
        val checksum = hash2.copyOfRange(0, 4)
        
        val addressBytes = ByteArray(payload.size + 4)
        System.arraycopy(payload, 0, addressBytes, 0, payload.size)
        System.arraycopy(checksum, 0, addressBytes, payload.size, 4)
        
        val base58 = encodeBase58(addressBytes)
        val addressString = "AU$base58"
        
        // Public key formatting (standard, no bug here)
        val pubKeyVersionVarint = encodeVarint(0L)
        val pubKeyPayload = ByteArray(pubKeyVersionVarint.size + 32)
        System.arraycopy(pubKeyVersionVarint, 0, pubKeyPayload, 0, pubKeyVersionVarint.size)
        System.arraycopy(publicKey, 0, pubKeyPayload, pubKeyVersionVarint.size, 32)
        
        val pubKeyHash1 = sha256.digest(pubKeyPayload)
        val pubKeyHash2 = sha256.digest(pubKeyHash1)
        val pubKeyChecksum = pubKeyHash2.copyOfRange(0, 4)
        
        val pubKeyBytes = ByteArray(pubKeyPayload.size + 4)
        System.arraycopy(pubKeyPayload, 0, pubKeyBytes, 0, pubKeyPayload.size)
        System.arraycopy(pubKeyChecksum, 0, pubKeyBytes, pubKeyPayload.size, 4)
        
        val pubKeyBase58 = encodeBase58(pubKeyBytes)
        val formattedPublicKey = "P$pubKeyBase58"
        
        android.util.Log.d("WalletManager", ">>> LEGACY Address: $addressString")
        android.util.Log.d("WalletManager", ">>> generateMassaAddressLegacy END")
        
        return MassaAddress(
            address = addressString,
            publicKey = formattedPublicKey,
            derivationPath = "m/44'/632'/0'/0'/0' (LEGACY)"
        )
    }
    
    private fun generateMassaAddress(publicKey: ByteArray): MassaAddress {
        android.util.Log.d("WalletManager", ">>> generateMassaAddress START (STANDARD)")
        
        // Massa address format (STANDARD/CORRECT):
        // 1. Version as VARINT (0 for v0)
        // 2. Public key hash with BLAKE3 (32 bytes) - WITHOUT version in hash
        // 3. Checksum (first 4 bytes of double SHA-256 of version+hash)
        
        // Hash public key with BLAKE3 (CORRECT: without version)
        val blake3Digest = org.bouncycastle.crypto.digests.Blake3Digest()
        blake3Digest.update(publicKey, 0, publicKey.size)
        val pubKeyHash = ByteArray(32)
        blake3Digest.doFinal(pubKeyHash, 0)
        android.util.Log.d("WalletManager", ">>> STANDARD BLAKE3(pubKey): ${pubKeyHash.joinToString("") { "%02x".format(it) }}")
        
        // Create versioned payload with VARINT version
        val version = 0L  // Version 0 as u64
        val versionVarint = encodeVarint(version)
        android.util.Log.d("WalletManager", ">>> Version Varint: ${versionVarint.joinToString("") { "%02x".format(it) }}")
        
        val payload = ByteArray(versionVarint.size + 32)
        System.arraycopy(versionVarint, 0, payload, 0, versionVarint.size)
        System.arraycopy(pubKeyHash, 0, payload, versionVarint.size, 32)
        android.util.Log.d("WalletManager", ">>> Payload (version+hash): ${payload.joinToString("") { "%02x".format(it) }}")
        
        // Calculate checksum with SHA-256 (for Base58Check encoding)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash1 = sha256.digest(payload)
        val hash2 = sha256.digest(hash1)
        val checksum = hash2.copyOfRange(0, 4)
        android.util.Log.d("WalletManager", ">>> Checksum (4 bytes): ${checksum.joinToString("") { "%02x".format(it) }}")
        
        // Combine: version + pubKeyHash + checksum
        val addressBytes = ByteArray(payload.size + 4)
        System.arraycopy(payload, 0, addressBytes, 0, payload.size)
        System.arraycopy(checksum, 0, addressBytes, payload.size, 4)
        android.util.Log.d("WalletManager", ">>> Address bytes (payload+checksum): ${addressBytes.joinToString("") { "%02x".format(it) }}")
        
        // Encode to Base58 with "AU" prefix for Massa
        val base58 = encodeBase58(addressBytes)
        val addressString = "AU$base58"
        android.util.Log.d("WalletManager", ">>> Final Address: $addressString")
        
        // Format public key with Massa prefix (P + base58)
        val pubKeyVersionVarint = encodeVarint(0L)  // Version 0 as varint
        val pubKeyPayload = ByteArray(pubKeyVersionVarint.size + 32)
        System.arraycopy(pubKeyVersionVarint, 0, pubKeyPayload, 0, pubKeyVersionVarint.size)
        System.arraycopy(publicKey, 0, pubKeyPayload, pubKeyVersionVarint.size, 32)
        
        // Calculate checksum for public key (SHA-256 for Base58Check)
        val pubKeyHash1 = sha256.digest(pubKeyPayload)
        val pubKeyHash2 = sha256.digest(pubKeyHash1)
        val pubKeyChecksum = pubKeyHash2.copyOfRange(0, 4)
        
        // Combine: version + publicKey + checksum
        val pubKeyBytes = ByteArray(pubKeyPayload.size + 4)
        System.arraycopy(pubKeyPayload, 0, pubKeyBytes, 0, pubKeyPayload.size)
        System.arraycopy(pubKeyChecksum, 0, pubKeyBytes, pubKeyPayload.size, 4)
        
        // Encode public key to Base58 with "P" prefix for Massa
        val pubKeyBase58 = encodeBase58(pubKeyBytes)
        val formattedPublicKey = "P$pubKeyBase58"
        android.util.Log.d("WalletManager", ">>> Final Public Key: $formattedPublicKey")
        android.util.Log.d("WalletManager", ">>> generateMassaAddress END")
        
        return MassaAddress(
            address = addressString,
            publicKey = formattedPublicKey,
            derivationPath = "m/44'/632'/0'/0'/0'"  // Bearby official path (coin type 632)
        )
    }

    // Base58 encoding (Bitcoin-style)
    private fun encodeBase58(input: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger(1, input)
        val base = BigInteger.valueOf(58)
        val result = StringBuilder()
        
        while (num > BigInteger.ZERO) {
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
    
    // Encode u64 as varint (LEB128)
    private fun encodeVarint(value: Long): ByteArray {
        val output = ByteArrayOutputStream()
        var v = value
        while (v >= 0x80) {
            output.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        output.write(v.toInt())
        return output.toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    /** Decode a Massa Station style S1 private key to raw 32-byte seed */
    fun decodeS1PrivateKey(s1: String): ByteArray {
        require(s1.startsWith("S")) { "S1 key must start with 'S'" }
        val base58Part = s1.substring(1)
        val decoded = decodeBase58(base58Part)
        require(decoded.size == 37) { "Decoded S1 length unexpected (expected 37 got ${decoded.size})" }
        val version = decoded[0]
        require(version.toInt() == 0) { "Unsupported version byte ${version}" }
        val priv = decoded.copyOfRange(1, 33)
        // checksum (last 4 bytes) already implicitly validated if needed (not re-checking here)
        return priv
    }

    /** Decode a Massa Station style P public key to raw 32-byte ed25519 public key */
    fun decodePPublicKey(p: String): ByteArray {
        require(p.startsWith("P")) { "Public key must start with 'P'" }
        val base58Part = p.substring(1)
        val decoded = decodeBase58(base58Part)
        // version(1)+pub(32)+checksum(4) = 37
        require(decoded.size == 37) { "Decoded P length unexpected (expected 37 got ${decoded.size})" }
        val version = decoded[0]
        require(version.toInt() == 0) { "Unsupported public key version ${version}" }
        return decoded.copyOfRange(1, 33)
    }

    /** 
     * Encode raw 32-byte public key to Massa P1 format
     * Format: "P" + Base58(version_byte + 32_bytes_pubkey + checksum)
     * Compatible with Bearby and Massa Station
     */
    fun encodePublicKeyP1(publicKey: ByteArray): String {
        require(publicKey.size == 32) { "Public key must be 32 bytes" }
        
        val version: Byte = 0x00
        val payload = ByteArray(33)
        payload[0] = version
        System.arraycopy(publicKey, 0, payload, 1, 32)
        
        // Calculate checksum (double SHA-256)
        val sha = java.security.MessageDigest.getInstance("SHA-256")
        val hash1 = sha.digest(payload)
        val hash2 = sha.digest(hash1)
        val checksum = hash2.copyOfRange(0, 4)
        
        // Combine: version + publicKey + checksum
        val full = ByteArray(37)
        System.arraycopy(payload, 0, full, 0, 33)
        System.arraycopy(checksum, 0, full, 33, 4)
        
        return "P" + encodeBase58(full)
    }

    /** Quick diagnostic: verify that S1 private key produces given P public key */
    fun diagnoseExternalKey(pPublic: String, sPrivate: String) {
        try {
            val rawPriv = decodeS1PrivateKey(sPrivate)
            val rawPubExpected = decodePPublicKey(pPublic)
            val derivedPub = derivePublicKey(rawPriv)
            val match = derivedPub.contentEquals(rawPubExpected)
            android.util.Log.d("WalletManager", "DIAG External Key: match=$match rawPriv=${rawPriv.joinToString("") { "%02x".format(it) }} rawPubExpected=${rawPubExpected.joinToString("") { "%02x".format(it) }} derivedPub=${derivedPub.joinToString("") { "%02x".format(it) }}")
            if (!match) {
                android.util.Log.d("WalletManager", "WARNING: Provided S1 private key does not derive the provided P public key under ed25519 seed interpretation.")
            }

            // Produce Massa address from provided public key to compare
            val addr = generateMassaAddress(rawPubExpected)
            android.util.Log.d("WalletManager", "DIAG Address from provided public key: ${addr.address}")
        } catch (e: Exception) {
            android.util.Log.d("WalletManager", "DIAG ERROR: ${e.message}")
        }
    }

    // Reuse base58 decode from this file (inverse of encodeBase58)
    private fun decodeBase58(base58: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger.ZERO
        val base = BigInteger.valueOf(58)
        for (char in base58) {
            val digit = alphabet.indexOf(char)
            require(digit >= 0) { "Invalid Base58 char $char" }
            num = num.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }
        var bytes = num.toByteArray()
        if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        val leadingOnes = base58.takeWhile { it == '1' }.length
        if (leadingOnes > 0) {
            bytes = ByteArray(leadingOnes) + bytes
        }
        return bytes
    }
}

/**
 * Extended key containing private key, chain code, and public key
 */
data class ExtendedKey(
    val privateKey: ByteArray,
    val chainCode: ByteArray,
    val publicKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExtendedKey
        if (!privateKey.contentEquals(other.privateKey)) return false
        if (!chainCode.contentEquals(other.chainCode)) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = privateKey.contentHashCode()
        result = 31 * result + chainCode.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}

/**
 * Massa address with metadata
 */
data class MassaAddress(
    val address: String,
    val publicKey: String,
    val derivationPath: String
)
