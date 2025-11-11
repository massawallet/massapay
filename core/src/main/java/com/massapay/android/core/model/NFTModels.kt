package com.massapay.android.core.model

data class NFT(
    val tokenId: String,
    val contractAddress: String,
    val name: String,
    val description: String,
    val imageUrl: String,
    val attributes: List<NFTAttribute>,
    val collection: NFTCollection?,
    val ownerAddress: String,
    val metadataUri: String,
    val standard: NFTStandard = NFTStandard.MRC1155
)

data class NFTAttribute(
    val traitType: String,
    val value: String,
    val displayType: String? = null
)

data class NFTCollection(
    val address: String,
    val name: String,
    val symbol: String,
    val description: String,
    val imageUrl: String?,
    val verified: Boolean = false
)

enum class NFTStandard {
    MRC1155,
    MRC721
}