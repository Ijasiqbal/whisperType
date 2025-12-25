package com.whispertype.app.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * AuthState - Represents the current authentication state
 */
sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * AuthResult - Represents the result of an authentication operation
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * FirebaseAuthManager - Manages Firebase Authentication for WhisperType
 * 
 * Supports:
 * - Email/Password authentication
 * - Google Sign-In via Credential Manager
 * - Anonymous authentication (fallback)
 */
class FirebaseAuthManager {

    companion object {
        private const val TAG = "FirebaseAuthManager"
        // Web client ID from google-services.json (client_type: 3)
        private const val WEB_CLIENT_ID = "133972345346-sgfb3qpjm2sbvl6h5u4nububfnan1ms9.apps.googleusercontent.com"
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    // Auth state flow for reactive UI updates
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Listen for auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _authState.value = if (user != null) {
                AuthState.Authenticated(user)
            } else {
                AuthState.Unauthenticated
            }
        }
    }

    /**
     * Get the current user, if signed in
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Check if a user is currently signed in
     */
    val isSignedIn: Boolean
        get() = auth.currentUser != null

    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            Log.d(TAG, "Signing in with email: $email")
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Log.d(TAG, "Email sign-in successful: ${user.uid}")
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Sign-in failed: No user returned")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Email sign-in failed", e)
            AuthResult.Error(e.message ?: "Sign-in failed")
        }
    }

    /**
     * Create a new account with email and password
     */
    suspend fun signUpWithEmail(email: String, password: String): AuthResult {
        return try {
            Log.d(TAG, "Creating account with email: $email")
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Log.d(TAG, "Email sign-up successful: ${user.uid}")
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Sign-up failed: No user returned")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Email sign-up failed", e)
            AuthResult.Error(e.message ?: "Sign-up failed")
        }
    }

    /**
     * Sign in with Google using Credential Manager
     */
    suspend fun signInWithGoogle(context: Context): AuthResult {
        return try {
            Log.d(TAG, "Starting Google Sign-In...")
            
            val credentialManager = CredentialManager.create(context)
            
            // Create Google ID option
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()
            
            // Create credential request
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            
            // Get credential
            val response: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = context
            )
            
            // Handle the credential
            handleGoogleSignInResult(response)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed: ${e.type}", e)
            AuthResult.Error(e.message ?: "Google Sign-In failed")
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            AuthResult.Error(e.message ?: "Google Sign-In failed")
        }
    }

    /**
     * Handle Google Sign-In credential response
     */
    private suspend fun handleGoogleSignInResult(response: GetCredentialResponse): AuthResult {
        val credential = response.credential
        
        return when {
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                
                // Authenticate with Firebase using the Google ID token
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(firebaseCredential).await()
                val user = result.user
                
                if (user != null) {
                    Log.d(TAG, "Google Sign-In successful: ${user.uid}")
                    AuthResult.Success(user)
                } else {
                    AuthResult.Error("Google Sign-In failed: No user returned")
                }
            }
            else -> {
                Log.e(TAG, "Unexpected credential type: ${credential.type}")
                AuthResult.Error("Unexpected credential type")
            }
        }
    }

    /**
     * Ensure the user is signed in (anonymously if needed)
     * 
     * @return The signed-in user, or null if sign-in failed
     */
    suspend fun ensureSignedIn(): FirebaseUser? {
        // If already signed in, return current user
        auth.currentUser?.let { user ->
            Log.d(TAG, "User already signed in: ${user.uid}")
            return user
        }

        // Sign in anonymously
        return try {
            Log.d(TAG, "Signing in anonymously...")
            val result = auth.signInAnonymously().await()
            val user = result.user
            Log.d(TAG, "Anonymous sign-in successful: ${user?.uid}")
            user
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in failed", e)
            null
        }
    }

    /**
     * Get the current user's ID token for API authentication
     * 
     * @param forceRefresh If true, force a token refresh from the server
     * @return The ID token, or null if not signed in or token retrieval fails
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): String? {
        val user = auth.currentUser
        if (user == null) {
            Log.w(TAG, "Cannot get token: user not signed in")
            return null
        }

        return try {
            val result = user.getIdToken(forceRefresh).await()
            val token = result.token
            Log.d(TAG, "Got ID token (length: ${token?.length ?: 0})")
            token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ID token", e)
            null
        }
    }

    /**
     * Sign out the current user
     */
    fun signOut() {
        Log.d(TAG, "Signing out user: ${auth.currentUser?.uid}")
        // Clear cached usage data to prevent stale trial status when switching accounts
        com.whispertype.app.data.UsageDataManager.clear()
        auth.signOut()
    }
}
