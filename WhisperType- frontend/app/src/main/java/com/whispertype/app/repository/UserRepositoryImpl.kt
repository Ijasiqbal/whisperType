package com.whispertype.app.repository

import android.util.Log
import com.whispertype.app.api.WhisperApiClient
import com.whispertype.app.data.UsageDataManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * UserRepositoryImpl - Implementation of UserRepository
 * 
 * Wraps UsageDataManager and WhisperApiClient to provide a clean, testable interface.
 * All state mutations go through here, ensuring consistency.
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val apiClient: WhisperApiClient
) : UserRepository {
    
    companion object {
        private const val TAG = "UserRepositoryImpl"
    }
    
    /**
     * Expose UsageDataManager's state as the single source of truth
     * Components should observe this instead of accessing UsageDataManager directly
     */
    override val usageState: StateFlow<UsageDataManager.UsageState>
        get() = UsageDataManager.usageState
    
    /**
     * Refresh user status from backend
     * Converts callback-based API to coroutine
     */
    override suspend fun refreshStatus(authToken: String): Result<Unit> {
        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "Refreshing user status from backend")
            
            apiClient.getTrialStatus(
                authToken = authToken,
                onSuccess = { status, freeSecondsUsed, freeSecondsRemaining, trialExpiryDateMs, warningLevel ->
                    Log.d(TAG, "Status refresh successful: $status")
                    continuation.resume(Result.success(Unit))
                },
                onError = { error ->
                    Log.e(TAG, "Status refresh failed: $error")
                    continuation.resume(Result.failure(Exception(error)))
                }
            )
        }
    }
    
    /**
     * Transcribe audio to text
     * Converts callback-based API to coroutine with proper Result type
     */
    override suspend fun transcribe(
        audioBytes: ByteArray,
        authToken: String,
        audioFormat: String,
        audioDurationMs: Long?
    ): Result<TranscriptionResult> {
        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "Starting transcription, size: ${audioBytes.size} bytes")
            
            apiClient.transcribe(
                audioBytes = audioBytes,
                authToken = authToken,
                audioFormat = audioFormat,
                audioDurationMs = audioDurationMs,
                callback = object : WhisperApiClient.TranscriptionCallback {
                    override fun onSuccess(text: String) {
                        Log.d(TAG, "Transcription successful: ${text.take(50)}...")
                        continuation.resume(Result.success(TranscriptionResult(text = text)))
                    }
                    
                    override fun onError(error: String) {
                        Log.e(TAG, "Transcription failed: $error")
                        continuation.resume(Result.failure(Exception(error)))
                    }
                    
                    override fun onTrialExpired(message: String) {
                        Log.w(TAG, "Trial expired: $message")
                        continuation.resume(
                            Result.success(
                                TranscriptionResult(
                                    text = "",
                                    isTrialExpired = true,
                                    expiryMessage = message
                                )
                            )
                        )
                    }
                }
            )
        }
    }
    
    /**
     * Clear all user data (e.g., on sign out)
     */
    override fun clearData() {
        Log.d(TAG, "Clearing user data")
        UsageDataManager.clear()
    }
}
