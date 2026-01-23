package com.whispertype.app.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import com.whispertype.app.api.WhisperApiClient
import com.whispertype.app.audio.AudioFileBenchmark
import com.whispertype.app.audio.AudioProcessor
import com.whispertype.app.auth.FirebaseAuthManager
import com.whispertype.app.speech.TranscriptionFlow
import kotlinx.coroutines.*
import java.io.File

private const val TAG = "BenchmarkTesting"

/**
 * Status of each benchmark flow
 */
private enum class BenchmarkStatus {
    IDLE,
    SIMULATING,      // Simulating recording at 1x speed
    PROCESSING,      // Post-recording processing (trimming for some flows)
    TRANSCRIBING,    // Sending to API
    SUCCESS,
    ERROR
}

/**
 * Result for each flow
 */
private data class FlowBenchmarkResult(
    val flow: TranscriptionFlow,
    var status: BenchmarkStatus = BenchmarkStatus.IDLE,
    var timeMs: Long? = null,           // Time from "stop" to result
    var transcription: String? = null,
    var error: String? = null
)

/**
 * BenchmarkTestingScreen - Accurate flow timing comparison using file simulation
 *
 * This screen:
 * 1. Loads a test audio file from assets
 * 2. Runs each flow SEQUENTIALLY with accurate timing
 * 3. Simulates recording at 1x speed with parallel processing where applicable
 * 4. Measures time from "stop" to "result received"
 *
 * DEBUG ONLY: This screen is only accessible in debug builds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkTestingScreen(
    onBack: () -> Unit
) {
    // Guard: Only render in debug builds
    if (!BuildConfig.DEBUG) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var isRunning by remember { mutableStateOf(false) }
    var currentFlowIndex by remember { mutableIntStateOf(-1) }
    var selectedAudioFile by remember { mutableStateOf("test_audio.mp3") }
    var results by remember { 
        mutableStateOf(TranscriptionFlow.entries.map { FlowBenchmarkResult(it) })
    }

    // Services
    val benchmark = remember { AudioFileBenchmark(context) }
    val apiClient = remember { WhisperApiClient() }
    val authManager = remember { FirebaseAuthManager() }
    val audioProcessor = remember { AudioProcessor(context) }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            apiClient.cancelAll()
        }
    }

    // Check if test audio exists in assets
    val availableAudioFiles = remember {
        try {
            context.assets.list("")?.filter { 
                it.endsWith(".mp3") || it.endsWith(".wav") || 
                it.endsWith(".m4a") || it.endsWith(".ogg") 
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error listing assets", e)
            emptyList()
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
                        Text("Benchmark Testing")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isRunning) {
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
                text = "Simulates recording from a test file and measures each flow's timing from 'stop' to 'result'.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Audio file selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Test Audio File",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (availableAudioFiles.isEmpty()) {
                        Text(
                            text = "⚠️ No audio files found in assets. Add a test_audio.mp3 file.",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626)
                        )
                    } else {
                        // Show available files
                        availableAudioFiles.forEach { fileName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedAudioFile == fileName,
                                    onClick = { selectedAudioFile = fileName },
                                    enabled = !isRunning,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF6366F1)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = fileName,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Run Benchmark button
            val buttonColor by animateColorAsState(
                targetValue = when {
                    isRunning -> Color(0xFF94A3B8)
                    availableAudioFiles.isEmpty() -> Color(0xFFCBD5E1)
                    else -> Color(0xFF6366F1)
                },
                animationSpec = tween(300),
                label = "buttonColor"
            )

            Button(
                onClick = {
                    if (!isRunning && availableAudioFiles.isNotEmpty()) {
                        isRunning = true
                        currentFlowIndex = 0

                        // Reset results
                        results = TranscriptionFlow.entries.map { FlowBenchmarkResult(it) }

                        // Run benchmark
                        scope.launch {
                            runBenchmark(
                                context = context,
                                audioFileName = selectedAudioFile,
                                benchmark = benchmark,
                                audioProcessor = audioProcessor,
                                apiClient = apiClient,
                                authManager = authManager,
                                onFlowStart = { index ->
                                    currentFlowIndex = index
                                    results = results.mapIndexed { i, result ->
                                        if (i == index) result.copy(status = BenchmarkStatus.SIMULATING)
                                        else result
                                    }
                                },
                                onFlowProcessing = { index ->
                                    results = results.mapIndexed { i, result ->
                                        if (i == index) result.copy(status = BenchmarkStatus.PROCESSING)
                                        else result
                                    }
                                },
                                onFlowTranscribing = { index ->
                                    results = results.mapIndexed { i, result ->
                                        if (i == index) result.copy(status = BenchmarkStatus.TRANSCRIBING)
                                        else result
                                    }
                                },
                                onFlowComplete = { index, timeMs, transcription ->
                                    results = results.mapIndexed { i, result ->
                                        if (i == index) result.copy(
                                            status = BenchmarkStatus.SUCCESS,
                                            timeMs = timeMs,
                                            transcription = transcription
                                        )
                                        else result
                                    }
                                },
                                onFlowError = { index, error ->
                                    results = results.mapIndexed { i, result ->
                                        if (i == index) result.copy(
                                            status = BenchmarkStatus.ERROR,
                                            error = error
                                        )
                                        else result
                                    }
                                },
                                onComplete = {
                                    isRunning = false
                                    currentFlowIndex = -1
                                    Toast.makeText(context, "Benchmark complete!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                enabled = !isRunning && availableAudioFiles.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Running Flow ${currentFlowIndex + 1}/${results.size}...", fontSize = 16.sp)
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Run",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Run Benchmark (All Flows)", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Results
            Text(
                text = "Results",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(results) { index, result ->
                    BenchmarkResultCard(
                        result = result,
                        isActive = currentFlowIndex == index
                    )
                }
            }
        }
    }
}

/**
 * Card showing a single flow's benchmark result
 */
@Composable
private fun BenchmarkResultCard(
    result: FlowBenchmarkResult,
    isActive: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isActive -> Color(0xFFEEF2FF)
                result.status == BenchmarkStatus.SUCCESS -> Color.White
                result.status == BenchmarkStatus.ERROR -> Color(0xFFFEE2E2)
                else -> Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.flow.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1E293B)
                )

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
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status
            when (result.status) {
                BenchmarkStatus.IDLE -> {
                    Text(
                        text = "Waiting...",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                BenchmarkStatus.SIMULATING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF6366F1)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Simulating recording (1x speed)...",
                            fontSize = 12.sp,
                            color = Color(0xFF6366F1)
                        )
                    }
                }
                BenchmarkStatus.PROCESSING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFF97316)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Processing audio...",
                            fontSize = 12.sp,
                            color = Color(0xFFF97316)
                        )
                    }
                }
                BenchmarkStatus.TRANSCRIBING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF22C55E)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Transcribing...",
                            fontSize = 12.sp,
                            color = Color(0xFF22C55E)
                        )
                    }
                }
                BenchmarkStatus.SUCCESS -> {
                    Text(
                        text = result.transcription?.take(100) ?: "No transcription",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        maxLines = 2
                    )
                }
                BenchmarkStatus.ERROR -> {
                    Text(
                        text = "Error: ${result.error}",
                        fontSize = 12.sp,
                        color = Color(0xFFDC2626)
                    )
                }
            }
        }
    }
}

/**
 * Run benchmark for all flows sequentially
 */
private suspend fun runBenchmark(
    context: android.content.Context,
    audioFileName: String,
    benchmark: AudioFileBenchmark,
    audioProcessor: AudioProcessor,
    apiClient: WhisperApiClient,
    authManager: FirebaseAuthManager,
    onFlowStart: (Int) -> Unit,
    onFlowProcessing: (Int) -> Unit,
    onFlowTranscribing: (Int) -> Unit,
    onFlowComplete: (Int, Long, String) -> Unit,
    onFlowError: (Int, String) -> Unit,
    onComplete: () -> Unit
) {
    // Ensure signed in
    val user = authManager.ensureSignedIn()
    if (user == null) {
        TranscriptionFlow.entries.forEachIndexed { index, _ ->
            onFlowError(index, "Authentication failed")
        }
        onComplete()
        return
    }

    val token = authManager.getIdToken()
    if (token == null) {
        TranscriptionFlow.entries.forEachIndexed { index, _ ->
            onFlowError(index, "Failed to get auth token")
        }
        onComplete()
        return
    }

    // Load audio file from assets
    val audioFile: File
    try {
        audioFile = benchmark.loadFromAssets(audioFileName)
        Log.d(TAG, "Loaded test audio: ${audioFile.length()} bytes")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load audio file", e)
        TranscriptionFlow.entries.forEachIndexed { index, _ ->
            onFlowError(index, "Failed to load audio: ${e.message}")
        }
        onComplete()
        return
    }

    // Run each flow sequentially
    TranscriptionFlow.entries.forEachIndexed { index, flow ->
        try {
            Log.d(TAG, "Starting benchmark for ${flow.name}")
            onFlowStart(index)

            // Step 1: Simulate recording
            // This runs at 1x speed with parallel RMS analysis
            val benchmarkResult = benchmark.simulateRecording(audioFile)
            Log.d(TAG, "${flow.name}: Simulation complete, speech=${benchmarkResult.speechDurationMs}ms")

            // Step 2: START TIMING (recording has "stopped")
            val startTime = System.currentTimeMillis()

            // Step 3: Post-recording processing based on flow
            val audioToSend: ByteArray
            val audioFormat = "wav"

            when (flow) {
                TranscriptionFlow.CLOUD_API -> {
                    // Post-recording trimming - ADDS DELAY
                    onFlowProcessing(index)
                    Log.d(TAG, "${flow.name}: Re-trimming raw audio (${benchmarkResult.rawAudioBytes.size} bytes)")

                    val tempFile = File(context.cacheDir, "benchmark_cloud_api_raw.wav")
                    tempFile.writeBytes(benchmarkResult.rawAudioBytes)
                    val processedResult = audioProcessor.trimSilence(tempFile, "wav")
                    audioToSend = processedResult.file.readBytes()
                    tempFile.delete()

                    Log.d(TAG, "${flow.name}: Trimmed to ${audioToSend.size} bytes")
                }
                else -> {
                    // GROQ_WHISPER, FLOW_3, FLOW_4 - No trimming
                    Log.d(TAG, "${flow.name}: Using raw audio (${benchmarkResult.rawAudioBytes.size} bytes)")
                    audioToSend = benchmarkResult.rawAudioBytes
                }
            }

            // Step 4: Send to API
            onFlowTranscribing(index)

            val transcription = withContext(Dispatchers.IO) {
                suspendCancellableCoroutine<String> { continuation ->
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

                    when (flow) {
                        TranscriptionFlow.CLOUD_API -> {
                            apiClient.transcribe(
                                audioBytes = audioToSend,
                                authToken = token,
                                audioFormat = audioFormat,
                                model = null,
                                audioDurationMs = benchmarkResult.originalDurationMs,
                                callback = callback
                            )
                        }
                        TranscriptionFlow.GROQ_WHISPER -> {
                            apiClient.transcribeWithGroq(
                                audioBytes = audioToSend,
                                authToken = token,
                                audioFormat = audioFormat,
                                audioDurationMs = benchmarkResult.originalDurationMs,
                                model = null,
                                callback = callback
                            )
                        }
                        TranscriptionFlow.FLOW_3 -> {
                            apiClient.transcribeWithGroq(
                                audioBytes = audioToSend,
                                authToken = token,
                                audioFormat = audioFormat,
                                audioDurationMs = benchmarkResult.originalDurationMs,
                                model = "whisper-large-v3-turbo",
                                callback = callback
                            )
                        }
                        TranscriptionFlow.FLOW_4 -> {
                            apiClient.transcribe(
                                audioBytes = audioToSend,
                                authToken = token,
                                audioFormat = audioFormat,
                                model = "gpt-4o-mini-transcribe",
                                audioDurationMs = benchmarkResult.originalDurationMs,
                                callback = callback
                            )
                        }
                        TranscriptionFlow.ARAMUS_OPENAI -> {
                            apiClient.transcribe(
                                audioBytes = audioToSend,
                                authToken = token,
                                audioFormat = audioFormat,
                                model = null, // Uses default OpenAI model
                                audioDurationMs = benchmarkResult.originalDurationMs,
                                callback = callback
                            )
                        }
                    }
                }
            }

            // Step 5: STOP TIMING
            val elapsedMs = System.currentTimeMillis() - startTime
            Log.d(TAG, "${flow.name}: Completed in ${elapsedMs}ms")

            onFlowComplete(index, elapsedMs, transcription)

        } catch (e: Exception) {
            Log.e(TAG, "${flow.name} failed: ${e.message}", e)
            onFlowError(index, e.message ?: "Unknown error")
        }

        // Small delay between flows
        delay(500)
    }

    // Cleanup
    try {
        File(context.cacheDir, "benchmark_$audioFileName").delete()
    } catch (e: Exception) {
        Log.w(TAG, "Cleanup failed: ${e.message}")
    }

    onComplete()
}
