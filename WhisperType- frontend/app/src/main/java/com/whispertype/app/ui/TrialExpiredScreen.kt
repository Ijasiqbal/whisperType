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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.Constants
import com.whispertype.app.R
import com.whispertype.app.data.UsageDataManager
import com.whispertype.app.ui.components.TrialExpiredScreenSkeleton

/**
 * TrialExpiredScreen - Upgrade flow when trial has ended
 * Shows 3-tier pricing options
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

    // Animation state
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val numberFormat = remember { java.text.NumberFormat.getNumberInstance() }

    // Plan tiers — Google Play handles regional pricing automatically
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
                ),
            contentAlignment = Alignment.Center
        ) {
            TrialExpiredScreenSkeleton(modifier = Modifier.fillMaxSize())
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
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header Icon
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -20 }
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = Color(0xFFEDE9FE),
                        shape = RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_microphone),
                    contentDescription = null,
                    tint = Color(0xFF7C3AED),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title and reason
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(200, delayMillis = 50))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Ready to Continue?",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = reasonText,
                    fontSize = 15.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Choose a plan to keep using VoxType",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Plan Cards
        plans.forEachIndexed { index, plan ->
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(200, delayMillis = 100 + (index * 50))) +
                        slideInVertically(tween(200, delayMillis = 100 + (index * 50))) { 30 }
            ) {
                CompactPlanCard(
                    plan = plan.copy(price = getFormattedPrice(plan.id) ?: plan.price),
                    onSelect = { onSelectPlan(plan.id) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Free tier reminder
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(200, delayMillis = 280))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AUTO model remains free & unlimited on all plans",
                        fontSize = 13.sp,
                        color = Color(0xFF16A34A)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Contact support
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(200, delayMillis = 320))
        ) {
            TextButton(onClick = onContactSupport) {
                Text(
                    text = "Need help? Contact Support",
                    fontSize = 14.sp,
                    color = Color(0xFF6366F1)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun CompactPlanCard(
    plan: PlanTier,
    onSelect: () -> Unit
) {
    val borderColor = if (plan.isPopular) Color(0xFF6366F1) else Color(0xFFE2E8F0)
    val backgroundColor = if (plan.isPopular) Color(0xFFFAFAFF) else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(
            width = if (plan.isPopular) 2.dp else 1.dp,
            color = borderColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (plan.isPopular) 3.dp else 1.dp
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )

                    if (plan.isPopular) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Popular",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Price
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = plan.price,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "/mo",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Features in a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                plan.features.take(2).forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = feature,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // CTA Button
            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (plan.isPopular) Color(0xFF6366F1) else Color(0xFF1E293B)
                ),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(
                    text = "Select ${plan.name}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
