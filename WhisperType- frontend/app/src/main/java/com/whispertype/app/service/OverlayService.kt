package com.whispertype.app.service

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.whispertype.app.MainActivity
import com.whispertype.app.R
import com.whispertype.app.audio.AudioRecorder
import com.whispertype.app.speech.SpeechRecognitionHelper

/**
 * OverlayService - Manages the floating mic overlay
 * 
 * This service:
 * 1. Displays a floating overlay using WindowManager
 * 2. Handles user interaction (mic button, drag, close)
 * 3. Coordinates with SpeechRecognitionHelper for STT
 * 4. Triggers text insertion via AccessibilityService
 * 
 * WINDOW FLAGS EXPLANATION:
 * 
 * TYPE_APPLICATION_OVERLAY (API 26+) / TYPE_PHONE (API 24-25):
 * - Allows the window to appear on top of other apps
 * - Requires SYSTEM_ALERT_WINDOW permission
 * 
 * FLAG_NOT_FOCUSABLE:
 * - CRITICAL: This prevents the overlay from stealing focus
 * - Without this, tapping the overlay would dismiss the keyboard
 *   and remove focus from the text field in the underlying app
 * 
 * FLAG_LAYOUT_NO_LIMITS:
 * - Allows the window to extend outside screen bounds during drag
 * 
 * FLAG_NOT_TOUCH_MODAL:
 * - Allow touches outside the overlay to pass through
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "whispertype_overlay_channel"
        
        // Intent actions
        const val ACTION_SHOW = "com.whispertype.app.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.whispertype.app.HIDE_OVERLAY"
        const val ACTION_TOGGLE = "com.whispertype.app.TOGGLE_OVERLAY"
        
        // Amplitude threshold for voice detection (MediaRecorder amplitude ranges 0-32767)
        private const val VOICE_ACTIVITY_THRESHOLD = 1000  // Amplitude above this = speaking
    }
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayVisible = false
    
    private var speechHelper: SpeechRecognitionHelper? = null
    
    // UI elements
    private var micButton: FrameLayout? = null
    private var micIcon: ImageView? = null
    private var statusText: TextView? = null
    private var previewText: TextView? = null
    private var closeButton: ImageButton? = null
    
    // Listening state
    private var isListening = false
    
    // Voice activity animation
    private var pulseAnimator: ObjectAnimator? = null
    private var isSpeaking = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService created")
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Initialize speech recognition
        speechHelper = SpeechRecognitionHelper(this, object : SpeechRecognitionHelper.Callback {
            override fun onReadyForSpeech() {
                updateUI(State.LISTENING)
            }
            
            override fun onBeginningOfSpeech() {
                // Already in listening state
            }
            
            override fun onPartialResults(partialText: String) {
                previewText?.visibility = View.VISIBLE
                previewText?.text = partialText
            }
            
            override fun onResults(finalText: String) {
                Log.d(TAG, "Final result: $finalText")
                handleRecognitionResult(finalText)
            }
            
            override fun onError(errorMessage: String) {
                Log.e(TAG, "Speech error: $errorMessage")
                updateUI(State.ERROR)
                statusText?.text = errorMessage
                
                // Reset to ready state after a delay
                statusText?.postDelayed({
                    if (!isListening) {
                        updateUI(State.IDLE)
                    }
                }, 2000)
            }
            
            override fun onEndOfSpeech() {
                updateUI(State.PROCESSING)
            }
        })
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_TOGGLE -> {
                if (isOverlayVisible) {
                    hideOverlay()
                } else {
                    showOverlay()
                }
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        hideOverlay()
        speechHelper?.destroy()
        super.onDestroy()
        Log.d(TAG, "OverlayService destroyed")
    }
    
    /**
     * Show the overlay on screen
     */
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showOverlay() {
        if (isOverlayVisible) {
            Log.d(TAG, "Overlay already visible")
            return
        }
        
        Log.d(TAG, "Showing overlay")
        
        // Inflate the overlay layout
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_mic_view, null)
        
        // Get UI references
        micButton = overlayView?.findViewById(R.id.mic_button_container)
        micIcon = overlayView?.findViewById(R.id.ic_mic)
        statusText = overlayView?.findViewById(R.id.tv_status)
        previewText = overlayView?.findViewById(R.id.tv_preview)
        closeButton = overlayView?.findViewById(R.id.btn_close)
        
        // Configure window parameters
        val layoutParams = createLayoutParams()
        
        // Set up touch handling for dragging
        setupTouchHandling(overlayView!!, layoutParams)
        
        // Set up button clicks
        setupButtonHandlers()
        
        // Add view to window
        try {
            windowManager?.addView(overlayView, layoutParams)
            isOverlayVisible = true
            updateUI(State.IDLE)
            
            // Auto-start recording when overlay is shown
            overlayView?.post {
                startListening()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
            Toast.makeText(this, "Failed to show overlay", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Hide the overlay from screen
     */
    fun hideOverlay() {
        if (!isOverlayVisible || overlayView == null) {
            return
        }
        
        Log.d(TAG, "Hiding overlay")
        
        // Stop any ongoing speech recognition
        if (isListening) {
            speechHelper?.stopListening()
            isListening = false
        }
        
        try {
            windowManager?.removeView(overlayView)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay view", e)
        }
        
        overlayView = null
        isOverlayVisible = false
        
        // Stop the service when overlay is hidden
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    /**
     * Create WindowManager.LayoutParams for the overlay
     * 
     * Key flags explained:
     * - TYPE_APPLICATION_OVERLAY: Required for overlay on API 26+
     * - FLAG_NOT_FOCUSABLE: Prevents stealing focus from text fields
     * - FLAG_LAYOUT_NO_LIMITS: Allows drag beyond screen edges
     */
    private fun createLayoutParams(): WindowManager.LayoutParams {
        // Window type depends on SDK version
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+: Use TYPE_APPLICATION_OVERLAY
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            // API 24-25: Use deprecated TYPE_PHONE
            // This is necessary for backward compatibility
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            // FLAG_NOT_FOCUSABLE is CRITICAL - it prevents stealing focus from text fields
            // FLAG_LAYOUT_NO_LIMITS allows the overlay to be dragged near screen edges
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Centered position
            gravity = Gravity.CENTER
        }
    }
    
    /**
     * Set up touch handling for dragging the overlay
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchHandling(view: View, params: WindowManager.LayoutParams) {
        val container = view.findViewById<View>(R.id.overlay_container)
        
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    
                    // Start dragging if moved more than a threshold
                    if (dx * dx + dy * dy > 100) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // If not dragging, it's a tap on the container (could be handled)
                    !isDragging
                }
                else -> false
            }
        }
    }
    
    /**
     * Set up click handlers for buttons
     */
    private fun setupButtonHandlers() {
        // Mic button stops recording (recording starts automatically when overlay shows)
        micButton?.setOnClickListener {
            if (isListening) {
                stopListening()
            }
        }
        
        // Close button hides the overlay
        closeButton?.setOnClickListener {
            hideOverlay()
        }
    }
    
    /**
     * Start speech recognition
     */
    private fun startListening() {
        Log.d(TAG, "Starting speech recognition")
        
        // Check if microphone permission is granted
        if (!speechHelper!!.hasPermission()) {
            Toast.makeText(
                this,
                "Microphone permission required. Open WhisperType to grant it.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Check if accessibility service is available for text insertion
        if (!WhisperTypeAccessibilityService.isRunning()) {
            Toast.makeText(
                this,
                "Accessibility service not running. Please enable it in settings.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Clear preview
        previewText?.text = ""
        previewText?.visibility = View.GONE
        
        // Set up amplitude callback for voice activity animation
        speechHelper?.getAudioRecorder()?.setAmplitudeCallback(object : AudioRecorder.AmplitudeCallback {
            override fun onAmplitude(amplitude: Int) {
                handleVoiceAmplitude(amplitude)
            }
        })
        
        // Start listening
        isListening = true
        speechHelper?.startListening()
    }
    
    /**
     * Stop speech recognition
     */
    private fun stopListening() {
        Log.d(TAG, "Stopping speech recognition")
        isListening = false
        speechHelper?.stopListening()
        
        // Stop voice activity animation
        stopPulseAnimation()
        speechHelper?.getAudioRecorder()?.setAmplitudeCallback(null)
        
        updateUI(State.IDLE)
    }
    
    /**
     * Handle voice amplitude updates for animation
     */
    private fun handleVoiceAmplitude(amplitude: Int) {
        val wasSpeaking = isSpeaking
        isSpeaking = amplitude > VOICE_ACTIVITY_THRESHOLD
        
        if (isSpeaking && !wasSpeaking) {
            // Started speaking - begin pulse animation
            startPulseAnimation()
        } else if (!isSpeaking && wasSpeaking) {
            // Stopped speaking - stop pulse animation
            stopPulseAnimation()
        }
    }
    
    /**
     * Start pulse animation on mic button to indicate voice detection
     */
    private fun startPulseAnimation() {
        if (pulseAnimator?.isRunning == true) return
        
        micButton?.let { button ->
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.15f, 1.0f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.15f, 1.0f)
            
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(button, scaleX, scaleY).apply {
                duration = 400
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }
    
    /**
     * Stop pulse animation
     */
    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        
        // Reset scale to normal
        micButton?.apply {
            scaleX = 1.0f
            scaleY = 1.0f
        }
        isSpeaking = false
    }
    
    /**
     * Handle the final recognition result
     */
    private fun handleRecognitionResult(text: String) {
        isListening = false
        
        if (text.isBlank()) {
            updateUI(State.ERROR)
            statusText?.text = getString(R.string.error_no_match)
            statusText?.postDelayed({ updateUI(State.IDLE) }, 2000)
            return
        }
        
        // Insert text using accessibility service
        val accessibilityService = WhisperTypeAccessibilityService.instance
        if (accessibilityService != null) {
            val success = accessibilityService.insertText(text)
            if (success) {
                updateUI(State.SUCCESS)
                statusText?.text = getString(R.string.overlay_inserted)
                
                // Auto-hide after successful insertion
                statusText?.postDelayed({
                    hideOverlay()
                }, 1000)
            } else {
                updateUI(State.ERROR)
                statusText?.text = getString(R.string.overlay_no_focus)
                statusText?.postDelayed({ updateUI(State.IDLE) }, 2000)
            }
        } else {
            // Accessibility service not available - copy to clipboard
            val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("WhisperType", text)
            clipboardManager.setPrimaryClip(clip)
            
            Toast.makeText(this, "Text copied to clipboard. Paste manually.", Toast.LENGTH_SHORT).show()
            updateUI(State.IDLE)
        }
    }
    
    /**
     * Update UI based on current state
     */
    private fun updateUI(state: State) {
        when (state) {
            State.IDLE -> {
                statusText?.text = getString(R.string.overlay_ready)
                micButton?.setBackgroundResource(R.drawable.mic_button_background)
                previewText?.visibility = View.GONE
            }
            State.LISTENING -> {
                statusText?.text = getString(R.string.overlay_listening)
                micButton?.setBackgroundResource(R.drawable.mic_button_listening)
            }
            State.PROCESSING -> {
                statusText?.text = getString(R.string.overlay_processing)
                micButton?.setBackgroundResource(R.drawable.mic_button_listening)
            }
            State.SUCCESS -> {
                statusText?.text = getString(R.string.overlay_inserted)
                micButton?.setBackgroundResource(R.drawable.mic_button_background)
            }
            State.ERROR -> {
                // Error message set by caller
                micButton?.setBackgroundResource(R.drawable.mic_button_background)
            }
        }
    }
    
    /**
     * Create notification channel for foreground service (required on API 26+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create foreground service notification
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Overlay UI states
     */
    enum class State {
        IDLE,
        LISTENING,
        PROCESSING,
        SUCCESS,
        ERROR
    }
}
