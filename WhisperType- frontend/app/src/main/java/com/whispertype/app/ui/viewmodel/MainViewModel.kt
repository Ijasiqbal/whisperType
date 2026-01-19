package com.whispertype.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whispertype.app.auth.AuthState
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.repository.AuthRepository
import com.whispertype.app.repository.BillingRepository
import com.whispertype.app.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MainViewModel - App-level ViewModel for authentication and global state
 * 
 * This ViewModel:
 * - Manages authentication state
 * - Coordinates billing initialization
 * - Refreshes user status on app resume
 * - Provides derived state for UI
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val billingRepository: BillingRepository
) : ViewModel() {
    
    /**
     * Authentication state - observe this for login/logout
     */
    val authState: StateFlow<AuthState> = authRepository.authState
    
    /**
     * Usage state - observe for Pro status, quota, etc.
     */
    val usageState: StateFlow<UsageDataManager.UsageState> = userRepository.usageState
    
    /**
     * Is user a Pro subscriber
     */
    val isProUser: StateFlow<Boolean> = billingRepository.isProUser
    
    /**
     * Derived state: is loading (auth loading AND usage loading)
     */
    val isLoading: StateFlow<Boolean> = usageState
        .map { it.isLoading }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    
    init {
        // Set up auth token provider for billing verification
        billingRepository.setAuthTokenProvider { authRepository.getCachedIdToken() }
        
        // Initialize billing when created
        billingRepository.initialize {
            viewModelScope.launch {
                billingRepository.querySubscription()
            }
        }
        
        // CRITICAL FIX: Refresh user status immediately when authentication state changes to Authenticated
        // This ensures Pro status is fetched from backend right after login, not just after transcription
        viewModelScope.launch {
            authState.collect { state ->
                if (state is AuthState.Authenticated) {
                    // User just became authenticated - fetch fresh status from backend
                    val token = authRepository.getIdToken()
                    if (token != null) {
                        userRepository.refreshStatus(token)
                    }
                }
            }
        }
    }
    
    /**
     * Refresh user status from backend
     * Call this on app resume
     */
    fun refreshUserStatus() {
        viewModelScope.launch {
            val token = authRepository.getIdToken() ?: return@launch
            userRepository.refreshStatus(token)
        }
    }
    
    /**
     * Sign out the current user
     * Clears all local data
     */
    fun signOut() {
        userRepository.clearData()
        authRepository.signOut()
    }
    
    /**
     * Get auth token for API calls
     */
    suspend fun getAuthToken(): String? {
        return authRepository.getIdToken()
    }
    
    /**
     * Get cached auth token (sync)
     */
    fun getCachedAuthToken(): String? {
        return authRepository.getCachedIdToken()
    }
    
    override fun onCleared() {
        super.onCleared()
        billingRepository.release()
    }
}
