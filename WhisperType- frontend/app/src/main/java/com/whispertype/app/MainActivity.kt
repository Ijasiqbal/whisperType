package com.whispertype.app

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.whispertype.app.service.OverlayService

/**
 * MainActivity - Onboarding and permission setup screen
 * 
 * This activity provides:
 * 1. Welcome/onboarding UI explaining what the app does
 * 2. Permission status indicators
 * 3. Buttons to navigate to system settings for each permission
 * 4. A test button to verify the overlay works
 * 
 * The app requires three permissions to function:
 * - Accessibility Service: To receive shortcut events and insert text
 * - Overlay Permission: To display the floating mic button
 * - Microphone Permission: For speech recognition
 */
class MainActivity : ComponentActivity() {
    
    // Permission request launcher for microphone
    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice input", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            WhisperTypeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onEnableAccessibility = { openAccessibilitySettings() },
                        onGrantOverlay = { openOverlaySettings() },
                        onGrantMicrophone = { requestMicrophonePermission() },
                        onTestOverlay = { testOverlay() }
                    )
                }
            }
        }
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        Toast.makeText(this, "Find and enable 'WhisperType Voice Input'", Toast.LENGTH_LONG).show()
    }
    
    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
    
    private fun requestMicrophonePermission() {
        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    
    private fun openAccessibilityShortcutSettings() {
        // On most Android versions, the accessibility shortcut settings are within
        // the main accessibility settings. Some devices have a dedicated screen.
        try {
            val intent = Intent("android.settings.ACCESSIBILITY_SHORTCUT_SETTINGS")
            startActivity(intent)
        } catch (e: Exception) {
            // Fall back to regular accessibility settings
            openAccessibilitySettings()
            Toast.makeText(
                this,
                "Look for 'Accessibility shortcut' or 'Volume key shortcut' in settings",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.M)
    private fun testOverlay() {
        // Check if all permissions are granted before testing
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Start the overlay service for testing
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        // Move to background so user can see the overlay
        moveTaskToBack(true)
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == packageName 
        }
    }
}

/**
 * Main Compose UI for the app
 */
@Composable
fun MainScreen(
    onEnableAccessibility: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantMicrophone: () -> Unit,
    onTestOverlay: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // State for permission statuses - refreshed when app resumes
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayGranted by remember { mutableStateOf(false) }
    var isMicrophoneGranted by remember { mutableStateOf(false) }
    
    // Refresh permissions when returning to app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = checkAccessibilityEnabled(context)
                isOverlayGranted = Settings.canDrawOverlays(context)
                isMicrophoneGranted = ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val allPermissionsGranted = isAccessibilityEnabled && isOverlayGranted && isMicrophoneGranted
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // App icon placeholder
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6366F1),
                            Color(0xFF8B5CF6)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎤",
                fontSize = 36.sp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "WhisperType",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Voice input for any text field",
            fontSize = 16.sp,
            color = Color(0xFF64748B)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Setup section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Setup Required",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Step 1: Accessibility Service
                PermissionStep(
                    stepNumber = 1,
                    title = "Enable Accessibility Service",
                    description = "Required to insert text into other apps",
                    isGranted = isAccessibilityEnabled,
                    buttonText = if (isAccessibilityEnabled) "Enabled" else "Enable",
                    onClick = onEnableAccessibility,
                    enabled = !isAccessibilityEnabled
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Step 2: Overlay Permission
                PermissionStep(
                    stepNumber = 2,
                    title = "Grant Overlay Permission",
                    description = "Required to show the floating mic button",
                    isGranted = isOverlayGranted,
                    buttonText = if (isOverlayGranted) "Granted" else "Grant",
                    onClick = onGrantOverlay,
                    enabled = !isOverlayGranted
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Step 3: Microphone Permission
                PermissionStep(
                    stepNumber = 3,
                    title = "Grant Microphone Permission",
                    description = "Required for voice recognition",
                    isGranted = isMicrophoneGranted,
                    buttonText = if (isMicrophoneGranted) "Granted" else "Grant",
                    onClick = onGrantMicrophone,
                    enabled = !isMicrophoneGranted
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Shortcut setup
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Activation Shortcut",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Choose how to activate WhisperType:",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Shortcut mode selector
                var selectedMode by remember { 
                    mutableStateOf(ShortcutPreferences.getShortcutMode(context)) 
                }
                var expanded by remember { mutableStateOf(false) }
                
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF1E293B)
                        )
                    ) {
                        Text(
                            text = selectedMode.displayName,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                        Text("▼")
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        ShortcutPreferences.ShortcutMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.displayName) },
                                onClick = {
                                    selectedMode = mode
                                    ShortcutPreferences.setShortcutMode(context, mode)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = when (selectedMode) {
                        ShortcutPreferences.ShortcutMode.DOUBLE_VOLUME_UP -> 
                            "Press volume up twice quickly"
                        ShortcutPreferences.ShortcutMode.DOUBLE_VOLUME_DOWN -> 
                            "Press volume down twice quickly"
                        ShortcutPreferences.ShortcutMode.BOTH_VOLUME_BUTTONS -> 
                            "Press both volume buttons together"
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Test overlay button
        if (allPermissionsGranted) {
            Button(
                onClick = onTestOverlay,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                )
            ) {
                Text("🎤 Test Overlay", fontSize = 16.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Usage instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "How to Use",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                UsageStep("1", "Open any app with a text field")
                Spacer(modifier = Modifier.height(8.dp))
                UsageStep("2", "Tap on the text field to focus it")
                Spacer(modifier = Modifier.height(8.dp))
                UsageStep("3", "Use your chosen shortcut above")
                Spacer(modifier = Modifier.height(8.dp))
                UsageStep("4", "Tap the mic and speak - text will be inserted!")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun PermissionStep(
    stepNumber: Int,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step indicator
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) Color(0xFF10B981) else Color(0xFFE2E8F0)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isGranted) "✓" else stepNumber.toString(),
                color = if (isGranted) Color.White else Color(0xFF64748B),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B)
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }
        
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) Color(0xFF10B981) else Color(0xFF6366F1),
                disabledContainerColor = Color(0xFF10B981)
            ),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(
                text = buttonText,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun UsageStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$number.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF6366F1),
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF475569)
        )
    }
}

/**
 * Helper function to check if our accessibility service is enabled
 */
fun checkAccessibilityEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    return enabledServices.any { 
        it.resolveInfo.serviceInfo.packageName == context.packageName 
    }
}

/**
 * App theme wrapper
 */
@Composable
fun WhisperTypeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6366F1),
            onPrimary = Color.White,
            secondary = Color(0xFF10B981),
            background = Color(0xFFF8FAFC),
            surface = Color.White,
            onBackground = Color(0xFF1E293B),
            onSurface = Color(0xFF1E293B)
        ),
        content = content
    )
}
