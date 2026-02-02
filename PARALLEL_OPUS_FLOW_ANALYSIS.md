# ParallelOpusRecorder - Complete Flow Analysis

## Overview

The `ParallelOpusRecorder` uses a **three-thread architecture** for parallel processing:

```
┌─────────────────────────────────────────────────────────────────┐
│                        RECORDING                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────────────┐                                          │
│   │  AudioRecord    │  Captures PCM @ 16kHz, 16-bit, mono     │
│   │  (Microphone)   │  320 samples per chunk (20ms)           │
│   └────────┬────────┘                                          │
│            │                                                    │
│            ▼                                                    │
│   ┌─────────────────┐                                          │
│   │ Recording Thread│  Reads audio, distributes to queues     │
│   │  (HIGH priority)│                                          │
│   └────────┬────────┘                                          │
│            │                                                    │
│      ┌─────┴─────┐                                             │
│      ▼           ▼                                             │
│ ┌─────────┐ ┌─────────────┐                                    │
│ │RMS Queue│ │Encoder Queue│                                    │
│ └────┬────┘ └──────┬──────┘                                    │
│      │             │                                            │
│      ▼             ▼                                            │
│ ┌─────────────┐ ┌─────────────┐                                │
│ │ RMS Thread  │ │Encoder Thread│                               │
│ │(BACKGROUND) │ │ (BACKGROUND) │                               │
│ └──────┬──────┘ └──────┬───────┘                               │
│        │               │                                        │
│        ▼               ▼                                        │
│ ┌─────────────┐ ┌─────────────┐                                │
│ │   Speech    │ │   Encoded   │                                │
│ │  Segments   │ │   Frames    │                                │
│ └─────────────┘ └─────────────┘                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      WHEN RECORDING STOPS                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. finalizeSegments()                                          │
│     - Close any open speech segment                             │
│     - Add padding (150ms before, 200ms after)                  │
│     - Merge overlapping segments                                │
│                                                                 │
│  2. filterAndMuxToOgg()                                         │
│     - Filter encoded frames by speech segments                  │
│     - Mux to OGG container                                      │
│     - Return both trimmed and raw versions                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Detailed Flow

### Step 1: Audio Capture (Recording Thread)

```kotlin
// Audio configuration
SAMPLE_RATE = 16000        // 16kHz
CHUNK_SAMPLES = 320        // 320 samples = 20ms (Opus optimal frame size)
AUDIO_FORMAT = PCM_16BIT   // 2 bytes per sample
```

**Every 20ms:**
- Reads 320 samples from microphone
- Writes to circular buffer (60 second capacity)
- Creates chunk with timestamp (microseconds)
- Queues chunk to BOTH RMS queue AND encoder queue

### Step 2: RMS Analysis Thread

```kotlin
// Speech detection parameters
SPEECH_THRESHOLD_DB = -40f    // Decibel threshold
MIN_SILENCE_DURATION_MS = 100 // Gap merge threshold (from Constants: 500ms)
```

**For each chunk:**
1. Calculate RMS in decibels: `20 * log10(rms / 32767)`
2. Compare to threshold: `rmsDb > -40dB` = speech detected
3. Track speech segments:
   - Speech starts → record start timestamp
   - Speech ends (silence detected) → save segment
   - If silence gap < 100ms → merge with previous segment

**Key: Segment Merging Logic**
```kotlin
if (lastSegment != null && chunk.timeUs - lastSpeechEnd < MIN_SILENCE_DURATION_MS * 1000) {
    // Short silence gap - extend previous segment instead of creating new one
    speechSegments.remove(lastSegment)
    currentSpeechStart = lastSegment.startUs
}
```

### Step 3: Encoder Thread

```kotlin
// Opus encoder settings
OPUS_BIT_RATE = 24000        // 24 kbps
COMPLEXITY = 5               // Balance quality/speed
Frame size = 20ms (320 samples)
```

**For each chunk:**
1. Queue PCM samples to MediaCodec input buffer
2. Drain output buffer for encoded Opus frames
3. Store encoded frames with timestamps

### Step 4: Segment Finalization (On Stop)

```kotlin
private fun finalizeSegments(totalDurationUs: Long) {
    // 1. Close any open speech segment (if recording stopped while speaking)
    if (currentSpeechStart != null) {
        speechSegments.offer(SpeechSegment(
            startUs = currentSpeechStart,
            endUs = totalDurationUs  // End at recording stop time
        ))
    }
    
    // 2. Add padding to all segments
    for (segment in speechSegments) {
        paddedStart = segment.startUs - 150ms   // BUFFER_BEFORE_MS
        paddedEnd = segment.endUs + 200ms       // BUFFER_AFTER_MS
    }
    
    // 3. Merge overlapping segments
    merged = mergeOverlappingSegments(paddedSegments)
}
```

### Step 5: Filter and Mux to OGG

```kotlin
private fun filterAndMuxToOgg(totalDurationUs: Long): MuxResult {
    // Filter encoded frames by speech segments
    val filteredFrames = framesList.filter { frame ->
        segmentsList.any { segment ->
            frame.presentationTimeUs >= segment.startUs &&
            frame.presentationTimeUs <= segment.endUs
        }
    }
    
    // Mux to OGG container
    val trimmedBytes = muxFramesToOgg(filteredFrames)
    val rawBytes = muxFramesToOgg(allFrames)
    
    return MuxResult(trimmedBytes, rawBytes, metadata)
}
```

---

## Fallback Behavior

### When NO Speech Segments Detected:

```kotlin
if (segmentsList.isEmpty()) {
    Log.w(TAG, "No speech detected")
    val rawBytes = muxFramesToOgg(framesList, "raw_opus_output.ogg")
    return MuxResult(
        trimmedBytes = ByteArray(0),  // Empty trimmed!
        rawBytes = rawBytes,          // All frames as raw
        metadata = Metadata(
            speechDurationMs = 0,
            speechSegmentCount = 0
        )
    )
}
```

**⚠️ Important:** When no segments detected, `trimmedBytes` is **empty**!  
The caller must check and use `rawBytes` as fallback.

---

## File Size Expectations

### Raw Audio (Uncompressed):
```
16kHz × 16-bit × mono = 32,000 bytes/second
10 seconds = 320 KB
```

### Opus Encoded:
```
24 kbps = 3,000 bytes/second
10 seconds = 30 KB

Compression ratio: ~10:1
```

### With Silence Trimming:
```
If 10 seconds recorded, 6 seconds speech:
- Raw WAV: 192 KB
- Trimmed OGG: 18 KB

Savings: ~91% reduction
```

---

## Logging Output to Verify

When running, check Logcat for these messages:

### During Recording:
```
D/ParallelOpusRecorder: Recording started with parallel RMS + Opus encoding
D/ParallelOpusRecorder: RMS analysis thread started
D/ParallelOpusRecorder: Encoder thread started
```

### On Stop:
```
D/ParallelOpusRecorder: Stopping recording...
D/ParallelOpusRecorder: Recording stopped. Duration: 5420ms, Segments: 2, EncodedFrames: 271
D/ParallelOpusRecorder: Finalized 1 speech segments  ← Merged 2 segments into 1
D/ParallelOpusRecorder: Filtering 271 frames by 1 segments
D/ParallelOpusRecorder: Filtered to 195 frames  ← 76 silent frames removed
D/ParallelOpusRecorder: Muxed 195 frames to OGG: 15234 bytes
```

### If Using Fallback (No Speech Detected):
```
D/ParallelOpusRecorder: No speech detected
D/ParallelOpusRecorder: Muxed 271 frames to OGG: 21234 bytes
```

---

## How to Verify Silence Trimming is Working

### Check 1: Segment Count
Look for: `Finalized X speech segments`
- `X > 0` → ✅ Speech detected, trimming will happen
- `X = 0` → ⚠️ Fallback mode, using all audio

### Check 2: Frame Filtering
Look for: `Filtering X frames by Y segments` → `Filtered to Z frames`
- If `Z < X` → ✅ Silence trimming is working
- If `Z = X` → ⚠️ All frames used (either continuous speech or no segments)

### Check 3: File Size
Compare `trimmedFileSizeBytes` vs `rawFileSizeBytes` in metadata:
- If trimmed < raw → ✅ Trimming saved bytes
- If trimmed = 0 → ⚠️ Fallback mode
- If trimmed = raw → Speech covered entire recording

### Check 4: Metadata
The callback returns metadata with:
```kotlin
Metadata(
    speechDurationMs = 4200,      // Only speech portion
    originalDurationMs = 5420,   // Total recording
    speechSegmentCount = 1,      // How many segments
    trimmedFileSizeBytes = 15234,// Final size
    rawFileSizeBytes = 21234     // Without trimming
)
```

---

## Current Parameters (from Constants.kt)

```kotlin
SILENCE_THRESHOLD_DB = -40f     // Speech detection threshold
MIN_SILENCE_DURATION_MS = 500L  // Merge if gap < 500ms
AUDIO_BUFFER_BEFORE_MS = 150L   // Padding before speech
AUDIO_BUFFER_AFTER_MS = 200L    // Padding after speech
AUDIO_BIT_RATE_OPUS = 24000     // Opus bitrate
```

---

## Summary: Is Silence Trimming Working?

**YES, if you see:**
- `Finalized X speech segments` where X > 0
- `Filtered to Z frames (from Y)` where Z < Y
- `trimmedFileSizeBytes < rawFileSizeBytes`

**FALLBACK MODE, if you see:**
- `No speech detected`
- `Finalized 0 speech segments`
- `trimmedBytes = ByteArray(0)`

The individual flow test likely IS working properly with proper trimming. Check the logs to confirm segment detection and frame filtering.
