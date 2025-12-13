package com.whispertype.app.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * FirebaseAuthManager - Manages Firebase Authentication for WhisperType
 * 
 * Supports anonymous authentication to identify users without requiring
 * account creation. Each anonymous user gets a unique UID that persists
 * across app sessions until the app is uninstalled.
 */
class FirebaseAuthManager {

    companion object {
        private const val TAG = "FirebaseAuthManager"
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

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
        auth.signOut()
    }
}
