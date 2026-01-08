package com.whispertype.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whispertype.app.R
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.ui.components.SkeletonText
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * ProfileScreen - Displays user profile, usage statistics, and trial/pro info
 */
@Composable
fun ProfileScreen(
    userEmail: String?,
    onSignOut: () -> Unit,
    onManageSubscription: () -> Unit = {}
) {
    val usageState by UsageDataManager.usageState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    
    // Animation state - trigger on first composition
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    // Gradient background matching app theme
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
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Animated Profile icon - using person icon instead of mic
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
                    painter = painterResource(id = R.drawable.ic_person),
                    contentDescription = "Profile",
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Animated User email and PRO badge
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(150, delayMillis = 30)) + slideInHorizontally(
                animationSpec = tween(150, delayMillis = 30),
                initialOffsetX = { -25 }
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (userEmail != null) {
                    Text(
                        text = userEmail,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E293B)
                    )
                }
                
                // PRO Member badge (only for PRO users)
                if (usageState.isProUser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF6366F1),
                                        Color(0xFF8B5CF6)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "PRO",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PRO Member",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Animated content
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(150, delayMillis = 60)) + slideInHorizontally(
                animationSpec = tween(150, delayMillis = 60),
                initialOffsetX = { -35 }
            )
        ) {
            Column {
                // Show Pro Status Card for PRO users, Trial Status Card for free users
                if (usageState.isProUser) {
                    ProStatusCard(usageState, onManageSubscription)
                } else {
                    TrialStatusCard(usageState)
                }
        
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
                    if (usageState.isLoading) {
                        // Show skeleton while loading - 48sp ≈ 58dp with font metrics
                        SkeletonText(
                            width = 100.dp,
                            height = 58.dp
                        )
                    } else {
                        Text(
                            text = usageState.formattedMonthlyUsage,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (usageState.isLoading) {
                        // Skeleton for "used" label - 18sp ≈ 22dp
                        SkeletonText(
                            width = 40.dp,
                            height = 22.dp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        Text(
                            text = "used",
                            fontSize = 18.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
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
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Transcription",
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(24.dp)
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
        }
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
            "You're almost out of free minutes!"
        UsageDataManager.WarningLevel.EIGHTY_PERCENT -> 
            "80% of your free trial used"
        UsageDataManager.WarningLevel.FIFTY_PERCENT -> 
            "50% of your free trial used"
        else -> null
    }
    
    val warningIcon = when (usageState.warningLevel) {
        UsageDataManager.WarningLevel.NINETY_FIVE_PERCENT,
        UsageDataManager.WarningLevel.EIGHTY_PERCENT -> Icons.Filled.Warning
        UsageDataManager.WarningLevel.FIFTY_PERCENT -> Icons.Filled.Info
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
                    if (usageState.isLoading) {
                        // 32sp ≈ 40dp with font metrics
                        SkeletonText(width = 80.dp, height = 40.dp)
                    } else {
                        Text(
                            text = usageState.formattedTimeRemaining,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }
                    if (usageState.isLoading) {
                        // Skeleton for "remaining" label - 14sp ≈ 18dp
                        Spacer(modifier = Modifier.height(4.dp))
                        SkeletonText(width = 70.dp, height = 18.dp)
                    } else {
                        Text(
                            text = "remaining",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    if (usageState.isLoading) {
                        // 24sp ≈ 30dp with font metrics
                        SkeletonText(width = 60.dp, height = 30.dp)
                    } else {
                        Text(
                            text = usageState.formattedTimeUsed,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (usageState.isLoading) {
                        // 12sp ≈ 16dp with font metrics
                        SkeletonText(width = 100.dp, height = 16.dp)
                    } else {
                        Text(
                            text = "of ${usageState.formattedTotalTime} used",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
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
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = "Calendar",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Trial expires: ${formatTrialExpiry(usageState.trialExpiryDateMs)}",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            
            // Warning message with icon
            if (warningMessage != null && warningIcon != null && usageState.isTrialValid) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = warningIcon,
                            contentDescription = "Warning",
                            tint = progressColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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

/**
 * PRO Status Card - Shows PRO subscription info with premium styling
 */
@Composable
private fun ProStatusCard(
    usageState: UsageDataManager.UsageState,
    onManageSubscription: () -> Unit
) {
    val proMinutesUsed = usageState.proSecondsUsed / 60
    val proMinutesLimit = usageState.proSecondsLimit / 60
    val proMinutesRemaining = usageState.proSecondsRemaining / 60
    val usagePercentage = if (usageState.proSecondsLimit > 0) {
        (usageState.proSecondsUsed.toFloat() / usageState.proSecondsLimit.toFloat())
    } else 0f
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6366F1),
                            Color(0xFF8B5CF6)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRO Subscription",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    
                    // Active badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Active",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF10B981)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Usage progress bar
                @Suppress("DEPRECATION")
                LinearProgressIndicator(
                    progress = usagePercentage.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Usage text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$proMinutesUsed / $proMinutesLimit min used",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "$proMinutesRemaining min left",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Divider(color = Color.White.copy(alpha = 0.2f))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Reset date countdown
                if (usageState.proResetDateMs > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = "Reset date",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = getResetCountdown(usageState.proResetDateMs),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                
                // Member since
                if (usageState.proSubscriptionStartDateMs > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Member since",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Member since ${formatMemberSince(usageState.proSubscriptionStartDateMs)}",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Manage subscription button
                OutlinedButton(
                    onClick = onManageSubscription,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.5f)))
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Manage",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Manage Subscription",
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Calculate reset countdown text
 */
private fun getResetCountdown(resetDateMs: Long): String {
    val now = System.currentTimeMillis()
    val daysRemaining = ((resetDateMs - now) / (24 * 60 * 60 * 1000)).toInt()
    return when {
        daysRemaining <= 0 -> "Resets today"
        daysRemaining == 1 -> "Resets tomorrow"
        else -> "Resets in $daysRemaining days"
    }
}

/**
 * Format member since date
 */
private fun formatMemberSince(timestampMs: Long): String {
    val formatter = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}
