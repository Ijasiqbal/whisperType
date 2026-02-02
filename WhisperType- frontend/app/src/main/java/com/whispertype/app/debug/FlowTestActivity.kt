package com.whispertype.app.debug

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.whispertype.app.audio.AudioRecorder
import com.whispertype.app.audio.DualOggRecorder
import com.whispertype.app.audio.SimpleOggRecorder
import com.whispertype.app.api.WhisperApiClient
import com.whispertype.app.auth.FirebaseAuthManager
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File

private const val TAG = "FlowTestActivity"

/**
 * FlowTestActivity - Debug activity for testing and comparing transcription flows
 *
 * Features:
 * - Dual Recording Test: Records once, compares Sequential vs Parallel OGG encoding (same audio input)
 * - Individual Flow Tests: Test each flow separately
 *
 * Compares:
 * - Sequential OGG: Encodes after recording stops (like MediaRecorder)
 * - Parallel OGG: Encodes during recording (new approach)
 *
 * Both flows use Groq whisper-large-v3 API for transcription.
 */
class FlowTestActivity : ComponentActivity() {

    companion object {
        private const val TAG = "FlowTestActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF6366F1),
                    secondary = Color(0xFF10B981)
                )
            ) {
                FlowTestScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

/**
 * Test state for a single flow
 */
sealed class FlowTestState {
    object Idle : FlowTestState()
    object Recording : FlowTestState()
    object Processing : FlowTestState()
    object Transcribing : FlowTestState()
    data class Success(val result: FlowTestResult) : FlowTestState()
    data class Error(val message: String) : FlowTestState()
}

/**
 * Result of a flow test
 */
data class FlowTestResult(
    val flowName: String,
    val audioSizeBytes: Int,
    val audioFormat: String,
    val recordingDurationMs: Long,
    val processingTimeMs: Long,      // Time from stop to API call
    val transcriptionTimeMs: Long,   // Time for API response
    val totalTimeMs: Long,           // Total time from stop to result
    val transcribedText: String,
    val modelUsed: String = "",      // Model used for transcription
    val silenceTrimmingApplied: Boolean = false  // True if silence was trimmed
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Test state for each flow
    var groqState by remember { mutableStateOf<FlowTestState>(FlowTestState.Idle) }
    var parallelOggState by remember { mutableStateOf<FlowTestState>(FlowTestState.Idle) }

    // Recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartTime by remember { mutableStateOf(0L) }
    var currentAmplitude by remember { mutableStateOf(0) }
    var activeFlow by remember { mutableStateOf<String?>(null) }
    
    // Recording callbacks (need to be stored to pass to stopRecording)
    var groqRecordingCallback by remember { mutableStateOf<AudioRecorder.RecordingCallback?>(null) }

    // Recorders
    val audioRecorder = remember { AudioRecorder(context) }
    val simpleOggRecorder = remember { SimpleOggRecorder(context) }
    val dualOggRecorder = remember { DualOggRecorder(context) }

    // Dual recording state
    var dualRecordingActive by remember { mutableStateOf(false) }
    var dualSequentialState by remember { mutableStateOf<FlowTestState>(FlowTestState.Idle) }
    var dualParallelState by remember { mutableStateOf<FlowTestState>(FlowTestState.Idle) }
    var dualRecordingStartTime by remember { mutableStateOf(0L) }

    // API client and auth
    val whisperApiClient = remember { WhisperApiClient() }
    val authManager = remember { FirebaseAuthManager() }

    // Check microphone permission
    val hasMicPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flow Comparison Test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFEF3C7)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Flow Comparison",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Compare two transcription flows:\n\n" +
                        "1. GROQ WHISPER (Standard): MediaRecorder → Groq whisper-large-v3\n" +
                        "2. PARALLEL OGG (New): AudioRecord + parallel OGG encoding → Groq whisper-large-v3",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DUAL RECORDING COMPARISON (Same audio input, fair comparison)
            DualRecordingCard(
                isRecording = dualRecordingActive,
                amplitude = if (dualRecordingActive) currentAmplitude else 0,
                sequentialState = dualSequentialState,
                parallelState = dualParallelState,
                onStartRecording = {
                    if (!hasMicPermission) {
                        Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
                        return@DualRecordingCard
                    }

                    if (!DualOggRecorder.isSupported()) {
                        Toast.makeText(context, "Requires Android 10+ for dual flow test", Toast.LENGTH_SHORT).show()
                        return@DualRecordingCard
                    }

                    // Reset states
                    dualSequentialState = FlowTestState.Recording
                    dualParallelState = FlowTestState.Recording
                    dualRecordingActive = true
                    dualRecordingStartTime = System.currentTimeMillis()

                    // Warm up Groq API
                    whisperApiClient.warmGroqFunction()

                    dualOggRecorder.setAmplitudeCallback(object : AudioRecorder.AmplitudeCallback {
                        override fun onAmplitude(amplitude: Int) {
                            currentAmplitude = amplitude
                        }
                    })

                    dualOggRecorder.startRecording(object : DualOggRecorder.DualRecordingCallback {
                        override fun onRecordingStarted() {
                            Log.d(TAG, "Dual OGG recording started")
                        }

                        override fun onRecordingStopped(result: DualOggRecorder.DualResult) {
                            val stopTime = System.currentTimeMillis()
                            val recordingDuration = stopTime - dualRecordingStartTime

                            Log.d(TAG, "Dual OGG recording stopped:")
                            Log.d(TAG, "  Sequential: ${result.sequentialOggBytes.size} bytes, encoding took ${result.sequentialMetadata.encodingTimeMs}ms")
                            Log.d(TAG, "  Parallel: ${result.parallelOggBytes.size} bytes, muxing took ${result.parallelMetadata.encodingTimeMs}ms")

                            // Both OGG outputs are ready, transcribe in parallel
                            dualSequentialState = FlowTestState.Transcribing
                            dualParallelState = FlowTestState.Transcribing

                            scope.launch {
                                try {
                                    val user = authManager.ensureSignedIn()
                                    val token = authManager.getIdToken()

                                    if (user == null || token == null) {
                                        dualSequentialState = FlowTestState.Error("Auth failed")
                                        dualParallelState = FlowTestState.Error("Auth failed")
                                        return@launch
                                    }

                                    // Transcribe both in parallel
                                    val sequentialJob = async {
                                        transcribeAudioWithGroq(
                                            audioBytes = result.sequentialOggBytes,
                                            flowName = "Sequential OGG",
                                            recordingDuration = recordingDuration,
                                            stopTime = stopTime,
                                            encodingTimeMs = result.sequentialMetadata.encodingTimeMs,
                                            token = token,
                                            whisperApiClient = whisperApiClient
                                        )
                                    }

                                    val parallelJob = async {
                                        if (result.parallelOggBytes.isEmpty()) {
                                            FlowTestState.Error("Parallel OGG encoding failed")
                                        } else {
                                            transcribeAudioWithGroq(
                                                audioBytes = result.parallelOggBytes,
                                                flowName = "Parallel OGG",
                                                recordingDuration = recordingDuration,
                                                stopTime = stopTime,
                                                encodingTimeMs = result.parallelMetadata.encodingTimeMs,
                                                token = token,
                                                whisperApiClient = whisperApiClient
                                            )
                                        }
                                    }

                                    dualSequentialState = sequentialJob.await()
                                    dualParallelState = parallelJob.await()

                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in dual flow transcription", e)
                                    dualSequentialState = FlowTestState.Error(e.message ?: "Unknown error")
                                    dualParallelState = FlowTestState.Error(e.message ?: "Unknown error")
                                }
                            }
                        }

                        override fun onRecordingError(error: String) {
                            dualSequentialState = FlowTestState.Error(error)
                            dualParallelState = FlowTestState.Error(error)
                            dualRecordingActive = false
                        }
                    })
                },
                onStopRecording = {
                    dualRecordingActive = false
                    dualOggRecorder.stopRecording()
                }
            )

            // Dual Comparison Summary
            val dualSequentialResult = (dualSequentialState as? FlowTestState.Success)?.result
            val dualParallelResult = (dualParallelState as? FlowTestState.Success)?.result

            if (dualSequentialResult != null && dualParallelResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                DualComparisonSummaryCard(dualSequentialResult, dualParallelResult)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Separator for individual tests
            Divider(thickness = 1.dp, color = Color(0xFFE2E8F0))
            Text(
                "Individual Flow Tests",
                modifier = Modifier.padding(vertical = 12.dp),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // FLOW 1: GROQ_WHISPER (Standard)
            FlowTestCard(
                title = "GROQ WHISPER (Standard)",
                subtitle = "MediaRecorder → Groq whisper-large-v3",
                state = groqState,
                isRecording = isRecording && activeFlow == "groq",
                amplitude = if (activeFlow == "groq") currentAmplitude else 0,
                accentColor = Color(0xFF10B981), // Green
                onStartTest = {
                    if (!hasMicPermission) {
                        Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
                        return@FlowTestCard
                    }

                    groqState = FlowTestState.Recording
                    isRecording = true
                    activeFlow = "groq"
                    recordingStartTime = System.currentTimeMillis()

                    // Warm up Groq API
                    whisperApiClient.warmGroqFunction()

                    audioRecorder.setAmplitudeCallback(object : AudioRecorder.AmplitudeCallback {
                        override fun onAmplitude(amplitude: Int) {
                            currentAmplitude = amplitude
                        }
                    })

                    groqRecordingCallback = object : AudioRecorder.RecordingCallback {
                        override fun onRecordingStarted() {
                            Log.d(TAG, "GROQ recording started")
                        }

                        override fun onRecordingStopped(audioBytes: ByteArray) {
                            val stopTime = System.currentTimeMillis()
                            val recordingDuration = stopTime - recordingStartTime

                            Log.d(TAG, "GROQ recording stopped: ${audioBytes.size} bytes")

                            groqState = FlowTestState.Processing
                            val processingStartTime = System.currentTimeMillis()

                            scope.launch {
                                try {
                                    val user = authManager.ensureSignedIn()
                                    val token = authManager.getIdToken()

                                    if (user == null || token == null) {
                                        groqState = FlowTestState.Error("Authentication failed")
                                        return@launch
                                    }

                                    val processingTime = System.currentTimeMillis() - processingStartTime
                                    groqState = FlowTestState.Transcribing
                                    val transcriptionStartTime = System.currentTimeMillis()

                                    // Get format from audioRecorder
                                    val format = audioRecorder.getAudioFormat()

                                    whisperApiClient.transcribeWithGroq(
                                        audioBytes,
                                        token,
                                        format,
                                        recordingDuration,
                                        "whisper-large-v3", // Use prompted L3 V3 model
                                        object : WhisperApiClient.TranscriptionCallback {
                                            override fun onSuccess(text: String) {
                                                val transcriptionTime = System.currentTimeMillis() - transcriptionStartTime
                                                val totalTime = System.currentTimeMillis() - stopTime

                                                groqState = FlowTestState.Success(
                                                    FlowTestResult(
                                                        flowName = "GROQ_WHISPER",
                                                        audioSizeBytes = audioBytes.size,
                                                        audioFormat = format.uppercase(),
                                                        recordingDurationMs = recordingDuration,
                                                        processingTimeMs = processingTime,
                                                        transcriptionTimeMs = transcriptionTime,
                                                        totalTimeMs = totalTime,
                                                        transcribedText = text,
                                                        modelUsed = "whisper-large-v3"
                                                    )
                                                )
                                            }

                                            override fun onError(error: String) {
                                                groqState = FlowTestState.Error(error)
                                            }
                                        }
                                    )
                                } catch (e: Exception) {
                                    groqState = FlowTestState.Error(e.message ?: "Unknown error")
                                }
                            }
                        }

                        override fun onRecordingError(error: String) {
                            groqState = FlowTestState.Error(error)
                            isRecording = false
                            activeFlow = null
                        }
                    }
                    audioRecorder.startRecording(groqRecordingCallback!!)
                },
                onStopTest = {
                    isRecording = false
                    activeFlow = null
                    groqRecordingCallback?.let { audioRecorder.stopRecording(it) }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // FLOW 2: PARALLEL OGG (New - AudioRecord + parallel OGG)
            FlowTestCard(
                title = "PARALLEL OGG (New)",
                subtitle = "AudioRecord + parallel OGG → Groq whisper-large-v3",
                state = parallelOggState,
                isRecording = isRecording && activeFlow == "parallel_ogg",
                amplitude = if (activeFlow == "parallel_ogg") currentAmplitude else 0,
                accentColor = Color(0xFF6366F1), // Purple
                onStartTest = {
                    if (!hasMicPermission) {
                        Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
                        return@FlowTestCard
                    }

                    if (!SimpleOggRecorder.isSupported()) {
                        Toast.makeText(context, "Requires Android 10+", Toast.LENGTH_SHORT).show()
                        return@FlowTestCard
                    }

                    parallelOggState = FlowTestState.Recording
                    isRecording = true
                    activeFlow = "parallel_ogg"
                    recordingStartTime = System.currentTimeMillis()

                    // Warm up Groq API
                    whisperApiClient.warmGroqFunction()

                    simpleOggRecorder.setAmplitudeCallback(object : AudioRecorder.AmplitudeCallback {
                        override fun onAmplitude(amplitude: Int) {
                            currentAmplitude = amplitude
                        }
                    })

                    simpleOggRecorder.startRecording(object : SimpleOggRecorder.RecordingCallback {
                        override fun onRecordingStarted() {
                            Log.d(TAG, "PARALLEL_OGG recording started")
                        }

                        override fun onRecordingStopped(trimmedOggBytes: ByteArray, rawOggBytes: ByteArray, metadata: SimpleOggRecorder.Metadata) {
                            val stopTime = System.currentTimeMillis()
                            val recordingDuration = stopTime - recordingStartTime

                            Log.d(TAG, "PARALLEL_OGG recording stopped: trimmed=${trimmedOggBytes.size} bytes, raw=${rawOggBytes.size} bytes, " +
                                    "segments=${metadata.speechSegmentCount}, trimming=${metadata.silenceTrimmingApplied}")

                            parallelOggState = FlowTestState.Processing
                            val processingStartTime = System.currentTimeMillis()

                            scope.launch {
                                try {
                                    val user = authManager.ensureSignedIn()
                                    val token = authManager.getIdToken()

                                    if (user == null || token == null) {
                                        parallelOggState = FlowTestState.Error("Authentication failed")
                                        return@launch
                                    }

                                    val processingTime = System.currentTimeMillis() - processingStartTime
                                    parallelOggState = FlowTestState.Transcribing
                                    val transcriptionStartTime = System.currentTimeMillis()

                                    // Use trimmed audio (with silence removed)
                                    whisperApiClient.transcribeWithGroq(
                                        trimmedOggBytes,
                                        token,
                                        "ogg",
                                        metadata.speechDurationMs, // Use speech duration, not total duration
                                        "whisper-large-v3", // Same model as standard flow
                                        object : WhisperApiClient.TranscriptionCallback {
                                            override fun onSuccess(text: String) {
                                                val transcriptionTime = System.currentTimeMillis() - transcriptionStartTime
                                                val totalTime = System.currentTimeMillis() - stopTime

                                                parallelOggState = FlowTestState.Success(
                                                    FlowTestResult(
                                                        flowName = "PARALLEL_OGG",
                                                        audioSizeBytes = trimmedOggBytes.size,
                                                        audioFormat = "OGG",
                                                        recordingDurationMs = recordingDuration,
                                                        processingTimeMs = processingTime,
                                                        transcriptionTimeMs = transcriptionTime,
                                                        totalTimeMs = totalTime,
                                                        transcribedText = text,
                                                        modelUsed = "whisper-large-v3",
                                                        silenceTrimmingApplied = metadata.silenceTrimmingApplied
                                                    )
                                                )
                                            }

                                            override fun onError(error: String) {
                                                parallelOggState = FlowTestState.Error(error)
                                            }
                                        }
                                    )
                                } catch (e: Exception) {
                                    parallelOggState = FlowTestState.Error(e.message ?: "Unknown error")
                                }
                            }
                        }

                        override fun onRecordingError(error: String) {
                            parallelOggState = FlowTestState.Error(error)
                            isRecording = false
                            activeFlow = null
                        }
                    })
                },
                onStopTest = {
                    isRecording = false
                    activeFlow = null
                    simpleOggRecorder.stopRecording()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Comparison Summary (if both have results)
            val groqResult = (groqState as? FlowTestState.Success)?.result
            val parallelOggResult = (parallelOggState as? FlowTestState.Success)?.result

            if (groqResult != null && parallelOggResult != null) {
                ComparisonSummaryCard(groqResult, parallelOggResult)
            }
        }
    }
}

@Composable
fun FlowTestCard(
    title: String,
    subtitle: String,
    state: FlowTestState,
    isRecording: Boolean,
    amplitude: Int,
    accentColor: Color,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            2.dp,
            when (state) {
                is FlowTestState.Success -> Color(0xFF10B981)
                is FlowTestState.Error -> Color(0xFFEF4444)
                is FlowTestState.Recording -> accentColor
                else -> Color(0xFFE2E8F0)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // Record/Stop button
                Button(
                    onClick = if (isRecording) onStopTest else onStartTest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFFEF4444) else accentColor
                    ),
                    enabled = state !is FlowTestState.Processing && state !is FlowTestState.Transcribing
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isRecording) "Stop" else "Start"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isRecording) "Stop" else "Test")
                }
            }

            // Amplitude indicator when recording
            if (isRecording) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = (amplitude / 32767f).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = accentColor,
                    trackColor = Color(0xFFE2E8F0)
                )
            }

            // Status indicator
            Spacer(modifier = Modifier.height(8.dp))
            when (state) {
                FlowTestState.Idle -> {
                    Text("Ready to test", color = Color.Gray, fontSize = 12.sp)
                }
                FlowTestState.Recording -> {
                    Text("Recording...", color = accentColor, fontSize = 12.sp)
                }
                FlowTestState.Processing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing...", color = Color(0xFFD97706), fontSize = 12.sp)
                    }
                }
                FlowTestState.Transcribing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Transcribing...", color = accentColor, fontSize = 12.sp)
                    }
                }
                is FlowTestState.Success -> {
                    val result = state.result
                    Column {
                        // Model used
                        Text(
                            "Model: ${result.modelUsed}",
                            fontSize = 11.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricItem("File Size", "${result.audioSizeBytes / 1024} KB")
                            MetricItem("Total Time", "${result.totalTimeMs} ms")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricItem("Processing", "${result.processingTimeMs} ms")
                            MetricItem("API Time", "${result.transcriptionTimeMs} ms")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Silence trimming status
                        Text(
                            if (result.silenceTrimmingApplied) "Silence Trimmed" else "No Trimming",
                            fontSize = 11.sp,
                            color = if (result.silenceTrimmingApplied) Color(0xFF10B981) else Color(0xFFF59E0B),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Transcription result
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Result:",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                            Text(
                                "${result.transcribedText.length} chars",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Text(
                            result.transcribedText,
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                is FlowTestState.Error -> {
                    Text(
                        "Error: ${state.message}",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(
            label,
            fontSize = 10.sp,
            color = Color.Gray
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ComparisonSummaryCard(groqResult: FlowTestResult, parallelOggResult: FlowTestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Comparison Summary",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF166534)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Metric", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Groq", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Parallel OGG", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Diff", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // File size comparison
            val sizeReduction = ((groqResult.audioSizeBytes - parallelOggResult.audioSizeBytes) * 100.0 / groqResult.audioSizeBytes).toInt()
            ComparisonRow(
                label = "File Size",
                groqValue = "${groqResult.audioSizeBytes / 1024} KB",
                parallelOggValue = "${parallelOggResult.audioSizeBytes / 1024} KB",
                improvement = if (sizeReduction > 0) "-$sizeReduction%" else "+${-sizeReduction}%",
                isPositive = sizeReduction > 0
            )

            // Total time comparison
            val timeDiff = groqResult.totalTimeMs - parallelOggResult.totalTimeMs
            ComparisonRow(
                label = "Total Time",
                groqValue = "${groqResult.totalTimeMs} ms",
                parallelOggValue = "${parallelOggResult.totalTimeMs} ms",
                improvement = if (timeDiff > 0) "-${timeDiff}ms" else "+${-timeDiff}ms",
                isPositive = timeDiff > 0
            )

            // API time comparison
            val apiTimeDiff = groqResult.transcriptionTimeMs - parallelOggResult.transcriptionTimeMs
            ComparisonRow(
                label = "API Time",
                groqValue = "${groqResult.transcriptionTimeMs} ms",
                parallelOggValue = "${parallelOggResult.transcriptionTimeMs} ms",
                improvement = if (apiTimeDiff > 0) "-${apiTimeDiff}ms" else "+${-apiTimeDiff}ms",
                isPositive = apiTimeDiff > 0
            )

            // Transcription comparison
            Spacer(modifier = Modifier.height(8.dp))
            val textsMatch = groqResult.transcribedText.trim().equals(parallelOggResult.transcribedText.trim(), ignoreCase = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Transcription Match", fontSize = 12.sp)
                Text(
                    if (textsMatch) "Identical" else "Different",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (textsMatch) Color(0xFF10B981) else Color(0xFFD97706)
                )
            }

            // Show character count difference
            if (!textsMatch) {
                val charDiff = parallelOggResult.transcribedText.length - groqResult.transcribedText.length
                Text(
                    "Character difference: ${if (charDiff > 0) "+$charDiff" else charDiff}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun ComparisonRow(
    label: String,
    groqValue: String,
    parallelOggValue: String,
    improvement: String,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(groqValue, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(parallelOggValue, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            improvement,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444),
            modifier = Modifier.weight(0.8f)
        )
    }
}

/**
 * Dual Recording Card - Records once and processes through both Sequential and Parallel OGG flows
 */
@Composable
fun DualRecordingCard(
    isRecording: Boolean,
    amplitude: Int,
    sequentialState: FlowTestState,
    parallelState: FlowTestState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
        border = BorderStroke(
            2.dp,
            when {
                isRecording -> Color(0xFF6366F1)
                sequentialState is FlowTestState.Success && parallelState is FlowTestState.Success -> Color(0xFF10B981)
                sequentialState is FlowTestState.Error || parallelState is FlowTestState.Error -> Color(0xFFEF4444)
                else -> Color(0xFFD97706)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Dual OGG Comparison",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF92400E)
                    )
                    Text(
                        "Same audio → Sequential vs Parallel encoding",
                        fontSize = 12.sp,
                        color = Color(0xFFB45309)
                    )
                }

                Button(
                    onClick = if (isRecording) onStopRecording else onStartRecording,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFFEF4444) else Color(0xFFD97706)
                    ),
                    enabled = sequentialState !is FlowTestState.Processing &&
                              sequentialState !is FlowTestState.Transcribing &&
                              parallelState !is FlowTestState.Processing &&
                              parallelState !is FlowTestState.Transcribing
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isRecording) "Stop" else "Start"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isRecording) "Stop" else "Record")
                }
            }

            // Amplitude indicator when recording
            if (isRecording) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = (amplitude / 32767f).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color(0xFFD97706),
                    trackColor = Color(0xFFFDE68A)
                )
                Text(
                    "Recording... Parallel encoding in progress",
                    fontSize = 12.sp,
                    color = Color(0xFFB45309),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Show flow states side by side
            if (sequentialState !is FlowTestState.Idle || parallelState !is FlowTestState.Idle) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color(0xFFFDE68A))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Sequential Flow Status
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sequential (Like MediaRecorder)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF10B981))
                        DualFlowStateIndicator(sequentialState, Color(0xFF10B981))
                    }

                    // Parallel Flow Status
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Parallel (New Approach)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF6366F1))
                        DualFlowStateIndicator(parallelState, Color(0xFF6366F1))
                    }
                }
            }
        }
    }
}

@Composable
fun DualFlowStateIndicator(state: FlowTestState, accentColor: Color) {
    when (state) {
        is FlowTestState.Idle -> {
            Text("Waiting...", color = Color.Gray, fontSize = 11.sp)
        }
        is FlowTestState.Recording -> {
            Text("Recording...", color = accentColor, fontSize = 11.sp)
        }
        is FlowTestState.Processing -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = Color(0xFFD97706)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Encoding...", color = Color(0xFFD97706), fontSize = 11.sp)
            }
        }
        is FlowTestState.Transcribing -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Transcribing...", color = accentColor, fontSize = 11.sp)
            }
        }
        is FlowTestState.Success -> {
            val result = state.result
            Column {
                Text("${result.totalTimeMs}ms total", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${result.audioSizeBytes / 1024}KB • ${result.processingTimeMs}ms encode", color = Color.Gray, fontSize = 10.sp)
                Text(
                    result.transcribedText,
                    fontSize = 11.sp,
                    color = Color(0xFF475569),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        is FlowTestState.Error -> {
            Text("Error: ${state.message}", color = Color(0xFFEF4444), fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
fun DualComparisonSummaryCard(sequentialResult: FlowTestResult, parallelResult: FlowTestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Dual Comparison (Same Audio)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF065F46)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Key insight: encoding time saved
            val encodingTimeSaved = sequentialResult.processingTimeMs - parallelResult.processingTimeMs
            if (encodingTimeSaved > 0) {
                Text(
                    "Parallel encoding saved ${encodingTimeSaved}ms post-recording!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF059669)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Metrics comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Sequential", fontSize = 10.sp, color = Color.Gray)
                    Text("${sequentialResult.audioSizeBytes / 1024} KB", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("${sequentialResult.processingTimeMs}ms encode", fontSize = 10.sp, color = Color.Gray)
                    Text("${sequentialResult.totalTimeMs}ms total", fontSize = 10.sp, color = Color.Gray)
                }
                Column {
                    Text("Parallel", fontSize = 10.sp, color = Color.Gray)
                    Text("${parallelResult.audioSizeBytes / 1024} KB", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("${parallelResult.processingTimeMs}ms encode", fontSize = 10.sp, color = Color.Gray)
                    Text("${parallelResult.totalTimeMs}ms total", fontSize = 10.sp, color = Color.Gray)
                }
                Column {
                    Text("Saved", fontSize = 10.sp, color = Color.Gray)
                    val sizeDiff = sequentialResult.audioSizeBytes - parallelResult.audioSizeBytes
                    Text(
                        "${if (sizeDiff >= 0) "-" else "+"}${kotlin.math.abs(sizeDiff) / 1024} KB",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (sizeDiff >= 0) Color(0xFF059669) else Color(0xFFDC2626)
                    )
                    Text(
                        "${if (encodingTimeSaved >= 0) "-" else "+"}${kotlin.math.abs(encodingTimeSaved)}ms",
                        fontSize = 10.sp,
                        color = if (encodingTimeSaved >= 0) Color(0xFF059669) else Color(0xFFDC2626)
                    )
                    val totalTimeSaved = sequentialResult.totalTimeMs - parallelResult.totalTimeMs
                    Text(
                        "${if (totalTimeSaved >= 0) "-" else "+"}${kotlin.math.abs(totalTimeSaved)}ms",
                        fontSize = 10.sp,
                        color = if (totalTimeSaved >= 0) Color(0xFF059669) else Color(0xFFDC2626)
                    )
                }
            }

            // Transcription match
            Spacer(modifier = Modifier.height(8.dp))
            val textsMatch = sequentialResult.transcribedText.trim().equals(parallelResult.transcribedText.trim(), ignoreCase = true)
            Text(
                "Transcription: ${if (textsMatch) "Identical" else "Different (${kotlin.math.abs(sequentialResult.transcribedText.length - parallelResult.transcribedText.length)} char diff)"}",
                fontSize = 11.sp,
                color = if (textsMatch) Color(0xFF059669) else Color(0xFFD97706)
            )
        }
    }
}

/**
 * Helper function to transcribe audio using Groq API and return the result as FlowTestState
 */
suspend fun transcribeAudioWithGroq(
    audioBytes: ByteArray,
    flowName: String,
    recordingDuration: Long,
    stopTime: Long,
    encodingTimeMs: Long,
    token: String,
    whisperApiClient: WhisperApiClient
): FlowTestState = suspendCancellableCoroutine { continuation ->
    val transcriptionStartTime = System.currentTimeMillis()

    whisperApiClient.transcribeWithGroq(
        audioBytes,
        token,
        "ogg",
        recordingDuration,
        "whisper-large-v3",
        object : WhisperApiClient.TranscriptionCallback {
            override fun onSuccess(text: String) {
                val transcriptionTime = System.currentTimeMillis() - transcriptionStartTime
                val totalTime = System.currentTimeMillis() - stopTime

                Log.d(TAG, "[$flowName] Transcription SUCCESS: ${text.length} chars, API time: ${transcriptionTime}ms")

                continuation.resume(
                    FlowTestState.Success(
                        FlowTestResult(
                            flowName = flowName,
                            audioSizeBytes = audioBytes.size,
                            audioFormat = "OGG",
                            recordingDurationMs = recordingDuration,
                            processingTimeMs = encodingTimeMs,  // Use encoding time as processing time
                            transcriptionTimeMs = transcriptionTime,
                            totalTimeMs = totalTime,
                            transcribedText = text,
                            modelUsed = "whisper-large-v3"
                        )
                    )
                ) {}
            }

            override fun onError(error: String) {
                Log.e(TAG, "[$flowName] Transcription ERROR: $error")
                continuation.resume(FlowTestState.Error(error)) {}
            }
        }
    )
}
