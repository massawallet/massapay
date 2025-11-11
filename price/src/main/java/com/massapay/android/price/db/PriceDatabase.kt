package com.massapay.android.price.db

import androidx.room.*
import com.massapay.android.price.model.PriceCache
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceCacheDao {
    @Query("SELECT * FROM price_cache WHERE coinId = :coinId")
    fun getPriceForCoin(coinId: String): Flow<PriceCache?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrice(priceCache: PriceCache)

    @Query("DELETE FROM price_cache WHERE timestamp < :timestamp")
    suspend fun deleteOldPrices(timestamp: Long)
}

@Database(entities = [PriceCache::class], version = 1)
abstract class PriceDatabase : RoomDatabase() {
    abstract fun priceCacheDao(): PriceCacheDao
}