package com.whispertype.app.repository

import android.content.Context
import com.whispertype.app.auth.AuthResult
import com.whispertype.app.auth.AuthState
import kotlinx.coroutines.flow.StateFlow

/**
 * AuthRepository - Single source of truth for authentication state
 * 
 * This repository:
 * - Exposes reactive auth state via StateFlow
 * - Handles all auth operations (email, Google, anonymous)
 * - Provides token access for API calls
 */
interface AuthRepository {
    /**
     * Reactive stream of authentication state
     */
    val authState: StateFlow<AuthState>
    
    /**
     * Check if user is currently signed in
     */
    val isSignedIn: Boolean
    
    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResult
    
    /**
     * Create account with email and password
     */
    suspend fun signUpWithEmail(email: String, password: String): AuthResult
    
    /**
     * Sign in with Google using Credential Manager
     */
    suspend fun signInWithGoogle(context: Context): AuthResult
    
    /**
     * Ensure user is signed in (anonymously if needed)
     */
    suspend fun ensureSignedIn(): Boolean
    
    /**
     * Get Firebase ID token for API authentication
     * 
     * @param forceRefresh Force refresh from server
     * @return ID token or null if not signed in
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): String?
    
    /**
     * Get cached ID token (synchronous, may be stale)
     * Use for quick access when freshness is not critical
     */
    fun getCachedIdToken(): String?
    
    /**
     * Sign out current user
     */
    fun signOut()
}
