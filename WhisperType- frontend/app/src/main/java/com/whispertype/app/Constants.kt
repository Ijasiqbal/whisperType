package com.whispertype.app

/**
 * App-wide constants to avoid magic numbers and hardcoded values
 */
object Constants {
    
    // Timeouts
    const val API_CONNECT_TIMEOUT_SECONDS = 30L
    const val API_READ_TIMEOUT_SECONDS = 60L
    const val API_WRITE_TIMEOUT_SECONDS = 30L
    
    // Audio - AAC (fallback for Android < 10)
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_BIT_RATE_AAC = 64000
    const val AUDIO_CHANNELS = 1
    const val MIN_AUDIO_SIZE_BYTES = 500
    const val SHORT_AUDIO_THRESHOLD_BYTES = 1000

    // Audio - Opus (Android 10+, more efficient for speech)
    const val AUDIO_BIT_RATE_OPUS = 24000  // 24kbps - excellent for speech, 60% smaller than AAC

    // Legacy alias
    const val AUDIO_BIT_RATE = AUDIO_BIT_RATE_AAC
    
    // Voice Detection
    const val VOICE_ACTIVITY_THRESHOLD = 1000
    const val AMPLITUDE_CHECK_INTERVAL_MS = 150L  // Battery optimized: 150ms still responsive, 33% less CPU
    const val AMPLITUDE_CHECK_INTERVAL_IDLE_MS = 300L  // When no voice detected, check less frequently
    
    // Shortcut Detection
    const val DOUBLE_PRESS_THRESHOLD_MS = 350L
    const val BOTH_BUTTONS_THRESHOLD_MS = 300L
    
    // Silence Detection
    const val SILENCE_THRESHOLD_DB = -40f
    const val MIN_SILENCE_DURATION_MS = 500L
    const val AUDIO_BUFFER_BEFORE_MS = 150L
    const val AUDIO_BUFFER_AFTER_MS = 200L
    const val ANALYSIS_WINDOW_MS = 50L
    const val MIN_SAVINGS_PERCENT = 10
    
    // UI Delays
    const val ERROR_MESSAGE_DELAY_MS = 2000L
    const val SUCCESS_MESSAGE_DELAY_MS = 1000L
    
    // Animation
    const val PULSE_ANIMATION_DURATION_MS = 400L
    
    // Amplitude Visualization
    const val MAX_AMPLITUDE = 32767  // Maximum amplitude value from MediaRecorder
    const val MIN_AMPLITUDE_BAR_SCALE = 0.3f  // Minimum bar scale when silent
    const val MAX_AMPLITUDE_BAR_SCALE = 1.0f  // Maximum bar scale at peak volume
    const val AMPLITUDE_SMOOTHING_FACTOR = 0.35f  // Smooth interpolation (0-1, higher = more responsive)
    
    // Battery Optimization
    const val SILENCE_DURATION_FOR_IDLE_MS = 2000L  // After 2s of silence, reduce monitoring frequency
    
    // File Names
    const val AUDIO_FILE_NAME_M4A = "whisper_recording.m4a"
    const val AUDIO_FILE_NAME_OGG = "whisper_recording.ogg"
    const val PROCESSED_AUDIO_FILE_NAME = "whisper_processed.wav"

    // Legacy alias
    const val AUDIO_FILE_NAME = AUDIO_FILE_NAME_M4A
    
    // Preferences
    const val PREFS_NAME = "whispertype_prefs"
}
