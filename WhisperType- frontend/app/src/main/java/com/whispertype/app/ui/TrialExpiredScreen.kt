package com.whispertype.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.R
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.ui.components.TrialExpiredScreenSkeleton
import kotlinx.coroutines.delay

/**
 * TrialExpiredScreen - Upgrade flow when trial has ended
 */
@Composable
fun TrialExpiredScreen(
    trialStatus: UsageDataManager.TrialStatus,
    priceDisplay: String = "₹79/month",
    creditsLimit: Int = 10000,
    planName: String = "VoxType Pro",
    isLoading: Boolean = false,
    onUpgrade: () -> Unit = {},
    onContactSupport: () -> Unit = {}
) {
    val reasonText = when (trialStatus) {
        UsageDataManager.TrialStatus.EXPIRED_TIME ->
            "Your free trial period has ended."
        UsageDataManager.TrialStatus.EXPIRED_USAGE ->
            "You've used all your free credits."
        else ->
            "Your free trial has ended."
    }
    
    // Animation state - trigger on first composition
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    // Show skeleton while loading remote config
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFEEF2FF),
                            Color(0xFFF8FAFC)
                        ),
                        center = Offset(0.5f, 0f),
                        radius = 1500f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            TrialExpiredScreenSkeleton(modifier = Modifier.fillMaxSize())
        }
        return
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFEEF2FF),
                        Color(0xFFF8FAFC)
                    ),
                    center = Offset(0.5f, 0f),
                    radius = 1500f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Icon
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(150)) + slideInHorizontally(
                    animationSpec = tween(150),
                    initialOffsetX = { -30 }
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = Color(0xFFEDE9FE),
                            shape = RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_microphone),
                        contentDescription = null,
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Animated Title and reason
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(150, delayMillis = 30)) + slideInHorizontally(
                    animationSpec = tween(150, delayMillis = 30),
                    initialOffsetX = { -25 }
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Ready to Continue?",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = reasonText,
                        fontSize = 16.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Animated Pro section card
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(150, delayMillis = 60)) + slideInHorizontally(
                    animationSpec = tween(150, delayMillis = 60),
                    initialOffsetX = { -35 }
                )
            ) {
                ProUpgradeCard(
                    planName = planName,
                    creditsLimit = creditsLimit,
                    priceDisplay = priceDisplay,
                    onUpgrade = onUpgrade
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Contact support
            TextButton(onClick = onContactSupport) {
                Text(
                    text = "Need help? Contact Support",
                    fontSize = 14.sp,
                    color = Color(0xFF6366F1)
                )
            }
        }
    }
}

@Composable
private fun ProUpgradeCard(
    planName: String,
    creditsLimit: Int,
    priceDisplay: String,
    onUpgrade: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = planName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "$creditsLimit credits every month",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6366F1)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = priceDisplay,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Benefits with icons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                BenefitRow("$creditsLimit credits resets monthly")
                BenefitRow("Higher limits for premium models")
                BenefitRow("Cancel anytime")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1)
                )
            ) {
                Text(
                    text = "Upgrade to Pro",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Check",
            tint = Color(0xFF22C55E),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF64748B)
        )
    }
}
