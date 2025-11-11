package com.massapay.android.core.model

data class Token(
    val address: String,
    val symbol: String,
    val decimals: Int,
    val name: String,
    val balance: String = "0",
    val usdValue: Double? = null
)

data class WalletAddress(
    val address: String,
    val label: String? = null,
    val index: Int = 0
)

data class Transaction(
    val hash: String,
    val from: String,
    val to: String,
    val amount: String,
    val token: Token,
    val timestamp: Long,
    val status: TransactionStatus,
    val fee: String,
    val massaPayData: MassaPayData? = null
)

data class MassaPayData(
    val qrData: String,
    val amount: String,
    val token: String
)

enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    FAILED
}