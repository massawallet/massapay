package com.massapay.android.price.model

import com.google.gson.annotations.SerializedName

data class CoinPaprikaResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("symbol") val symbol: String,
    @SerializedName("rank") val rank: Int,
    @SerializedName("total_supply") val totalSupply: Long,
    @SerializedName("max_supply") val maxSupply: Long,
    @SerializedName("beta_value") val betaValue: Double,
    @SerializedName("first_data_at") val firstDataAt: String,
    @SerializedName("last_updated") val lastUpdated: String,
    @SerializedName("quotes") val quotes: Quotes
)

data class Quotes(
    @SerializedName("USD") val usd: UsdQuote
)

data class UsdQuote(
    @SerializedName("price") val price: Double,
    @SerializedName("volume_24h") val volume24h: Double,
    @SerializedName("volume_24h_change_24h") val volume24hChange: Double,
    @SerializedName("market_cap") val marketCap: Double,
    @SerializedName("market_cap_change_24h") val marketCapChange24h: Double,
    @SerializedName("percent_change_15m") val percentChange15m: Double,
    @SerializedName("percent_change_30m") val percentChange30m: Double,
    @SerializedName("percent_change_1h") val percentChange1h: Double,
    @SerializedName("percent_change_6h") val percentChange6h: Double,
    @SerializedName("percent_change_12h") val percentChange12h: Double,
    @SerializedName("percent_change_24h") val percentChange24h: Double,
    @SerializedName("percent_change_7d") val percentChange7d: Double,
    @SerializedName("percent_change_30d") val percentChange30d: Double,
    @SerializedName("percent_change_1y") val percentChange1y: Double,
    @SerializedName("ath_price") val athPrice: Double,
    @SerializedName("ath_date") val athDate: String,
    @SerializedName("percent_from_price_ath") val percentFromAth: Double
)

// Data class for UI state
data class MassaStats(
    val price: Double = 0.0,
    val percentChange24h: Double = 0.0,
    val percentChange7d: Double = 0.0,
    val percentChange30d: Double = 0.0,
    val volume24h: Double = 0.0,
    val marketCap: Double = 0.0,
    val athPrice: Double = 0.0,
    val percentFromAth: Double = 0.0,
    val rank: Int = 0,
    val totalSupply: Long = 0,
    val lastUpdated: String = ""
)
