# Silence Trimming Indicator Feature

## Overview

Added a `silenceTrimmingApplied` boolean flag across all audio recorders and the Flow Test UI to clearly show whether silence trimming actually happened or if the fallback mode was used.

## Why This Matters

Previously, it wasn't obvious whether:
- Speech detection worked properly
- Silence was actually trimmed
- The system fell back to using all audio

Now you can see at a glance: **✓ Silence Trimmed** or **✗ No Trimming (Fallback)**

## Changes Made

### 1. ParallelOpusRecorder ✅

**File**: `ParallelOpusRecorder.kt`

**Added to Metadata**:
```kotlin
data class Metadata(
    val speechDurationMs: Long,
    val originalDurationMs: Long,
    val speechSegmentCount: Int,
    val trimmedFileSizeBytes: Int,
    val rawFileSizeBytes: Int,
    val silenceTrimmingApplied: Boolean  // NEW!
)
```

**Logic**:
- `false` when no speech segments detected → fallback mode
- `true` when frames were filtered → `filteredFrames.size < framesList.size`

### 2. RealtimeRmsRecorder ✅

**File**: `RealtimeRmsRecorder.kt`

**Added to RmsMetadata**:
```kotlin
data class RmsMetadata(
    val speechDurationMs: Long,
    val originalDurationMs: Long,
    val speechSegmentCount: Int,
    val silenceTrimmingApplied: Boolean = false  // NEW!
)
```

**Logic**:
- `false` when no speech segments → returns empty trimmed WAV
- `true` when speech detected and extracted

### 3. DualFlowRecorder ✅

**File**: `DualFlowRecorder.kt`

**Added to RmsMetadata**:
```kotlin
data class RmsMetadata(
    val speechSegments: List<SpeechSegment>,
    val totalSamples: Int,
    val silenceTrimmingApplied: Boolean  // NEW!
)
```

**Logic**:
- Calculated when creating metadata in `stopRecording()`
- `wavTrimmingApplied = wavSegments.isNotEmpty() && wavWriteIndex > SAMPLE_RATE`
- `oggTrimmingApplied = oggSegments.isNotEmpty() && oggWriteIndex > SAMPLE_RATE`

### 4. FlowTestActivity UI ✅

**File**: `FlowTestActivity.kt`

**Added to FlowTestResult**:
```kotlin
data class FlowTestResult(
    val flowName: String,
    val audioSizeBytes: Int,
    val audioFormat: String,
    val recordingDurationMs: Long,
    val processingTimeMs: Long,
    val transcriptionTimeMs: Long,
    val totalTimeMs: Long,
    val transcribedText: String,
    val silenceTrimmingApplied: Boolean = false  // NEW!
)
```

**UI Display**:
```kotlin
// Individual flow test cards now show:
Text(
    if (result.silenceTrimmingApplied) "✓ Silence Trimmed" else "✗ No Trimming (Fallback)",
    color = if (result.silenceTrimmingApplied) Green else Orange
)
```

## Visual Examples

### When Trimming Works ✅
```
┌─────────────────────────────────────┐
│ PARALLEL_OPUS Flow                  │
├─────────────────────────────────────┤
│ File Size: 18 KB  Total: 1234 ms   │
│ Processing: 45 ms  API: 1189 ms    │
│                                      │
│ ✓ Silence Trimmed                   │  ← GREEN
│                                      │
│ Result:          156 chars          │
│ The quick brown fox...              │
└─────────────────────────────────────┘
```

### When Fallback Mode Used ⚠️
```
┌─────────────────────────────────────┐
│ ARAMUS_OPENAI Flow                  │
├─────────────────────────────────────┤
│ File Size: 320 KB  Total: 2456 ms  │
│ Processing: 89 ms  API: 2367 ms    │
│                                      │
│ ✗ No Trimming (Fallback)            │  ← ORANGE/YELLOW
│                                      │
│ Result:          12 chars           │
│ (silence or very short audio)       │
└─────────────────────────────────────┘
```

## How to Interpret Results

### ✓ Silence Trimmed (Green)
- **Meaning**: Speech detection worked
- **Action**: Trimmed audio sent to API
- **File Size**: Smaller (only speech)
- **Quality**: Optimal - no unnecessary silence

### ✗ No Trimming (Fallback) (Orange)
- **Meaning**: No speech segments detected
- **Possible Reasons**:
  1. Recording was too quiet (below -40dB threshold)
  2. Very short recording (< 1 second)
  3. Microphone issue
  4. Stopped before 1.5s silence (DualFlow - now fixed!)
- **Action**: Full audio sent to API
- **File Size**: Larger (all audio)
- **Transcription**: May include silence or be empty

## Testing the Feature

### Test 1: Normal Speech
1. Record for 5-10 seconds while speaking clearly
2. Expected: **✓ Silence Trimmed** (green)
3. File size should be smaller than recording duration × 32KB/sec

### Test 2: Whisper/Quiet Speech
1. Record while whispering very quietly
2. Expected: **✗ No Trimming (Fallback)** (orange)
3. File size matches full recording duration

### Test 3: Short Recording
1. Record for just 1-2 seconds
2. Expected: May show **✓ Silence Trimmed** if speech detected
3. Check segment count in logs

## Debugging with Logs

Combined with existing logs, you can now fully diagnose:

```
D/ParallelOpusRecorder: Finalized 2 speech segments
D/ParallelOpusRecorder: Filtering 271 frames by 2 segments
D/ParallelOpusRecorder: Filtered to 195 frames

UI shows: ✓ Silence Trimmed ← Confirms trimming worked
```

vs

```
D/ParallelOpusRecorder: No speech detected
D/ParallelOpusRecorder: Muxed 271 frames to OGG

UI shows: ✗ No Trimming (Fallback) ← Confirms fallback mode
```

## API Impact

### When Trimming Applied
- **Pros**: Lower bandwidth, faster transcription, lower costs
- **Audio sent**: Only speech segments + padding
- **Quality**: Higher (API focuses on actual content)

### When Fallback Used
- **Pros**: No speech lost
- **Audio sent**: Complete recording
- **Quality**: May be lower if lots of silence

## Summary

This feature provides instant visual feedback about:
1. ✅ Whether silence detection is working
2. ✅ Which mode was used (trimmed vs fallback)
3. ✅ Why file sizes differ between flows
4. ✅ If thresholds need adjustment

**Color coding**:
- 🟢 Green = Trimming worked (optimal)
- 🟠 Orange = Fallback mode (debug needed)

Check this indicator along with character counts and file sizes to fully understand each flow's behavior!
