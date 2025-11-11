package com.massapay.android.network.model

import com.google.gson.annotations.SerializedName

data class JsonRpcRequest(
    @SerializedName("jsonrpc")
    val jsonRpc: String = "2.0",
    @SerializedName("method")
    val method: String,
    @SerializedName("params")
    val params: List<Any>,
    @SerializedName("id")
    val id: Int = 1
)

data class JsonRpcResponse<T>(
    @SerializedName("jsonrpc")
    val jsonRpc: String,
    @SerializedName("result")
    val result: T?,
    @SerializedName("error")
    val error: JsonRpcError?,
    @SerializedName("id")
    val id: Int
)

data class JsonRpcError(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String
)

// Response models
data class AddressResponse(
    @SerializedName("address")
    val address: String,
    @SerializedName("final_balance")
    val finalBalance: String?,
    @SerializedName("candidate_balance")
    val candidateBalance: String?,
    @SerializedName("balance") 
    val balance: String?
)

data class NetworkStatusResponse(
    @SerializedName("chain_id")
    val chainId: String,
    @SerializedName("current_cycle")
    val currentCycle: Long,
    @SerializedName("current_time")
    val currentTime: Long,
    @SerializedName("next_slot")
    val nextSlot: SlotInfo
)

data class SlotInfo(
    @SerializedName("period")
    val period: Long,
    @SerializedName("thread")
    val thread: Int
)

data class OperationResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("in_pool")
    val inPool: Boolean,
    @SerializedName("in_blocks")
    val inBlocks: List<String>,
    @SerializedName("is_final")
    val isFinal: Boolean,
    @SerializedName("type")
    val type: String,
    @SerializedName("sender_address")
    val senderAddress: String,
    @SerializedName("recipient_address")
    val recipientAddress: String,
    @SerializedName("amount")
    val amount: String,
    @SerializedName("fee")
    val fee: String,
    @SerializedName("timestamp")
    val timestamp: Long? = null,
    @SerializedName("slot")
    val slot: SlotInfo? = null
)