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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.Constants
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.ui.components.PlanScreenSkeleton

/**
 * Plan tier data class
 */
data class PlanTier(
    val id: String,
    val name: String,
    val price: String,
    val credits: Int,
    val isPopular: Boolean = false,
    val features: List<String>
)

/**
 * PlanScreen - Shows current plan status and 3-tier upgrade options
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

    // Animation state
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    // Plan tiers — Google Play handles regional pricing automatically
    val plans = remember {
        listOf(
            PlanTier(
                id = Constants.PRODUCT_ID_STARTER,
                name = "Starter",
                price = Constants.PRICE_STARTER_FALLBACK,
                credits = Constants.CREDITS_STARTER,
                features = listOf(
                    "${Constants.CREDITS_STARTER} credits/month",
                    "~200 min STANDARD",
                    "~100 min PREMIUM",
                    "Unlimited AUTO"
                )
            ),
            PlanTier(
                id = Constants.PRODUCT_ID_PRO,
                name = "Pro",
                price = Constants.PRICE_PRO_FALLBACK,
                credits = Constants.CREDITS_PRO,
                isPopular = true,
                features = listOf(
                    "${Constants.CREDITS_PRO} credits/month",
                    "~600 min STANDARD",
                    "~300 min PREMIUM",
                    "Unlimited AUTO"
                )
            ),
            PlanTier(
                id = Constants.PRODUCT_ID_UNLIMITED,
                name = "Unlimited",
                price = Constants.PRICE_UNLIMITED_FALLBACK,
                credits = Constants.CREDITS_UNLIMITED,
                features = listOf(
                    "${Constants.CREDITS_UNLIMITED} credits/month",
                    "~1500 min STANDARD",
                    "~750 min PREMIUM",
                    "Unlimited AUTO"
                )
            )
        )
    }

    // Show skeleton while loading
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFEEF2FF), Color(0xFFF8FAFC)),
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
                    colors = listOf(Color(0xFFEEF2FF), Color(0xFFF8FAFC)),
                    center = Offset(0.5f, 0f),
                    radius = 1500f
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Current Plan Status (for non-Pro users)
        AnimatedVisibility(
            visible = isVisible && !usageState.isProUser,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -20 }
        ) {
            CurrentStatusCard(usageState, isExpired)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(200, delayMillis = 50))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (usageState.isProUser) "Manage Your Plan" else "Choose Your Plan",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AUTO model is always free, unlimited",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Plan Cards
        plans.forEachIndexed { index, plan ->
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(200, delayMillis = 100 + (index * 50))) +
                        slideInVertically(tween(200, delayMillis = 100 + (index * 50))) { 30 }
            ) {
                PlanCard(
                    plan = plan.copy(price = getFormattedPrice(plan.id) ?: plan.price),
                    isCurrentPlan = usageState.isProUser && usageState.proCreditsLimit == plan.credits,
                    onSelect = { onSelectPlan(plan.id) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Contact support
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(200, delayMillis = 300))
        ) {
            TextButton(onClick = onContactSupport) {
                Text(
                    text = "Need help? Contact Support",
                    fontSize = 14.sp,
                    color = Color(0xFF6366F1)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CurrentStatusCard(
    usageState: UsageDataManager.UsageState,
    isExpired: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpired) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
        )
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExpired) Color(0xFFDC2626) else Color(0xFF16A34A)
                )
                Text(
                    text = if (isExpired) "Expired" else "${usageState.freeCreditsRemaining} credits left",
                    fontSize = 14.sp,
                    color = if (isExpired) Color(0xFFDC2626).copy(alpha = 0.8f) else Color(0xFF64748B)
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        color = if (isExpired) Color(0xFFDC2626).copy(alpha = 0.1f)
                               else Color(0xFF16A34A).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isExpired) "Upgrade Now" else "Active",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isExpired) Color(0xFFDC2626) else Color(0xFF16A34A)
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
    val borderColor = when {
        isCurrentPlan -> Color(0xFF16A34A)
        plan.isPopular -> Color(0xFF6366F1)
        else -> Color(0xFFE2E8F0)
    }

    val backgroundColor = when {
        plan.isPopular -> Color(0xFFFAFAFF)
        else -> Color.White
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(
            width = if (plan.isPopular || isCurrentPlan) 2.dp else 1.dp,
            color = borderColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (plan.isPopular) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header row with name and badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plan.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )

                if (plan.isPopular) {
                    Box(
                        modifier = Modifier
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Popular",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                if (isCurrentPlan) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF16A34A),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Current",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Price
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = plan.price,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "/month",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Features
            plan.features.forEach { feature ->
                FeatureRow(feature)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CTA Button
            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (plan.isPopular) Color(0xFF6366F1) else Color(0xFF1E293B),
                    disabledContainerColor = Color(0xFFE2E8F0)
                ),
                enabled = !isCurrentPlan
            ) {
                Text(
                    text = when {
                        isCurrentPlan -> "Current Plan"
                        else -> "Select ${plan.name}"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = Color(0xFF22C55E),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF64748B)
        )
    }
}
