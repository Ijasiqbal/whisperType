package com.whispertype.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.R
import com.whispertype.app.ui.theme.*

/**
 * @param startExit When true, triggers the exit animation.
 * @param onExitComplete Called after exit animation finishes.
 */
@Composable
fun SplashScreen(
    startExit: Boolean = false,
    onExitComplete: () -> Unit = {}
) {
    // ── ENTRANCE: Orb scale-in ──────────────────────────────────
    val orbScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        orbScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // ── ENTRANCE: Orb alpha ─────────────────────────────────────
    val orbAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        orbAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
    }

    // ── ENTRANCE: Text fade-in ──────────────────────────────────
    val textAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing)
        )
    }

    // ── EXIT: Triggered when startExit becomes true ─────────────
    val exitProgress = remember { Animatable(0f) }
    LaunchedEffect(startExit) {
        if (startExit) {
            exitProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
            onExitComplete()
        }
    }

    // Derived exit values
    val exitAlpha = 1f - exitProgress.value
    val exitOrbScale = 1f + exitProgress.value * 0.3f   // orb grows slightly
    val exitTranslateY = exitProgress.value * -80f       // content floats up

    // ── Subtle pulsing glow behind orb ──────────────────────────
    val glowScale by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exitAlpha }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Cream,
                        Color(0xFFFFF3ED),
                        Color(0xFFFEEBE2),
                        Color(0xFFFCE1D4)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                translationY = exitTranslateY
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Pulsing glow ring behind the orb
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(glowScale)
                        .graphicsLayer { alpha = 0.15f * exitAlpha }
                        .clip(CircleShape)
                        .background(RustGradient)
                )

                // Main orb — same as home screen
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = orbScale.value * exitOrbScale
                            scaleY = orbScale.value * exitOrbScale
                            alpha = orbAlpha.value * exitAlpha
                        }
                        .shadow(
                            elevation = 24.dp,
                            shape = CircleShape,
                            ambientColor = Rust.copy(alpha = 0.25f),
                            spotColor = Rust.copy(alpha = 0.3f)
                        )
                        .clip(CircleShape)
                        .background(RustGradient),
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

            Spacer(modifier = Modifier.height(32.dp))

            // ── App name ────────────────────────────────────────
            Text(
                text = "Vozcribe",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = DMSerifDisplay,
                    fontWeight = FontWeight.Normal,
                    fontSize = 38.sp,
                    letterSpacing = (-1).sp,
                    color = Slate800.copy(alpha = textAlpha.value * exitAlpha)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "voice to text, perfected",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                    color = Slate500.copy(alpha = textAlpha.value * exitAlpha)
                )
            )
        }
    }
}
