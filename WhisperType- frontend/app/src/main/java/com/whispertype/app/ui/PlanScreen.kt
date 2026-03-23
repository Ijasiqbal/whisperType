package com.whispertype.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.Constants
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.ui.components.PlanScreenSkeleton
import com.whispertype.app.ui.theme.*
import java.text.NumberFormat

/**
 * Plan tier data class
 */
data class PlanTier(
    val id: String,
    val name: String,
    val price: String,
    val credits: Int,
    val isPopular: Boolean = false,
    val tagline: String = "",
    val features: List<String>
)

/**
 * PlanScreen - Redesigned with hero card for popular plan and warm premium aesthetic
 */
@Composable
fun PlanScreen(
    isLoading: Boolean = false,
    getFormattedPrice: (productId: String) -> String? = { null },
    onSelectPlan: (planId: String) -> Unit = {},
    onContactSupport: () -> Unit = {}
) {
    val usageState by UsageDataManager.usageState.collectAsState()
    val isExpired = !usageState.isTrialValid && usageState.currentPlan == UsageDataManager.Plan.FREE

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val numberFormat = remember { NumberFormat.getNumberInstance() }

    val plans = remember {
        listOf(
            PlanTier(
                id = Constants.PRODUCT_ID_STARTER,
                name = "Starter",
                price = Constants.PRICE_STARTER_FALLBACK,
                credits = Constants.CREDITS_STARTER,
                tagline = "For everyday use",
                features = listOf(
                    "${numberFormat.format(Constants.CREDITS_STARTER)} credits/month",
                    "~200 min standard quality",
                    "~100 min premium quality",
                    "Unlimited AUTO mode"
                )
            ),
            PlanTier(
                id = Constants.PRODUCT_ID_PRO,
                name = "Pro",
                price = Constants.PRICE_PRO_FALLBACK,
                credits = Constants.CREDITS_PRO,
                isPopular = true,
                tagline = "Best for power users",
                features = listOf(
                    "${numberFormat.format(Constants.CREDITS_PRO)} credits/month",
                    "~600 min standard quality",
                    "~300 min premium quality",
                    "Unlimited AUTO mode"
                )
            ),
            PlanTier(
                id = Constants.PRODUCT_ID_UNLIMITED,
                name = "Unlimited",
                price = Constants.PRICE_UNLIMITED_FALLBACK,
                credits = Constants.CREDITS_UNLIMITED,
                tagline = "For professionals",
                features = listOf(
                    "${numberFormat.format(Constants.CREDITS_UNLIMITED)} credits/month",
                    "~1,500 min standard quality",
                    "~750 min premium quality",
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
                .background(ScreenBackground)
        ) {
            PlanScreenSkeleton(modifier = Modifier.fillMaxSize())
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
        Spacer(modifier = Modifier.height(12.dp))

        // Current status card (non-Pro users)
        AnimatedVisibility(
            visible = isVisible && !usageState.isProUser,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -20 }
        ) {
            CurrentStatusCard(usageState, isExpired)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(400, delayMillis = 100))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (usageState.isProUser) "Manage Your Plan" else "Choose Your Plan",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Slate800
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Unlock premium transcription. AUTO mode is always free.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500,
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
                    slideInVertically(tween(500, delayMillis = 200 + (index * 80))) { 50 }
            ) {
                val isCurrentPlan = usageState.isProUser && usageState.proCreditsLimit == plan.credits
                PlanCard(
                    plan = plan.copy(price = getFormattedPrice(plan.id) ?: plan.price),
                    isCurrentPlan = isCurrentPlan,
                    onSelect = { onSelectPlan(plan.id) }
                )
            }
            if (index < plans.lastIndex) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CurrentStatusCard(
    usageState: UsageDataManager.UsageState,
    isExpired: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isExpired) RedLightTint else GreenTint,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Free Trial",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isExpired) ErrorDark else SuccessDark
                )
                Text(
                    text = if (isExpired) "Expired" else "${usageState.freeCreditsRemaining} credits left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isExpired) ErrorDark.copy(alpha = 0.8f) else Slate500
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isExpired) ErrorDark.copy(alpha = 0.1f)
                    else SuccessDark.copy(alpha = 0.1f)
            ) {
                Text(
                    text = if (isExpired) "Upgrade Now" else "Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isExpired) ErrorDark else SuccessDark,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: PlanTier,
    isCurrentPlan: Boolean,
    onSelect: () -> Unit
) {
    val isHero = plan.isPopular && !isCurrentPlan

    val borderColor = when {
        isCurrentPlan -> SuccessDark
        plan.isPopular -> Rust
        else -> Slate200
    }

    val cardModifier = if (isHero) {
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Rust.copy(alpha = 0.15f),
                spotColor = Rust.copy(alpha = 0.2f)
            )
    } else {
        Modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHero) WarmWhite else WarmWhite
        ),
        border = BorderStroke(
            width = if (plan.isPopular || isCurrentPlan) 2.dp else 1.dp,
            color = borderColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHero) 0.dp else 1.dp
        )
    ) {
        Column {
            // Banner: Popular
            if (plan.isPopular && !isCurrentPlan) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(RustDark, Rust, RustLight)
                            )
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MOST POPULAR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            // Banner: Current plan
            if (isCurrentPlan) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SuccessDark)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "YOUR CURRENT PLAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header: name + tagline left, price right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = plan.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = Slate800
                        )
                        if (plan.tagline.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = plan.tagline,
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = plan.price,
                            style = MaterialTheme.typography.displaySmall,
                            color = if (plan.isPopular) Rust else Slate800,
                            maxLines = 1
                        )
                        Text(
                            text = "/mo",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400,
                            modifier = Modifier.padding(bottom = 5.dp, start = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Divider(color = Slate100, thickness = 1.dp)

                Spacer(modifier = Modifier.height(14.dp))

                // Features
                plan.features.forEach { feature ->
                    FeatureRow(
                        text = feature,
                        accentColor = if (plan.isPopular) Rust else Emerald
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // CTA
                Button(
                    onClick = onSelect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .then(
                            if (isHero) Modifier.shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(14.dp),
                                ambientColor = Rust.copy(alpha = 0.2f),
                                spotColor = Rust.copy(alpha = 0.3f)
                            ) else Modifier
                        ),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isCurrentPlan -> Slate200
                            plan.isPopular -> Rust
                            else -> Slate800
                        },
                        disabledContainerColor = Slate200
                    ),
                    enabled = !isCurrentPlan,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Text(
                        text = when {
                            isCurrentPlan -> "Current Plan"
                            else -> "Get ${plan.name}"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(text: String, accentColor: Color = Emerald) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(20.dp),
            shape = RoundedCornerShape(6.dp),
            color = accentColor.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate600
        )
    }
}
