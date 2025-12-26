package com.whispertype.app.ui

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whispertype.app.R
import com.whispertype.app.data.UsageDataManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * ProfileScreen - Displays user profile, usage statistics, and trial info
 */
@Composable
fun ProfileScreen(
    userEmail: String?,
    onSignOut: () -> Unit
) {
    val usageState by UsageDataManager.usageState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Profile icon
        Box(
            modifier = Modifier
                .size(100.dp)
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
                contentDescription = "Profile",
                tint = Color.White,
                modifier = Modifier.size(50.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // User email
        if (userEmail != null) {
            Text(
                text = userEmail,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Trial Status Card (Iteration 2)
        TrialStatusCard(usageState)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Usage Card (Monthly - keeping for backward compatibility)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Usage This Month",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = usageState.formattedMonthlyUsage,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "used",
                        fontSize = 18.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                if (usageState.lastUpdated > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Last updated: ${formatTimestamp(usageState.lastUpdated)}",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Last transcription info
        if (usageState.lastSecondsUsed > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📝",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Last Transcription",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "${usageState.lastSecondsUsed} seconds",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Sign out button
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFDC2626)
            )
        ) {
            Text(
                text = "Sign Out",
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Trial Status Card - Shows trial progress, remaining time, and warnings
 */
@Composable
private fun TrialStatusCard(usageState: UsageDataManager.UsageState) {
    val progressColor = when (usageState.warningLevel) {
        UsageDataManager.WarningLevel.NINETY_FIVE_PERCENT -> Color(0xFFDC2626)
        UsageDataManager.WarningLevel.EIGHTY_PERCENT -> Color(0xFFF97316)
        UsageDataManager.WarningLevel.FIFTY_PERCENT -> Color(0xFFEAB308)
        else -> Color(0xFF22C55E)
    }
    
    val warningMessage = when (usageState.warningLevel) {
        UsageDataManager.WarningLevel.NINETY_FIVE_PERCENT -> 
            "⚠️ You're almost out of free minutes!"
        UsageDataManager.WarningLevel.EIGHTY_PERCENT -> 
            "⚠️ 80% of your free trial used"
        UsageDataManager.WarningLevel.FIFTY_PERCENT -> 
            "ℹ️ 50% of your free trial used"
        else -> null
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (usageState.isTrialValid) Color.White else Color(0xFFFEE2E2)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Free Trial",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
                
                // Status badge
                val (badgeColor, badgeText) = if (usageState.isTrialValid) {
                    Color(0xFF22C55E) to "Active"
                } else {
                    Color(0xFFDC2626) to "Expired"
                }
                
                Box(
                    modifier = Modifier
                        .background(
                            color = badgeColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = badgeColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress bar
            val progress = (usageState.usagePercentage / 100f).coerceIn(0f, 1f)
            @Suppress("DEPRECATION")
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = Color(0xFFE2E8F0)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Minutes remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = usageState.formattedTimeRemaining,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "remaining",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = usageState.formattedTimeUsed,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "of ${usageState.formattedTotalTime} used",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            
            // Trial expiry date
            if (usageState.trialExpiryDateMs > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📅",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Trial expires: ${formatTrialExpiry(usageState.trialExpiryDateMs)}",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            
            // Warning message
            if (warningMessage != null && usageState.isTrialValid) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = progressColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = warningMessage,
                        fontSize = 14.sp,
                        color = progressColor
                    )
                }
            }
        }
    }
}

/**
 * Format timestamp to readable string
 */
private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

/**
 * Format trial expiry date
 */
private fun formatTrialExpiry(timestampMs: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}
