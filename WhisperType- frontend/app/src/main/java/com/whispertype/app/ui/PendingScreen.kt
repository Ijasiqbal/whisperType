package com.whispertype.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.R
import com.whispertype.app.ShortcutPreferences
import com.whispertype.app.api.WhisperApiClient
import com.whispertype.app.auth.FirebaseAuthManager
import com.whispertype.app.data.PendingTranscriptionManager
import com.whispertype.app.data.PendingTranscriptionManager.PendingTranscription
import com.whispertype.app.speech.TranscriptionFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PendingScreen(
    onPendingCountChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val manager = remember { PendingTranscriptionManager.getInstance(context) }
    val authManager = remember { FirebaseAuthManager() }
    val apiClient = remember { WhisperApiClient() }
    var entries by remember { mutableStateOf(manager.getAll()) }
    var retryingIds by remember { mutableStateOf(setOf<String>()) }

    fun refreshEntries() {
        entries = manager.getAll()
        onPendingCountChanged(entries.count { it.status == PendingTranscription.Status.PENDING })
    }

    LaunchedEffect(Unit) { refreshEntries() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF8FAFC), Color(0xFFEEF2FF), Color(0xFFF8FAFC)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.MAX_VALUE, Float.MAX_VALUE)
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Text(
                text = "Pending Transcriptions",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 8.dp)
            )

            if (entries.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No pending transcriptions",
                            fontSize = 16.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Failed recordings will appear here",
                            fontSize = 13.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            } else {
                // Retry All button
                if (entries.any { it.status == PendingTranscription.Status.PENDING }) {
                    Button(
                        onClick = {
                            val pendingEntries = entries.filter { it.status == PendingTranscription.Status.PENDING }
                            pendingEntries.forEach { entry ->
                                if (entry.id !in retryingIds) {
                                    retryingIds = retryingIds + entry.id
                                    scope.launch {
                                        retryTranscription(context, manager, authManager, apiClient, entry) {
                                            retryingIds = retryingIds - entry.id
                                            refreshEntries()
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry All")
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        PendingTranscriptionCard(
                            entry = entry,
                            isRetrying = entry.id in retryingIds,
                            onRetry = { tier ->
                                retryingIds = retryingIds + entry.id
                                scope.launch {
                                    retryTranscription(context, manager, authManager, apiClient, entry, tier) {
                                        retryingIds = retryingIds - entry.id
                                        refreshEntries()
                                    }
                                }
                            },
                            onDelete = {
                                manager.delete(entry.id)
                                refreshEntries()
                            },
                            onCopy = { text ->
                                clipboardManager.setText(AnnotatedString(text))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingTranscriptionCard(
    entry: PendingTranscription,
    isRetrying: Boolean,
    onRetry: (ShortcutPreferences.ModelTier?) -> Unit,
    onDelete: () -> Unit,
    onCopy: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val isCompleted = entry.status == PendingTranscription.Status.COMPLETED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row: time, duration, status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(
                            id = if (isCompleted) R.drawable.ic_check else R.drawable.ic_mic
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isCompleted) Color(0xFF10B981) else Color(0xFF6366F1)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dateFormat.format(Date(entry.timestamp)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${entry.durationMs / 1000}s",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Model tier badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEEF2FF)
                ) {
                    Text(
                        text = entry.failedModelTier,
                        fontSize = 11.sp,
                        color = Color(0xFF6366F1),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isCompleted && entry.transcribedText != null) {
                // Show transcribed text
                Text(
                    text = entry.transcribedText!!,
                    fontSize = 14.sp,
                    color = Color(0xFF1E293B),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8FAFC))
                        .padding(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Copy and Delete buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { onCopy(entry.transcribedText!!) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_content_copy),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", fontSize = 13.sp)
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            } else {
                // Show error message
                Text(
                    text = entry.errorMessage,
                    fontSize = 12.sp,
                    color = Color(0xFFEF4444),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Retry and Delete buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF6366F1)
                        )
                    } else {
                        // Retry button
                        Button(
                            onClick = { onRetry(null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6366F1)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry", fontSize = 13.sp)
                        }
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Retry a pending transcription by loading audio from local storage and re-sending to API.
 */
private suspend fun retryTranscription(
    context: android.content.Context,
    manager: PendingTranscriptionManager,
    authManager: FirebaseAuthManager,
    apiClient: WhisperApiClient,
    entry: PendingTranscription,
    tier: ShortcutPreferences.ModelTier? = null,
    onComplete: () -> Unit
) {
    val audioBytes = manager.loadAudioBytes(entry) ?: run {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
            onComplete()
        }
        return
    }

    // Determine which tier to use
    val selectedTier = tier ?: try {
        ShortcutPreferences.ModelTier.valueOf(entry.failedModelTier)
    } catch (_: Exception) {
        ShortcutPreferences.getModelTier(context)
    }

    val flow = TranscriptionFlow.fromModelTier(selectedTier)

    withContext(Dispatchers.IO) {
        val user = authManager.ensureSignedIn()
        if (user == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                onComplete()
            }
            return@withContext
        }

        val token = authManager.getIdToken()
        if (token == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to get auth token", Toast.LENGTH_SHORT).show()
                onComplete()
            }
            return@withContext
        }

        val callback = object : WhisperApiClient.TranscriptionCallback {
            override fun onSuccess(text: String) {
                manager.markCompleted(entry.id, text)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Transcription complete", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
            }

            override fun onError(error: String) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Retry failed: $error", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
            }
        }

        // Route to appropriate API based on flow (must match SpeechRecognitionHelper.transcribeWithFlow)
        when (flow) {
            TranscriptionFlow.FLOW_3 -> {
                apiClient.transcribeWithGroq(audioBytes, token, entry.audioFormat, entry.durationMs, "whisper-large-v3-turbo", callback)
            }
            TranscriptionFlow.GROQ_WHISPER -> {
                apiClient.transcribeWithGroq(audioBytes, token, entry.audioFormat, entry.durationMs, null, callback)
            }
            TranscriptionFlow.PARALLEL_OPUS -> {
                apiClient.transcribe(audioBytes, token, entry.audioFormat, "gpt-4o-mini-transcribe", entry.durationMs, callback)
            }
            TranscriptionFlow.TWO_STAGE_AUTO -> {
                apiClient.transcribeWithTwoStage(audioBytes, token, entry.audioFormat, entry.durationMs, null, "AUTO", callback)
            }
            TranscriptionFlow.TWO_STAGE_NEWER_AUTO -> {
                apiClient.transcribeWithTwoStage(audioBytes, token, entry.audioFormat, entry.durationMs, "openai/gpt-oss-20b", "STANDARD", callback)
            }
            else -> {
                apiClient.transcribe(audioBytes, token, entry.audioFormat, "gpt-4o-mini-transcribe", entry.durationMs, callback)
            }
        }
    }
}
