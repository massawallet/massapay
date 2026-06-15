package com.massapay.android.security.wallet

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.massapay.android.core.model.Account
import com.massapay.android.core.model.AccountColor
import com.massapay.android.core.model.CreateAccountRequest
import com.massapay.android.core.model.UpdateAccountRequest
import com.massapay.android.security.storage.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages multiple accounts for the wallet
 * 
 * Architecture (Massa-compatible):
 * - Each account has its OWN unique private key and mnemonic
 * - Massa does NOT support BIP-44 derivation from a master seed
 * - Each account is completely independent
 * - Keys are stored encrypted in EncryptedSharedPreferences
 * 
 * Features:
 * - Create new accounts with unique seed phrases
 * - Import existing accounts via private key or mnemonic
 * - Switch between accounts
 * - Rename and customize accounts
 * - Delete accounts (except primary)
 * - Track balances per account
 */
@Singleton
class AccountManager @Inject constructor(
    private val walletManager: WalletManager,
    private val mnemonicManager: MnemonicManager,
    private val secureStorage: SecureStorage,
    @ApplicationContext private val context: Context
) {
    
    private companion object {
        const val PREFS_NAME = "massa_accounts_encrypted"
        const val KEY_ACCOUNTS = "accounts_json"
        const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        const val DEFAULT_ACCOUNT_NAME = "Account"
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // State flows for reactive UI
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    
    private val _activeAccount = MutableStateFlow<Account?>(null)
    val activeAccount: StateFlow<Account?> = _activeAccount.asStateFlow()
    
    init {
        loadAccounts()
    }
    
    /**
     * Initialize from existing wallet (migration from old system)
     * Creates the first account from the existing wallet in SecureStorage
     */
    fun initializeFromExistingWallet(): Result<Account> {
        return try {
            // Check if accounts already exist
            if (_accounts.value.isNotEmpty()) {
                val active = _activeAccount.value ?: _accounts.value.first()
                return Result.success(active)
            }
            
            // Try to get existing wallet data
            val existingMnemonic = secureStorage.getMnemonic("default_wallet")
            val existingS1Key = secureStorage.getMnemonic("s1_private_key")
            val existingAddress = secureStorage.getActiveWallet()
            
            if (existingAddress == null) {
                return Result.failure(Exception("No existing wallet found"))
            }
            
            val (privateKeyS1, mnemonic, publicKey) = when {
                existingS1Key != null -> {
                    // S1 import - derive public key
                    val privateBytes = walletManager.decodeS1PrivateKey(existingS1Key)
                    val publicBytes = walletManager.derivePublicKey(privateBytes)
                    val pubKey = walletManager.encodePublicKeyP1(publicBytes)
                    Triple(existingS1Key, "", pubKey)
                }
                existingMnemonic != null -> {
                    // Mnemonic-based wallet
                    val s1Key = walletManager.getPrivateKeyS1(existingMnemonic)
                    val privateBytes = walletManager.getPrivateKey(existingMnemonic)
                    val publicBytes = walletManager.derivePublicKey(privateBytes)
                    val pubKey = walletManager.encodePublicKeyP1(publicBytes)
                    Triple(s1Key, existingMnemonic, pubKey)
                }
                else -> {
                    return Result.failure(Exception("No wallet credentials found"))
                }
            }
            
            // Create main account
            val account = Account(
                id = UUID.randomUUID().toString(),
                name = "Main Account",
                accountIndex = 0,
                address = existingAddress,
                publicKey = publicKey,
                privateKeyS1 = privateKeyS1,
                mnemonic = mnemonic,
                isActive = true,
                color = AccountColor.BLUE
            )
            
            // Save account
            saveAccount(account)
            setActiveAccount(account.id)
            
            Result.success(account)
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to initialize from existing wallet", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create a new account with a fresh mnemonic/private key
     */
    fun createAccount(request: CreateAccountRequest): Result<Account> {
        return try {
            // Generate new mnemonic for this account
            val newMnemonic = mnemonicManager.generateMnemonic(24)
            
            // Derive keys and address from new mnemonic using deriveAddress
            val massaAddress = walletManager.deriveAddress(
                mnemonic = newMnemonic,
                passphrase = "",
                accountIndex = 0,
                addressIndex = 0
            )
            
            // Get private key in S1 format
            val privateKeyS1 = walletManager.getPrivateKeyS1(newMnemonic)
            
            val currentAccounts = _accounts.value
            val nextIndex = (currentAccounts.maxOfOrNull { it.accountIndex } ?: -1) + 1
            
            // Create account with auto-assigned color if not provided
            val color = request.color ?: AccountColor.fromIndex(nextIndex)
            
            val account = Account(
                id = UUID.randomUUID().toString(),
                name = request.name.ifBlank { "$DEFAULT_ACCOUNT_NAME ${nextIndex + 1}" },
                accountIndex = nextIndex,
                address = massaAddress.address,
                publicKey = massaAddress.publicKey,
                privateKeyS1 = privateKeyS1,
                mnemonic = newMnemonic,
                isActive = false,
                color = color
            )
            
            saveAccount(account)
            
            Result.success(account)
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to create account", e)
            Result.failure(e)
        }
    }
    
    /**
     * Import an account from S1 private key
     */
    fun importAccountFromS1(name: String, s1PrivateKey: String, color: AccountColor? = null): Result<Account> {
        return try {
            // Use importFromS1PrivateKey which handles address derivation correctly
            val massaAddress = walletManager.importFromS1PrivateKey(s1PrivateKey)
            
            // Check if this address already exists
            if (_accounts.value.any { it.address == massaAddress.address }) {
                return Result.failure(Exception("This wallet is already imported"))
            }
            
            val currentAccounts = _accounts.value
            val nextIndex = (currentAccounts.maxOfOrNull { it.accountIndex } ?: -1) + 1
            
            val account = Account(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "$DEFAULT_ACCOUNT_NAME ${nextIndex + 1}" },
                accountIndex = nextIndex,
                address = massaAddress.address,
                publicKey = massaAddress.publicKey,
                privateKeyS1 = s1PrivateKey,
                mnemonic = "", // No mnemonic for S1 imports
                isActive = false,
                color = color ?: AccountColor.fromIndex(nextIndex)
            )
            
            saveAccount(account)
            
            Result.success(account)
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to import account from S1", e)
            Result.failure(e)
        }
    }
    
    /**
     * Import an account from mnemonic
     */
    fun importAccountFromMnemonic(name: String, mnemonic: String, color: AccountColor? = null): Result<Account> {
        return try {
            // Use deriveAddress which handles the full BIP-44 derivation
            val massaAddress = walletManager.deriveAddress(
                mnemonic = mnemonic,
                passphrase = "",
                accountIndex = 0,
                addressIndex = 0
            )
            val privateKeyS1 = walletManager.getPrivateKeyS1(mnemonic)
            
            // Check if this address already exists
            if (_accounts.value.any { it.address == massaAddress.address }) {
                return Result.failure(Exception("This wallet is already imported"))
            }
            
            val currentAccounts = _accounts.value
            val nextIndex = (currentAccounts.maxOfOrNull { it.accountIndex } ?: -1) + 1
            
            val account = Account(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "$DEFAULT_ACCOUNT_NAME ${nextIndex + 1}" },
                accountIndex = nextIndex,
                address = massaAddress.address,
                publicKey = massaAddress.publicKey,
                privateKeyS1 = privateKeyS1,
                mnemonic = mnemonic,
                isActive = false,
                color = color ?: AccountColor.fromIndex(nextIndex)
            )
            
            saveAccount(account)
            
            Result.success(account)
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to import account from mnemonic", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update account metadata (name, color)
     */
    fun updateAccount(request: UpdateAccountRequest): Result<Account> {
        return try {
            val currentAccounts = _accounts.value.toMutableList()
            val index = currentAccounts.indexOfFirst { it.id == request.accountId }
            
            if (index == -1) {
                return Result.failure(Exception("Account not found"))
            }
            
            val account = currentAccounts[index]
            val updatedAccount = account.copy(
                name = request.name ?: account.name,
                color = request.color ?: account.color
            )
            
            currentAccounts[index] = updatedAccount
            saveAccounts(currentAccounts)
            
            // Update active account if it was the one updated
            if (_activeAccount.value?.id == request.accountId) {
                _activeAccount.value = updatedAccount
            }
            
            Result.success(updatedAccount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete an account (cannot delete the primary account or active account)
     */
    fun deleteAccount(accountId: String): Result<Unit> {
        return try {
            val currentAccounts = _accounts.value
            val account = currentAccounts.find { it.id == accountId }
                ?: return Result.failure(Exception("Account not found"))
            
            // Cannot delete first account (index 0)
            if (account.accountIndex == 0) {
                return Result.failure(Exception("Cannot delete the main account"))
            }
            
            // Cannot delete active account
            if (account.isActive) {
                return Result.failure(Exception("Cannot delete active account. Switch to another account first."))
            }
            
            val updatedAccounts = currentAccounts.filter { it.id != accountId }
            saveAccounts(updatedAccounts)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Switch to a different account
     */
    fun setActiveAccount(accountId: String): Result<Account> {
        return try {
            val currentAccounts = _accounts.value.toMutableList()
            if (currentAccounts.none { it.id == accountId }) {
                return Result.failure(Exception("Account not found"))
            }
            
            // Deactivate all accounts
            val updatedAccounts = currentAccounts.map { it.copy(isActive = false) }
            
            // Activate selected account
            val activeIndex = updatedAccounts.indexOfFirst { it.id == accountId }
            val activeAccount = updatedAccounts[activeIndex].copy(
                isActive = true,
                lastUsed = System.currentTimeMillis()
            )
            
            val finalAccounts = updatedAccounts.toMutableList()
            finalAccounts[activeIndex] = activeAccount
            
            saveAccounts(finalAccounts)
            
            // Update active account ID
            prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, accountId).apply()
            _activeAccount.value = activeAccount
            
            // Also update SecureStorage for compatibility with other parts of the app
            secureStorage.setActiveWallet(activeAccount.address)
            
            Result.success(activeAccount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get account by ID
     */
    fun getAccount(accountId: String): Account? {
        return _accounts.value.find { it.id == accountId }
    }
    
    /**
     * Get the private key S1 for the active account
     */
    fun getActiveAccountPrivateKeyS1(): String? {
        return _activeAccount.value?.privateKeyS1
    }
    
    /**
     * Get the mnemonic for the active account (if available)
     */
    fun getActiveAccountMnemonic(): String? {
        val mnemonic = _activeAccount.value?.mnemonic
        return if (mnemonic.isNullOrBlank()) null else mnemonic
    }
    
    /**
     * Get the public key P1 for the active account
     */
    fun getActiveAccountPublicKeyP1(): String? {
        return _activeAccount.value?.publicKey
    }
    
    /**
     * Update account balance (called after fetching from network)
     */
    fun updateAccountBalance(accountId: String, balance: String) {
        val currentAccounts = _accounts.value.toMutableList()
        val index = currentAccounts.indexOfFirst { it.id == accountId }
        
        if (index != -1) {
            currentAccounts[index] = currentAccounts[index].copy(balance = balance)
            saveAccounts(currentAccounts)
            
            // Update active account if it was the one updated
            if (_activeAccount.value?.id == accountId) {
                _activeAccount.value = currentAccounts[index]
            }
        }
    }
    
    /**
     * Get private key bytes for signing transactions
     */
    fun getPrivateKeyForAccount(accountId: String): ByteArray? {
        val account = getAccount(accountId) ?: return null
        return try {
            walletManager.decodeS1PrivateKey(account.privateKeyS1)
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to decode private key", e)
            null
        }
    }
    
    /**
     * Get total balance across all accounts
     */
    fun getTotalBalance(): Double {
        return _accounts.value.sumOf { 
            it.balance.toDoubleOrNull() ?: 0.0 
        }
    }
    
    // ========== Private Helper Methods ==========
    
    private fun saveAccount(account: Account) {
        val currentAccounts = _accounts.value.toMutableList()
        currentAccounts.add(account)
        saveAccounts(currentAccounts)
    }
    
    private fun saveAccounts(accounts: List<Account>) {
        val jsonArray = JSONArray()
        accounts.forEach { account ->
            val json = JSONObject().apply {
                put("id", account.id)
                put("name", account.name)
                put("accountIndex", account.accountIndex)
                put("address", account.address)
                put("publicKey", account.publicKey)
                put("privateKeyS1", account.privateKeyS1)
                put("mnemonic", account.mnemonic)
                put("balance", account.balance)
                put("isActive", account.isActive)
                put("createdAt", account.createdAt)
                put("lastUsed", account.lastUsed)
                put("color", account.color.name)
            }
            jsonArray.put(json)
        }
        
        prefs.edit().putString(KEY_ACCOUNTS, jsonArray.toString()).apply()
        _accounts.value = accounts
    }
    
    private fun loadAccounts() {
        try {
            val jsonString = prefs.getString(KEY_ACCOUNTS, null)
            
            if (jsonString == null) {
                // No accounts stored yet - try to initialize from existing wallet
                initializeFromExistingWallet()
                return
            }
            
            val jsonArray = JSONArray(jsonString)
            val accounts = mutableListOf<Account>()
            
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val account = Account(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    accountIndex = json.getInt("accountIndex"),
                    address = json.getString("address"),
                    publicKey = json.getString("publicKey"),
                    privateKeyS1 = json.optString("privateKeyS1", ""),
                    mnemonic = json.optString("mnemonic", ""),
                    balance = json.optString("balance", "0.0"),
                    isActive = json.getBoolean("isActive"),
                    createdAt = json.getLong("createdAt"),
                    lastUsed = json.getLong("lastUsed"),
                    color = try {
                        AccountColor.valueOf(json.getString("color"))
                    } catch (e: Exception) {
                        AccountColor.BLUE
                    }
                )
                accounts.add(account)
            }
            
            _accounts.value = accounts
            
            // Load active account
            val activeId = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null)
            _activeAccount.value = accounts.find { it.id == activeId && it.isActive }
                ?: accounts.find { it.isActive }
                ?: accounts.firstOrNull()
            _activeAccount.value?.address?.let { secureStorage.setActiveWallet(it) }
                
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "Failed to load accounts", e)
            _accounts.value = emptyList()
            // Try to recover from existing wallet
            initializeFromExistingWallet()
        }
    }
    
    /**
     * Clear all accounts (used when deleting wallet)
     */
    fun clearAllAccounts() {
        prefs.edit().clear().apply()
        _accounts.value = emptyList()
        _activeAccount.value = null
    }
}
