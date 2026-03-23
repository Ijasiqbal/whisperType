package com.whispertype.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.R
import com.whispertype.app.auth.AuthResult
import com.whispertype.app.auth.FirebaseAuthManager
import com.whispertype.app.config.RemoteConfigManager
import com.whispertype.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * LoginScreen - Authentication screen with animated waveform and warm premium aesthetic
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

    // Full-bleed warm gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // ── Animated waveform in circle ──────────────────────
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(800))
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(
                            elevation = 24.dp,
                            shape = CircleShape,
                            ambientColor = Rust.copy(alpha = 0.2f),
                            spotColor = Rust.copy(alpha = 0.3f)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Rust, RustLight, RustAmber)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_microphone),
                        contentDescription = "Vozcribe",
                        tint = Color.White,
                        modifier = Modifier.size(84.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── Title block ──────────────────────────────────────
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(700, delayMillis = 300)) +
                    slideInVertically(
                        animationSpec = tween(700, delayMillis = 300),
                        initialOffsetY = { 40 }
                    )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Vozcribe",
                        style = MaterialTheme.typography.displayLarge,
                        color = Slate800
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Your voice, perfectly typed",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.3.sp
                        ),
                        color = Slate500
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1.4f))

            // ── Sign-in area (no card) ───────────────────────────
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 600)) +
                    slideInVertically(
                        animationSpec = tween(600, delayMillis = 600),
                        initialOffsetY = { 60 }
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Error message
                    if (errorMessage != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = RedLightTint,
                            tonalElevation = 0.dp
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = ErrorDark,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Google Sign-In button — warm gradient fill
                    Button(
                        onClick = { handleGoogleSignIn() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(16.dp),
                                ambientColor = Rust.copy(alpha = 0.25f),
                                spotColor = Rust.copy(alpha = 0.35f)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(),
                        enabled = !isLoading
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (isLoading) 0.7f else 1f)
                                .background(RustGradientHorizontal),
                            contentAlignment = Alignment.Center
                        ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
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
                                    tint = Color.Unspecified
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Continue with Google",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        }
                    }

                    // Guest login
                    if (guestLoginEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(
                            onClick = { handleAnonymousSignIn() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !isLoading
                        ) {
                            Text(
                                text = "Continue as Guest",
                                style = MaterialTheme.typography.labelMedium,
                                color = Slate500
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

