package com.whispertype.app.repository

import android.content.Context
import com.whispertype.app.auth.AuthResult
import com.whispertype.app.auth.AuthState
import com.whispertype.app.auth.FirebaseAuthManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthRepositoryImpl - Implementation of AuthRepository
 * 
 * Wraps FirebaseAuthManager to provide a testable interface.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authManager: FirebaseAuthManager
) : AuthRepository {
    
    override val authState: StateFlow<AuthState>
        get() = authManager.authState
    
    override val isSignedIn: Boolean
        get() = authManager.isSignedIn
    
    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return authManager.signInWithEmail(email, password)
    }
    
    override suspend fun signUpWithEmail(email: String, password: String): AuthResult {
        return authManager.signUpWithEmail(email, password)
    }
    
    override suspend fun signInWithGoogle(context: Context): AuthResult {
        return authManager.signInWithGoogle(context)
    }
    
    override suspend fun ensureSignedIn(): Boolean {
        return authManager.ensureSignedIn() != null
    }
    
    override suspend fun getIdToken(forceRefresh: Boolean): String? {
        return authManager.getIdToken(forceRefresh)
    }
    
    override fun getCachedIdToken(): String? {
        return authManager.getCachedIdToken()
    }
    
    override fun signOut() {
        authManager.signOut()
    }
}
