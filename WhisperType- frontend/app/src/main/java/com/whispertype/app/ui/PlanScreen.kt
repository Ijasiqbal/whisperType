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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.Constants
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.ui.components.PlanScreenSkeleton
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

    val numberFormat = remember { NumberFormat.getNumberInstance() }

    // Plan tiers — Google Play handles regional pricing automatically
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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Unlock premium transcription. AUTO mode is always free.",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
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
            if (index < plans.lastIndex) {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            width = if (plan.isPopular || isCurrentPlan) 2.dp else 1.dp,
            color = borderColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (plan.isPopular) 6.dp else 1.dp
        )
    ) {
        Column {
            // Full-width banner for Popular plan
            if (plan.isPopular && !isCurrentPlan) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            )
                        )
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MOST POPULAR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Full-width banner for Current plan
            if (isCurrentPlan) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF16A34A))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "YOUR CURRENT PLAN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Header: name + tagline on left, price on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = plan.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        if (plan.tagline.isNotEmpty()) {
                            Text(
                                text = plan.tagline,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = plan.price,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (plan.isPopular) Color(0xFF6366F1) else Color(0xFF1E293B)
                        )
                        Text(
                            text = "/mo",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Divider between header and features
                Divider(
                    color = Color(0xFFF1F5F9),
                    thickness = 1.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Features
                plan.features.forEach { feature ->
                    FeatureRow(
                        text = feature,
                        accentColor = if (plan.isPopular) Color(0xFF6366F1) else Color(0xFF22C55E)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // CTA Button
                Button(
                    onClick = onSelect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (plan.isPopular) Color(0xFF6366F1) else Color(0xFF1E293B),
                        disabledContainerColor = Color(0xFFE2E8F0)
                    ),
                    enabled = !isCurrentPlan,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (plan.isPopular) 4.dp else 0.dp
                    )
                ) {
                    Text(
                        text = when {
                            isCurrentPlan -> "Current Plan"
                            else -> "Get ${plan.name}"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(text: String, accentColor: Color = Color(0xFF22C55E)) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(
                    color = accentColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(5.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color(0xFF475569)
        )
    }
}
