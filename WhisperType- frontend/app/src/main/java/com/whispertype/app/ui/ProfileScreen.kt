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
                        UnifiedProCard(usageState, onManageSubscription)
                    } else {
                        UnifiedTrialCard(usageState)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Inline footer stats ─────────────────────
                    if (usageState.lastCreditsUsed > 0 || usageState.lastUpdated > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (usageState.lastCreditsUsed > 0) {
                                Text(
                                    text = "Last session: ${usageState.lastCreditsUsed} credits",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate400
                                )
                            }
                            if (usageState.lastCreditsUsed > 0 && usageState.lastUpdated > 0) {
                                Text(
                                    text = "  ·  ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate300
                                )
                            }
                            if (usageState.lastUpdated > 0) {
                                Text(
                                    text = "Updated ${formatTimestamp(usageState.lastUpdated)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate400
                                )
                            }
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

// ── Shared Usage Arc Gauge ──────────────────────────────────────────

@Composable
private fun UsageArcGauge(
    animatedProgress: Float,
    trackColor: Color,
    arcColor: Color,
    centerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        val strokeWidth = with(LocalDensity.current) { 8.dp.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            drawArc(
                color = trackColor,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
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

        centerContent()
    }
}

// ── Unified Trial Card (arc + stats in one) ────────────────────────

@Composable
private fun UnifiedTrialCard(usageState: UsageDataManager.UsageState) {
    val progressColor = when (usageState.warningLevel) {
        UsageDataManager.WarningLevel.NINETY_FIVE_PERCENT -> ErrorDark
        UsageDataManager.WarningLevel.EIGHTY_PERCENT -> WarningOrange
        UsageDataManager.WarningLevel.FIFTY_PERCENT -> WarningYellow
        else -> Success
    }

    val warningMessage = when (usageState.warningLevel) {
        UsageDataManager.WarningLevel.NINETY_FIVE_PERCENT ->
            "Almost out of free credits!"
        UsageDataManager.WarningLevel.EIGHTY_PERCENT ->
            "80% of free credits used"
        UsageDataManager.WarningLevel.FIFTY_PERCENT ->
            "50% of free credits used"
        else -> null
    }

    val usedCount = usageState.freeCreditsUsed
    val totalCount = usageState.freeTierCredits
    val targetProgress = if (totalCount > 0) {
        (usedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = if (usageState.isLoading) 0f else targetProgress,
        animationSpec = tween(1200, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "arc_progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (usageState.isTrialValid) WarmWhite else RedLightTint
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header row
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

            // Arc + stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UsageArcGauge(
                    animatedProgress = animatedProgress,
                    trackColor = Slate200,
                    arcColor = progressColor,
                    centerContent = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (usageState.isLoading) {
                                SkeletonText(width = 40.dp, height = 24.dp)
                            } else {
                                Text(
                                    text = "${usageState.freeCreditsRemaining}",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Slate800
                                )
                            }
                            Text(
                                text = "left",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Stats on the right
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$usedCount of $totalCount",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Slate700
                    )
                    Text(
                        text = "credits used",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(
                        progress = targetProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = progressColor,
                        trackColor = Slate200
                    )

                    // Expiry date
                    if (usageState.trialExpiryDateMs > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Expires ${formatTrialExpiry(usageState.trialExpiryDateMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500
                            )
                        }
                    }
                }
            }

            // Warning banner
            if (warningMessage != null && usageState.isTrialValid) {
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
                            imageVector = if (usageState.warningLevel == UsageDataManager.WarningLevel.FIFTY_PERCENT)
                                Icons.Filled.Info else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = progressColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = warningMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = progressColor
                        )
                    }
                }
            }
        }
    }
}

// ── Unified PRO Card (arc + stats in one) ──────────────────────────

@Composable
private fun UnifiedProCard(
    usageState: UsageDataManager.UsageState,
    onManageSubscription: () -> Unit
) {
    val usedCount = usageState.proCreditsUsed
    val totalCount = usageState.proCreditsLimit
    val targetProgress = if (totalCount > 0) {
        (usedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = if (usageState.isLoading) 0f else targetProgress,
        animationSpec = tween(1200, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "arc_progress"
    )

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
                // Header
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

                // Arc + stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UsageArcGauge(
                        animatedProgress = animatedProgress,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        arcColor = Color.White,
                        centerContent = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (usageState.isLoading) {
                                    SkeletonText(width = 40.dp, height = 24.dp)
                                } else {
                                    Text(
                                        text = "${usageState.proCreditsRemaining}",
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "left",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Stats on the right
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$usedCount / $totalCount",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "credits used this cycle",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        @Suppress("DEPRECATION")
                        LinearProgressIndicator(
                            progress = targetProgress.coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )

                        // Reset date
                        if (usageState.proResetDateMs > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.DateRange,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = getResetCountdown(usageState.proResetDateMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                // Member since + manage
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.White.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (usageState.proSubscriptionStartDateMs > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Gold.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Since ${formatMemberSince(usageState.proSubscriptionStartDateMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    TextButton(
                        onClick = onManageSubscription,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Manage",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
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
