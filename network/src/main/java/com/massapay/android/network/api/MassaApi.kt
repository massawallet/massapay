package com.massapay.android.network.api

import com.massapay.android.network.model.*
import retrofit2.http.Body
import retrofit2.http.POST

interface MassaApi {
    @POST(".")
    suspend fun getAddresses(@Body request: JsonRpcRequest): JsonRpcResponse<List<AddressResponse>>

    @POST(".")
    suspend fun getStatus(@Body request: JsonRpcRequest): JsonRpcResponse<NetworkStatusResponse>

    @POST(".")
    suspend fun sendOperation(@Body request: JsonRpcRequest): JsonRpcResponse<List<String>>

    @POST(".")
    suspend fun getOperations(@Body request: JsonRpcRequest): JsonRpcResponse<List<OperationResponse>>

    @POST(".")
    suspend fun getNFTs(@Body request: JsonRpcRequest): JsonRpcResponse<List<Map<String, Any>>>

    @POST(".")
    suspend fun callView(@Body request: JsonRpcRequest): JsonRpcResponse<Map<String, Any>>
}