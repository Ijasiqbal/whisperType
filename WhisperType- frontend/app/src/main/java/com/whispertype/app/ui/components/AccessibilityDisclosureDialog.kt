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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whispertype.app.ui.theme.*

/**
 * AccessibilityDisclosureDialog - Prominent disclosure for Google Play compliance
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
                    color = IndigoTint,
                    shape = RoundedCornerShape(32.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Voice Input",
                tint = Rust,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title
        Text(
            text = "Enable Voice Input",
            style = MaterialTheme.typography.headlineSmall,
            color = Slate800,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = "To type using your voice in any app, Vozcribe uses Android's Accessibility Service to:",
            style = MaterialTheme.typography.titleSmall,
            color = Slate800,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "\u2022 Place transcribed text into text fields",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate500,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "\u2022 Enable quick activation via volume buttons",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate500,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "\u2022 Work seamlessly across your favorite apps",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate500,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy note
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Slate50
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "\u2139\uFE0F",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "How it works",
                        style = MaterialTheme.typography.titleSmall,
                        color = Slate700
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Vozcribe only interacts with text fields when you activate voice input. Your transcribed speech is inserted directly where you're typing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
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
                containerColor = Rust
            )
        ) {
            Text(
                text = "Open Settings",
                style = MaterialTheme.typography.labelLarge
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
                    contentColor = Rust
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
                    style = MaterialTheme.typography.labelLarge
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
                style = MaterialTheme.typography.labelMedium,
                color = Slate500
            )
        }
    }
}
