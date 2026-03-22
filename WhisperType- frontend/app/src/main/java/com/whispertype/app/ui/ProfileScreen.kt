package com.whispertype.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.ui.components.SkeletonText
import com.whispertype.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * ProfileScreen - Redesigned with circular usage arc, user initials avatar,
 * and warm premium aesthetic.
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

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ── Avatar with user initial ─────────────────────────
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500))
            ) {
                val initial = userEmail?.firstOrNull()?.uppercaseChar() ?: 'V'
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .shadow(
                            elevation = 20.dp,
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
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = DMSerifDisplay,
                            color = Color.White,
                            fontSize = 36.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Email + PRO badge ────────────────────────────────
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 100)) +
                    slideInVertically(
                        animationSpec = tween(500, delayMillis = 100),
                        initialOffsetY = { 20 }
                    )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (userEmail != null) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.titleMedium,
                            color = Slate700
                        )
                    }

                    if (usageState.isProUser) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 4.dp,
                            color = Color.Transparent,
                            modifier = Modifier
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(RustDark, Rust, RustLight)
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "PRO",
                                    tint = Gold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PRO",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Main content ─────────────────────────────────────
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 200)) +
                    slideInVertically(
                        animationSpec = tween(500, delayMillis = 200),
                        initialOffsetY = { 40 }
                    )
            ) {
                Column {
                    if (usageState.isProUser) {
                        ProStatusCard(usageState, onManageSubscription)
                    } else {
                        TrialStatusCard(usageState)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Usage arc card ────────────────────────
                    UsageArcCard(usageState)

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Info row: last transcription + extra info ─
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Last transcription mini card
                        if (usageState.lastCreditsUsed > 0) {
                            MiniStatCard(
                                label = "Last session",
                                value = "${usageState.lastCreditsUsed}",
                                sublabel = "credits used",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null,
                                        tint = Rust,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Last updated mini card
                        if (usageState.lastUpdated > 0) {
                            MiniStatCard(
                                label = "Updated",
                                value = formatTimestamp(usageState.lastUpdated),
                                sublabel = "",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Filled.DateRange,
                                        contentDescription = null,
                                        tint = Rust,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // ── Actions ───────────────────────────────
                    OutlinedButton(
                        onClick = onReportIssue,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
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
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ExitToApp,
                            contentDescription = null,
                            tint = ErrorDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.labelMedium,
                            color = ErrorDark
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// ── Usage Arc Card ──────────────────────────────────────────────────

@Composable
private fun UsageArcCard(usageState: UsageDataManager.UsageState) {
    val usedCount: Int
    val totalCount: Int
    val label: String

    if (usageState.isProUser) {
        usedCount = usageState.proCreditsUsed
        totalCount = usageState.proCreditsLimit
        label = "credits used this cycle"
    } else {
        usedCount = usageState.freeCreditsUsed
        totalCount = usageState.freeTierCredits
        label = "free credits used"
    }

    val targetProgress = if (totalCount > 0) {
        (usedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Animate the arc sweep
    val animatedProgress by animateFloatAsState(
        targetValue = if (usageState.isLoading) 0f else targetProgress,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "arc_progress"
    )

    val arcColor = when (usageState.warningLevel) {
        UsageDataManager.WarningLevel.NINETY_FIVE_PERCENT -> ErrorDark
        UsageDataManager.WarningLevel.EIGHTY_PERCENT -> WarningOrange
        UsageDataManager.WarningLevel.FIFTY_PERCENT -> WarningYellow
        else -> Rust
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WarmWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Usage This Month",
                style = MaterialTheme.typography.titleSmall,
                color = Slate500
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Arc chart
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                val trackColor = Slate200
                val strokeWidth = with(LocalDensity.current) { 10.dp.toPx() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val diameter = size.minDimension
                    val arcSize = Size(diameter, diameter)
                    val topLeft = Offset(
                        (size.width - diameter) / 2f,
                        (size.height - diameter) / 2f
                    )

                    // Track (full arc — 240 degrees, centered at bottom)
                    drawArc(
                        color = trackColor,
                        startAngle = 150f,
                        sweepAngle = 240f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Progress arc
                    if (animatedProgress > 0f) {
                        drawArc(
                            color = arcColor,
                            startAngle = 150f,
                            sweepAngle = 240f * animatedProgress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Center text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (usageState.isLoading) {
                        SkeletonText(width = 60.dp, height = 36.dp)
                    } else {
                        Text(
                            text = "$usedCount",
                            style = MaterialTheme.typography.displayMedium,
                            color = Slate800
                        )
                    }
                    Text(
                        text = "of $totalCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500
            )
        }
    }
}

// ── Mini stat card ──────────────────────────────────────────────────

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    sublabel: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WarmWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = Slate800
            )
            if (sublabel.isNotEmpty()) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }
        }
    }
}

// ── Trial Status Card ───────────────────────────────────────────────

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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (usageState.isTrialValid) WarmWhite else RedLightTint
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
                    color = Slate700
                )

                val (badgeColor, badgeText) = if (usageState.isTrialValid) {
                    Success to "Active"
                } else {
                    ErrorDark to "Expired"
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = Slate200
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Credits display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    if (usageState.isLoading) {
                        SkeletonText(width = 70.dp, height = 36.dp)
                    } else {
                        Text(
                            text = "${usageState.freeCreditsRemaining}",
                            style = MaterialTheme.typography.displaySmall,
                            color = Slate800
                        )
                    }
                    Text(
                        text = "credits remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (usageState.isLoading) {
                        SkeletonText(width = 50.dp, height = 24.dp)
                    } else {
                        Text(
                            text = "${usageState.freeCreditsUsed}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Slate400
                        )
                    }
                    Text(
                        text = "of ${usageState.freeTierCredits} used",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }
            }

            // Trial expiry
            if (usageState.trialExpiryDateMs > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = Slate200)
                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Expires ${formatTrialExpiry(usageState.trialExpiryDateMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500
                    )
                }
            }

            // Warning
            if (warningMessage != null && warningIcon != null && usageState.isTrialValid) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = progressColor.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = warningIcon,
                            contentDescription = null,
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

// ── PRO Status Card ─────────────────────────────────────────────────

@Composable
private fun ProStatusCard(
    usageState: UsageDataManager.UsageState,
    onManageSubscription: () -> Unit
) {
    val usagePercentage = if (usageState.proCreditsLimit > 0) {
        (usageState.proCreditsUsed.toFloat() / usageState.proCreditsLimit.toFloat())
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(RustDark, Rust, RustLight)
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
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF90EE90),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                @Suppress("DEPRECATION")
                LinearProgressIndicator(
                    progress = usagePercentage.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${usageState.proCreditsUsed} / ${usageState.proCreditsLimit}",
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
                Divider(color = Color.White.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))

                // Reset date
                if (usageState.proResetDateMs > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Gold.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
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

                // Manage subscription
                OutlinedButton(
                    onClick = onManageSubscription,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0.4f)
                            )
                        )
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
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

// ── Helpers ──────────────────────────────────────────────────────────

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatTrialExpiry(timestampMs: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}

private fun getResetCountdown(resetDateMs: Long): String {
    val now = System.currentTimeMillis()
    val daysRemaining = ((resetDateMs - now) / (24 * 60 * 60 * 1000)).toInt()
    return when {
        daysRemaining <= 0 -> "Resets today"
        daysRemaining == 1 -> "Resets tomorrow"
        else -> "Resets in $daysRemaining days"
    }
}

private fun formatMemberSince(timestampMs: Long): String {
    val formatter = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}
