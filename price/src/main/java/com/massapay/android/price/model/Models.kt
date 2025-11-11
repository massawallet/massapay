package com.massapay.android.price.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

typealias CoinGeckoResponse = Map<String, Map<String, Double>>

@Entity(tableName = "price_cache")
data class PriceCache(
    @PrimaryKey
    val coinId: String,
    val usdPrice: Double,
    val timestamp: Long
)