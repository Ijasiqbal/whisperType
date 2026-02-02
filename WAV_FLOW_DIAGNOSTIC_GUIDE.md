# WAV Flow Quality Diagnostic Guide

## Changes Made (Step 2)

After your report that WAV flow still shows poor output, I've made two key improvements:

### 1. **Added Comprehensive Logging** ✅
The app now logs detailed information when transcriptions complete:
- Full transcription text
- Character count
- Word count  
- API timing

**Where to check**: In Android Studio Logcat, filter for `FlowTestActivity` and look for:
```
[WAV (ARAMUS)] Transcription SUCCESS
[WAV (ARAMUS)] Full text (XXX chars): [full transcription text]
[WAV (ARAMUS)] Word count: XX
[OGG (PARALLEL_OPUS)] Transcription SUCCESS
[OGG (PARALLEL_OPUS)] Full text (XXX chars): [full transcription text]
[OGG (PARALLEL_OPUS)] Word count: XX
```

### 2. **Improved UI Display** ✅
- **Dual Flow Card**: Increased from 2 to **6 lines** of text
- **Individual Flow Cards**: Increased from 4 to **8 lines** of text
- **Added character counts** to see if text is being truncated
- Slightly larger font (11sp instead of 10sp) for better readability

## How to Diagnose the Issue

### Step 1: Run the Dual Flow Test
1. Open the Flow Test Activity in your app
2. Record the same speech sample
3. Wait for both WAV and OGG to complete

### Step 2: Check the Logcat
Open Android Studio Logcat and search for `FlowTestActivity`. You should see logs like this:

```
D/FlowTestActivity: [WAV (ARAMUS)] Transcription SUCCESS
D/FlowTestActivity: [WAV (ARAMUS)] Full text (142 chars): This is an example transcription that shows the full text received from the API
D/FlowTestActivity: [WAV (ARAMUS)] Word count: 18
D/FlowTestActivity: [WAV (ARAMUS)] Timing - API: 1234ms, Total: 1456ms

D/FlowTestActivity: [OGG (PARALLEL_OPUS)] Transcription SUCCESS
D/FlowTestActivity: [OGG (PARALLEL_OPUS)] Full text (156 chars): This is an example transcription that shows the full text received from the API with maybe slightly different wording
D/FlowTestActivity: [OGG (PARALLEL_OPUS)] Word count: 21
D/FlowTestActivity: [OGG (PARALLEL_OPUS)] Timing - API: 1189ms, Total: 1312ms
```

### Step 3: Compare the Results

#### Scenario A: Character counts are similar (within 10-20%)
**Diagnosis**: ✅ **Transcriptions are both good quality**
- The UI was just truncating the display
- Both flows are working correctly
- Any small differences are normal API variance

#### Scenario B: WAV has significantly fewer characters (50% or less)
**Diagnosis**: ⚠️ **WAV is still being over-trimmed**
- Check `DualFlowRecorder` logs for speech segments
- Look for: `WAV RMS thread finished, detected X segments`
- Compare with: `OGG RMS thread finished, detected Y segments`
- If WAV segments << OGG segments, the dB threshold needs adjustment

#### Scenario C: WAV character count is 0 or very low (< 10 chars)
**Diagnosis**: 🔴 **Critical issue - audio not being captured**
- Check for errors in DualFlowRecorder
- Verify WAV file size is reasonable
- Check if speech detection is triggering at all

## Additional Debug Info to Check

### In Logcat, also look for:
```
D/DualFlowRecorder: Dual flow recording started
D/DualFlowRecorder: Recording thread finished, total samples: XXXXX
D/DualFlowRecorder: WAV RMS thread finished, detected X segments
D/DualFlowRecorder: OGG RMS thread finished, detected Y segments
D/DualFlowRecorder: OGG encoder thread finished, encoded XXX frames
D/DualFlowRecorder: Dual recording stopped: WAV=XXXXX bytes, OGG=YYYYY bytes
```

### Compare:
- **WAV bytes** should be around 32,000 bytes per second (16kHz * 2 bytes)
- **OGG bytes** should be around 3,000 bytes per second (24kbps)
- **Segment counts** should be similar (±1-2 segments difference is normal)

## What to Report Back

Please run a test and provide:
1. **Character counts** from both WAV and OGG (shown in UI or Logcat)
2. **Word counts** from Logcat
3. **Speech segment counts** from DualFlowRecorder logs
4. **Full transcription texts** if they're different

This will tell us exactly what's happening:
- UI truncation issue (now fixed)
- Still a trimming issue (needs threshold adjustment)
- Something else entirely

## Quick Test Script

Say this during recording to make comparison easy:
> "Testing one two three. The quick brown fox jumps over the lazy dog. This is a dual flow recording test comparing WAV and OGG formats."

Expected results:
- **~160 characters**
- **~25 words**
- Should be identical or nearly identical between WAV and OGG
