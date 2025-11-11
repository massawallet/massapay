package com.massapay.android.price.api

import com.massapay.android.price.model.CoinPaprikaResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface CoinPaprikaApi {
    @GET("v1/tickers/{coin_id}")
    suspend fun getTicker(
        @Path("coin_id") coinId: String = "mas-massa"
    ): CoinPaprikaResponse
}
