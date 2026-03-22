package com.whispertype.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.Constants
import com.whispertype.app.R
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.ui.components.TrialExpiredScreenSkeleton
import com.whispertype.app.ui.theme.*

/**
 * TrialExpiredScreen - Upgrade flow when trial has ended
 */
@Composable
fun TrialExpiredScreen(
    trialStatus: UsageDataManager.TrialStatus,
    isLoading: Boolean = false,
    getFormattedPrice: (productId: String) -> String? = { null },
    onSelectPlan: (planId: String) -> Unit = {},
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

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val numberFormat = remember { java.text.NumberFormat.getNumberInstance() }

    val plans = remember {
        listOf(
            PlanTier(
                id = Constants.PRODUCT_ID_STARTER,
                name = "Starter",
                price = Constants.PRICE_STARTER_FALLBACK,
                credits = Constants.CREDITS_STARTER,
                features = listOf(
                    "${numberFormat.format(Constants.CREDITS_STARTER)} credits/month",
                    "~200 min standard quality",
                    "Unlimited AUTO mode"
                )
            ),
            PlanTier(
                id = Constants.PRODUCT_ID_PRO,
                name = "Pro",
                price = Constants.PRICE_PRO_FALLBACK,
                credits = Constants.CREDITS_PRO,
                isPopular = true,
                features = listOf(
                    "${numberFormat.format(Constants.CREDITS_PRO)} credits/month",
                    "~600 min standard quality",
                    "Unlimited AUTO mode"
                )
            ),
            PlanTier(
                id = Constants.PRODUCT_ID_UNLIMITED,
                name = "Unlimited",
                price = Constants.PRICE_UNLIMITED_FALLBACK,
                credits = Constants.CREDITS_UNLIMITED,
                features = listOf(
                    "${numberFormat.format(Constants.CREDITS_UNLIMITED)} credits/month",
                    "~1,500 min standard quality",
                    "Unlimited AUTO mode"
                )
            )
        )
    }

    // Skeleton while loading
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBackground),
            contentAlignment = Alignment.Center
        ) {
            TrialExpiredScreenSkeleton(modifier = Modifier.fillMaxSize())
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Header icon
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -30 }
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = Rust.copy(alpha = 0.15f),
                        spotColor = Rust.copy(alpha = 0.2f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(IndigoLight, IndigoTint)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_microphone),
                    contentDescription = null,
                    tint = Rust,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title and reason
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(500, delayMillis = 100))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Ready to Continue?",
                    style = MaterialTheme.typography.displaySmall,
                    color = Slate800,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = reasonText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate500,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Choose a plan to keep using Vozcribe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Plan cards
        plans.forEachIndexed { index, plan ->
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(500, delayMillis = 200 + (index * 80))) +
                    slideInVertically(tween(500, delayMillis = 200 + (index * 80))) { 40 }
            ) {
                CompactPlanCard(
                    plan = plan.copy(price = getFormattedPrice(plan.id) ?: plan.price),
                    onSelect = { onSelectPlan(plan.id) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Free tier reminder
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(400, delayMillis = 450))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = GreenTint
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(22.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = SuccessDark.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = SuccessDark,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "AUTO model remains free & unlimited on all plans",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SuccessDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Contact support
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(400, delayMillis = 500))
        ) {
            TextButton(onClick = onContactSupport) {
                Text(
                    text = "Need help? Contact Support",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Rust
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun CompactPlanCard(
    plan: PlanTier,
    onSelect: () -> Unit
) {
    val isHero = plan.isPopular
    val borderColor = if (isHero) Rust else Slate200

    val cardModifier = if (isHero) {
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Rust.copy(alpha = 0.12f),
                spotColor = Rust.copy(alpha = 0.18f)
            )
    } else {
        Modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WarmWhite),
        border = BorderStroke(
            width = if (isHero) 2.dp else 1.dp,
            color = borderColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHero) 0.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plan.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Slate800
                    )

                    if (isHero) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(RustDark, Rust)
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Popular",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = plan.price,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (isHero) Rust else Slate800
                    )
                    Text(
                        text = "/mo",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Features row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                plan.features.take(2).forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (isHero) Rust else Emerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CTA
            Button(
                onClick = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .then(
                        if (isHero) Modifier.shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(14.dp),
                            ambientColor = Rust.copy(alpha = 0.15f),
                            spotColor = Rust.copy(alpha = 0.2f)
                        ) else Modifier
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHero) Rust else Slate800
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "Select ${plan.name}",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}
