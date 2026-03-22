package com.whispertype.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whispertype.app.ui.theme.*

/**
 * Opens the Play Store page for this app.
 * Falls back to browser if Play Store app is not available.
 */
private fun openPlayStore(context: Context) {
    val packageName = context.packageName
    try {
        // Try to open Play Store app directly
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$packageName")
            setPackage("com.android.vending")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to browser if Play Store app not available
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        }
        context.startActivity(intent)
    }
}

/**
 * ForceUpdateDialog - Blocking dialog that requires user to update the app
 */
@Composable
fun ForceUpdateDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit = {}
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = WarmWhite
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = WarningTint,
                            shape = RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Update Required",
                        tint = Warning,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Slate800,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Message
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate500,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Update button
                Button(
                    onClick = { openPlayStore(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Rust
                    )
                ) {
                    Text(
                        text = "Update Now",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Exit button
                OutlinedButton(
                    onClick = {
                        (context as? Activity)?.finishAffinity()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Slate500
                    )
                ) {
                    Text(
                        text = "Exit App",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * SoftUpdateDialog - Dismissible dialog suggesting user to update
 */
@Composable
fun SoftUpdateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Update Available",
                tint = Rust,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Update Available",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "A new version of Vozcribe is available. We recommend updating for the best experience and latest features.",
                textAlign = TextAlign.Center,
                color = Slate500
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    openPlayStore(context)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Rust
                )
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = Slate500)
            }
        },
        containerColor = WarmWhite,
        shape = RoundedCornerShape(20.dp)
    )
}
