package com.whispertype.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whispertype.app.auth.AuthResult
import com.whispertype.app.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * LoginViewModel - ViewModel for Login/SignUp screen
 * 
 * Handles email/password auth, Google Sign-In, and anonymous sign-in
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    /**
     * Loading state
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * Error message
     */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    /**
     * Sign in with email and password
     */
    fun signInWithEmail(
        email: String, 
        password: String,
        onSuccess: () -> Unit
    ) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Please enter email and password"
            return
        }
        
        if (password.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters"
            return
        }
        
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            when (val result = authRepository.signInWithEmail(email, password)) {
                is AuthResult.Success -> {
                    _isLoading.value = false
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _isLoading.value = false
                    _errorMessage.value = result.message
                }
            }
        }
    }
    
    /**
     * Create account with email and password
     */
    fun signUpWithEmail(
        email: String, 
        password: String,
        onSuccess: () -> Unit
    ) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Please enter email and password"
            return
        }
        
        if (password.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters"
            return
        }
        
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            when (val result = authRepository.signUpWithEmail(email, password)) {
                is AuthResult.Success -> {
                    _isLoading.value = false
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _isLoading.value = false
                    _errorMessage.value = result.message
                }
            }
        }
    }
    
    /**
     * Sign in with Google
     */
    fun signInWithGoogle(context: Context, onSuccess: () -> Unit) {
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(context)) {
                is AuthResult.Success -> {
                    _isLoading.value = false
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _isLoading.value = false
                    _errorMessage.value = result.message
                }
            }
        }
    }
    
    /**
     * Sign in anonymously (guest mode)
     */
    fun signInAnonymously(onSuccess: () -> Unit) {
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            val success = authRepository.ensureSignedIn()
            _isLoading.value = false
            if (success) {
                onSuccess()
            } else {
                _errorMessage.value = "Anonymous sign-in failed"
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
