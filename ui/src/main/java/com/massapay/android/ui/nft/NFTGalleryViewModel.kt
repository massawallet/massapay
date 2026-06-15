package com.massapay.android.ui.nft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.core.model.NFT
import com.massapay.android.network.repository.NFTRepository
import com.massapay.android.network.repository.MassaRepository
import com.massapay.android.security.storage.SecureStorage
import com.massapay.android.security.wallet.AccountManager
import com.massapay.android.security.wallet.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class NFTGalleryViewModel @Inject constructor(
    private val nftRepository: NFTRepository,
    private val massaRepository: MassaRepository,
    private val secureStorage: SecureStorage,
    private val accountManager: AccountManager,
    private val walletManager: WalletManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(NFTGalleryState())
    val uiState: StateFlow<NFTGalleryState> = _uiState

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
                loadNFTs(address)
            }
        }
    }

    fun loadNFTs() {
        val address = accountManager.activeAccount.value?.address
            ?: secureStorage.getActiveWallet()
            ?: return
        currentAddress = address
        loadNFTs(address)
    }

    private fun loadNFTs(address: String) {
        viewModelScope.launch {
            nftRepository.getNFTs(address).collect { result ->
                _uiState.update { state ->
                    if (currentAddress != address) {
                        return@update state
                    }
                    when (result) {
                        is com.massapay.android.core.util.Result.Success -> state.copy(
                            nfts = result.data,
                            isLoading = false,
                            error = null
                        )
                        is com.massapay.android.core.util.Result.Error -> state.copy(
                            isLoading = false,
                            error = result.exception.message
                        )
                        is com.massapay.android.core.util.Result.Loading -> state.copy(
                            isLoading = true,
                            error = null
                        )
                        else -> state
                    }
                }
            }
        }
    }
    
    /**
     * Force refresh NFTs by clearing cache first
     */
    fun refreshNFTs() {
        nftRepository.clearCache()
        loadNFTs()
    }

    fun transferNFT(nft: NFT, toAddress: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTransferring = true, error = null) }
            
            val activeAccount = accountManager.activeAccount.value
            val fromAddress = activeAccount?.address ?: secureStorage.getActiveWallet() ?: run {
                _uiState.update { it.copy(isTransferring = false, error = "No wallet found") }
                return@launch
            }
            
            // Get private key from AccountManager (same as SendViewModel)
            val privateKeyBytes = if (activeAccount != null) {
                accountManager.getPrivateKeyForAccount(activeAccount.id)
            } else {
                // Fallback for legacy single account
                val mnemonic = secureStorage.getMnemonic("default_wallet")
                if (mnemonic != null) {
                    walletManager.getPrivateKey(mnemonic)
                } else {
                    null
                }
            }

            if (privateKeyBytes == null) {
                _uiState.update { it.copy(isTransferring = false, error = "Could not retrieve private key") }
                return@launch
            }
            
            val privateKey = privateKeyBytes.joinToString("") { "%02x".format(it) }
            
            // Derive public key
            val publicKeyBytes = walletManager.derivePublicKey(privateKeyBytes)
            val publicKey = walletManager.encodePublicKeyP1(publicKeyBytes)
            
            try {
                // Serialize parameters for transferFrom(from, to, tokenId)
                // Format: from_address + to_address + tokenId_u256
                val paramBytes = ByteArrayOutputStream()
                
                // Encode 'from' address (4-byte length prefix + UTF-8 bytes)
                val fromBytes = fromAddress.toByteArray(Charsets.UTF_8)
                paramBytes.write(fromBytes.size and 0xFF)
                paramBytes.write((fromBytes.size shr 8) and 0xFF)
                paramBytes.write((fromBytes.size shr 16) and 0xFF)
                paramBytes.write((fromBytes.size shr 24) and 0xFF)
                paramBytes.write(fromBytes)
                
                // Encode 'to' address (4-byte length prefix + UTF-8 bytes)
                val toBytes = toAddress.toByteArray(Charsets.UTF_8)
                paramBytes.write(toBytes.size and 0xFF)
                paramBytes.write((toBytes.size shr 8) and 0xFF)
                paramBytes.write((toBytes.size shr 16) and 0xFF)
                paramBytes.write((toBytes.size shr 24) and 0xFF)
                paramBytes.write(toBytes)
                
                // Encode tokenId as u256 (32 bytes, little-endian)
                val tokenIdLong = nft.tokenId.toLongOrNull() ?: 0L
                var value = tokenIdLong
                for (i in 0..7) {
                    paramBytes.write((value and 0xFF).toInt())
                    value = value shr 8
                }
                // Pad remaining 24 bytes with zeros
                for (i in 8..31) {
                    paramBytes.write(0)
                }
                
                // Convert to the parameter format expected by callSmartContract
                val parameterArray = paramBytes.toByteArray().map { it.toInt() and 0xFF }
                val parameterJson = parameterArray.joinToString(",", "[", "]")
                
                android.util.Log.d("NFTGalleryVM", "Transferring NFT ${nft.tokenId} from $fromAddress to $toAddress")
                android.util.Log.d("NFTGalleryVM", "Contract: ${nft.contractAddress}")
                
                val result = massaRepository.callSmartContract(
                    from = fromAddress,
                    targetAddress = nft.contractAddress,
                    functionName = "transferFrom",
                    parameter = parameterJson,
                    coins = "0", // No MAS sent for NFT transfer
                    fee = "0.01", // Standard fee
                    maxGas = "100000000", // 100M gas
                    privateKey = privateKey,
                    publicKey = publicKey
                )

                _uiState.update { state ->
                    when (result) {
                        is com.massapay.android.core.util.Result.Success -> {
                            android.util.Log.d("NFTGalleryVM", "Transfer successful! OpId: ${result.data}")
                            state.copy(
                                isTransferring = false,
                                transferSuccess = true,
                                error = null
                            )
                        }
                        is com.massapay.android.core.util.Result.Error -> {
                            android.util.Log.e("NFTGalleryVM", "Transfer failed: ${result.exception.message}")
                            state.copy(
                                isTransferring = false,
                                error = result.exception.message
                            )
                        }
                        else -> state
                    }
                }

                if (result is com.massapay.android.core.util.Result.Success) {
                    loadNFTs() // Reload list after successful transfer
                }
            } catch (e: Exception) {
                android.util.Log.e("NFTGalleryVM", "Transfer exception: ${e.message}", e)
                _uiState.update { it.copy(
                    isTransferring = false,
                    error = e.message ?: "Transfer failed"
                ) }
            }
        }
    }

    fun resetTransferState() {
        _uiState.update { it.copy(
            isTransferring = false,
            transferSuccess = false,
            error = null
        ) }
    }
    
    /**
     * Add a custom NFT collection by contract address
     */
    fun addCustomCollection(contractAddress: String, name: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingCollection = true) }
            
            val result = nftRepository.addCustomCollection(contractAddress, name)
            
            when (result) {
                is com.massapay.android.core.util.Result.Success -> {
                    _uiState.update { it.copy(
                        isAddingCollection = false,
                        addCollectionSuccess = true,
                        error = null
                    ) }
                    // Reload NFTs to include the new collection
                    loadNFTs()
                }
                is com.massapay.android.core.util.Result.Error -> {
                    _uiState.update { it.copy(
                        isAddingCollection = false,
                        error = result.exception.message
                    ) }
                }
                else -> {}
            }
        }
    }
    
    fun resetAddCollectionState() {
        _uiState.update { it.copy(
            isAddingCollection = false,
            addCollectionSuccess = false
        ) }
    }
}

data class NFTGalleryState(
    val nfts: List<NFT> = emptyList(),
    val isLoading: Boolean = true,
    val isTransferring: Boolean = false,
    val transferSuccess: Boolean = false,
    val isAddingCollection: Boolean = false,
    val addCollectionSuccess: Boolean = false,
    val error: String? = null
)
