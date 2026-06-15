package com.massapay.android.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.advancedFeatureDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "advanced_feature_preferences"
)

data class AdvancedFeatureState(
    val nftEnabled: Boolean = false,
    val swapEnabled: Boolean = false,
    val stakingEnabled: Boolean = false
)

@Singleton
class AdvancedFeatureManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nftEnabledKey = booleanPreferencesKey("advanced_nft_enabled")
    private val swapEnabledKey = booleanPreferencesKey("advanced_swap_enabled")
    private val stakingEnabledKey = booleanPreferencesKey("advanced_staking_enabled")

    val featureState: Flow<AdvancedFeatureState> = context.advancedFeatureDataStore.data
        .map { preferences ->
            AdvancedFeatureState(
                nftEnabled = preferences[nftEnabledKey] ?: false,
                swapEnabled = preferences[swapEnabledKey] ?: false,
                stakingEnabled = preferences[stakingEnabledKey] ?: false
            )
        }

    suspend fun setNftEnabled(enabled: Boolean) {
        context.advancedFeatureDataStore.edit { preferences ->
            preferences[nftEnabledKey] = enabled
        }
    }

    suspend fun setSwapEnabled(enabled: Boolean) {
        context.advancedFeatureDataStore.edit { preferences ->
            preferences[swapEnabledKey] = enabled
        }
    }

    suspend fun setStakingEnabled(enabled: Boolean) {
        context.advancedFeatureDataStore.edit { preferences ->
            preferences[stakingEnabledKey] = enabled
        }
    }
}
