package com.whispertype.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import java.text.SimpleDateFormat
import java.util.*

/**
 * PlanScreen - Shows current plan status and upgrade option
 * 
 * Displays:
 * - Current plan (Free Trial or Pro)
 * - Usage stats and remaining time/minutes
 * - Upgrade option for trial users
 * - Calm, informative design
 */
@Composable
fun PlanScreen(
    priceDisplay: String = "₹79/month",
    minutesLimit: Int = 150,
    onUpgrade: () -> Unit = {},
    onContactSupport: () -> Unit = {}
) {
    val usageState by UsageDataManager.usageState.collectAsState()
    val isExpired = !usageState.isTrialValid && usageState.currentPlan == UsageDataManager.Plan.FREE_TRIAL
    
    Column(
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
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Current Plan Card
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
                    text = if (usageState.isProUser) "WhisperType Pro" else "Free Trial",
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
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Upgrade section (only for non-Pro users)
        if (!usageState.isProUser) {
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
                    if (isExpired) {
                        Text(
                            text = "Continue Using WhisperType",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Upgrade to Pro",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Value proposition
                    Text(
                        text = "$minutesLimit minutes every month",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6366F1)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Price
                    Text(
                        text = priceDisplay,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Benefits
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        BenefitItem("✓ $minutesLimit minutes resets monthly")
                        BenefitItem("✓ No trial expiry worries")
                        BenefitItem("✓ Cancel anytime")
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Upgrade button
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
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Contact support
        TextButton(onClick = onContactSupport) {
            Text(
                text = "Need help? Contact Support",
                fontSize = 14.sp,
                color = Color(0xFF6366F1)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun BenefitItem(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = Color(0xFF64748B),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
