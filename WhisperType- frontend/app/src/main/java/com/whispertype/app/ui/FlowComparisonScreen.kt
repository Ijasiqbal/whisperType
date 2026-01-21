package com.whispertype.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispertype.app.BuildConfig
import com.whispertype.app.R
import com.whispertype.app.api.WhisperApiClient
import com.whispertype.app.audio.AudioRecorder
import com.whispertype.app.auth.FirebaseAuthManager
import com.whispertype.app.speech.TranscriptionFlow
import kotlinx.coroutines.*
import java.util.Collections

private const val TAG = "FlowComparison"
private const val PREFS_NAME = "flow_comparison_prefs"
private const val KEY_FLOW_ORDER = "flow_order"

/**
 * Save the flow order to SharedPreferences
 */
private fun saveFlowOrder(context: Context, flows: List<TranscriptionFlow>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val orderString = flows.joinToString(",") { it.name }
    prefs.edit().putString(KEY_FLOW_ORDER, orderString).apply()
    Log.d(TAG, "Saved flow order: $orderString")
}

/**
 * Load the flow order from SharedPreferences
 * Returns null if no saved order exists
 */
private fun loadFlowOrder(context: Context): List<TranscriptionFlow>? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val orderString = prefs.getString(KEY_FLOW_ORDER, null) ?: return null
    
    return try {
        val savedFlows = orderString.split(",").mapNotNull { name ->
            try {
                TranscriptionFlow.valueOf(name)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Unknown flow in saved order: $name")
                null
            }
        }
        
        // Ensure all current flows are included (in case new flows were added)
        val allFlows = TranscriptionFlow.entries.toMutableList()
        val orderedFlows = savedFlows.filter { it in allFlows }.toMutableList()
        allFlows.filter { it !in orderedFlows }.forEach { orderedFlows.add(it) }
        
        Log.d(TAG, "Loaded flow order: ${orderedFlows.map { it.name }}")
        orderedFlows
    } catch (e: Exception) {
        Log.e(TAG, "Error loading flow order", e)
        null
    }
}

/**
 * Status of each transcription flow result
 */
enum class FlowStatus {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    SUCCESS,
    ERROR
}

/**
 * Data class representing a flow result
 */
data class FlowResult(
    val flow: TranscriptionFlow,
    var status: FlowStatus = FlowStatus.IDLE,
    var text: String? = null,
    var timeMs: Long? = null,
    var error: String? = null,
    var finishOrder: Int = 0  // 1 = first, 2 = second, 3 = third
)

/**
 * FlowComparisonScreen - Debug tool for comparing transcription flows
 * 
 * Records audio once and sends to all 3 flows simultaneously,
 * showing response times and transcription results for comparison.
 * 
 * DEBUG ONLY: This screen is only accessible in debug builds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowComparisonScreen(
    onBack: () -> Unit
) {
    // Guard: Only render in debug builds
    if (!BuildConfig.DEBUG) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Flow results state - load saved order from SharedPreferences
    var flowResults by remember {
        val savedOrder = loadFlowOrder(context)
        val orderedFlows = savedOrder ?: TranscriptionFlow.entries.toList()
        mutableStateOf(
            orderedFlows.map { FlowResult(it) }.toMutableList()
        )
    }
    
    // Recording state
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    
    // Audio recorder
    val audioRecorder = remember { AudioRecorder(context) }
    val apiClient = remember { WhisperApiClient() }
    val authManager = remember { FirebaseAuthManager() }
    
    // Track finish order
    var finishCounter by remember { mutableIntStateOf(0) }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (audioRecorder.isRecording()) {
                audioRecorder.cancelRecording()
            }
            audioRecorder.release()
            apiClient.cancelAll()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEA580C)
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
                        Text("Flow Comparison")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFFF7ED)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFFFF7ED))
                .padding(16.dp)
        ) {
            // Instructions
            Text(
                text = "Record audio and compare all flows simultaneously. Drag to reorder.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Record button
            RecordButton(
                isRecording = isRecording,
                isProcessing = isProcessing,
                onStartRecording = {
                    // Reset all results
                    finishCounter = 0
                    flowResults = flowResults.map { 
                        it.copy(
                            status = FlowStatus.RECORDING,
                            text = null,
                            timeMs = null,
                            error = null,
                            finishOrder = 0
                        )
                    }.toMutableList()
                    
                    val started = audioRecorder.startRecording(object : AudioRecorder.RecordingCallback {
                        override fun onRecordingStarted() {
                            Log.d(TAG, "Recording started")
                        }
                        override fun onRecordingStopped(audioBytes: ByteArray) {
                            // Handled in stop
                        }
                        override fun onRecordingError(error: String) {
                            Log.e(TAG, "Recording error: $error")
                            isRecording = false
                            // Reset flow states to IDLE on error
                            flowResults = flowResults.map {
                                it.copy(status = FlowStatus.IDLE)
                            }.toMutableList()
                            Toast.makeText(context, "Recording failed: $error", Toast.LENGTH_SHORT).show()
                        }
                    })
                    
                    // Only set recording state if actually started
                    if (started) {
                        isRecording = true
                        Log.d(TAG, "Recording started successfully")
                    } else {
                        Log.e(TAG, "Failed to start recording")
                        // Reset flow states
                        flowResults = flowResults.map {
                            it.copy(status = FlowStatus.IDLE)
                        }.toMutableList()
                        Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
                    }
                },
                onStopRecording = {
                    isRecording = false
                    isProcessing = true
                    
                    // Update all flows to transcribing state
                    flowResults = flowResults.map {
                        it.copy(status = FlowStatus.TRANSCRIBING)
                    }.toMutableList()
                    
                    audioRecorder.stopRecording(object : AudioRecorder.RecordingCallback {
                        override fun onRecordingStarted() {}
                        override fun onRecordingStopped(audioBytes: ByteArray) {
                            Log.d(TAG, "Recording stopped, ${audioBytes.size} bytes")
                            val audioFormat = audioRecorder.getAudioFormat()
                            val durationMs = (audioBytes.size.toLong() * 8 / 64).coerceAtLeast(1000)
                            
                            // Launch parallel transcriptions
                            scope.launch {
                                transcribeAllFlows(
                                    context = context,
                                    audioBytes = audioBytes,
                                    audioFormat = audioFormat,
                                    durationMs = durationMs,
                                    apiClient = apiClient,
                                    authManager = authManager,
                                    flowResults = flowResults,
                                    onFlowComplete = { flow, text, timeMs ->
                                        finishCounter++
                                        flowResults = flowResults.map {
                                            if (it.flow == flow) {
                                                it.copy(
                                                    status = FlowStatus.SUCCESS,
                                                    text = text,
                                                    timeMs = timeMs,
                                                    finishOrder = finishCounter
                                                )
                                            } else it
                                        }.toMutableList()
                                        
                                        // Check if all done
                                        if (flowResults.all { it.status == FlowStatus.SUCCESS || it.status == FlowStatus.ERROR }) {
                                            isProcessing = false
                                        }
                                    },
                                    onFlowError = { flow, error ->
                                        finishCounter++
                                        flowResults = flowResults.map {
                                            if (it.flow == flow) {
                                                it.copy(
                                                    status = FlowStatus.ERROR,
                                                    error = error,
                                                    finishOrder = finishCounter
                                                )
                                            } else it
                                        }.toMutableList()
                                        
                                        if (flowResults.all { it.status == FlowStatus.SUCCESS || it.status == FlowStatus.ERROR }) {
                                            isProcessing = false
                                        }
                                    }
                                )
                            }
                        }
                        override fun onRecordingError(error: String) {
                            Log.e(TAG, "Stop error: $error")
                            isProcessing = false
                        }
                    })
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Flow results list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(flowResults) { index, result ->
                    FlowResultCard(
                        result = result,
                        canMoveUp = index > 0,
                        canMoveDown = index < flowResults.size - 1,
                        onMoveUp = {
                            if (index > 0) {
                                val newList = flowResults.toMutableList()
                                Collections.swap(newList, index, index - 1)
                                flowResults = newList
                                // Save new order
                                saveFlowOrder(context, newList.map { it.flow })
                            }
                        },
                        onMoveDown = {
                            if (index < flowResults.size - 1) {
                                val newList = flowResults.toMutableList()
                                Collections.swap(newList, index, index + 1)
                                flowResults = newList
                                // Save new order
                                saveFlowOrder(context, newList.map { it.flow })
                            }
                        },
                        onCopy = {
                            result.text?.let { text ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", text))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Record/Stop button with animated state
 */
@Composable
private fun RecordButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = when {
            isProcessing -> Color(0xFF94A3B8)
            isRecording -> Color(0xFFDC2626)
            else -> Color(0xFF6366F1)
        },
        animationSpec = tween(300),
        label = "buttonColor"
    )
    
    Button(
        onClick = {
            if (!isProcessing) {
                if (isRecording) onStopRecording() else onStartRecording()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        enabled = !isProcessing,
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Processing...", fontSize = 16.sp)
        } else if (isRecording) {
            Icon(
                painter = painterResource(id = R.drawable.ic_stop),
                contentDescription = "Stop",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Stop Recording", fontSize = 16.sp)
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_mic),
                contentDescription = "Record",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Record & Compare All", fontSize = 16.sp)
        }
    }
}

/**
 * Card showing a single flow's result with reorder controls
 */
@Composable
private fun FlowResultCard(
    result: FlowResult,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCopy: () -> Unit
) {
    val isVisible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible.value = true }
    
    AnimatedVisibility(
        visible = isVisible.value,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (result.status) {
                    FlowStatus.SUCCESS -> Color.White
                    FlowStatus.ERROR -> Color(0xFFFEE2E2)
                    else -> Color.White
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header row: Flow name + medal + timing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Reorder buttons
                        Column {
                            IconButton(
                                onClick = onMoveUp,
                                enabled = canMoveUp,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move up",
                                    tint = if (canMoveUp) Color(0xFF64748B) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onMoveDown,
                                enabled = canMoveDown,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                    tint = if (canMoveDown) Color(0xFF64748B) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Medal for finish order
                        if (result.finishOrder in 1..3) {
                            val medal = when (result.finishOrder) {
                                1 -> "🥇"
                                2 -> "🥈"
                                3 -> "🥉"
                                else -> ""
                            }
                            Text(
                                text = medal,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        
                        // Flow name
                        Text(
                            text = result.flow.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                    
                    // Timing badge
                    if (result.timeMs != null) {
                        val timeText = if (result.timeMs!! < 1000) {
                            "${result.timeMs}ms"
                        } else {
                            String.format("%.2fs", result.timeMs!! / 1000.0)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF22C55E).copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = timeText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF16A34A),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Status / Result
                when (result.status) {
                    FlowStatus.IDLE -> {
                        Text(
                            text = "Waiting for recording...",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    FlowStatus.RECORDING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFDC2626)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recording...",
                                fontSize = 14.sp,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                    FlowStatus.TRANSCRIBING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF6366F1)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Transcribing...",
                                fontSize = 14.sp,
                                color = Color(0xFF6366F1)
                            )
                        }
                    }
                    FlowStatus.SUCCESS -> {
                        Text(
                            text = result.text ?: "",
                            fontSize = 14.sp,
                            color = Color(0xFF1E293B),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Copy button
                        OutlinedButton(
                            onClick = onCopy,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_content_copy),
                                contentDescription = "Copy",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 12.sp)
                        }
                    }
                    FlowStatus.ERROR -> {
                        Text(
                            text = "Error: ${result.error ?: "Unknown error"}",
                            fontSize = 14.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Run transcription on all flows in parallel
 */
private suspend fun transcribeAllFlows(
    context: Context,
    audioBytes: ByteArray,
    audioFormat: String,
    durationMs: Long,
    apiClient: WhisperApiClient,
    authManager: FirebaseAuthManager,
    flowResults: List<FlowResult>,
    onFlowComplete: (TranscriptionFlow, String, Long) -> Unit,
    onFlowError: (TranscriptionFlow, String) -> Unit
) {
    // Get auth token
    val user = authManager.ensureSignedIn()
    if (user == null) {
        flowResults.forEach { onFlowError(it.flow, "Authentication failed") }
        return
    }
    
    val token = authManager.getIdToken()
    if (token == null) {
        flowResults.forEach { onFlowError(it.flow, "Failed to get auth token") }
        return
    }
    
    Log.d(TAG, "Starting parallel transcription for ${flowResults.size} flows")
    
    // Launch all flows in parallel
    coroutineScope {
        flowResults.forEach { result ->
            launch(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                
                // Use suspendCancellableCoroutine to convert callback to suspend
                try {
                    val text = suspendCancellableCoroutine<String> { continuation ->
                        val callback = object : WhisperApiClient.TranscriptionCallback {
                            override fun onSuccess(text: String) {
                                if (continuation.isActive) {
                                    continuation.resume(text) {}
                                }
                            }
                            override fun onError(error: String) {
                                if (continuation.isActive) {
                                    continuation.cancel(Exception(error))
                                }
                            }
                            override fun onTrialExpired(message: String) {
                                if (continuation.isActive) {
                                    continuation.cancel(Exception(message))
                                }
                            }
                        }
                        
                        when (result.flow) {
                            TranscriptionFlow.CLOUD_API -> {
                                Log.d(TAG, "Starting CLOUD_API transcription")
                                apiClient.transcribe(
                                    audioBytes = audioBytes,
                                    authToken = token,
                                    audioFormat = audioFormat,
                                    model = null,
                                    audioDurationMs = durationMs,
                                    callback = callback
                                )
                            }
                            TranscriptionFlow.GROQ_WHISPER -> {
                                Log.d(TAG, "Starting GROQ_WHISPER transcription")
                                apiClient.transcribeWithGroq(
                                    audioBytes = audioBytes,
                                    authToken = token,
                                    audioFormat = audioFormat,
                                    audioDurationMs = durationMs,
                                    model = null,  // Default: whisper-large-v3
                                    callback = callback
                                )
                            }
                            TranscriptionFlow.FLOW_3 -> {
                                Log.d(TAG, "Starting FLOW_3 (Groq Turbo) transcription")
                                apiClient.transcribeWithGroq(
                                    audioBytes = audioBytes,
                                    authToken = token,
                                    audioFormat = audioFormat,
                                    audioDurationMs = durationMs,
                                    model = "whisper-large-v3-turbo",
                                    callback = callback
                                )
                            }
                            TranscriptionFlow.FLOW_4 -> {
                                Log.d(TAG, "Starting FLOW_4 (OpenAI Mini No Trim) transcription")
                                apiClient.transcribe(
                                    audioBytes = audioBytes,
                                    authToken = token,
                                    audioFormat = audioFormat,
                                    model = "gpt-4o-mini-transcribe",
                                    audioDurationMs = durationMs,
                                    callback = callback
                                )
                            }
                        }
                    }
                    
                    val elapsedMs = System.currentTimeMillis() - startTime
                    Log.d(TAG, "${result.flow.name} completed in ${elapsedMs}ms")
                    
                    withContext(Dispatchers.Main) {
                        onFlowComplete(result.flow, text, elapsedMs)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "${result.flow.name} failed: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onFlowError(result.flow, e.message ?: "Unknown error")
                    }
                }
            }
        }
    }
}
