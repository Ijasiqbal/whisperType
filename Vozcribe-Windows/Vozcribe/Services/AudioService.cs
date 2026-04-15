using System.Collections.Concurrent;
using NAudio.CoreAudioApi;
using NAudio.Wave;
using Vozcribe.Models;

namespace Vozcribe.Services;

public class AudioService : IDisposable
{
    private WasapiCapture? _capture;
    private WaveFormat? _captureFormat;
    private readonly ConcurrentQueue<short[]> _chunks = new();
    private bool _isRecording;

    public event Action<float>? AmplitudeUpdated;
    public bool IsRecording => _isRecording;

    public void StartRecording()
    {
        while (_chunks.TryDequeue(out _)) { }

        var targetFormat = new WaveFormat(Constants.SampleRate, Constants.BitsPerSample, Constants.Channels);
        _capture = new WasapiCapture
        {
            WaveFormat = targetFormat
        };

        _captureFormat = _capture.WaveFormat;
        _capture.DataAvailable += OnDataAvailable;
        _capture.RecordingStopped += OnRecordingStopped;
        _capture.StartRecording();
        _isRecording = true;
    }

    public AudioCaptureResult StopRecording()
    {
        if (_capture == null)
            return new AudioCaptureResult([], 0);

        _isRecording = false;
        _capture.StopRecording();

        var allSamples = _chunks.SelectMany(c => c).ToArray();
        int durationMs = allSamples.Length * 1000 / Constants.SampleRate;

        return new AudioCaptureResult(allSamples, durationMs);
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs e)
    {
        if (e.BytesRecorded == 0) return;

        int sampleCount = e.BytesRecorded / 2;
        var samples = new short[sampleCount];
        Buffer.BlockCopy(e.Buffer, 0, samples, 0, e.BytesRecorded);

        _chunks.Enqueue(samples);

        float rmsDb = SilenceDetector.CalculateRmsDb(samples);
        float rmsLinear = (float)Math.Pow(10, rmsDb / 20) * short.MaxValue;
        float normalized = Math.Min(rmsLinear / Constants.MaxAmplitude, 1f);
        AmplitudeUpdated?.Invoke(normalized);
    }

    private void OnRecordingStopped(object? sender, StoppedEventArgs e) { }

    public void Dispose()
    {
        _capture?.Dispose();
        _capture = null;
    }
}

public record AudioCaptureResult(short[] Samples, int DurationMs);
