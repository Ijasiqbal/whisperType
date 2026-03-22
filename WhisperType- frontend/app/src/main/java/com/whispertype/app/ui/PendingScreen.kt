package com.whispertype.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
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
import com.whispertype.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private val PillShape = RoundedCornerShape(50)

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
            .background(ScreenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Header
            Text(
                text = "Pending Transcriptions",
                style = MaterialTheme.typography.headlineLarge,
                color = Slate800,
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
                            tint = Slate300
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No pending transcriptions",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Slate500
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Failed recordings will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate400
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
                        colors = ButtonDefaults.buttonColors(containerColor = Rust),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Retry All",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WarmWhite),
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
                        tint = if (isCompleted) Emerald else Rust
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dateFormat.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.titleSmall,
                        color = Slate600
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${entry.durationMs / 1000}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }

                // Model tier badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = IndigoTint
                ) {
                    Text(
                        text = entry.failedModelTier,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = Rust,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isCompleted && entry.transcribedText != null) {
                // Transcribed text
                Text(
                    text = entry.transcribedText!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate800,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate100)
                        .padding(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Copy and Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { onCopy(entry.transcribedText!!) },
                        colors = ButtonDefaults.buttonColors(containerColor = Rust),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_content_copy),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", style = MaterialTheme.typography.labelMedium)
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Slate400
                        )
                    }
                }
            } else {
                // Error message
                Text(
                    text = entry.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Three pill buttons
                if (isRetrying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Rust
                    )
                } else {
                    var showModelMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Retry pill
                        Button(
                            onClick = { onRetry(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = Rust),
                            shape = PillShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }

                        // Model picker pill
                        Box {
                            OutlinedButton(
                                onClick = { showModelMenu = true },
                                shape = PillShape,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Rust
                                ),
                                border = BorderStroke(1.dp, Rust),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = try {
                                        ShortcutPreferences.ModelTier.valueOf(entry.failedModelTier).displayName
                                    } catch (_: Exception) { entry.failedModelTier },
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false }
                            ) {
                                ShortcutPreferences.ModelTier.entries.forEach { tier ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "${tier.displayName} (${tier.creditCost})" +
                                                    if (tier.name == entry.failedModelTier) " - failed" else "",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        onClick = {
                                            showModelMenu = false
                                            onRetry(tier)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Delete pill
                        TextButton(
                            onClick = onDelete,
                            shape = PillShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(14.dp),
                                tint = Slate400
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Delete",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate400
                            )
                        }
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
