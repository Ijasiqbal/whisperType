package com.whispertype.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.whispertype.app.R
import com.whispertype.app.auth.AuthResult
import com.whispertype.app.auth.FirebaseAuthManager
import com.whispertype.app.config.RemoteConfigManager
import kotlinx.coroutines.launch

/**
 * LoginScreen - Authentication screen with Google Sign-In and Guest access
 */
@Composable
fun LoginScreen(
    authManager: FirebaseAuthManager,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Remote Config: Guest login enabled state
    val guestLoginEnabled by RemoteConfigManager.guestLoginEnabled.collectAsState()

    // UI State
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Animation states
    var isVisible by remember { mutableStateOf(false) }
    
    // Trigger entrance animation on composition
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    // Handle Google Sign-In
    fun handleGoogleSignIn() {
        isLoading = true
        errorMessage = null
        
        scope.launch {
            val result = authManager.signInWithGoogle(context)
            isLoading = false
            when (result) {
                is AuthResult.Success -> onAuthSuccess()
                is AuthResult.Error -> errorMessage = result.message
            }
        }
    }
    
    // Handle Anonymous Sign-In
    fun handleAnonymousSignIn() {
        isLoading = true
        errorMessage = null
        
        scope.launch {
            val user = authManager.ensureSignedIn()
            isLoading = false
            if (user != null) {
                onAuthSuccess()
            } else {
                errorMessage = "Anonymous sign-in failed"
            }
        }
    }
    
    // Gradient background
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated App icon
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
                    animationSpec = tween(600),
                    initialOffsetY = { -50 }
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
                        contentDescription = "Wozcribe Icon",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Animated Title
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 150)) + slideInVertically(
                    animationSpec = tween(600, delayMillis = 150),
                    initialOffsetY = { -30 }
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Wozcribe",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Sign in to continue",
                        fontSize = 16.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Animated Login Card
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 300)) + slideInVertically(
                    animationSpec = tween(600, delayMillis = 300),
                    initialOffsetY = { 80 }
                )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        // Error message
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFDC2626),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        // Google Sign-In button with icon
                        Button(
                            onClick = { handleGoogleSignIn() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    ambientColor = Color(0xFF6366F1).copy(alpha = 0.4f),
                                    spotColor = Color(0xFF6366F1).copy(alpha = 0.4f)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6366F1)
                            ),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_google),
                                        contentDescription = "Google",
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified // Preserve original colors
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Continue with Google",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        
                        
                        // Guest login - controlled by Firebase Remote Config
                        if (guestLoginEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Divider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Divider(modifier = Modifier.weight(1f))
                                Text(
                                    text = "  or  ",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp
                                )
                                Divider(modifier = Modifier.weight(1f))
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Anonymous Sign-In button
                            OutlinedButton(
                                onClick = { handleAnonymousSignIn() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF64748B)
                                ),
                                enabled = !isLoading
                            ) {
                                Text(
                                    text = "Continue as Guest",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
