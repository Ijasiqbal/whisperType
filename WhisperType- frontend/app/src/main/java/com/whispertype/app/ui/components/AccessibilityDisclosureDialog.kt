package com.whispertype.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * AccessibilityDisclosureDialog - Prominent disclosure for Google Play compliance
 * 
 * This dialog explains to users:
 * 1. Why VoxType needs accessibility service access
 * 2. How the accessibility service is used
 * 3. What data is (not) collected
 * 
 * Required for Google Play's AccessibilityServices API policy compliance.
 * 
 * @param guideVideoId Optional YouTube video ID - if provided, shows "Guide Me" button
 * @param onContinue Callback when user wants to proceed to settings
 * @param onDismiss Callback when dialog is dismissed
 * @param onShowGuide Callback when user wants to watch the guide video
 */
@Composable
fun AccessibilityDisclosureDialog(
    guideVideoId: String?,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    onShowGuide: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            MainDialogContent(
                showGuideButton = !guideVideoId.isNullOrBlank(),
                onContinue = onContinue,
                onDismiss = onDismiss,
                onShowGuide = onShowGuide
            )
        }
    }
}

@Composable
private fun MainDialogContent(
    showGuideButton: Boolean,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    onShowGuide: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = Color(0xFFEEF2FF), // Indigo-50
                    shape = RoundedCornerShape(32.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Voice Input",
                tint = Color(0xFF6366F1), // Indigo-500
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title
        Text(
            text = "Enable Voice Input",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = "To type using your voice in any app, VoxType uses Android's Accessibility Service to:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1E293B),
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "• Place transcribed text into text fields",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "• Enable quick activation via volume buttons",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "• Work seamlessly across your favorite apps",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy note
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF8FAFC) // Slate-50
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "ℹ️",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "How it works",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155) // Slate-700
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "VoxType only interacts with text fields when you activate voice input. Your transcribed speech is inserted directly where you're typing.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B), // Slate-500
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Continue button
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6366F1) // Indigo-500
            )
        ) {
            Text(
                text = "Open Settings",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Guide me button - only shown if video ID is configured
        if (showGuideButton) {
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onShowGuide,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF6366F1) // Indigo-500
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Guide Me",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Cancel button
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Cancel",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}
