package com.massapay.android.price.api

import com.massapay.android.price.model.CoinGeckoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface CoinGeckoApi {
    @GET("simple/price")
    suspend fun getPrice(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrencies: String = "usd",
        @Query("include_24hr_change") include24hrChange: Boolean = true
    ): CoinGeckoResponse
}