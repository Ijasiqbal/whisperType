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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.LayoutTransition
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.whispertype.app.MainActivity
import com.whispertype.app.R
import com.whispertype.app.Constants
import com.whispertype.app.audio.AudioRecorder
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.speech.SpeechRecognitionHelper
import com.whispertype.app.view.CircularWaveformView
import com.whispertype.app.view.LinearWaveformView
import java.lang.ref.WeakReference

/**
 * Extension function to safely cancel and clean up an ObjectAnimator
 * Prevents memory leaks by properly removing listeners
 */
private fun ObjectAnimator?.cancelAndCleanup(): ObjectAnimator? {
    this?.let { animator ->
        if (animator.isRunning) {
            animator.cancel()
        }
        animator.removeAllListeners()
        animator.removeAllUpdateListeners()
    }
    return null
}

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
 * 
 * MEMORY SAFETY:
 * - Uses WeakReferences for UI elements to prevent leaks
 * - Properly cleans up animators and callbacks
 * - Thread-safe state management
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
    }
    
    private var windowManager: WindowManager? = null
    
    // Use WeakReference to prevent memory leaks if service is destroyed
    private var overlayViewRef: WeakReference<View>? = null
    private val overlayView: View? get() = overlayViewRef?.get()
    
    @Volatile
    private var isOverlayVisible = false
    
    private var speechHelper: SpeechRecognitionHelper? = null
    
    // UI elements - using WeakReferences to prevent leaks
    private var micButtonRef: WeakReference<FrameLayout>? = null
    private val micButton: FrameLayout? get() = micButtonRef?.get()
    
    private var micIconRef: WeakReference<ImageView>? = null
    private val micIcon: ImageView? get() = micIconRef?.get()
    
    private var statusTextRef: WeakReference<TextView>? = null
    private val statusText: TextView? get() = statusTextRef?.get()
    
    private var previewTextRef: WeakReference<TextView>? = null
    private val previewText: TextView? get() = previewTextRef?.get()
    
    private var closeButtonRef: WeakReference<ImageButton>? = null
    private val closeButton: ImageButton? get() = closeButtonRef?.get()
    
    private var progressIndicatorRef: WeakReference<View>? = null
    private val progressIndicator: View? get() = progressIndicatorRef?.get()
    
    private var copyButtonRef: WeakReference<android.widget.Button>? = null
    private val copyButton: android.widget.Button? get() = copyButtonRef?.get()
    
    // Pill container for background changes
    private var pillContainerRef: WeakReference<View>? = null
    private val pillContainer: View? get() = pillContainerRef?.get()
    
    // Mic button wrapper (contains pulse rings and mic button)
    private var micButtonWrapperRef: WeakReference<View>? = null
    private val micButtonWrapper: View? get() = micButtonWrapperRef?.get()
    
    // Pulse ring UI elements (Liquid Glass style)
    private var pulseRingInnerRef: WeakReference<View>? = null
    private val pulseRingInner: View? get() = pulseRingInnerRef?.get()
    
    private var pulseRingOuterRef: WeakReference<View>? = null
    private val pulseRingOuter: View? get() = pulseRingOuterRef?.get()
    
    private var successIconRef: WeakReference<ImageView>? = null
    private val successIcon: ImageView? get() = successIconRef?.get()
    
    private var errorIconRef: WeakReference<ImageView>? = null
    private val errorIcon: ImageView? get() = errorIconRef?.get()
    
    private var clipboardIconRef: WeakReference<ImageView>? = null
    private val clipboardIcon: ImageView? get() = clipboardIconRef?.get()
    
    // Linear waveform visualizer (for pill layout)
    private var linearWaveformRef: WeakReference<LinearWaveformView>? = null
    private val linearWaveform: LinearWaveformView? get() = linearWaveformRef?.get()
    
    // Circular waveform visualizer (legacy, kept for reference)
    private var circularWaveformRef: WeakReference<CircularWaveformView>? = null
    private val circularWaveform: CircularWaveformView? get() = circularWaveformRef?.get()
    
    // Pending text for clipboard copy (when no text field is focused)
    private var pendingText: String? = null
    
    // Listening state
    @Volatile
    private var isListening = false
    
    // Voice activity animation
    private var pulseAnimator: ObjectAnimator? = null
    
    // Recording ring animators (iOS-style layered pulse)
    private var ringAnimator: ObjectAnimator? = null
    private var ringOuterAnimator: ObjectAnimator? = null
    
    // Success checkmark animation
    private var successAnimator: ObjectAnimator? = null
    
    // Recording animation (subtle pulse to indicate recording is active)
    private var recordingPulseAnimator: ObjectAnimator? = null
    
    @Volatile
    private var isSpeaking = false
    
    // Battery optimization: Handler for batching UI updates
    private val uiHandler = Handler(Looper.getMainLooper())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService created")
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Initialize speech recognition
        speechHelper = SpeechRecognitionHelper(this, object : SpeechRecognitionHelper.Callback {
            override fun onReadyForSpeech() {
                updateUI(State.RECORDING)
            }
            
            override fun onBeginningOfSpeech() {
                // Already in listening state
            }
            
            override fun onPartialResults(partialText: String) {
                // Battery optimization: Batch UI updates to reduce main thread wakeups
                uiHandler.removeCallbacksAndMessages("partial")
                uiHandler.postAtTime({
                    previewText?.visibility = View.VISIBLE
                    previewText?.text = partialText
                }, "partial", System.currentTimeMillis() + 50)  // 50ms debounce
            }
            
            override fun onResults(finalText: String) {
                Log.d(TAG, "Final result: $finalText")
                handleRecognitionResult(finalText)
            }
            
            override fun onError(errorMessage: String) {
                Log.e(TAG, "Speech error: $errorMessage")
                updateUI(State.ERROR)
                statusText?.text = errorMessage
                
                // Reset to ready state after a delay (using shared handler for better battery)
                uiHandler.postDelayed({
                    if (!isListening) {
                        updateUI(State.IDLE)
                    }
                }, Constants.ERROR_MESSAGE_DELAY_MS)
            }
            
            override fun onEndOfSpeech() {
                updateUI(State.PROCESSING)
            }
            
            override fun onTranscribing() {
                updateUI(State.TRANSCRIBING)
            }
        })
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}, isOverlayVisible=$isOverlayVisible")
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        when (intent?.action) {
            ACTION_SHOW -> {
                Log.d(TAG, "ACTION_SHOW received")
                showOverlay()
            }
            ACTION_HIDE -> {
                Log.d(TAG, "ACTION_HIDE received")
                hideOverlay()
            }
            ACTION_TOGGLE -> {
                Log.d(TAG, "ACTION_TOGGLE received, will ${if (isOverlayVisible) "hide" else "show"}")
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
        // Clean up in proper order to prevent leaks
        hideOverlay()
        
        // Destroy speech helper and release resources
        speechHelper?.destroy()
        speechHelper = null
        
        // Clean up UI handler to prevent leaks
        uiHandler.removeCallbacksAndMessages(null)
        
        // Clean up window manager reference
        windowManager = null
        
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
        
        // Inflate the new pill overlay layout
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.overlay_pill_view, null)
        overlayViewRef = WeakReference(view)
        
        // Get UI references for pill layout
        pillContainerRef = WeakReference(view.findViewById(R.id.pill_container))
        micButtonWrapperRef = WeakReference(view.findViewById(R.id.mic_button_wrapper))
        micButtonRef = WeakReference(view.findViewById(R.id.mic_button_container))
        micIconRef = WeakReference(view.findViewById(R.id.ic_mic))
        statusTextRef = WeakReference(view.findViewById(R.id.tv_status))
        previewTextRef = WeakReference(view.findViewById(R.id.tv_preview))
        closeButtonRef = WeakReference(view.findViewById(R.id.btn_close))
        progressIndicatorRef = WeakReference(view.findViewById(R.id.progress_indicator))
        copyButtonRef = WeakReference(view.findViewById(R.id.btn_copy))
        
        // Pulse ring UI elements (Liquid Glass style)
        pulseRingInnerRef = WeakReference(view.findViewById(R.id.pulse_ring_inner))
        pulseRingOuterRef = WeakReference(view.findViewById(R.id.pulse_ring_outer))
        successIconRef = WeakReference(view.findViewById(R.id.ic_success))
        errorIconRef = WeakReference(view.findViewById(R.id.ic_error))
        clipboardIconRef = WeakReference(view.findViewById(R.id.ic_clipboard))
        
        // Linear waveform visualizer (inline in pill)
        linearWaveformRef = WeakReference(view.findViewById(R.id.linear_waveform))
        
        // Enable smooth layout transitions for state changes
        setupLayoutTransitions()
        
        // Configure window parameters
        val layoutParams = createLayoutParams()
        
        // Set up touch handling for dragging
        setupTouchHandling(view, layoutParams)
        
        // Set up button clicks
        setupButtonHandlers()
        
        // Add view to window
        try {
            windowManager?.addView(view, layoutParams)
            isOverlayVisible = true
            updateUI(State.IDLE)
            
            // Auto-start recording when overlay is shown
            view.post {
                startListening()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
            Toast.makeText(this, "Failed to show overlay", Toast.LENGTH_SHORT).show()
            cleanupViewReferences()
        }
    }
    
    /**
     * Hide the overlay from screen and clean up resources
     */
    fun hideOverlay() {
        if (!isOverlayVisible) {
            return
        }
        
        Log.d(TAG, "Hiding overlay")
        
        // Stop any ongoing speech recognition
        if (isListening) {
            try {
                speechHelper?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping speech recognition", e)
            }
            isListening = false
        }
        
        // Stop pulse animation to prevent leaks
        stopPulseAnimation()
        stopRecordingPulseAnimation()
        
        // Remove view from window manager
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            }
        }
        
        // Clean up all view references
        cleanupViewReferences()
        
        isOverlayVisible = false
        
        // Stop the service when overlay is hidden
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service", e)
        }
    }
    
    private fun cleanupViewReferences() {
        overlayViewRef = null
        pillContainerRef = null
        micButtonWrapperRef = null
        micButtonRef = null
        micIconRef = null
        statusTextRef = null
        previewTextRef = null
        closeButtonRef = null
        progressIndicatorRef = null
        copyButtonRef = null
        pulseRingInnerRef = null
        pulseRingOuterRef = null
        successIconRef = null
        errorIconRef = null
        clipboardIconRef = null
        linearWaveformRef = null
        circularWaveformRef = null
        pendingText = null
        
        // Clean up animators
        stopRingAnimations()
        stopSuccessAnimation()
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
        val container = view.findViewById<View>(R.id.pill_container)
        
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
        
        // Copy button copies pending text to clipboard
        copyButton?.setOnClickListener {
            copyToClipboard()
        }
    }
    
    /**
     * Set up smooth layout transitions for state changes
     * Adds subtle easing animations when pill size/content changes
     */
    private fun setupLayoutTransitions() {
        // Enable layout transitions on pill container
        (pillContainer as? LinearLayout)?.let { container ->
            val transition = LayoutTransition().apply {
                // Set animation durations (short for snappy feel)
                setDuration(150L)
                
                // Enable all transition types
                enableTransitionType(LayoutTransition.CHANGING)
                enableTransitionType(LayoutTransition.APPEARING)
                enableTransitionType(LayoutTransition.DISAPPEARING)
                enableTransitionType(LayoutTransition.CHANGE_APPEARING)
                enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
                
                // Use smooth interpolator
                setInterpolator(LayoutTransition.CHANGE_APPEARING, AccelerateDecelerateInterpolator())
                setInterpolator(LayoutTransition.CHANGE_DISAPPEARING, AccelerateDecelerateInterpolator())
                setInterpolator(LayoutTransition.CHANGING, AccelerateDecelerateInterpolator())
            }
            container.layoutTransition = transition
        }
        
        // Also enable on the text container inside the pill
        overlayView?.findViewById<LinearLayout>(R.id.pill_container)?.let { pill ->
            // Find the text container (first LinearLayout child)
            for (i in 0 until pill.childCount) {
                val child = pill.getChildAt(i)
                if (child is LinearLayout) {
                    child.layoutTransition = LayoutTransition().apply {
                        setDuration(120L)
                        enableTransitionType(LayoutTransition.CHANGING)
                    }
                    break
                }
            }
        }
    }
    
    /**
     * Start speech recognition
     */
    private fun startListening() {
        Log.d(TAG, "Starting speech recognition")
        
        // === ITERATION 2: Check trial status before starting ===
        val usageState = UsageDataManager.usageState.value
        Log.d(TAG, "Trial check: status=${usageState.trialStatus}, isTrialValid=${usageState.isTrialValid}")
        
        if (!usageState.isTrialValid) {
            Log.w(TAG, "Trial expired, blocking transcription")
            Toast.makeText(
                this,
                "Your free trial has ended. Open WhisperType to learn more.",
                Toast.LENGTH_LONG
            ).show()
            hideOverlay()
            return
        }
        
        // Show warning toast if at warning threshold (but still allow transcription)
        when (usageState.warningLevel) {
            UsageDataManager.WarningLevel.NINETY_FIVE_PERCENT -> {
                Toast.makeText(
                    this,
                    "⚠️ You're almost out of free trial minutes!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            UsageDataManager.WarningLevel.EIGHTY_PERCENT -> {
                Toast.makeText(
                    this,
                    "You've used 80% of your free trial",
                    Toast.LENGTH_SHORT
                ).show()
            }
            UsageDataManager.WarningLevel.FIFTY_PERCENT -> {
                // Only show 50% warning occasionally (not every time)
                val lastWarning = usageState.lastUpdated
                if (System.currentTimeMillis() - lastWarning > 60000) { // Once per minute max
                    Toast.makeText(
                        this,
                        "You've used half of your free trial",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            else -> { /* No warning needed */ }
        }
        
        // Check if microphone permission is granted
        if (!speechHelper!!.hasPermission()) {
            Toast.makeText(
                this,
                "Microphone permission required. Open WhisperType to grant it.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Note: We no longer check WhisperTypeAccessibilityService.isRunning() here
        // because recording audio doesn't require the accessibility service.
        // The accessibility service is only needed when inserting text, which is
        // checked in handleRecognitionResult(). This fixes the issue where the
        // overlay wouldn't work after app process restart (singleton was null).
        
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
        
        // Note: Don't set IDLE state here - onEndOfSpeech callback will set PROCESSING state,
        // then onTranscribing will set TRANSCRIBING state
    }
    
    /**
     * Handle voice amplitude updates for animation
     * Thread-safe handling of amplitude callbacks
     */
    private fun handleVoiceAmplitude(amplitude: Int) {
        if (!isOverlayVisible) return
        
        // Debug logging
        Log.d(TAG, "Amplitude received: $amplitude")
        
        val wasSpeaking = isSpeaking
        isSpeaking = amplitude > Constants.VOICE_ACTIVITY_THRESHOLD
        
        if (isSpeaking && !wasSpeaking) {
            // Started speaking - begin pulse animation
            startPulseAnimation()
        } else if (!isSpeaking && wasSpeaking) {
            // Stopped speaking - stop pulse animation
            stopPulseAnimation()
        }
        
        // Animate waveform bars based on current amplitude (ensure on main thread)
        uiHandler.post {
            updateAmplitudeBars(amplitude)
        }
    }
    
    /**
     * Update waveform based on current audio amplitude
     */
    private fun updateAmplitudeBars(amplitude: Int) {
        // Use linear waveform for pill layout
        linearWaveform?.updateAmplitude(amplitude)
        // Legacy circular waveform (if still in use)
        circularWaveform?.updateAmplitude(amplitude)
    }
    
    /**
     * Reset waveform to default state and hide it
     */
    private fun resetAmplitudeBars() {
        linearWaveform?.visibility = View.GONE
        linearWaveform?.reset()
        circularWaveform?.visibility = View.GONE
        circularWaveform?.reset()
    }
    
    /**
     * Show the waveform visualizer
     */
    private fun showWaveform() {
        linearWaveform?.visibility = View.VISIBLE
        // Legacy: circularWaveform?.visibility = View.VISIBLE
    }
    
    /**
     * Start pulse animation on mic button to indicate voice detection
     * Properly manages animator lifecycle to prevent leaks
     * Battery optimized: Uses hardware layer for smooth animation
     */
    private fun startPulseAnimation() {
        // Stop any existing animation first
        if (pulseAnimator?.isRunning == true) return
        
        micButton?.let { button ->
            // Battery optimization: Use hardware layer during animation
            button.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.15f, 1.0f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.15f, 1.0f)
            
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(button, scaleX, scaleY).apply {
                duration = Constants.PULSE_ANIMATION_DURATION_MS
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }
    
    /**
     * Stop pulse animation and clean up animator to prevent leaks
     * Battery optimized: Resets hardware layer
     */
    private fun stopPulseAnimation() {
        pulseAnimator = pulseAnimator.cancelAndCleanup()
        
        // Reset scale to normal and restore layer type
        micButton?.apply {
            scaleX = 1.0f
            scaleY = 1.0f
            setLayerType(View.LAYER_TYPE_NONE, null)  // Battery: Remove hardware layer
        }
        isSpeaking = false
    }
    
    /**
     * Start subtle recording pulse animation to indicate recording is active
     * Animates the purple button container for better visibility
     */
    private fun startRecordingPulseAnimation() {
        if (recordingPulseAnimator?.isRunning == true) return
        
        micButton?.let { button ->
            button.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // Subtle scale animation (1.0 -> 1.05 -> 1.0) on the purple button
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.05f, 1.0f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.05f, 1.0f)
            
            recordingPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(button, scaleX, scaleY).apply {
                duration = 800L  // Slower, more subtle than voice detection
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }
    
    /**
     * Stop recording pulse animation
     */
    private fun stopRecordingPulseAnimation() {
        recordingPulseAnimator = recordingPulseAnimator.cancelAndCleanup()
        
        // Reset the purple button scale
        micButton?.apply {
            scaleX = 1.0f
            scaleY = 1.0f
            setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }
    
    /**
     * Start iOS-style recording ring animations
     * Creates a layered pulsing ring effect behind the mic button
     */
    private fun startRingAnimations() {
        // Stop any existing ring animations
        stopRingAnimations()
        
        // Make rings visible
        pulseRingInner?.visibility = View.VISIBLE
        pulseRingOuter?.visibility = View.VISIBLE
        
        // Inner ring animation - faster, smaller
        pulseRingInner?.let { ring ->
            ring.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.25f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.25f)
            val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.8f, 0f)
            
            ringAnimator = ObjectAnimator.ofPropertyValuesHolder(ring, scaleX, scaleY, alpha).apply {
                duration = 1200L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
        
        // Outer ring animation - slower, larger, delayed
        pulseRingOuter?.let { ring ->
            ring.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.35f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.35f)
            val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.5f, 0f)
            
            ringOuterAnimator = ObjectAnimator.ofPropertyValuesHolder(ring, scaleX, scaleY, alpha).apply {
                duration = 1500L
                startDelay = 300L  // Start slightly after inner ring
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }
    
    /**
     * Stop ring animations and clean up
     */
    private fun stopRingAnimations() {
        ringAnimator = ringAnimator.cancelAndCleanup()
        ringOuterAnimator = ringOuterAnimator.cancelAndCleanup()
        
        // Reset and hide rings
        pulseRingInner?.apply {
            scaleX = 1.0f
            scaleY = 1.0f
            alpha = 0f
            visibility = View.GONE
            setLayerType(View.LAYER_TYPE_NONE, null)
        }
        pulseRingOuter?.apply {
            scaleX = 1.0f
            scaleY = 1.0f
            alpha = 0f
            visibility = View.GONE
            setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }
    
    /**
     * Play success checkmark animation
     * Scales the checkmark from 0 to 1 with a satisfying overshoot bounce
     */
    private fun playSuccessAnimation() {
        successIcon?.let { icon ->
            // Hide mic icon, show success icon
            micIcon?.visibility = View.GONE
            icon.visibility = View.VISIBLE
            icon.scaleX = 0f
            icon.scaleY = 0f
            
            icon.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0f, 1.15f, 1f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0f, 1.15f, 1f)
            
            successAnimator = ObjectAnimator.ofPropertyValuesHolder(icon, scaleX, scaleY).apply {
                duration = 400L
                interpolator = android.view.animation.OvershootInterpolator(2f)
                start()
            }
        }
    }
    
    /**
     * Stop success animation and reset
     */
    private fun stopSuccessAnimation() {
        successAnimator = successAnimator.cancelAndCleanup()
        
        // Hide success icon
        successIcon?.apply {
            visibility = View.GONE
            scaleX = 0f
            scaleY = 0f
            setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }
    
    /**
     * Play shake animation on error
     * Subtle horizontal shake to indicate something went wrong
     */
    private fun playShakeAnimation() {
        val button = micButton ?: return
        // Use WeakReference to prevent memory leak in listener
        val buttonRef = WeakReference(button)
        
        button.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        ObjectAnimator.ofFloat(
            button, View.TRANSLATION_X,
            0f, -8f, 8f, -8f, 8f, -4f, 4f, 0f
        ).apply {
            duration = 400L
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    buttonRef.get()?.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
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
                
                // Auto-hide after successful insertion (using shared handler)
                uiHandler.postDelayed({
                    hideOverlay()
                }, Constants.SUCCESS_MESSAGE_DELAY_MS)
            } else {
                // No text field focused - show copy button option
                pendingText = text
                updateUI(State.NO_FOCUS)
                statusText?.text = getString(R.string.overlay_no_focus)
                copyButton?.visibility = View.VISIBLE
            }
        } else {
            // Accessibility service not available - show copy button option
            // This can happen if the service was killed or not fully initialized yet
            pendingText = text
            updateUI(State.NO_FOCUS)
            statusText?.text = "Tap copy to save to clipboard"
            copyButton?.visibility = View.VISIBLE
        }
    }
    
    /**
     * Update UI based on current state
     * Uses Liquid Glass effects and animations for pill layout
     */
    private fun updateUI(state: State) {
        // Reset visibility of elements that may be hidden in certain states
        errorIcon?.visibility = View.GONE
        clipboardIcon?.visibility = View.GONE
        micButtonWrapper?.visibility = View.VISIBLE  // Restore wrapper if coming from NO_FOCUS
        micButton?.visibility = View.VISIBLE
        
        when (state) {
            State.IDLE -> {
                statusText?.text = getString(R.string.overlay_ready)
                pillContainer?.setBackgroundResource(R.drawable.pill_liquid_glass_background)
                micButton?.setBackgroundResource(R.drawable.mic_button_liquid_glass)
                previewText?.visibility = View.GONE
                progressIndicator?.visibility = View.GONE
                micIcon?.visibility = View.VISIBLE
                copyButton?.visibility = View.GONE
                // Reset animations
                stopRingAnimations()
                stopSuccessAnimation()
                resetAmplitudeBars()
            }
            State.RECORDING -> {
                statusText?.text = getString(R.string.overlay_recording)
                pillContainer?.setBackgroundResource(R.drawable.pill_border_recording)
                micButton?.setBackgroundResource(R.drawable.mic_button_liquid_glass)
                progressIndicator?.visibility = View.GONE
                micIcon?.visibility = View.VISIBLE
                copyButton?.visibility = View.GONE
                stopSuccessAnimation()
                // Start pulse ring animation
                startRingAnimations()
                // Show inline waveform during recording
                showWaveform()
            }
            State.PROCESSING -> {
                statusText?.text = getString(R.string.overlay_processing)
                pillContainer?.setBackgroundResource(R.drawable.pill_liquid_glass_background)
                micButton?.setBackgroundResource(R.drawable.mic_button_liquid_glass)
                progressIndicator?.visibility = View.VISIBLE
                micIcon?.visibility = View.GONE
                copyButton?.visibility = View.GONE
                // Stop all recording animations
                stopPulseAnimation()
                stopRecordingPulseAnimation()
                stopRingAnimations()
                stopSuccessAnimation()
                resetAmplitudeBars()
            }
            State.TRANSCRIBING -> {
                statusText?.text = getString(R.string.overlay_transcribing)
                pillContainer?.setBackgroundResource(R.drawable.pill_liquid_glass_background)
                micButton?.setBackgroundResource(R.drawable.mic_button_liquid_glass)
                progressIndicator?.visibility = View.VISIBLE
                micIcon?.visibility = View.GONE
                copyButton?.visibility = View.GONE
                resetAmplitudeBars()
            }
            State.SUCCESS -> {
                statusText?.text = getString(R.string.overlay_inserted)
                pillContainer?.setBackgroundResource(R.drawable.pill_border_success)
                micButton?.setBackgroundResource(R.drawable.mic_button_liquid_glass)
                progressIndicator?.visibility = View.GONE
                copyButton?.visibility = View.GONE
                stopRingAnimations()
                resetAmplitudeBars()
                // Play success checkmark animation
                playSuccessAnimation()
            }
            State.ERROR -> {
                // Error message set by caller
                pillContainer?.setBackgroundResource(R.drawable.pill_border_error)
                micButton?.setBackgroundResource(R.drawable.mic_button_liquid_glass)
                progressIndicator?.visibility = View.GONE
                micIcon?.visibility = View.GONE
                errorIcon?.visibility = View.VISIBLE
                copyButton?.visibility = View.GONE
                stopRingAnimations()
                stopSuccessAnimation()
                resetAmplitudeBars()
                // Play shake animation for error feedback
                playShakeAnimation()
            }
            State.NO_FOCUS -> {
                // No text field focused - show combined copy button (icon + text)
                pillContainer?.setBackgroundResource(R.drawable.pill_liquid_glass_background)
                previewText?.visibility = View.GONE
                progressIndicator?.visibility = View.GONE
                // Hide the entire mic button wrapper - copy button has the icon now
                micButtonWrapper?.visibility = View.GONE
                stopRingAnimations()
                stopSuccessAnimation()
                resetAmplitudeBars()
                // copyButton visibility is set by caller
            }
        }
    }
    
    /**
     * Copy pending text to clipboard
     */
    private fun copyToClipboard() {
        val text = pendingText
        if (text.isNullOrBlank()) {
            return
        }
        
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("WhisperType", text)
        clipboardManager.setPrimaryClip(clip)
        
        // Show success feedback
        updateUI(State.SUCCESS)
        statusText?.text = getString(R.string.overlay_copied)
        copyButton?.visibility = View.GONE
        pendingText = null
        
        // Auto-hide after copying
        uiHandler.postDelayed({
            hideOverlay()
        }, Constants.SUCCESS_MESSAGE_DELAY_MS)
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
        RECORDING,
        PROCESSING,
        TRANSCRIBING,
        SUCCESS,
        ERROR,
        NO_FOCUS
    }
}
