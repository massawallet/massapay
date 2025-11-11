package com.massapay.android.network.repository

import com.massapay.android.core.model.NFT
import com.massapay.android.core.model.NFTCollection
import com.massapay.android.core.model.NFTAttribute
import com.massapay.android.core.util.Result
import com.massapay.android.network.api.MassaApi
import com.massapay.android.network.model.JsonRpcRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NFTRepository @Inject constructor(
    private val massaApi: MassaApi,
) {
    // Use an internal default IPFS gateway so Hilt doesn't need a String binding
    private val ipfsGateway: String = "https://ipfs.io/ipfs/"
    fun getNFTs(address: String): Flow<Result<List<NFT>>> = flow {
        try {
            // Emitir estado de carga
            emit(Result.Loading)

            // Obtener NFTs de la dirección
            val request = JsonRpcRequest(
                method = "get_nfts",
                params = listOf(address)
            )
            
            val response = massaApi.getNFTs(request)
            response.error?.let {
                emit(Result.Error(Exception(it.message)))
                return@flow
            }

            // Procesar y enriquecer los NFTs con metadatos (usar bucle para poder llamar a funciones suspend)
            val nfts = mutableListOf<NFT>()
            response.result?.let { list ->
                for (nftDataAny in list) {
                    val nftData = nftDataAny as? Map<String, Any> ?: continue
                    val metadataUri = (nftData["metadataUri"] ?: nftData["metadata_uri"]) as? String
                    val tokenId = (nftData["tokenId"] ?: nftData["token_id"]) as? String ?: ""
                    val contractAddress = (nftData["contractAddress"] ?: nftData["contract_address"]) as? String ?: ""

                    val metadata = fetchNFTMetadata(metadataUri ?: "")
                    val nft = NFT(
                        tokenId = tokenId,
                        contractAddress = contractAddress,
                        name = metadata?.name ?: "Unknown NFT",
                        description = metadata?.description ?: "",
                        imageUrl = resolveIpfsUrl(metadata?.image),
                        attributes = metadata?.attributes ?: emptyList(),
                        collection = getCollectionInfo(contractAddress),
                        ownerAddress = address,
                        metadataUri = metadataUri ?: ""
                    )
                    nfts.add(nft)
                }
            }

            emit(Result.Success(nfts))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    suspend fun transferNFT(
        from: String,
        to: String,
        contractAddress: String,
        tokenId: String
    ): Result<String> {
        return try {
            val operation = mapOf(
                "sender" to from,
                "recipient" to to,
                "contract" to contractAddress,
                "function" to "transferFrom",
                "args" to listOf(from, to, tokenId)
            )

            val request = JsonRpcRequest(
                method = "send_operations",
                params = listOf(listOf(operation))
            )

            val response = massaApi.sendOperation(request)
            response.error?.let {
                return Result.Error(Exception(it.message))
            }

            val operationId = response.result?.firstOrNull() ?: throw Exception("No transaction hash returned")
            Result.Success(operationId)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private suspend fun getCollectionInfo(contractAddress: String): NFTCollection? {
        return try {
            val request = JsonRpcRequest(
                method = "call_view",
                params = listOf(
                    contractAddress,
                    "collectionInfo",
                    emptyList<String>()
                )
            )

            val response = massaApi.callView(request)
            if (response.error != null) return null

            val result = response.result as? Map<String, Any> ?: return null
            NFTCollection(
                address = contractAddress,
                name = result["name"] as? String ?: "",
                symbol = result["symbol"] as? String ?: "",
                description = result["description"] as? String ?: "",
                imageUrl = (result["image"] as? String)?.let { resolveIpfsUrl(it) },
                verified = result["verified"] as? Boolean ?: false
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchNFTMetadata(uri: String): NFTMetadata? {
        return try {
            // Implementar llamada HTTP para obtener los metadatos
            // Usar Retrofit o similar para hacer la llamada
            null // TODO: Implementar
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveIpfsUrl(url: String?): String {
        return when {
            url == null -> ""
            url.startsWith("ipfs://") -> {
                val hash = url.removePrefix("ipfs://")
                "$ipfsGateway$hash"
            }
            else -> url
        }
    }
}

private data class NFTMetadata(
    val name: String?,
    val description: String?,
    val image: String?,
    val attributes: List<NFTAttribute>?
)