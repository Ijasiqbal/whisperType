using Vozcribe.Models;

namespace Vozcribe.Services;

public record SpeechSegment(int StartIndex, int EndIndex);

public record TrimResult(short[] Samples, bool WasTrimmed, int SpeechSegmentCount,
    int OriginalDurationMs, int SpeechDurationMs);

public static class SilenceDetector
{
    private const int ChunkSizeSamples = 1600; // 100ms at 16kHz

    public static float CalculateRmsDb(ReadOnlySpan<short> samples)
    {
        if (samples.Length == 0) return -100f;

        double sumSquares = 0;
        for (int i = 0; i < samples.Length; i++)
            sumSquares += (double)samples[i] * samples[i];

        double rms = Math.Sqrt(sumSquares / samples.Length);
        if (rms < 1) return -100f;
        return (float)(20 * Math.Log10(rms / short.MaxValue));
    }

    public static List<SpeechSegment> DetectSpeechSegments(short[] samples, int sampleRate)
    {
        var segments = new List<SpeechSegment>();
        int minSilenceSamples = sampleRate * Constants.MinSilenceDurationMs / 1000;
        bool inSpeech = false;
        int speechStart = 0;
        int silenceCount = 0;

        for (int offset = 0; offset < samples.Length; offset += ChunkSizeSamples)
        {
            int length = Math.Min(ChunkSizeSamples, samples.Length - offset);
            var chunk = new ReadOnlySpan<short>(samples, offset, length);
            float db = CalculateRmsDb(chunk);
            bool isSpeech = db > Constants.SilenceThresholdDb;

            if (isSpeech)
            {
                if (!inSpeech)
                {
                    if (segments.Count > 0)
                    {
                        var last = segments[^1];
                        int gap = offset - last.EndIndex;
                        if (gap < minSilenceSamples)
                        {
                            segments.RemoveAt(segments.Count - 1);
                            speechStart = last.StartIndex;
                        }
                        else
                        {
                            speechStart = offset;
                        }
                    }
                    else
                    {
                        speechStart = offset;
                    }
                    inSpeech = true;
                }
                silenceCount = 0;
            }
            else if (inSpeech)
            {
                silenceCount += length;
                if (silenceCount >= minSilenceSamples)
                {
                    segments.Add(new SpeechSegment(speechStart, offset - silenceCount + length));
                    inSpeech = false;
                    silenceCount = 0;
                }
            }
        }

        if (inSpeech)
            segments.Add(new SpeechSegment(speechStart, samples.Length));

        return segments;
    }

    public static TrimResult TrimAudio(short[] samples, int sampleRate)
    {
        int originalDurationMs = samples.Length * 1000 / sampleRate;
        var segments = DetectSpeechSegments(samples, sampleRate);

        if (segments.Count == 0)
            return new TrimResult(samples, false, 0, originalDurationMs, 0);

        int bufferBefore = sampleRate * Constants.AudioBufferBeforeMs / 1000;
        int bufferAfter = sampleRate * Constants.AudioBufferAfterMs / 1000;

        var padded = segments.Select(s => new SpeechSegment(
            Math.Max(0, s.StartIndex - bufferBefore),
            Math.Min(samples.Length, s.EndIndex + bufferAfter)
        )).ToList();

        var merged = new List<SpeechSegment> { padded[0] };
        for (int i = 1; i < padded.Count; i++)
        {
            var last = merged[^1];
            if (padded[i].StartIndex <= last.EndIndex)
                merged[^1] = new SpeechSegment(last.StartIndex, Math.Max(last.EndIndex, padded[i].EndIndex));
            else
                merged.Add(padded[i]);
        }

        int trimmedLength = merged.Sum(s => s.EndIndex - s.StartIndex);
        int savingsPercent = (int)(((double)(samples.Length - trimmedLength) / samples.Length) * 100);

        if (savingsPercent < Constants.MinSavingsPercent || trimmedLength * 2 < Constants.MinAudioSizeBytes)
            return new TrimResult(samples, false, segments.Count, originalDurationMs, originalDurationMs);

        var trimmed = new short[trimmedLength];
        int pos = 0;
        foreach (var seg in merged)
        {
            int len = seg.EndIndex - seg.StartIndex;
            Array.Copy(samples, seg.StartIndex, trimmed, pos, len);
            pos += len;
        }

        int speechDurationMs = trimmedLength * 1000 / sampleRate;
        return new TrimResult(trimmed, true, segments.Count, originalDurationMs, speechDurationMs);
    }
}
