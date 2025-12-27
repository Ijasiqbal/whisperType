package com.whispertype.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.whispertype.app.ui.components.PlanScreenSkeleton
import com.whispertype.app.ui.components.SkeletonText
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * PlanScreen - Shows current plan status and upgrade option
 */
@Composable
fun PlanScreen(
    priceDisplay: String = "₹79/month",
    minutesLimit: Int = 150,
    planName: String = "WhisperType Pro",
    isLoading: Boolean = false,
    onUpgrade: () -> Unit = {},
    onContactSupport: () -> Unit = {}
) {
    val usageState by UsageDataManager.usageState.collectAsState()
    val isExpired = !usageState.isTrialValid && usageState.currentPlan == UsageDataManager.Plan.FREE_TRIAL
    
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
                )
        ) {
            PlanScreenSkeleton(modifier = Modifier.fillMaxSize())
        }
        return
    }
    
    Column(
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
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Animated Current Plan Card
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(150)) + slideInHorizontally(
                animationSpec = tween(150),
                initialOffsetX = { -30 }
            )
        ) {
            CurrentPlanCard(usageState, planName, isExpired)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Animated Upgrade section (only for non-Pro users)
        AnimatedVisibility(
            visible = isVisible && !usageState.isProUser,
            enter = fadeIn(animationSpec = tween(150, delayMillis = 60)) + slideInHorizontally(
                animationSpec = tween(150, delayMillis = 60),
                initialOffsetX = { -35 }
            )
        ) {
            UpgradeCard(
                isExpired = isExpired,
                minutesLimit = minutesLimit,
                priceDisplay = priceDisplay,
                onUpgrade = onUpgrade
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Contact support - animated to appear after other elements
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(150, delayMillis = 90))
        ) {
            TextButton(onClick = onContactSupport) {
                Text(
                    text = "Need help? Contact Support",
                    fontSize = 14.sp,
                    color = Color(0xFF6366F1)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CurrentPlanCard(
    usageState: UsageDataManager.UsageState,
    planName: String,
    isExpired: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (usageState.isProUser) Color(0xFF6366F1) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (usageState.isProUser) planName else "Free Trial",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (usageState.isProUser) Color.White else Color(0xFF1E293B),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Status badge
            val statusText = when {
                usageState.isProUser -> "Active"
                isExpired -> "Expired"
                else -> "Active"
            }
            val statusColor = when {
                usageState.isProUser -> Color(0xFF10B981)
                isExpired -> Color(0xFFDC2626)
                else -> Color(0xFF10B981)
            }
            
            Box(
                modifier = Modifier
                    .background(
                        color = statusColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Usage info
            if (usageState.isProUser) {
                Text(
                    text = "${usageState.proSecondsRemaining / 60} min remaining",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (usageState.proResetDateMs > 0) {
                    val resetDate = SimpleDateFormat(
                        "MMM d", Locale.getDefault()
                    ).format(Date(usageState.proResetDateMs))
                    Text(
                        text = "Resets on $resetDate",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Text(
                    text = usageState.formattedTimeRemaining + " remaining",
                    fontSize = 18.sp,
                    color = if (isExpired) Color(0xFFDC2626) else Color(0xFF1E293B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun UpgradeCard(
    isExpired: Boolean,
    minutesLimit: Int,
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
                text = if (isExpired) "Continue Using WhisperType" else "Upgrade to Pro",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "$minutesLimit minutes every month",
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
                BenefitItem("$minutesLimit minutes resets monthly")
                BenefitItem("No trial expiry worries")
                BenefitItem("Cancel anytime")
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
                    text = if (isExpired) "Upgrade Now" else "Upgrade to Pro",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BenefitItem(text: String) {
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
