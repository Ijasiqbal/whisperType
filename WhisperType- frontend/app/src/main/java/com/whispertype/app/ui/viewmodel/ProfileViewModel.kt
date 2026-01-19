package com.whispertype.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.repository.AuthRepository
import com.whispertype.app.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ProfileViewModel - ViewModel for Profile screen
 * 
 * Provides usage data and handles sign out
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    /**
     * Usage state including trial/Pro status, quota, etc.
     */
    val usageState: StateFlow<UsageDataManager.UsageState> = userRepository.usageState
    
    /**
     * Refresh data from backend
     */
    fun refreshData() {
        viewModelScope.launch {
            val token = authRepository.getIdToken() ?: return@launch
            userRepository.refreshStatus(token)
        }
    }
    
    /**
     * Sign out and clear data
     */
    fun signOut() {
        userRepository.clearData()
        authRepository.signOut()
    }
}
