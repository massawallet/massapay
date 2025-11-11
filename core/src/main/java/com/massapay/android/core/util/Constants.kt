package com.massapay.android.core.util

object Constants {
    // Network
    const val MASSA_MAINNET_RPC = "https://mainnet.massa.net/api/v2/"
    const val COINGECKO_API = "https://api.coingecko.com/api/v3/"
    const val MASSA_CHAIN_ID = "massa-mainnet"
    
    // Preferences
    const val PREF_ENCRYPTED_FILE = "massa_wallet_prefs"
    const val PREF_KEY_ACTIVE_WALLET = "active_wallet"
    const val PREF_KEY_RPC_ENDPOINT = "rpc_endpoint"
    
    // BIP-39
    const val MNEMONIC_WORD_COUNT = 24
    const val DEFAULT_DERIVATION_PATH = "m/44'/297'/0'/0/0"
    
    // QR Code
    const val MASSA_QR_SCHEME = "massa:"
    const val QR_PARAM_AMOUNT = "amount"
    const val QR_PARAM_TOKEN = "token"
    
    // Cache
    const val PRICE_CACHE_DURATION = 30_000L // 30 seconds
    
    // UI
    const val ADDRESS_DISPLAY_CHARS = 8
}