package com.whispertype.app.ui.components

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.BuildConfig
import com.whispertype.app.speech.TranscriptionFlow

private const val TAG = "TranscriptionFlowSelector"

/**
 * Debug-only UI component for selecting the transcription flow
 * 
 * This component is only visible in debug builds.
 * It allows switching between different transcription pipelines for testing.
 */
@Composable
fun TranscriptionFlowSelector() {
    // Only show in debug builds
    if (!BuildConfig.DEBUG) {
        return
    }
    
    val context = LocalContext.current
    var selectedFlow by remember { 
        mutableStateOf(TranscriptionFlow.getSelectedFlow(context)) 
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)), // Orange tint for debug
        border = BorderStroke(1.dp, Color(0xFFFED7AA)) // Orange border
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Debug badge + title
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFEA580C) // Orange-600
                ) {
                    Text(
                        text = "DEBUG",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Transcription Flow",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Flow options
            TranscriptionFlow.entries.forEach { flow ->
                FlowOption(
                    flow = flow,
                    isSelected = selectedFlow == flow,
                    onClick = {
                        selectedFlow = flow
                        TranscriptionFlow.setSelectedFlow(context, flow)
                        
                        when (flow) {
                            TranscriptionFlow.CLOUD_API -> {
                                Log.d(TAG, "Selected CLOUD_API flow (default)")
                                Toast.makeText(context, "Using Cloud API flow", Toast.LENGTH_SHORT).show()
                            }
                            TranscriptionFlow.GROQ_WHISPER -> {
                                Log.d(TAG, "Selected GROQ_WHISPER flow")
                                Toast.makeText(context, "Using Groq Whisper (Fast)", Toast.LENGTH_SHORT).show()
                            }
                            TranscriptionFlow.FLOW_3 -> {
                                Log.d(TAG, "Selected FLOW_3 (Groq Turbo)")
                                Toast.makeText(context, "Using Groq Turbo (Fastest)", Toast.LENGTH_SHORT).show()
                            }
                            TranscriptionFlow.FLOW_4 -> {
                                Log.d(TAG, "Selected FLOW_4 (OpenAI Mini No Trim)")
                                Toast.makeText(context, "Using OpenAI Mini (No Trim)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                
                if (flow != TranscriptionFlow.entries.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun FlowOption(
    flow: TranscriptionFlow,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFFFFEDD5) else Color.White
    val borderColor = if (isSelected) Color(0xFFF97316) else Color(0xFFE2E8F0)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFFF97316), // Orange-500
                    unselectedColor = Color(0xFF94A3B8)
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = flow.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = flow.description,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
            
            // Show "Active" badge for first option
            if (flow == TranscriptionFlow.CLOUD_API) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF22C55E).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Active",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF16A34A),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
