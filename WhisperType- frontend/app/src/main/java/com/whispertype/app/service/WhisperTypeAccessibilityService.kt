package com.whispertype.app.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.whispertype.app.Constants
import com.whispertype.app.MainActivity
import com.whispertype.app.R
import com.whispertype.app.ShortcutPreferences

/**
 * WhisperTypeAccessibilityService
 * 
 * This is the core accessibility service that:
 * 1. Detects volume up double-press to trigger the overlay
 * 2. Receives accessibility shortcut events (accessibility button)
 * 3. Can access the currently focused input field
 * 4. Can insert text into the focused field
 * 
 * VOLUME BUTTON ACTIVATION:
 * - Double-press volume up within 500ms to trigger the overlay
 * - Single presses still control volume normally
 * - Uses onKeyEvent() to intercept key events
 * 
 * ACCESSIBILITY BUTTON SUPPORT (API 26+):
 * - Uses AccessibilityButtonController for proper callback registration
 * - Works with the floating accessibility button if user prefers
 * 
 * TEXT INSERTION STRATEGY:
 * - Primary: ACTION_SET_TEXT - Sets the entire text of the focused field
 * - Fallback: Clipboard + ACTION_PASTE - Copy text to clipboard and paste
 * - Limitation: Some apps (banking, secure fields) block accessibility actions
 * 
 * PRIVACY:
 * - We only access window content to find the focused editable field
 * - We do NOT log or store any content from user's screen
 */
class WhisperTypeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WhisperTypeA11y"
        
        // Foreground service notification
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "whispertype_accessibility_channel"
        
        // Singleton instance for other components to communicate with
        var instance: WhisperTypeAccessibilityService? = null
            private set

        @Volatile
        private var isConnected: Boolean = false
        
        /**
         * Check if the service is currently running
         */
        fun isRunning(): Boolean = isConnected
    }
    
    // Volume key tracking for double-press detection
    // lastVolume*Time tracks the most recent press of each button (used in BOTH_VOLUME_BUTTONS mode)
    // previousVolume*Time tracks the previous press of the same button (for double-press detection)
    // lastBothButtonsTime tracks when we last triggered in "both buttons" mode (to prevent repeated triggers)
    // lastPressedButton tracks which button was pressed last (to detect alternating presses = both buttons gesture)
    private var lastVolumeUpTime: Long = 0
    private var lastVolumeDownTime: Long = 0
    private var previousVolumeUpTime: Long = 0
    private var previousVolumeDownTime: Long = 0
    private var lastBothButtonsTime: Long = 0
    private var lastPressedButton: Int = 0 // 0 = none, KEYCODE_VOLUME_UP or KEYCODE_VOLUME_DOWN
    
    // Accessibility button controller (API 26+)
    private var accessibilityButtonController: AccessibilityButtonController? = null
    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Auto-show mic icon: debounce and dispatch state
    private var lastFocusEventTime: Long = 0
    private val focusDebounceMs: Long = 150
    private var lastContentChangedTime: Long = 0
    private val contentChangedDebounceMs: Long = 400
    private var lastDispatchedShown: Boolean = false

    // Floating mic icon overlay (TYPE_ACCESSIBILITY_OVERLAY)
    private var floatingMicView: ImageView? = null
    private var floatingMicParams: WindowManager.LayoutParams? = null
    private var isMicIconShown = false

    // Drag tracking for floating icon
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialParamX = 0
    private var initialParamY = 0
    private var isDragging = false
    private val dragThreshold = 10
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "AccessibilityService created, instance set")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isConnected = true
        Log.d(TAG, "AccessibilityService connected")
        
        // Configure the service
        serviceInfo = serviceInfo.apply {
            // Request the accessibility button/shortcut capability and key event filtering
            flags = flags or 
                AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }

        // Apply (optional) keep-alive foreground notification mode.
        applyForegroundModeFromPrefs()
        
        // Set up accessibility button callback (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupAccessibilityButton()
        }
    }
    
    /**
     * Set up the accessibility button callback for API 26+
     * This handles the floating accessibility button tap
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupAccessibilityButton() {
        accessibilityButtonController = getAccessibilityButtonController()
        
        if (accessibilityButtonController == null) {
            Log.w(TAG, "AccessibilityButtonController is not available")
            return
        }
        
        accessibilityButtonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                Log.d(TAG, "Accessibility button clicked")
                toggleOverlay()
            }
            
            override fun onAvailabilityChanged(controller: AccessibilityButtonController, available: Boolean) {
                Log.d(TAG, "Accessibility button availability changed: $available")
            }
        }
        
        accessibilityButtonController?.registerAccessibilityButtonCallback(accessibilityButtonCallback!!)
        Log.d(TAG, "Accessibility button callback registered")
    }
    
    override fun onDestroy() {
        // Unregister accessibility button callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && accessibilityButtonCallback != null) {
            accessibilityButtonController?.unregisterAccessibilityButtonCallback(accessibilityButtonCallback!!)
        }
        
        // Ensure the floating mic icon is removed when the service is torn down
        removeFloatingMicIcon()
        lastDispatchedShown = false

        isConnected = false
        instance = null
        Log.d(TAG, "AccessibilityService destroyed")
        super.onDestroy()
    }

    /**
     * Called from the app UI after the user toggles the keep-alive preference.
     */
    fun refreshForegroundMode() {
        applyForegroundModeFromPrefs()
    }
    
    /**
     * Processes accessibility events for focus-field detection to drive the
     * auto-show floating mic icon feature. Gated by [ShortcutPreferences.isAutoShowIconEnabled]
     * so there is zero cost when the feature is off.
     *
     * - TYPE_VIEW_FOCUSED: evaluates whether the focused node is a dictatable text
     *   field (editable, visible, non-password, non-sensitive input type). If so,
     *   shows a floating mic icon via TYPE_ACCESSIBILITY_OVERLAY; otherwise hides it.
     *   Debounced to 150 ms to suppress burst events from a single user tap.
     * - TYPE_WINDOW_STATE_CHANGED: user switched apps/activities — hides the icon.
     * - TYPE_WINDOW_CONTENT_CHANGED: checks if editable focus is lost (debounced 400ms).
     * - All other event types are ignored.
     *
     * Privacy: no content from event.source (text, contentDescription, etc.) is logged.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Master gate — feature disabled means zero cost
        if (!ShortcutPreferences.isAutoShowIconEnabled(this)) return
        if (event == null) return

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Ignore events from our own package — adding the accessibility
                    // overlay fires this event, which would immediately hide the icon.
                    if (event.packageName?.toString() == packageName) return

                    // Only hide if there is no longer an editable field focused.
                    // A blind hide causes flicker when switching between fields.
                    val focused = findFocusedEditableNode()
                    if (focused == null) {
                        dispatchHideIcon()
                    } else {
                        focused.recycle()
                    }
                    return
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    val now = System.currentTimeMillis()
                    if (now - lastFocusEventTime < focusDebounceMs) return
                    lastFocusEventTime = now

                    val source = event.source ?: run {
                        dispatchHideIcon()
                        return
                    }
                    try {
                        val isDictatable = source.isEditable &&
                            source.isVisibleToUser &&
                            !source.isPassword &&
                            isDictatableInputType(source.inputType)
                        if (isDictatable) {
                            dispatchShowIcon()
                        } else {
                            dispatchHideIcon()
                        }
                    } finally {
                        source.recycle()
                    }
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // When window content changes and we're currently showing the icon,
                    // check if an editable field still has focus. If not, hide the icon.
                    // Debounced at 400ms because this event fires very frequently
                    // (every keystroke, scroll, layout change).
                    if (lastDispatchedShown) {
                        val now = System.currentTimeMillis()
                        if (now - lastContentChangedTime < contentChangedDebounceMs) return
                        lastContentChangedTime = now

                        val focusedNode = findFocusedEditableNode()
                        if (focusedNode == null) {
                            dispatchHideIcon()
                        } else {
                            focusedNode.recycle()
                        }
                    }
                }
                else -> {
                    // Ignore other event types
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "onAccessibilityEvent error", t)
        }
    }
    
    /**
     * Returns true if the given inputType is one we're willing to dictate into.
     * Uses a blacklist approach: allow everything EXCEPT known non-dictatable types.
     * Many apps (especially Compose-based UIs) report inputType=0 (TYPE_NULL),
     * so a whitelist requiring TYPE_CLASS_TEXT misses most real-world fields.
     */
    private fun isDictatableInputType(inputType: Int): Boolean {
        val cls = inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION

        // Reject phone and datetime classes — nobody dictates into these
        if (cls == android.text.InputType.TYPE_CLASS_PHONE) return false
        if (cls == android.text.InputType.TYPE_CLASS_DATETIME) return false

        // For text class, reject password variations
        if (cls == android.text.InputType.TYPE_CLASS_TEXT) {
            return when (variation) {
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> false
                else -> true
            }
        }

        // TYPE_NULL (0), TYPE_CLASS_NUMBER, and anything else: allow if the node
        // is editable (the isEditable check in the caller already gates this).
        // Many real text fields report TYPE_NULL — we must not reject them.
        return true
    }

    // ── Floating mic icon overlay (TYPE_ACCESSIBILITY_OVERLAY) ──────────

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun getSavedMicX(): Int =
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("auto_show_mic_x", 16.dp())

    private fun getSavedMicY(): Int =
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("auto_show_mic_y", 140.dp())

    private fun saveMicPosition(x: Int, y: Int) {
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt("auto_show_mic_x", x)
            .putInt("auto_show_mic_y", y)
            .apply()
    }

    /**
     * Re-check focus and show the floating mic icon if an editable field is
     * currently focused. Used after the recording overlay closes, since focus
     * usually returns to the same field without firing TYPE_VIEW_FOCUSED.
     */
    fun reevaluateFocusForIcon() {
        if (!ShortcutPreferences.isAutoShowIconEnabled(this)) return
        // Delay slightly to let window focus settle after our overlay is removed.
        mainHandler.postDelayed({
            try {
                val focused = findFocusedEditableNode() ?: return@postDelayed
                try {
                    val isDictatable = focused.isVisibleToUser &&
                        !focused.isPassword &&
                        isDictatableInputType(focused.inputType)
                    if (isDictatable) dispatchShowIcon()
                } finally {
                    focused.recycle()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "reevaluateFocusForIcon error", t)
            }
        }, 250)
    }

    /**
     * Show the floating mic icon only if it is not already shown.
     */
    private fun dispatchShowIcon() {
        if (lastDispatchedShown) return
        lastDispatchedShown = true
        mainHandler.post { showFloatingMicIcon() }
    }

    /**
     * Hide the floating mic icon only if it is currently shown.
     */
    private fun dispatchHideIcon() {
        if (!lastDispatchedShown) return
        lastDispatchedShown = false
        mainHandler.post { removeFloatingMicIcon() }
    }

    /**
     * Add floating mic icon to WindowManager using TYPE_ACCESSIBILITY_OVERLAY.
     * This window type is trusted (no untrusted-touch blocking on Android 12+),
     * doesn't need SYSTEM_ALERT_WINDOW, and doesn't require a separate service.
     */
    private fun showFloatingMicIcon() {
        if (isMicIconShown) return

        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val view = buildFloatingMicView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = getSavedMicX()
            y = getSavedMicY()
        }

        try {
            wm.addView(view, params)
            floatingMicView = view
            floatingMicParams = params
            isMicIconShown = true

            // Entrance animation
            view.scaleX = 0.6f
            view.scaleY = 0.6f
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show floating mic icon", e)
        }
    }

    /**
     * Remove floating mic icon from WindowManager.
     */
    private fun removeFloatingMicIcon() {
        val view = floatingMicView ?: return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove floating mic icon", e)
        } finally {
            floatingMicView = null
            floatingMicParams = null
            isMicIconShown = false
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun buildFloatingMicView(): ImageView {
        val size = 56.dp()
        val padding = 12.dp()

        val bg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(androidx.core.content.ContextCompat.getColor(this@WhisperTypeAccessibilityService, R.color.primary))
        }

        return ImageView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(size, size)
            background = bg
            setPadding(padding, padding, padding, padding)
            setImageResource(R.drawable.ic_microphone)
            imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            contentDescription = "Dictate with Vozcribe"
            isClickable = true
            isFocusable = true
            elevation = 8f

            setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        isDragging = false
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        initialParamX = floatingMicParams?.x ?: 0
                        initialParamY = floatingMicParams?.y ?: 0
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (!isDragging && (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold)) {
                            isDragging = true
                        }
                        if (isDragging) {
                            val params = floatingMicParams ?: return@setOnTouchListener true
                            params.x = initialParamX - dx.toInt()
                            params.y = initialParamY - dy.toInt()
                            try {
                                val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                                wm?.updateViewLayout(floatingMicView, params)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to update view during drag", e)
                            }
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            val params = floatingMicParams
                            if (params != null) saveMicPosition(params.x, params.y)
                        } else {
                            onFloatingMicTapped()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    /**
     * Handle tap on floating mic icon: trigger the recording overlay and hide the icon.
     */
    private fun onFloatingMicTapped() {
        Log.d(TAG, "Floating mic tapped, triggering overlay")
        removeFloatingMicIcon()
        lastDispatchedShown = false
        toggleOverlay()
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    /**
     * Standard Service lifecycle method.
     * We implement this to allow "nudging" the service via startService()
     * from the main app if it gets into a zombie state.
     * 
     * Also handles enabling/disabling foreground service mode dynamically.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called: action=${intent?.action}")

        applyForegroundModeFromPrefs()
        
        // Return START_STICKY to encourage the system to restart the service if killed
        return START_STICKY
    }

    private fun applyForegroundModeFromPrefs() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val isForegroundEnabled = prefs.getBoolean("foreground_service_enabled", false)

        if (isForegroundEnabled) {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Keep-alive enabled but POST_NOTIFICATIONS is not granted; skipping foreground notification")
                return
            }
            try {
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, createNotification())
                Log.d(TAG, "Keep-alive enabled: running in foreground mode")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to enter foreground mode (notification may be blocked)", t)
            }
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                Log.d(TAG, "Keep-alive disabled: foreground mode stopped")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to stop foreground mode", t)
            }
        }
    }
    
    /**
     * Handle key events for volume button detection
     * 
     * Supports three modes based on user preference:
     * - DOUBLE_VOLUME_UP: Double-press volume up within 500ms
     * - DOUBLE_VOLUME_DOWN: Double-press volume down within 500ms  
     * - BOTH_VOLUME_BUTTONS: Press both volume buttons simultaneously
     * 
     * @return true if the event was consumed (don't pass to system), false otherwise
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false
        
        val currentTime = System.currentTimeMillis()
        val keyCode = event.keyCode
        
        val isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        
        if (!isVolumeUp && !isVolumeDown) {
            return false // Not a volume button, don't handle
        }
        
        val mode = ShortcutPreferences.getShortcutMode(this)
        Log.d(TAG, "Key: ${if (isVolumeUp) "UP" else "DOWN"}, mode=$mode")
        
        // Handle each mode with strict button filtering to prevent cross-triggering
        return when (mode) {
            ShortcutPreferences.ShortcutMode.DOUBLE_VOLUME_UP -> {
                // ONLY process volume UP buttons, completely ignore DOWN
                if (!isVolumeUp) {
                    Log.d(TAG, "DOUBLE_VOLUME_UP mode: Ignoring DOWN button")
                    return false
                }
                handleDoubleVolumeUp(event, currentTime)
            }
            ShortcutPreferences.ShortcutMode.DOUBLE_VOLUME_DOWN -> {
                // ONLY process volume DOWN buttons, completely ignore UP
                if (!isVolumeDown) {
                    Log.d(TAG, "DOUBLE_VOLUME_DOWN mode: Ignoring UP button")
                    return false
                }
                handleDoubleVolumeDown(event, currentTime)
            }
            ShortcutPreferences.ShortcutMode.BOTH_VOLUME_BUTTONS -> {
                // Update timestamps for both buttons to detect simultaneous press
                if (isVolumeUp) {
                    lastVolumeUpTime = currentTime
                } else {
                    lastVolumeDownTime = currentTime
                }
                
                // Track which button was just pressed
                lastPressedButton = keyCode
                
                handleBothButtons(event, currentTime)
            }
        }
    }
    
    /**
     * Handle double volume up detection
     * 
     * Only triggers if two volume up presses occurred within 350ms
     * 
     * @param currentTime The timestamp when the key was pressed
     */
    private fun handleDoubleVolumeUp(event: KeyEvent, currentTime: Long): Boolean {
        // Check for double-press: compare with previousVolumeUpTime
        val timeSinceLastPress = currentTime - previousVolumeUpTime
        
        // Update previous for next check
        previousVolumeUpTime = currentTime
        
        if (timeSinceLastPress < Constants.DOUBLE_PRESS_THRESHOLD_MS && timeSinceLastPress > 0) {
            previousVolumeUpTime = 0 // Reset
            Log.d(TAG, "Double volume-up detected! Triggering overlay.")
            toggleOverlay()
            return true
        }
        
        return false
    }
    
    /**
     * Handle double volume down detection
     * 
     * Only triggers if two volume down presses occurred within 350ms
     * 
     * @param currentTime The timestamp when the key was pressed
     */
    private fun handleDoubleVolumeDown(event: KeyEvent, currentTime: Long): Boolean {
        // Check for double-press: compare with previousVolumeDownTime
        val timeSinceLastPress = currentTime - previousVolumeDownTime
        
        // Update previous for next check
        previousVolumeDownTime = currentTime
        
        if (timeSinceLastPress < Constants.DOUBLE_PRESS_THRESHOLD_MS && timeSinceLastPress > 0) {
            previousVolumeDownTime = 0 // Reset
            Log.d(TAG, "Double volume-down detected! Triggering overlay.")
            toggleOverlay()
            return true
        }
        
        return false
    }
    
    /**
     * Handle both buttons pressed simultaneously
     * Detects when volume up and volume down are pressed within 300ms of each other
     * 
     * @param currentTime The timestamp when the key was pressed
     */
    private fun handleBothButtons(event: KeyEvent, currentTime: Long): Boolean {
        val keyName = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) "UP" else "DOWN"
        
        Log.d(TAG, "BOTH_MODE: Key $keyName pressed at $currentTime")
        Log.d(TAG, "BOTH_MODE: lastUp=$lastVolumeUpTime, lastDown=$lastVolumeDownTime")
        
        // Check if both buttons were pressed within 300ms of each other
        // Both timestamps must be non-zero (both buttons pressed)
        if (lastVolumeUpTime > 0 && lastVolumeDownTime > 0) {
            val timeDiff = kotlin.math.abs(lastVolumeUpTime - lastVolumeDownTime)
            Log.d(TAG, "BOTH_MODE: Both pressed! TimeDiff=$timeDiff ms")
            
            if (timeDiff < Constants.BOTH_BUTTONS_THRESHOLD_MS) {
                val timeSinceLastTrigger = currentTime - lastBothButtonsTime
                Log.d(TAG, "BOTH_MODE: Within threshold! TimeSinceLastTrigger=$timeSinceLastTrigger ms")
                
                // Prevent repeated triggers
                if (timeSinceLastTrigger > Constants.DOUBLE_PRESS_THRESHOLD_MS) {
                    lastBothButtonsTime = currentTime
                    // Reset both timestamps
                    lastVolumeUpTime = 0
                    lastVolumeDownTime = 0
                    Log.d(TAG, "BOTH_MODE: TRIGGERING OVERLAY!")
                    toggleOverlay()
                    return true
                } else {
                    Log.d(TAG, "BOTH_MODE: Blocked by repeat prevention")
                }
            } else {
                Log.d(TAG, "BOTH_MODE: TimeDiff too large (>300ms)")
            }
        } else {
            Log.d(TAG, "BOTH_MODE: Waiting for other button")
        }
        
        return false
    }
    
    /**
     * Public trigger method that can be called from MainActivity for testing
     * or from a notification action. This works on all API levels.
     */
    fun triggerOverlay() {
        toggleOverlay()
    }
    
    /**
     * Toggle the overlay service visibility
     */
    private fun toggleOverlay() {
        Log.d(TAG, "toggleOverlay() called")
        
        // First check if overlay permission is granted
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted")
            Toast.makeText(
                this, 
                "Overlay permission required. Open Vozcribe app to grant it.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        Log.d(TAG, "Overlay permission granted, starting OverlayService")
        
        // Toggle the overlay
        val overlayIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_TOGGLE
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(overlayIntent)
        } else {
            startService(overlayIntent)
        }
        Log.d(TAG, "OverlayService started")
    }
    
    /**
     * Find the currently focused editable node (text field)
     * 
     * This traverses the accessibility tree to find a focused node that
     * accepts text input.
     * 
     * @return AccessibilityNodeInfo of the focused editable field, or null if none found
     */
    fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        // Try to get the input-focused node first
        val inputFocusNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        // Check if it's editable
        if (inputFocusNode?.isEditable == true) {
            return inputFocusNode
        }
        // Not editable — recycle before trying next approach
        inputFocusNode?.recycle()

        // If not found, try accessibility focus
        val accessibilityFocusNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (accessibilityFocusNode?.isEditable == true) {
            return accessibilityFocusNode
        }
        // Not editable — recycle before tree search
        accessibilityFocusNode?.recycle()

        // Search through the tree for any focused editable node
        return findEditableInTree(rootInActiveWindow)
    }
    
    /**
     * Recursively search for an editable focused node in the accessibility tree
     */
    private fun findEditableInTree(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.isEditable && node.isFocused) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findEditableInTree(child)
            if (result != null) {
                // Recycle the intermediate child if it's not the result itself
                if (child != null && child !== result) {
                    child.recycle()
                }
                return result
            }
            child?.recycle()
        }

        return null
    }
    
    /**
     * Insert text into the currently focused editable field
     * 
     * Strategy:
     * 1. Primary: Use ACTION_SET_TEXT to set/append text
     * 2. Fallback: Use clipboard + ACTION_PASTE
     * 
     * @param text The text to insert
     * @return true if insertion succeeded, false otherwise
     */
    fun insertText(text: String): Boolean {
        val focusedNode = findFocusedEditableNode()

        if (focusedNode == null) {
            Log.w(TAG, "No focused editable node found")
            Toast.makeText(this, "No text field focused", Toast.LENGTH_SHORT).show()
            return false
        }

        try {
            // Try primary strategy: ACTION_SET_TEXT
            val success = insertTextWithAction(focusedNode, text)

            if (!success) {
                // Fallback: Use clipboard
                Log.d(TAG, "ACTION_SET_TEXT failed, trying clipboard fallback")
                return insertTextWithClipboard(focusedNode, text)
            }

            return true
        } finally {
            focusedNode.recycle()
        }
    }
    
    /**
     * Insert text using ACTION_SET_TEXT
     * 
     * Note: This replaces the entire text in the field on most Android versions.
     * On API 21+, we can use Bundle with EXTRA_TEXT to append, but behavior
     * varies by app and Android version.
     * 
     * IMPORTANT: Some apps (like WhatsApp and YouTube) return the placeholder/hint text
     * in node.text when the field is empty, but node.hintText may not match or be null.
     * 
     * SOLUTION:
     * - For apps where hintText properly matches placeholder (like Gemini): use simple direct approach
     * - For apps where hintText is empty or doesn't match nodeText (like WhatsApp, YouTube):
     *   use the paste-space workaround to clear placeholder first
     */
    private fun insertTextWithAction(node: AccessibilityNodeInfo, text: String): Boolean {
        val nodeText = node.text?.toString() ?: ""
        val hintText = node.hintText?.toString() ?: ""
        
        Log.d(TAG, "insertTextWithAction: nodeText='$nodeText', hintText='$hintText'")
        
        // Detect if we have a potential placeholder issue:
        // - nodeText is not empty (there's something showing)
        // - hintText is empty OR hintText doesn't match nodeText
        // This suggests nodeText might be showing placeholder text that we can't reliably detect
        val hasPotentialPlaceholderIssue = nodeText.isNotEmpty() && 
            (hintText.isEmpty() || nodeText != hintText)
        
        val existingText: String
        var targetNode: AccessibilityNodeInfo = node
        var updatedNode: AccessibilityNodeInfo? = null

        if (hasPotentialPlaceholderIssue) {
            // Use paste-space workaround for apps with placeholder detection issues
            Log.d(TAG, "Potential placeholder issue detected, using paste-space workaround")

            // Paste a space to clear placeholder (while preserving any real content)
            val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val spaceClip = android.content.ClipData.newPlainText("WhisperType", " ")
            clipboardManager.setPrimaryClip(spaceClip)
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)

            // Re-find the focused node to get updated text
            updatedNode = findFocusedEditableNode()
            existingText = updatedNode?.text?.toString() ?: ""
            targetNode = updatedNode ?: node

            Log.d(TAG, "After paste space: existingText='$existingText'")
        } else {
            // Simple approach for apps where placeholder detection works correctly
            // If nodeText matches hintText, the field is empty (showing placeholder)
            // If nodeText is empty, the field is empty
            existingText = if (nodeText == hintText || nodeText.isEmpty()) "" else nodeText

            Log.d(TAG, "Simple approach: existingText='$existingText'")
        }

        // Compose final text = existing content + transcript, trimmed
        val finalText = (existingText + text).trim()

        Log.d(TAG, "Final text (trimmed): '$finalText'")

        // Set the final text
        val arguments = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
        }

        try {
            val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "ACTION_SET_TEXT result: $success, finalText='$finalText'")
            return success
        } finally {
            // Recycle the updatedNode if it's a different node than the original
            if (updatedNode != null && updatedNode !== node) {
                updatedNode.recycle()
            }
        }
    }
    
    /**
     * Insert text using clipboard and paste action
     * 
     * This is a fallback strategy that should work on most apps,
     * but requires the user's clipboard to be temporarily overwritten.
     */
    private fun insertTextWithClipboard(node: AccessibilityNodeInfo, text: String): Boolean {
        // Copy text to clipboard
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("WhisperType", text)
        clipboardManager.setPrimaryClip(clip)
        
        // Perform paste action
        val success = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        Log.d(TAG, "ACTION_PASTE result: $success")
        
        if (!success) {
            // If paste failed too, inform the user they can manually paste
            Toast.makeText(
                this,
                "Text copied to clipboard. Please paste manually.",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        return success
    }
    
    /**
     * Perform auto-send by finding and clicking the send button in the current app
     * Returns true if successful, false otherwise
     */
    fun performAutoSend(): Boolean {
        Log.d(TAG, "performAutoSend: Starting auto-send attempt")
        
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "performAutoSend: No active window, cannot auto-send")
            return false
        }
        
        // Try to find and click the send button
        val sendButton = findSendButton(rootNode)
        if (sendButton != null) {
            Log.d(TAG, "performAutoSend: Found send button, clicking...")
            val success = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            sendButton.recycle()
            Log.d(TAG, "performAutoSend: Click action result: $success")
            rootNode.recycle()
            return success
        }
        
        Log.w(TAG, "performAutoSend: Could not find send button")
        rootNode.recycle()
        return false
    }
    
    /**
     * Recursively search for a send button in the node tree
     * Looks for buttons with text like "Send", "Post", "Submit", etc.
     */
    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Check if this node is a clickable button-like element
        if (node.isClickable) {
            val text = node.text?.toString()?.lowercase()
            val contentDesc = node.contentDescription?.toString()?.lowercase()
            
            // Check for send-like text
            val sendKeywords = listOf("send", "post", "submit", "share")
            if (sendKeywords.any { keyword ->
                    text?.contains(keyword) == true || contentDesc?.contains(keyword) == true
                }) {
                Log.d(TAG, "findSendButton: Found potential send button with text='$text' contentDesc='$contentDesc'")
                return node
            }
        }
        
        // Recursively search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findSendButton(child)
            child.recycle()
            if (result != null) {
                return result
            }
        }
        
        return null
    }
    
    /**
     * Create notification channel for foreground service (API 26+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vozcribe Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Vozcribe accessibility service running"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
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
            .setContentTitle("Vozcribe is active")
            .setContentText("Ready for voice input shortcuts")
            .setSmallIcon(R.drawable.ic_microphone)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }
}
