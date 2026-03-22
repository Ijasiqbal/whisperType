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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whispertype.app.R
import com.whispertype.app.auth.AuthResult
import com.whispertype.app.auth.FirebaseAuthManager
import com.whispertype.app.config.RemoteConfigManager
import com.whispertype.app.ui.theme.*
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
                ScreenBackground
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
                            ambientColor = Rust.copy(alpha = 0.3f),
                            spotColor = Rust.copy(alpha = 0.3f)
                        )
                        .clip(CircleShape)
                        .background(
                            RustGradient
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_microphone),
                        contentDescription = "Vozcribe Icon",
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
                        text = "Vozcribe",
                        style = MaterialTheme.typography.displayMedium,
                        color = Slate800
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Sign in to continue",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate500
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
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        // Error message
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = ErrorDark,
                                style = MaterialTheme.typography.bodyMedium,
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
                                    ambientColor = Rust.copy(alpha = 0.4f),
                                    spotColor = Rust.copy(alpha = 0.4f)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Rust
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
                                        style = MaterialTheme.typography.labelLarge
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
                                    color = Slate400,
                                    style = MaterialTheme.typography.bodyMedium
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
                                    contentColor = Slate500
                                ),
                                enabled = !isLoading
                            ) {
                                Text(
                                    text = "Continue as Guest",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
