package com.massapay.android.ui.nft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.massapay.android.core.model.NFT
import com.massapay.android.network.repository.NFTRepository
import com.massapay.android.security.storage.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NFTGalleryViewModel @Inject constructor(
    private val nftRepository: NFTRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(NFTGalleryState())
    val uiState: StateFlow<NFTGalleryState> = _uiState

    init {
        loadNFTs()
    }

    fun loadNFTs() {
        viewModelScope.launch {
            val address = secureStorage.getActiveWallet() ?: return@launch
            
            nftRepository.getNFTs(address).collect { result ->
                _uiState.update { state ->
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

    fun transferNFT(nft: NFT, toAddress: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTransferring = true) }
            
            val fromAddress = secureStorage.getActiveWallet() ?: return@launch
            
            val result = nftRepository.transferNFT(
                from = fromAddress,
                to = toAddress,
                contractAddress = nft.contractAddress,
                tokenId = nft.tokenId
            )

            _uiState.update { state ->
                when (result) {
                    is com.massapay.android.core.util.Result.Success -> state.copy(
                        isTransferring = false,
                        transferSuccess = true,
                        error = null
                    )
                    is com.massapay.android.core.util.Result.Error -> state.copy(
                        isTransferring = false,
                        error = result.exception.message
                    )
                    else -> state
                }
            }

            if (result is com.massapay.android.core.util.Result.Success) {
                loadNFTs() // Recargar la lista después de una transferencia exitosa
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
}

data class NFTGalleryState(
    val nfts: List<NFT> = emptyList(),
    val isLoading: Boolean = true,
    val isTransferring: Boolean = false,
    val transferSuccess: Boolean = false,
    val error: String? = null
)