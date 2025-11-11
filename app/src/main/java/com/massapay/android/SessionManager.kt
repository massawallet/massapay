package com.massapay.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor() {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked
    
    private var inactivityJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // Timeout in milliseconds (5 minutes = 300000ms)
    private val sessionTimeout = 300000L
    
    init {
        // Start as locked
        _isLocked.value = true
    }
    
    /**
     * Unlock the session after successful authentication
     */
    fun unlock() {
        _isLocked.value = false
        resetInactivityTimer()
    }
    
    /**
     * Lock the session immediately
     */
    fun lock() {
        _isLocked.value = true
        cancelInactivityTimer()
    }
    
    /**
     * Reset the inactivity timer (called on user interaction)
     */
    fun resetInactivityTimer() {
        cancelInactivityTimer()
        inactivityJob = scope.launch {
            delay(sessionTimeout)
            lock()
        }
    }
    
    /**
     * Cancel the inactivity timer
     */
    private fun cancelInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = null
    }
    
    /**
     * Called when app goes to background
     */
    fun onAppPaused() {
        lock()
    }
    
    /**
     * Called when app comes to foreground
     */
    fun onAppResumed() {
        // Session remains locked until user authenticates
    }
}
