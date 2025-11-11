package com.massapay.android.price.di

import android.content.Context
import androidx.room.Room
import com.massapay.android.core.util.Constants
import com.massapay.android.price.api.CoinGeckoApi
import com.massapay.android.price.api.CoinPaprikaApi
import com.massapay.android.price.db.PriceDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PriceModule {

    @Provides
    @Singleton
    fun provideCoinGeckoApi(okHttpClient: OkHttpClient): CoinGeckoApi {
        return Retrofit.Builder()
            .baseUrl(Constants.COINGECKO_API)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCoinPaprikaApi(okHttpClient: OkHttpClient): CoinPaprikaApi {
        return Retrofit.Builder()
            .baseUrl("https://api.coinpaprika.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinPaprikaApi::class.java)
    }

    @Provides
    @Singleton
    fun providePriceDatabase(
        @ApplicationContext context: Context
    ): PriceDatabase {
        return Room.databaseBuilder(
            context,
            PriceDatabase::class.java,
            "price_database"
        ).build()
    }

    @Provides
    @Singleton
    fun providePriceCacheDao(database: PriceDatabase) = database.priceCacheDao()
}