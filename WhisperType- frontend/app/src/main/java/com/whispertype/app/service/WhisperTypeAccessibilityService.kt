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
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
     * We don't actively process accessibility events, but this callback
     * is required by the service. We keep it minimal to reduce overhead.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only listen for events to maintain service state
        // We don't actively process them to minimize battery/CPU impact
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
                "Overlay permission required. Open WhisperType app to grant it.",
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
                "WhisperType Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps WhisperType accessibility service running"
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
            .setContentTitle("WhisperType is active")
            .setContentText("Ready for voice input shortcuts")
            .setSmallIcon(R.drawable.ic_microphone)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }
}
