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
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.whispertype.app.auth.AuthState
import com.whispertype.app.auth.FirebaseAuthManager
import com.whispertype.app.service.OverlayService
import com.whispertype.app.ui.LoginScreen
import com.whispertype.app.ui.ProfileScreen
import com.whispertype.app.ui.PlanScreen
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.config.RemoteConfigManager
import com.whispertype.app.billing.BillingManagerFactory
import com.whispertype.app.billing.IBillingManager
import com.whispertype.app.ui.viewmodel.MainViewModel
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.whispertype.app.service.WhisperTypeAccessibilityService
import com.whispertype.app.util.MiuiHelper
import com.whispertype.app.util.ForceUpdateChecker
import com.whispertype.app.ui.components.ForceUpdateDialog
import com.whispertype.app.ui.components.SoftUpdateDialog
import com.whispertype.app.ui.components.AccessibilityDisclosureDialog

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
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    // Firebase Auth Manager
    private val authManager = FirebaseAuthManager()
    
    // Billing Manager (auto-selects Mock in DEBUG, Real in RELEASE)
    private lateinit var billingManager: IBillingManager
    
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
        // Enable edge-to-edge to properly handle system bars on devices with button navigation
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Initialize billing manager
        billingManager = BillingManagerFactory.create(this)
        
        // Wire up auth token provider for backend verification
        billingManager.setAuthTokenProvider { authManager.getCachedIdToken() }
        
        billingManager.initialize {
            // Billing ready - query product details
            lifecycleScope.launch {
                billingManager.queryProSubscription()
            }
        }
        
        setContent {
            WhisperTypeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Use MainViewModel instead of direct manager access
                    val mainViewModel: MainViewModel = hiltViewModel()
                    val lifecycleOwner = LocalLifecycleOwner.current
                    
                    // Observe auth state from ViewModel
                    val authState by mainViewModel.authState.collectAsStateWithLifecycle()
                    
                    // Proactive refresh on app resume - fixes "data not updating" issue
                    LaunchedEffect(lifecycleOwner, authState) {
                        // Only refresh when authenticated
                        if (authState is AuthState.Authenticated) {
                            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                                mainViewModel.refreshUserStatus()
                            }
                        }
                    }
                    
                    // Observe update config from Remote Config
                    val updateConfig by RemoteConfigManager.updateConfig.collectAsStateWithLifecycle()
                    
                    // Check if force update is required
                    val updateStatus = ForceUpdateChecker.checkUpdateStatus(
                        currentVersionCode = BuildConfig.VERSION_CODE,
                        config = updateConfig
                    )
                    
                    // State for showing soft update dialog
                    var showSoftUpdateDialog by remember { mutableStateOf(false) }
                    
                    // Show soft update dialog once per session if needed
                    LaunchedEffect(updateStatus) {
                        if (updateStatus == ForceUpdateChecker.UpdateStatus.SOFT_UPDATE && !showSoftUpdateDialog) {
                            showSoftUpdateDialog = true
                        }
                    }
                    
                    // Handle update status
                    when (updateStatus) {
                        ForceUpdateChecker.UpdateStatus.FORCE_UPDATE -> {
                            // Show blocking force update dialog
                            ForceUpdateDialog(
                                title = updateConfig.forceUpdateTitle,
                                message = updateConfig.forceUpdateMessage
                            )
                        }
                        ForceUpdateChecker.UpdateStatus.SOFT_UPDATE -> {
                            // Show soft update dialog (dismissible)
                            if (showSoftUpdateDialog) {
                                SoftUpdateDialog(
                                    onDismiss = { showSoftUpdateDialog = false }
                                )
                            }
                            // Continue showing normal app content
                            AppContent(authState)
                        }
                        ForceUpdateChecker.UpdateStatus.UP_TO_DATE -> {
                            // Show normal app content
                            AppContent(authState)
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun AppContent(authState: AuthState) {
        when (authState) {
            is AuthState.Loading -> {
                // Show loading while checking auth state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF6366F1)
                    )
                }
            }
            is AuthState.Unauthenticated -> {
                // Show login screen
                LoginScreen(
                    authManager = authManager,
                    onAuthSuccess = { /* State will update automatically via authState flow */ }
                )
            }
            is AuthState.Authenticated -> {
                // Show main app with bottom navigation
                AppWithBottomNav(
                    onEnableAccessibility = { openAccessibilitySettings() },
                    onGrantOverlay = { openOverlaySettings() },
                    onGrantMicrophone = { requestMicrophonePermission() },
                    onTestOverlay = { testOverlay() },
                    onSignOut = { authManager.signOut() },
                    userEmail = authState.user.email ?: "",
                    onUpgrade = { launchBillingFlow() }
                )
            }
            is AuthState.Error -> {
                // Show login screen with error state
                LoginScreen(
                    authManager = authManager,
                    onAuthSuccess = { /* State will update automatically via authState flow */ }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::billingManager.isInitialized) {
            billingManager.release()
        }
    }
    
    /**
     * Launch the billing purchase flow
     */
    private fun launchBillingFlow() {
        billingManager.launchPurchaseFlow(
            activity = this,
            onSuccess = {
                Toast.makeText(this, "Welcome to VoxType Pro!", Toast.LENGTH_LONG).show()
            },
            onError = { errorMessage ->
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        Toast.makeText(this, "Find and enable 'VoxType Voice Input'", Toast.LENGTH_LONG).show()
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
        return checkAccessibilityEnabled(this)
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
    onTestOverlay: () -> Unit,
    onSignOut: () -> Unit = {},
    userEmail: String? = null,
    showServiceWarning: Boolean = false,
    onFixService: () -> Unit = {},
    onIgnoreBatteryOptimizations: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State for foreground service toggle
    var isForegroundServiceEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("foreground_service_enabled", false)
        )
    }

    var pendingEnableKeepAlive by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!pendingEnableKeepAlive) return@rememberLauncherForActivityResult
        pendingEnableKeepAlive = false

        if (isGranted) {
            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("foreground_service_enabled", true)
                .apply()
            isForegroundServiceEnabled = true
            WhisperTypeAccessibilityService.instance?.refreshForegroundMode()
            Toast.makeText(context, "Keep-alive enabled. You should see a notification.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Notification permission is required to show the keep-alive notification", Toast.LENGTH_LONG).show()
        }
    }
    
    // State for showing foreground service explanation dialog
    var showForegroundServiceDialog by remember { mutableStateOf(false) }
    
    // State for showing battery optimization explanation dialog
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    
    // State for showing MIUI setup dialog
    var showMiuiSetupDialog by remember { mutableStateOf(false) }
    
    // State for showing accessibility disclosure dialog (Google Play compliance)
    var showAccessibilityDisclosureDialog by remember { mutableStateOf(false) }
    
    // Check if this is a MIUI device and show prompt if needed
    val isMiuiDevice = remember { MiuiHelper.isMiuiDevice() }
    var showMiuiCard by remember { mutableStateOf(MiuiHelper.shouldShowSetupPrompt(context)) }
    
    // State for permission statuses - start with false, will be updated immediately
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayGranted by remember { mutableStateOf(false) }
    var isMicrophoneGranted by remember { mutableStateOf(false) }
    
    // Check permissions immediately when composable is first created
    LaunchedEffect(Unit) {
        isAccessibilityEnabled = checkAccessibilityEnabled(context)
        isOverlayGranted = Settings.canDrawOverlays(context)
        isMicrophoneGranted = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        android.util.Log.d("MainScreen", "Initial permission check: accessibility=$isAccessibilityEnabled")
    }
    
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
                android.util.Log.d("MainScreen", "Resume permission check: accessibility=$isAccessibilityEnabled")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val allPermissionsGranted = isAccessibilityEnabled && isOverlayGranted && isMicrophoneGranted
    
    // Animation state for entrance animations
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    // Radial gradient background matching app theme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFEEF2FF),  // Light indigo center
                        Color(0xFFF8FAFC)   // Fade to white
                    ),
                    center = Offset(0.5f, 0f),  // Top center
                    radius = 1500f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // Animated App icon
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(150)) + slideInHorizontally(
                animationSpec = tween(150),
                initialOffsetX = { -30 }
            )
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        ambientColor = Color(0xFF6366F1).copy(alpha = 0.3f),
                        spotColor = Color(0xFF6366F1).copy(alpha = 0.3f)
                    )
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
                Icon(
                    painter = painterResource(id = R.drawable.ic_microphone),
                    contentDescription = "VoxType Icon",
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Animated Title
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(150, delayMillis = 30)) + slideInHorizontally(
                animationSpec = tween(150, delayMillis = 30),
                initialOffsetX = { -25 }
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "VoxType",
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
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Animated content section
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(150, delayMillis = 60)) + slideInHorizontally(
                animationSpec = tween(150, delayMillis = 60),
                initialOffsetX = { -35 }
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Service Zombie Warning (commented out)
                // if (showServiceWarning && isAccessibilityEnabled) {
                //     ServiceZombieWarning(
                //         onFixService = onFixService,
                //         onIgnoreBatteryOptimizations = onIgnoreBatteryOptimizations
                //     )
                //     Spacer(modifier = Modifier.height(16.dp))
                // }

                // User info section
                if (userEmail != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                border = BorderStroke(1.dp, Color(0xFFE0E7FF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Signed in as",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = userEmail,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1E293B)
                        )
                    }
                    TextButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFDC2626)
                        )
                    ) {
                        Text("Sign Out")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Setup section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                    onClick = { 
                        android.util.Log.d("AccessibilityDisclosure", "Enable button clicked, showing disclosure dialog")
                        showAccessibilityDisclosureDialog = true 
                    },
                    enabled = !isAccessibilityEnabled
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
        
        // MIUI-specific setup card (only shown on Xiaomi/Redmi/POCO devices)
        if (showMiuiCard && isMiuiDevice) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "MIUI Setup Required",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // AutoStart permission step
                    PermissionStep(
                        stepNumber = 1,
                        title = "Enable AutoStart",
                        description = "Keeps VoxType running in the background",
                        isGranted = false,
                        buttonText = "Enable",
                        onClick = { 
                            MiuiHelper.openAutoStartSettings(context)
                            MiuiHelper.markSetupPromptShown(context)
                        },
                        enabled = true
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Advanced Settings (Collapsible Accordion)
        var isAdvancedSettingsExpanded by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Clickable Header (Accordion Toggle)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAdvancedSettingsExpanded = !isAdvancedSettingsExpanded }
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Troubleshooting",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9) // Slate-100
                            ) {
                                Text(
                                    text = "OPTIONAL",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B), // Slate-500
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Only if the shortcut stops responding",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    
                    Icon(
                        imageVector = if (isAdvancedSettingsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isAdvancedSettingsExpanded) "Collapse" else "Expand",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Expandable Content
                AnimatedVisibility(
                    visible = isAdvancedSettingsExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Divider(color = Color(0xFFE2E8F0))
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Foreground Service Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Keep Service Alive",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "Shows a persistent notification",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            
                            Switch(
                                checked = isForegroundServiceEnabled,
                                onCheckedChange = { newValue ->
                                    if (newValue) {
                                        // Show explanation dialog before enabling
                                        showForegroundServiceDialog = true
                                    } else {
                                        // Disable immediately
                                        isForegroundServiceEnabled = false
                                        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                                            .edit()
                                            .putBoolean("foreground_service_enabled", false)
                                            .apply()
                                        WhisperTypeAccessibilityService.instance?.refreshForegroundMode()
                                        Toast.makeText(context, "Foreground service disabled", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF6366F1),
                                    checkedTrackColor = Color(0xFFE0E7FF)
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Battery Optimization Exemption
                        OutlinedButton(
                            onClick = { showBatteryOptimizationDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF64748B)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Battery Optimization",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Disable Battery Optimization",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Prevents Android from putting the service to sleep",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Shortcut setup
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                    text = "Choose how to activate VoxType:",
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
        Button(
            onClick = onTestOverlay,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF10B981)
            )
        ) {
            Text("Test Overlay", fontSize = 16.sp)
        }
        
        // Accessibility Disclosure Dialog (Google Play compliance)
        if (showAccessibilityDisclosureDialog) {
            android.util.Log.d("AccessibilityDisclosure", "Dialog is being rendered, showAccessibilityDisclosureDialog=true")
            AccessibilityDisclosureDialog(
                onContinue = {
                    showAccessibilityDisclosureDialog = false
                    onEnableAccessibility()
                },
                onDismiss = {
                    showAccessibilityDisclosureDialog = false
                }
            )
        }
        
        // Foreground Service Explanation Dialog
        if (showForegroundServiceDialog) {
            AlertDialog(
                onDismissRequest = { showForegroundServiceDialog = false },
                title = {
                    Text(
                        text = "Keep Service Alive",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "This feature keeps VoxType running in the background by showing a persistent notification.",
                            fontSize = 14.sp,
                            color = Color(0xFF475569)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "What you'll see:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• A permanent notification in your notification tray",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Good news:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = "• No battery drain (service is idle until activated)",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "• No performance impact",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "• Prevents the service from being killed by Android",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Enable this if you experience issues with the service not responding to volume key shortcuts.",
                            fontSize = 12.sp,
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showForegroundServiceDialog = false

                            val needsNotificationPermission =
                                Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED

                            if (needsNotificationPermission) {
                                pendingEnableKeepAlive = true
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@Button
                            }

                            isForegroundServiceEnabled = true
                            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("foreground_service_enabled", true)
                                .apply()

                            WhisperTypeAccessibilityService.instance?.refreshForegroundMode()

                            Toast.makeText(
                                context,
                                "Keep-alive enabled. You should see a notification.",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        )
                    ) {
                        Text("Enable")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForegroundServiceDialog = false }) {
                        Text("Cancel", color = Color(0xFF64748B))
                    }
                }
            )
        }
        
        // Battery Optimization Explanation Dialog
        if (showBatteryOptimizationDialog) {
            AlertDialog(
                onDismissRequest = { showBatteryOptimizationDialog = false },
                title = {
                    Text(
                        text = "Disable Battery Optimization",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "This setting prevents Android from putting VoxType to sleep to save battery.",
                            fontSize = 14.sp,
                            color = Color(0xFF475569)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "What happens:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• VoxType can run in the background without restrictions",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "• The service won't be killed by Android's battery saver",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Battery impact:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = "• Minimal - The service is idle most of the time",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "• Uses resources only when you activate it",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "• No background tasks or polling",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Enable this if the volume shortcut stops working after your phone has been idle for a while.",
                            fontSize = 12.sp,
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBatteryOptimizationDialog = false
                            onIgnoreBatteryOptimizations()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        )
                    ) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatteryOptimizationDialog = false }) {
                        Text("Cancel", color = Color(0xFF64748B))
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Usage instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
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
        }
    }
}

/**
 * Enum for bottom navigation tabs
 */
enum class BottomNavTab(val label: String, val iconRes: Int) {
    HOME("Home", R.drawable.ic_home),
    PROFILE("Profile", R.drawable.ic_person),
    PLAN("Pricing", R.drawable.ic_plan)
}

/**
 * Main app container with bottom navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWithBottomNav(
    onEnableAccessibility: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantMicrophone: () -> Unit,
    onTestOverlay: () -> Unit,
    onSignOut: () -> Unit,
    userEmail: String?,
    onUpgrade: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }
    
    // Observe usage/trial state from UsageDataManager
    val usageState by UsageDataManager.usageState.collectAsStateWithLifecycle()
    
    // Initialize and observe Remote Config for plan configuration
    val planConfig by RemoteConfigManager.planConfig.collectAsStateWithLifecycle()
    val isConfigLoading by RemoteConfigManager.isLoading.collectAsStateWithLifecycle()
    
    // Initialize Remote Config on first composition
    LaunchedEffect(Unit) {
        RemoteConfigManager.initialize()
    }
    
    // Check accessibility service status on resume
    var isAccessibilityServiceRunning by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Check if service is actually running
                // We use a small delay to allow service to bind if it was just engaged
                lifecycleOwner.lifecycleScope.launch {
                    delay(500)
                    isAccessibilityServiceRunning = WhisperTypeAccessibilityService.isRunning()
                    android.util.Log.d("MainActivity", "Service running check: $isAccessibilityServiceRunning")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Fetch trial status on first composition (when user is authenticated)
    LaunchedEffect(Unit) {
        // Get current user's auth token and fetch trial status
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            android.util.Log.w("MainActivity", "No current user, marking loading complete")
            UsageDataManager.markLoadingComplete()
            return@LaunchedEffect
        }

        currentUser.getIdToken(false)
            .addOnSuccessListener { result ->
                val token = result.token
                if (token != null) {
                    com.whispertype.app.api.WhisperApiClient().getTrialStatus(
                        authToken = token,
                        onSuccess = { status, freeSecondsUsed, freeSecondsRemaining, trialExpiryDateMs, warningLevel ->
                            android.util.Log.d("MainActivity", "Trial status fetched: $status, $freeSecondsRemaining seconds remaining")
                            // Update UsageDataManager with the fetched status
                            UsageDataManager.updateTrialStatus(
                                status = status,
                                freeSecondsUsed = freeSecondsUsed,
                                freeSecondsRemaining = freeSecondsRemaining,
                                trialExpiryDateMs = trialExpiryDateMs,
                                warningLevel = warningLevel
                            )
                        },
                        onError = { error ->
                            android.util.Log.e("MainActivity", "Failed to fetch trial status: $error")
                            UsageDataManager.markLoadingComplete()
                        }
                    )
                } else {
                    android.util.Log.w("MainActivity", "Token is null, marking loading complete")
                    UsageDataManager.markLoadingComplete()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MainActivity", "Failed to get ID token: ${e.message}")
                UsageDataManager.markLoadingComplete()
            }
    }
    
    // Auto-redirect to Plan tab when trial expires (soft redirect, not blocking)
    val isTrialExpired = !usageState.isTrialValid && usageState.currentPlan == UsageDataManager.Plan.FREE
    
    LaunchedEffect(isTrialExpired) {
        if (isTrialExpired) {
            selectedTab = BottomNavTab.PLAN
            android.util.Log.d("MainActivity", "Trial expired, redirecting to Plan tab")
        }
    }
    
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(72.dp)
            ) {
                BottomNavTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                painter = painterResource(id = tab.iconRes),
                                contentDescription = tab.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF6366F1),
                            selectedTextColor = Color(0xFF6366F1),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8),
                            indicatorColor = Color(0xFFEEF2FF)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                BottomNavTab.HOME -> {
                    MainScreen(
                        onEnableAccessibility = onEnableAccessibility,
                        onGrantOverlay = onGrantOverlay,
                        onGrantMicrophone = onGrantMicrophone,
                        onTestOverlay = onTestOverlay,
                        onSignOut = onSignOut,
                        userEmail = userEmail,
                        showServiceWarning = !isAccessibilityServiceRunning,
                        onFixService = {
                            // Accessibility services can't be reliably restarted from the app process.
                            // Best recovery is to open settings and toggle the service OFF/ON.
                            onEnableAccessibility()
                        },
                        onIgnoreBatteryOptimizations = {
                            try {
                                val context = (lifecycleOwner as ComponentActivity)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    // Direct to app-specific battery optimization
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                    Toast.makeText(
                                        context,
                                        "Please allow VoxType to run unrestricted",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Battery optimization not available on this Android version",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to open battery optimization", e)
                                // Fallback to general settings
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    (lifecycleOwner as ComponentActivity).startActivity(intent)
                                } catch (e2: Exception) {
                                    Toast.makeText(
                                        (lifecycleOwner as ComponentActivity),
                                        "Could not open battery settings",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                }
                BottomNavTab.PLAN -> {
                    PlanScreen(
                        priceDisplay = planConfig.proPriceDisplay,
                        minutesLimit = planConfig.proMinutesLimit,
                        planName = planConfig.proPlanName,
                        isLoading = isConfigLoading,
                        onUpgrade = onUpgrade,
                        onContactSupport = {
                            // TODO: Open support link
                            android.util.Log.d("MainActivity", "Contact support clicked")
                        }
                    )
                }
                BottomNavTab.PROFILE -> {
                    val context = LocalContext.current
                    ProfileScreen(
                        userEmail = userEmail,
                        onSignOut = onSignOut,
                        onManageSubscription = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse(
                                        "https://play.google.com/store/account/subscriptions" +
                                        "?sku=whispertype_pro_monthly&package=${context.packageName}"
                                    )
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to open subscriptions", e)
                                Toast.makeText(context, "Could not open subscriptions", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
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
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color(0xFF94A3B8)
            )
        }
        
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(36.dp).padding(start = 8.dp),
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
fun ServiceZombieWarning(
    onFixService: () -> Unit,
    onIgnoreBatteryOptimizations: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)) // Red-50
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFEF4444), CircleShape), // Red-500
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Service Needs Restart",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF991B1B) // Red-800
                    )
                    Text(
                        text = "System put the service to sleep.",
                        fontSize = 12.sp,
                        color = Color(0xFFB91C1C) // Red-700
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onFixService,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)) // Red-600
                ) {
                    Text("Fix Service")
                }
                
                OutlinedButton(
                    onClick = onIgnoreBatteryOptimizations,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB91C1C))
                ) {
                    Text("Permanent Fix", fontSize = 11.sp)
                }
            }
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
 * Uses Settings.Secure API which is more reliable than AccessibilityManager
 */
fun checkAccessibilityEnabled(context: Context): Boolean {
    val serviceId = "${context.packageName}/${context.packageName}.service.WhisperTypeAccessibilityService"
    
    // Primary check: Use Settings.Secure (most reliable)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: ""
    
    val isEnabled = enabledServices.split(':').any { service ->
        service.equals(serviceId, ignoreCase = true) ||
        service.contains("WhisperTypeAccessibilityService", ignoreCase = true)
    }
    
    android.util.Log.d("MainActivity", "Accessibility check: serviceId=$serviceId, enabledServices=$enabledServices, isEnabled=$isEnabled")
    
    return isEnabled
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
