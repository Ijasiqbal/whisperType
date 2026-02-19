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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
 * 
 * Features:
 * - Non-dismissible (cannot be closed by clicking outside or back button)
 * - Opens Play Store when user clicks Update
 * - Option to exit app
 * - Matches app's design language (indigo theme)
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
                containerColor = Color.White
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
                            color = Color(0xFFFEF3C7), // Amber-100
                            shape = RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Update Required",
                        tint = Color(0xFFF59E0B), // Amber-500
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Title
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Message
                Text(
                    text = message,
                    fontSize = 15.sp,
                    color = Color(0xFF64748B),
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
                        containerColor = Color(0xFF6366F1) // Indigo-500
                    )
                ) {
                    Text(
                        text = "Update Now",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Exit button
                OutlinedButton(
                    onClick = {
                        // Exit the app safely
                        (context as? Activity)?.finishAffinity()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF64748B)
                    )
                ) {
                    Text(
                        text = "Exit App",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * SoftUpdateDialog - Dismissible dialog suggesting user to update
 * 
 * Features:
 * - Can be dismissed by user
 * - Opens Play Store when user clicks Update
 * - Option to continue with current version
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
                tint = Color(0xFF6366F1),
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
                text = "A new version of Wozcribe is available. We recommend updating for the best experience and latest features.",
                textAlign = TextAlign.Center,
                color = Color(0xFF64748B)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    openPlayStore(context)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1)
                )
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = Color(0xFF64748B))
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}
