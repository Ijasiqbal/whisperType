package com.whispertype.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shimmer effect brush for skeleton loading animation
 */
@Composable
fun shimmerBrush(): Brush {
    val shimmerColors = listOf(
        Color(0xFFE2E8F0),
        Color(0xFFF1F5F9),
        Color(0xFFE2E8F0)
    )
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnimation - 500f, 0f),
        end = Offset(translateAnimation, 0f)
    )
}

/**
 * Skeleton loader for text content
 */
@Composable
fun SkeletonText(
    width: Dp = 120.dp,
    height: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(shimmerBrush())
    )
}

/**
 * Skeleton loader for circular avatars/icons
 */
@Composable
fun SkeletonCircle(
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(shimmerBrush())
    )
}

/**
 * Skeleton loader for rectangular cards
 */
@Composable
fun SkeletonCard(
    height: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(shimmerBrush())
    )
}

/**
 * Profile screen skeleton - shows while loading user data
 */
@Composable
fun ProfileScreenSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Avatar skeleton
        SkeletonCircle(size = 100.dp)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Email skeleton
        SkeletonText(width = 180.dp, height = 20.dp)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Trial status card skeleton
        SkeletonCard(height = 200.dp)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Usage card skeleton
        SkeletonCard(height = 150.dp)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Last transcription skeleton
        SkeletonCard(height = 60.dp)
    }
}

/**
 * Plan screen skeleton - shows while loading plan data (3-tier pricing)
 */
@Composable
fun PlanScreenSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Current status card skeleton
        SkeletonCard(height = 70.dp)

        Spacer(modifier = Modifier.height(20.dp))

        // Title skeleton
        SkeletonText(width = 180.dp, height = 24.dp)

        Spacer(modifier = Modifier.height(8.dp))

        SkeletonText(width = 220.dp, height = 14.dp)

        Spacer(modifier = Modifier.height(24.dp))

        // 3 Plan cards skeleton
        repeat(3) {
            SkeletonCard(height = 200.dp)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Trial expired screen skeleton - shows while loading remote config (3-tier pricing)
 */
@Composable
fun TrialExpiredScreenSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Icon skeleton
        SkeletonCircle(size = 72.dp)

        Spacer(modifier = Modifier.height(20.dp))

        // Title skeleton
        SkeletonText(width = 200.dp, height = 26.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // Reason text skeleton
        SkeletonText(width = 240.dp, height = 15.dp)

        Spacer(modifier = Modifier.height(4.dp))

        SkeletonText(width = 200.dp, height = 14.dp)

        Spacer(modifier = Modifier.height(28.dp))

        // 3 Compact plan cards skeleton
        repeat(3) {
            SkeletonCard(height = 140.dp)
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Free tier reminder skeleton
        SkeletonCard(height = 45.dp)
    }
}

/**
 * Generic loading indicator with shimmer effect
 */
@Composable
fun ShimmerLoadingIndicator(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            SkeletonCard(height = 80.dp)
        }
    }
}
