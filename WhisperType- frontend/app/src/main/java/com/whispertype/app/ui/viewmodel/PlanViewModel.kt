package com.whispertype.app.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.repository.AuthRepository
import com.whispertype.app.repository.BillingRepository
import com.whispertype.app.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PlanViewModel - ViewModel for Plan/Upgrade screen
 * 
 * Handles subscription purchase and displays current plan status
 */
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val billingRepository: BillingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    /**
     * Usage state including current plan
     */
    val usageState: StateFlow<UsageDataManager.UsageState> = userRepository.usageState
    
    /**
     * Is Pro user
     */
    val isProUser: StateFlow<Boolean> = billingRepository.isProUser
    
    /**
     * Purchase in progress state
     */
    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()
    
    /**
     * Error message from purchase
     */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    /**
     * Get formatted price for UI
     */
    fun getFormattedPrice(): String {
        return billingRepository.getFormattedPrice() ?: "₹79/month"
    }
    
    /**
     * Launch upgrade purchase flow
     */
    fun upgrade(activity: Activity) {
        _isPurchasing.value = true
        _errorMessage.value = null
        
        billingRepository.launchPurchase(
            activity = activity,
            onSuccess = {
                _isPurchasing.value = false
                // Refresh user status after successful purchase
                viewModelScope.launch {
                    val token = authRepository.getIdToken() ?: return@launch
                    userRepository.refreshStatus(token)
                }
            },
            onError = { error ->
                _isPurchasing.value = false
                _errorMessage.value = error
            }
        )
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
