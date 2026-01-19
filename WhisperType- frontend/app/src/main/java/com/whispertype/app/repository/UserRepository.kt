package com.whispertype.app.repository

import com.whispertype.app.data.UsageDataManager
import kotlinx.coroutines.flow.StateFlow

/**
 * UserRepository - Single source of truth for user data and usage status
 * 
 * This repository:
 * - Exposes reactive user state via StateFlow
 * - Handles API calls for status refresh
 * - Manages transcription requests
 * - Eliminates direct access to UsageDataManager singleton from UI
 */
interface UserRepository {
    /**
     * Reactive stream of usage state (trial status, Pro status, quota, etc.)
     */
    val usageState: StateFlow<UsageDataManager.UsageState>
    
    /**
     * Refresh user status from backend
     * Call this on app resume and after purchases
     * 
     * @param authToken Firebase Auth ID token
     * @return Result indicating success or failure
     */
    suspend fun refreshStatus(authToken: String): Result<Unit>
    
    /**
     * Transcribe audio to text
     * 
     * @param audioBytes Raw audio data
     * @param authToken Firebase Auth ID token
     * @param audioFormat Audio format (m4a, wav, etc.)
     * @param audioDurationMs Audio duration in milliseconds
     * @return Result containing transcribed text or error
     */
    suspend fun transcribe(
        audioBytes: ByteArray,
        authToken: String,
        audioFormat: String = "m4a",
        audioDurationMs: Long? = null
    ): Result<TranscriptionResult>
    
    /**
     * Clear all user data (call on sign out)
     */
    fun clearData()
}

/**
 * Result of a successful transcription
 */
data class TranscriptionResult(
    val text: String,
    val secondsUsed: Int = 0,
    val isTrialExpired: Boolean = false,
    val expiryMessage: String? = null
)
