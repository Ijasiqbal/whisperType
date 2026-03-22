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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whispertype.app.R
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.ui.components.SkeletonText
import com.whispertype.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * ProfileScreen - Displays user profile, usage statistics, and trial/pro info
 */
@Composable
fun ProfileScreen(
    userEmail: String?,
    onSignOut: () -> Unit,
    onManageSubscription: () -> Unit = {},
    onReportIssue: () -> Unit = {}
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
                ScreenBackground
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
                        style = MaterialTheme.typography.titleLarge,
                        color = Slate800
                    )
                }

                // PRO Member badge (only for PRO users)
                if (usageState.isProUser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                RustGradient,
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
                            style = MaterialTheme.typography.titleSmall,
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
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate500
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (usageState.isLoading) {
                        SkeletonText(
                            width = 100.dp,
                            height = 58.dp
                        )
                    } else {
                        Text(
                            text = usageState.formattedMonthlyUsage,
                            style = MaterialTheme.typography.displayLarge,
                            color = Slate800
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (usageState.isLoading) {
                        SkeletonText(
                            width = 40.dp,
                            height = 22.dp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        Text(
                            text = "used",
                            style = MaterialTheme.typography.titleLarge,
                            color = Slate500,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                if (usageState.lastUpdated > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Last updated: ${formatTimestamp(usageState.lastUpdated)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Last transcription info
        if (usageState.lastCreditsUsed > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate100)
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
                        tint = Rust,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Last Transcription",
                            style = MaterialTheme.typography.titleSmall,
                            color = Slate800
                        )
                        Text(
                            text = "${usageState.lastCreditsUsed} credits",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Report an Issue button
        OutlinedButton(
            onClick = onReportIssue,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Rust
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Report",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Report an Issue",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sign out button
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ErrorDark
            )
        ) {
            Text(
                text = "Sign Out",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

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
        UsageDataManager.WarningLevel.NINETY_FIVE_PERCENT -> ErrorDark
        UsageDataManager.WarningLevel.EIGHTY_PERCENT -> WarningOrange
        UsageDataManager.WarningLevel.FIFTY_PERCENT -> WarningYellow
        else -> Success
    }

    val warningMessage = when (usageState.warningLevel) {
        UsageDataManager.WarningLevel.NINETY_FIVE_PERCENT ->
            "You're almost out of free credits!"
        UsageDataManager.WarningLevel.EIGHTY_PERCENT ->
            "80% of your free credits used"
        UsageDataManager.WarningLevel.FIFTY_PERCENT ->
            "50% of your free credits used"
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
            containerColor = if (usageState.isTrialValid) Color.White else RedTint
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
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate500
                )

                // Status badge
                val (badgeColor, badgeText) = if (usageState.isTrialValid) {
                    Success to "Active"
                } else {
                    ErrorDark to "Expired"
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
                        style = MaterialTheme.typography.labelSmall,
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
                trackColor = Slate200
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Credits remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    if (usageState.isLoading) {
                        SkeletonText(width = 80.dp, height = 40.dp)
                    } else {
                        Text(
                            text = "${usageState.freeCreditsRemaining}",
                            style = MaterialTheme.typography.displayMedium,
                            color = Slate800
                        )
                    }
                    if (usageState.isLoading) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SkeletonText(width = 100.dp, height = 18.dp)
                    } else {
                        Text(
                            text = "credits remaining",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (usageState.isLoading) {
                        SkeletonText(width = 60.dp, height = 30.dp)
                    } else {
                        Text(
                            text = "${usageState.freeCreditsUsed}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Slate400
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (usageState.isLoading) {
                        SkeletonText(width = 100.dp, height = 16.dp)
                    } else {
                        Text(
                            text = "of ${usageState.freeTierCredits} used",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                    }
                }
            }

            // Trial expiry date
            if (usageState.trialExpiryDateMs > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Slate200)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = "Calendar",
                        tint = Slate500,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Trial expires: ${formatTrialExpiry(usageState.trialExpiryDateMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500
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
                            style = MaterialTheme.typography.bodyMedium,
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
    // Calculate usage percentage for credits
    val usagePercentage = if (usageState.proCreditsLimit > 0) {
        (usageState.proCreditsUsed.toFloat() / usageState.proCreditsLimit.toFloat())
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
                    RustGradient
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
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    // Active badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = Emerald.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = Emerald
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

                // Usage text (credits)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${usageState.proCreditsUsed} / ${usageState.proCreditsLimit} credits",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "${usageState.proCreditsRemaining} left",
                        style = MaterialTheme.typography.bodyMedium,
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
                            style = MaterialTheme.typography.bodyMedium,
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
                            style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.labelMedium
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

