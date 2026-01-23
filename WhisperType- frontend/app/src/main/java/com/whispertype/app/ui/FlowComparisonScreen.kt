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
import com.whispertype.app.audio.AudioProcessor
import com.whispertype.app.audio.AudioRecorder
import com.whispertype.app.audio.RealtimeRmsRecorder
import com.whispertype.app.auth.FirebaseAuthManager
import com.whispertype.app.speech.TranscriptionFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.Collections

private const val TAG = "FlowComparison"
private const val PREFS_NAME = "flow_comparison_prefs"
private const val KEY_FLOW_ORDER = "flow_order"
private const val KEY_ENABLED_FLOWS = "enabled_flows"

/**
 * Save enabled flows to SharedPreferences
 */
private fun saveEnabledFlows(context: Context, enabledFlows: Set<TranscriptionFlow>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val enabledString = enabledFlows.joinToString(",") { it.name }
    prefs.edit().putString(KEY_ENABLED_FLOWS, enabledString).apply()
    Log.d(TAG, "Saved enabled flows: $enabledString")
}

/**
 * Load enabled flows from SharedPreferences
 */
private fun loadEnabledFlows(context: Context): Set<TranscriptionFlow> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val enabledString = prefs.getString(KEY_ENABLED_FLOWS, null)

    return if (enabledString != null) {
        enabledString.split(",").mapNotNull { name ->
            try {
                TranscriptionFlow.valueOf(name)
            } catch (e: IllegalArgumentException) {
                null
            }
        }.toSet()
    } else {
        // Default: enable all flows except CLOUD_API
        TranscriptionFlow.entries.filter { it != TranscriptionFlow.CLOUD_API }.toSet()
    }
}

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

    // All available flows (exclude CLOUD_API)
    val allFlows = remember {
        TranscriptionFlow.entries.filter { it != TranscriptionFlow.CLOUD_API }
    }

    // MediaRecorder flows (non-ARAMUS flows)
    val mediaRecorderFlows = remember {
        allFlows.filter { !it.name.startsWith("ARAMUS") }
    }

    // AudioRecord flows (ARAMUS flows)
    val audioRecordFlows = remember {
        allFlows.filter { it.name.startsWith("ARAMUS") }
    }

    // Enabled flows state
    var enabledFlows by remember {
        mutableStateOf(loadEnabledFlows(context))
    }

    // Show flow selector
    var showFlowSelector by remember { mutableStateOf(false) }

    // Flow results state for MediaRecorder flows
    var flowResults by remember {
        val savedOrder = loadFlowOrder(context)
        val orderedFlows = (savedOrder ?: allFlows)
            .filter { it in mediaRecorderFlows }
        mutableStateOf(
            orderedFlows.map { FlowResult(it) }.toMutableList()
        )
    }

    // Aramus flow results (AudioRecord-based)
    var aramusResults by remember {
        mutableStateOf(
            audioRecordFlows.map { FlowResult(it) }.toMutableList()
        )
    }

    // Recording state
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // Audio recorders - dual recorder setup
    val audioRecorder = remember { AudioRecorder(context) }  // MediaRecorder for standard flows
    val realtimeRmsRecorder = remember { RealtimeRmsRecorder(context) }  // AudioRecord for Aramus
    val audioProcessor = remember { AudioProcessor(context) }  // For OGG encoding
    val apiClient = remember { WhisperApiClient() }
    val authManager = remember { FirebaseAuthManager() }

    // Track finish order (separate counters for each column)
    var mediaRecorderFinishCounter by remember { mutableIntStateOf(0) }
    var audioRecordFinishCounter by remember { mutableIntStateOf(0) }

    // Store Aramus audio bytes from callback (since file is deleted after processing)
    var aramusTrimmedAudioBytes by remember { mutableStateOf<ByteArray?>(null) }

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
            // Flow selector row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${enabledFlows.size} flows selected",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                TextButton(onClick = { showFlowSelector = !showFlowSelector }) {
                    Text(
                        text = if (showFlowSelector) "Hide Selector" else "Select Flows",
                        fontSize = 12.sp
                    )
                }
            }

            // Flow selector (expandable)
            AnimatedVisibility(visible = showFlowSelector) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Select flows to compare:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        allFlows.forEach { flow ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newEnabled = if (flow in enabledFlows) {
                                            enabledFlows - flow
                                        } else {
                                            enabledFlows + flow
                                        }
                                        enabledFlows = newEnabled
                                        saveEnabledFlows(context, newEnabled)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = flow in enabledFlows,
                                    onCheckedChange = { checked ->
                                        val newEnabled = if (checked) {
                                            enabledFlows + flow
                                        } else {
                                            enabledFlows - flow
                                        }
                                        enabledFlows = newEnabled
                                        saveEnabledFlows(context, newEnabled)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = flow.displayName,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = if (flow.name.startsWith("ARAMUS")) "AudioRecord" else "MediaRecorder",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Record button
            RecordButton(
                isRecording = isRecording,
                isProcessing = isProcessing,
                onStartRecording = {
                    // Reset all results
                    mediaRecorderFinishCounter = 0
                    audioRecordFinishCounter = 0
                    aramusTrimmedAudioBytes = null

                    // Filter flows based on enabled selection
                    val enabledMediaFlows = flowResults.filter { it.flow in enabledFlows }
                    val enabledAramusFlows = aramusResults.filter { it.flow in enabledFlows }

                    flowResults = flowResults.map {
                        if (it.flow in enabledFlows) {
                            it.copy(status = FlowStatus.RECORDING, text = null, timeMs = null, error = null, finishOrder = 0)
                        } else {
                            it.copy(status = FlowStatus.IDLE, text = null, timeMs = null, error = null, finishOrder = 0)
                        }
                    }.toMutableList()

                    aramusResults = aramusResults.map {
                        if (it.flow in enabledFlows) {
                            it.copy(status = FlowStatus.RECORDING, text = null, timeMs = null, error = null, finishOrder = 0)
                        } else {
                            it.copy(status = FlowStatus.IDLE, text = null, timeMs = null, error = null, finishOrder = 0)
                        }
                    }.toMutableList()

                    // Check if we need each recorder type
                    val needMediaRecorder = enabledMediaFlows.isNotEmpty()
                    val needAudioRecord = enabledAramusFlows.isNotEmpty()

                    var mediaRecorderStarted = !needMediaRecorder  // true if not needed
                    var audioRecordStarted = !needAudioRecord

                    // Start MediaRecorder if needed
                    if (needMediaRecorder) {
                        mediaRecorderStarted = audioRecorder.startRecording(object : AudioRecorder.RecordingCallback {
                            override fun onRecordingStarted() {
                                Log.d(TAG, "MediaRecorder started")
                            }
                            override fun onRecordingStopped(audioBytes: ByteArray) {}
                            override fun onRecordingError(error: String) {
                                Log.e(TAG, "MediaRecorder error: $error")
                            }
                        })
                    }

                    // Start AudioRecord if needed
                    if (needAudioRecord) {
                        realtimeRmsRecorder.startRecording(object : RealtimeRmsRecorder.RecordingCallback {
                            override fun onRecordingStarted() {
                                Log.d(TAG, "AudioRecord (Aramus) started")
                                audioRecordStarted = true
                            }
                            override fun onRecordingStopped(trimmedAudioBytes: ByteArray, rawAudioBytes: ByteArray, metadata: RealtimeRmsRecorder.RmsMetadata) {
                                Log.d(TAG, "AudioRecord (Aramus) stopped. Trimmed: ${trimmedAudioBytes.size} bytes")
                                aramusTrimmedAudioBytes = trimmedAudioBytes
                            }
                            override fun onRecordingError(error: String) {
                                Log.e(TAG, "AudioRecord (Aramus) error: $error")
                                aramusResults = aramusResults.map {
                                    it.copy(status = FlowStatus.ERROR, error = error)
                                }.toMutableList()
                            }
                        })
                    }

                    if (mediaRecorderStarted || audioRecordStarted) {
                        isRecording = true
                        Log.d(TAG, "Recorders started (Media: $needMediaRecorder, Audio: $needAudioRecord)")
                    } else {
                        Log.e(TAG, "Failed to start recorders")
                        flowResults = flowResults.map { it.copy(status = FlowStatus.IDLE) }.toMutableList()
                        aramusResults = aramusResults.map { it.copy(status = FlowStatus.IDLE) }.toMutableList()
                        Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
                    }
                },
                onStopRecording = {
                    isRecording = false
                    isProcessing = true

                    val recordingStopTime = System.currentTimeMillis()

                    // Filter enabled flows
                    val enabledMediaFlows = flowResults.filter { it.flow in enabledFlows }
                    val enabledAramusFlows = aramusResults.filter { it.flow in enabledFlows }

                    // Update enabled flows to transcribing state
                    flowResults = flowResults.map {
                        if (it.flow in enabledFlows) it.copy(status = FlowStatus.TRANSCRIBING) else it
                    }.toMutableList()
                    aramusResults = aramusResults.map {
                        if (it.flow in enabledFlows) it.copy(status = FlowStatus.TRANSCRIBING) else it
                    }.toMutableList()

                    // Stop AudioRecord if it was started
                    if (enabledAramusFlows.isNotEmpty()) {
                        realtimeRmsRecorder.stopRecording()
                    }

                    // Stop MediaRecorder and process if it was started
                    if (enabledMediaFlows.isNotEmpty()) {
                        audioRecorder.stopRecording(object : AudioRecorder.RecordingCallback {
                            override fun onRecordingStarted() {}
                            override fun onRecordingStopped(audioBytes: ByteArray) {
                                Log.d(TAG, "MediaRecorder stopped, ${audioBytes.size} bytes")
                                val audioFormat = audioRecorder.getAudioFormat()
                                val durationMs = (audioBytes.size.toLong() * 8 / 64).coerceAtLeast(1000)

                                scope.launch {
                                    transcribeAllFlows(
                                        context = context,
                                        audioBytes = audioBytes,
                                        audioFormat = audioFormat,
                                        durationMs = durationMs,
                                        apiClient = apiClient,
                                        authManager = authManager,
                                        flowResults = flowResults.filter { it.flow in enabledFlows },
                                        recordingStopTime = recordingStopTime,
                                        onFlowComplete = { flow, text, timeMs ->
                                            mediaRecorderFinishCounter++
                                            flowResults = flowResults.map {
                                                if (it.flow == flow) {
                                                    it.copy(status = FlowStatus.SUCCESS, text = text, timeMs = timeMs, finishOrder = mediaRecorderFinishCounter)
                                                } else it
                                            }.toMutableList()
                                            checkIfAllDone(flowResults, aramusResults, enabledFlows) { isProcessing = false }
                                        },
                                        onFlowError = { flow, error ->
                                            mediaRecorderFinishCounter++
                                            flowResults = flowResults.map {
                                                if (it.flow == flow) {
                                                    it.copy(status = FlowStatus.ERROR, error = error, finishOrder = mediaRecorderFinishCounter)
                                                } else it
                                            }.toMutableList()
                                            checkIfAllDone(flowResults, aramusResults, enabledFlows) { isProcessing = false }
                                        }
                                    )
                                }
                            }
                            override fun onRecordingError(error: String) {
                                Log.e(TAG, "MediaRecorder stop error: $error")
                            }
                        })
                    }

                    // Process Aramus flows (with pre-trimmed audio from RealtimeRmsRecorder)
                    if (enabledAramusFlows.isNotEmpty()) {
                        scope.launch {
                            transcribeAramusFlows(
                                flows = enabledAramusFlows.map { it.flow },
                                trimmedAudioBytesProvider = { aramusTrimmedAudioBytes },
                                audioProcessor = audioProcessor,
                                apiClient = apiClient,
                                authManager = authManager,
                                recordingStopTime = recordingStopTime,
                                onFlowComplete = { flow, text, timeMs ->
                                    audioRecordFinishCounter++
                                    aramusResults = aramusResults.map {
                                        if (it.flow == flow) {
                                            it.copy(status = FlowStatus.SUCCESS, text = text, timeMs = timeMs, finishOrder = audioRecordFinishCounter)
                                        } else it
                                    }.toMutableList()
                                    checkIfAllDone(flowResults, aramusResults, enabledFlows) { isProcessing = false }
                                },
                                onFlowError = { flow, error ->
                                    audioRecordFinishCounter++
                                    aramusResults = aramusResults.map {
                                        if (it.flow == flow) {
                                            it.copy(status = FlowStatus.ERROR, error = error, finishOrder = audioRecordFinishCounter)
                                        } else it
                                    }.toMutableList()
                                    checkIfAllDone(flowResults, aramusResults, enabledFlows) { isProcessing = false }
                                }
                            )
                        }
                    }

                    // If no flows enabled, stop processing
                    if (enabledMediaFlows.isEmpty() && enabledAramusFlows.isEmpty()) {
                        isProcessing = false
                        Toast.makeText(context, "No flows selected", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Side-by-side comparison layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left column: MediaRecorder flows
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Column header
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF6366F1).copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "MediaRecorder",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6366F1)
                            )
                            Text(
                                text = "Sequential processing",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Flow results list (filtered by enabled flows)
                    val filteredFlowResults = flowResults.filter { it.flow in enabledFlows }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filteredFlowResults) { index, result ->
                            CompactFlowResultCard(
                                result = result,
                                canMoveUp = index > 0,
                                canMoveDown = index < filteredFlowResults.size - 1,
                                onMoveUp = {
                                    if (index > 0) {
                                        // Find actual indices in full list
                                        val currentIdx = flowResults.indexOfFirst { it.flow == result.flow }
                                        val prevFlow = filteredFlowResults[index - 1].flow
                                        val prevIdx = flowResults.indexOfFirst { it.flow == prevFlow }
                                        if (currentIdx >= 0 && prevIdx >= 0) {
                                            val newList = flowResults.toMutableList()
                                            Collections.swap(newList, currentIdx, prevIdx)
                                            flowResults = newList
                                            saveFlowOrder(context, newList.map { it.flow })
                                        }
                                    }
                                },
                                onMoveDown = {
                                    if (index < filteredFlowResults.size - 1) {
                                        val currentIdx = flowResults.indexOfFirst { it.flow == result.flow }
                                        val nextFlow = filteredFlowResults[index + 1].flow
                                        val nextIdx = flowResults.indexOfFirst { it.flow == nextFlow }
                                        if (currentIdx >= 0 && nextIdx >= 0) {
                                            val newList = flowResults.toMutableList()
                                            Collections.swap(newList, currentIdx, nextIdx)
                                            flowResults = newList
                                            saveFlowOrder(context, newList.map { it.flow })
                                        }
                                    }
                                },
                                onCopy = {
                                    result.text?.let { text ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", text))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                // Right column: AudioRecord (Aramus) flows
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Column header
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF22C55E).copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "AudioRecord",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                            Text(
                                text = "Parallel RMS processing",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Aramus flow results list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(aramusResults.filter { it.flow in enabledFlows }) { _, result ->
                            CompactFlowResultCard(
                                result = result,
                                canMoveUp = false,
                                canMoveDown = false,
                                onMoveUp = {},
                                onMoveDown = {},
                                onCopy = {
                                    result.text?.let { text ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", text))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                showReorderButtons = false,
                                highlightColor = Color(0xFF22C55E)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Check if all enabled flows are done (both MediaRecorder and AudioRecord columns)
 */
private fun checkIfAllDone(
    mediaRecorderResults: List<FlowResult>,
    aramusResults: List<FlowResult>,
    enabledFlows: Set<TranscriptionFlow>,
    onAllDone: () -> Unit
) {
    val enabledMediaResults = mediaRecorderResults.filter { it.flow in enabledFlows }
    val enabledAramusResults = aramusResults.filter { it.flow in enabledFlows }

    val mediaRecorderDone = enabledMediaResults.isEmpty() || enabledMediaResults.all {
        it.status == FlowStatus.SUCCESS || it.status == FlowStatus.ERROR
    }
    val aramusDone = enabledAramusResults.isEmpty() || enabledAramusResults.all {
        it.status == FlowStatus.SUCCESS || it.status == FlowStatus.ERROR
    }

    if (mediaRecorderDone && aramusDone) {
        onAllDone()
    }
}

/**
 * Transcribe using multiple Aramus flows (with different post-processing: WAV vs OGG)
 */
private suspend fun transcribeAramusFlows(
    flows: List<TranscriptionFlow>,
    trimmedAudioBytesProvider: () -> ByteArray?,
    audioProcessor: AudioProcessor,
    apiClient: WhisperApiClient,
    authManager: FirebaseAuthManager,
    recordingStopTime: Long,
    onFlowComplete: (TranscriptionFlow, String, Long) -> Unit,
    onFlowError: (TranscriptionFlow, String) -> Unit
) {
    // Get auth token
    val user = authManager.ensureSignedIn()
    if (user == null) {
        flows.forEach { flow ->
            withContext(Dispatchers.Main) { onFlowError(flow, "Authentication failed") }
        }
        return
    }

    val token = authManager.getIdToken()
    if (token == null) {
        flows.forEach { flow ->
            withContext(Dispatchers.Main) { onFlowError(flow, "Failed to get auth token") }
        }
        return
    }

    // Wait briefly for RealtimeRmsRecorder callback to complete
    delay(200)

    // Get trimmed audio from callback
    val trimmedAudioBytes = trimmedAudioBytesProvider()
    if (trimmedAudioBytes == null || trimmedAudioBytes.isEmpty()) {
        flows.forEach { flow ->
            withContext(Dispatchers.Main) { onFlowError(flow, "No audio data from Aramus recorder") }
        }
        return
    }

    val estimatedDurationMs = (trimmedAudioBytes.size.toLong() * 1000 / 32000).coerceAtLeast(1000)
    Log.d(TAG, "Aramus: Processing ${flows.size} flows with ${trimmedAudioBytes.size} bytes")

    // Launch each flow in parallel
    coroutineScope {
        flows.forEach { flow ->
            launch(Dispatchers.IO) {
                try {
                    // Determine audio format based on flow
                    val (audioBytes, audioFormat) = when (flow) {
                        TranscriptionFlow.ARAMUS_OPENAI -> {
                            // Send WAV directly
                            Log.d(TAG, "ARAMUS_OPENAI: Sending WAV (${trimmedAudioBytes.size} bytes)")
                            Pair(trimmedAudioBytes, "wav")
                        }
                        else -> Pair(trimmedAudioBytes, "wav")
                    }

                    // Make API call
                    val text = suspendCancellableCoroutine<String> { continuation ->
                        val callback = object : WhisperApiClient.TranscriptionCallback {
                            override fun onSuccess(text: String) {
                                if (continuation.isActive) continuation.resume(text) {}
                            }
                            override fun onError(error: String) {
                                if (continuation.isActive) continuation.cancel(Exception(error))
                            }
                            override fun onTrialExpired(message: String) {
                                if (continuation.isActive) continuation.cancel(Exception(message))
                            }
                        }

                        apiClient.transcribe(
                            audioBytes = audioBytes,
                            authToken = token,
                            audioFormat = audioFormat,
                            model = "gpt-4o-mini-transcribe",
                            audioDurationMs = estimatedDurationMs,
                            callback = callback
                        )
                    }

                    val elapsedMs = System.currentTimeMillis() - recordingStopTime
                    Log.d(TAG, "${flow.name} completed in ${elapsedMs}ms")

                    withContext(Dispatchers.Main) {
                        onFlowComplete(flow, text, elapsedMs)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "${flow.name} failed: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onFlowError(flow, e.message ?: "Unknown error")
                    }
                }
            }
        }
    }
}

/**
 * Transcribe using Aramus flow (RealtimeRmsRecorder with parallel RMS + GPT-4o-mini-transcribe)
 * @deprecated Use transcribeAramusFlows instead
 */
private suspend fun transcribeAramusFlow(
    trimmedAudioBytesProvider: () -> ByteArray?,
    apiClient: WhisperApiClient,
    authManager: FirebaseAuthManager,
    recordingStopTime: Long,
    onComplete: (String, Long) -> Unit,
    onError: (String) -> Unit
) {
    try {
        // Get auth token
        val user = authManager.ensureSignedIn()
        if (user == null) {
            withContext(Dispatchers.Main) { onError("Authentication failed") }
            return
        }

        val token = authManager.getIdToken()
        if (token == null) {
            withContext(Dispatchers.Main) { onError("Failed to get auth token") }
            return
        }

        // Wait briefly for RealtimeRmsRecorder callback to complete
        delay(200)

        // Get trimmed audio from callback (file is deleted after processing)
        val trimmedAudioBytes = trimmedAudioBytesProvider()
        if (trimmedAudioBytes == null || trimmedAudioBytes.isEmpty()) {
            withContext(Dispatchers.Main) { onError("No audio data from Aramus recorder") }
            return
        }

        // Estimate duration: WAV at 16kHz, mono, 16-bit = 32000 bytes per second
        val estimatedDurationMs = (trimmedAudioBytes.size.toLong() * 1000 / 32000).coerceAtLeast(1000)

        Log.d(TAG, "Aramus: Sending pre-trimmed WAV audio, size: ${trimmedAudioBytes.size} bytes")

        // Use suspendCancellableCoroutine to convert callback to suspend
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

            // Use gpt-4o-mini-transcribe model (same as FLOW_4)
            apiClient.transcribe(
                audioBytes = trimmedAudioBytes,
                authToken = token,
                audioFormat = "wav",
                model = "gpt-4o-mini-transcribe",
                audioDurationMs = estimatedDurationMs,
                callback = callback
            )
        }

        val elapsedMs = System.currentTimeMillis() - recordingStopTime
        Log.d(TAG, "Aramus completed in ${elapsedMs}ms (from recording stop)")

        withContext(Dispatchers.Main) {
            onComplete(text, elapsedMs)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Aramus failed: ${e.message}")
        withContext(Dispatchers.Main) {
            onError(e.message ?: "Unknown error")
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
 * Compact card for side-by-side comparison layout
 */
@Composable
private fun CompactFlowResultCard(
    result: FlowResult,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCopy: () -> Unit,
    showReorderButtons: Boolean = true,
    highlightColor: Color = Color(0xFF6366F1)
) {
    val isVisible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible.value = true }

    AnimatedVisibility(
        visible = isVisible.value,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (result.status) {
                    FlowStatus.SUCCESS -> Color.White
                    FlowStatus.ERROR -> Color(0xFFFEE2E2)
                    else -> Color.White
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Header row: Flow name + timing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Reorder buttons (optional)
                        if (showReorderButtons) {
                            Column {
                                IconButton(
                                    onClick = onMoveUp,
                                    enabled = canMoveUp,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move up",
                                        tint = if (canMoveUp) Color(0xFF64748B) else Color(0xFFCBD5E1),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onMoveDown,
                                    enabled = canMoveDown,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move down",
                                        tint = if (canMoveDown) Color(0xFF64748B) else Color(0xFFCBD5E1),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Medal for finish order
                        if (result.finishOrder in 1..4) {
                            val medal = when (result.finishOrder) {
                                1 -> "🥇"
                                2 -> "🥈"
                                3 -> "🥉"
                                else -> "#${result.finishOrder}"
                            }
                            Text(
                                text = medal,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }

                        // Flow name (shortened for compact view)
                        Text(
                            text = result.flow.displayName.take(20),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Timing badge
                    if (result.timeMs != null) {
                        val timeText = if (result.timeMs!! < 1000) {
                            "${result.timeMs}ms"
                        } else {
                            String.format("%.1fs", result.timeMs!! / 1000.0)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = highlightColor.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = timeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = highlightColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Status / Result
                when (result.status) {
                    FlowStatus.IDLE -> {
                        Text(
                            text = "Waiting...",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    FlowStatus.RECORDING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = Color(0xFFDC2626)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recording...",
                                fontSize = 11.sp,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                    FlowStatus.TRANSCRIBING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = highlightColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Transcribing...",
                                fontSize = 11.sp,
                                color = highlightColor
                            )
                        }
                    }
                    FlowStatus.SUCCESS -> {
                        Text(
                            text = result.text ?: "",
                            fontSize = 11.sp,
                            color = Color(0xFF1E293B),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onCopy() }
                        )
                    }
                    FlowStatus.ERROR -> {
                        Text(
                            text = result.error?.take(50) ?: "Error",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
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
    recordingStopTime: Long,
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
    // Note: All flows use the same recordingStopTime to measure total time from recording stop to result
    coroutineScope {
        flowResults.forEach { result ->
            launch(Dispatchers.IO) {
                try {
                    // For CLOUD_API, do silence trimming BEFORE the API call (like real usage)
                    // This must be done outside suspendCancellableCoroutine since trimSilence is a suspend function
                    val finalAudioBytes: ByteArray
                    val finalAudioFormat: String
                    val finalDurationMs: Long

                    if (result.flow == TranscriptionFlow.CLOUD_API) {
                        Log.d(TAG, "CLOUD_API: Starting silence trimming")

                        // Write audio to temp file for processing
                        val tempFile = File(context.cacheDir, "flow_comparison_cloud_api.${audioFormat}")
                        tempFile.writeBytes(audioBytes)

                        // Process audio to remove silence
                        val audioProcessor = AudioProcessor(context)
                        val processedResult = audioProcessor.trimSilence(tempFile, audioFormat)
                        finalAudioBytes = processedResult.file.readBytes()
                        finalAudioFormat = processedResult.format  // "wav" after processing
                        finalDurationMs = processedResult.originalDurationMs

                        val savedPercent = if (audioBytes.isNotEmpty()) {
                            ((audioBytes.size - finalAudioBytes.size) * 100 / audioBytes.size)
                        } else 0
                        Log.d(TAG, "CLOUD_API: Audio trimmed ${audioBytes.size} -> ${finalAudioBytes.size} bytes ($savedPercent% saved)")

                        // Clean up temp file (but not the processed file which may be different)
                        if (tempFile.exists() && tempFile != processedResult.file) {
                            tempFile.delete()
                        }
                    } else {
                        // Other flows use raw audio without trimming
                        finalAudioBytes = audioBytes
                        finalAudioFormat = audioFormat
                        finalDurationMs = durationMs
                    }

                    // Use suspendCancellableCoroutine to convert callback to suspend
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
                                Log.d(TAG, "CLOUD_API: Sending trimmed audio to API")
                                apiClient.transcribe(
                                    audioBytes = finalAudioBytes,
                                    authToken = token,
                                    audioFormat = finalAudioFormat,
                                    model = null,
                                    audioDurationMs = finalDurationMs,
                                    callback = callback
                                )
                            }
                            TranscriptionFlow.GROQ_WHISPER -> {
                                Log.d(TAG, "Starting GROQ_WHISPER transcription (no trim)")
                                apiClient.transcribeWithGroq(
                                    audioBytes = finalAudioBytes,
                                    authToken = token,
                                    audioFormat = finalAudioFormat,
                                    audioDurationMs = finalDurationMs,
                                    model = null,  // Default: whisper-large-v3
                                    callback = callback
                                )
                            }
                            TranscriptionFlow.FLOW_3 -> {
                                Log.d(TAG, "Starting FLOW_3 (Groq Turbo) transcription (no trim)")
                                apiClient.transcribeWithGroq(
                                    audioBytes = finalAudioBytes,
                                    authToken = token,
                                    audioFormat = finalAudioFormat,
                                    audioDurationMs = finalDurationMs,
                                    model = "whisper-large-v3-turbo",
                                    callback = callback
                                )
                            }
                            TranscriptionFlow.FLOW_4 -> {
                                Log.d(TAG, "Starting FLOW_4 (OpenAI Mini No Trim) transcription")
                                apiClient.transcribe(
                                    audioBytes = finalAudioBytes,
                                    authToken = token,
                                    audioFormat = finalAudioFormat,
                                    model = "gpt-4o-mini-transcribe",
                                    audioDurationMs = finalDurationMs,
                                    callback = callback
                                )
                            }
                            TranscriptionFlow.ARAMUS_OPENAI -> {
                                // Skip - handled separately in transcribeAramusFlows
                                Log.d(TAG, "Skipping ARAMUS_OPENAI in transcribeAllFlows (handled separately)")
                                continuation.cancel(Exception("Handled separately"))
                            }
                        }
                    }
                    
                    // Calculate elapsed time from when recording stopped (includes audio processing + API call)
                    val elapsedMs = System.currentTimeMillis() - recordingStopTime
                    Log.d(TAG, "${result.flow.name} completed in ${elapsedMs}ms (from recording stop)")
                    
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
