package com.whispertype.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.R
import com.whispertype.app.data.UsageDataManager

/**
 * TrialExpiredScreen - Upgrade flow when trial has ended
 * 
 * Iteration 3: Calm, matter-of-fact upgrade screen
 * - Clear message about trial ending
 * - Single Pro option with clear value proposition
 * - No urgency, no sales pressure
 */
@Composable
fun TrialExpiredScreen(
    trialStatus: UsageDataManager.TrialStatus,
    priceDisplay: String = "₹79/month",
    minutesLimit: Int = 150,
    onUpgrade: () -> Unit = {},
    onContactSupport: () -> Unit = {}
) {
    val reasonText = when (trialStatus) {
        UsageDataManager.TrialStatus.EXPIRED_TIME -> 
            "Your 3-month free trial period has ended."
        UsageDataManager.TrialStatus.EXPIRED_USAGE -> 
            "You've used all your free trial minutes."
        else -> 
            "Your free trial has ended."
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF8FAFC),
                        Color(0xFFE2E8F0)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
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
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title - calm, not alarming
            Text(
                text = "Ready to Continue?",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Reason
            Text(
                text = reasonText,
                fontSize = 16.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Pro section - single clear option
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "WhisperType Pro",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Clear value proposition
                    Text(
                        text = "$minutesLimit minutes every month",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6366F1)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Price - clear and simple
                    Text(
                        text = priceDisplay,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Benefits - brief list
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        BenefitRow("✓ $minutesLimit minutes resets monthly")
                        BenefitRow("✓ No trial expiry anxiety")
                        BenefitRow("✓ Cancel anytime")
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Single upgrade button
                    Button(
                        onClick = onUpgrade,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        )
                    ) {
                        Text(
                            text = "Upgrade to Pro",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Contact support
            TextButton(onClick = onContactSupport) {
                Text(
                    text = "Need help? Contact Support",
                    fontSize = 14.sp,
                    color = Color(0xFF6366F1)
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = Color(0xFF64748B),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

