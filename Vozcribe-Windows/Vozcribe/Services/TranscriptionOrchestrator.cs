using Vozcribe.Models;
using Vozcribe.ViewModels;

namespace Vozcribe.Services;

public class TranscriptionOrchestrator : ViewModelBase
{
    private readonly AudioService _audio;
    private readonly ApiClient _api;
    private readonly TextInsertionService _textInsertion;
    private readonly PendingTranscriptionService _pending;
    private readonly SettingsService _settings;
    private HotkeyService? _hotkeyService;

    private RecordingState _state = RecordingState.Idle;
    private ModelTier _currentTier;
    private float _currentAmplitude;
    private int _recordingDurationMs;
    private byte[]? _lastAudioData;
    private System.Timers.Timer? _durationTimer;

    public RecordingState State
    {
        get => _state;
        private set
        {
            SetField(ref _state, value);
            if (_hotkeyService != null)
                _hotkeyService.IsActive = value.Type == RecordingStateType.Recording
                    || value.Type == RecordingStateType.Processing;
        }
    }

    public void SetHotkeyService(HotkeyService hotkeyService) => _hotkeyService = hotkeyService;

    public ModelTier CurrentTier
    {
        get => _currentTier;
        set => SetField(ref _currentTier, value);
    }

    public float CurrentAmplitude
    {
        get => _currentAmplitude;
        private set => SetField(ref _currentAmplitude, value);
    }

    public int RecordingDurationMs
    {
        get => _recordingDurationMs;
        private set => SetField(ref _recordingDurationMs, value);
    }

    public TranscriptionOrchestrator(
        AudioService audio, ApiClient api, TextInsertionService textInsertion,
        PendingTranscriptionService pending, SettingsService settings)
    {
        _audio = audio;
        _api = api;
        _textInsertion = textInsertion;
        _pending = pending;
        _settings = settings;
        _currentTier = ModelTier.FromTierCode(settings.Settings.ModelTierCode);

        _audio.AmplitudeUpdated += amplitude =>
        {
            CurrentAmplitude = CurrentAmplitude * (1 - Constants.AmplitudeSmoothingFactor)
                + amplitude * Constants.AmplitudeSmoothingFactor;
        };
    }

    public void ToggleRecording()
    {
        if (State.Type == RecordingStateType.Recording)
            _ = StopAndTranscribeAsync().ContinueWith(t =>
            {
                if (t.Exception != null)
                    State = RecordingState.Failed($"Unexpected error: {t.Exception.InnerException?.Message}");
            }, TaskContinuationOptions.OnlyOnFaulted);
        else if (State.Type == RecordingStateType.Idle || State.Type == RecordingStateType.Success
            || State.Type == RecordingStateType.Error)
            StartRecording();
    }

    private void StartRecording()
    {
        _textInsertion.CaptureCurrentTextField();

        State = RecordingState.Recording;
        RecordingDurationMs = 0;
        CurrentAmplitude = 0;

        _audio.StartRecording();

        _durationTimer = new System.Timers.Timer(100);
        _durationTimer.Elapsed += (_, _) => RecordingDurationMs += 100;
        _durationTimer.Start();

        _ = _api.WarmupEndpointsAsync();
    }

    private async Task StopAndTranscribeAsync()
    {
        _durationTimer?.Stop();
        _durationTimer?.Dispose();

        var result = _audio.StopRecording();
        State = RecordingState.Processing;

        if (result.Samples.Length * 2 < Constants.MinAudioSizeBytes)
        {
            State = RecordingState.Failed("Recording too short");
            return;
        }

        var trimResult = SilenceDetector.TrimAudio(result.Samples, Constants.SampleRate);

        var opusData = OpusEncoderService.Encode(trimResult.Samples, Constants.SampleRate);
        _lastAudioData = opusData;

        if (opusData.Length < Constants.MinAudioSizeBytes)
        {
            State = RecordingState.Failed("Recording too short");
            return;
        }

        try
        {
            var transcription = await _api.TranscribeAsync(
                opusData, trimResult.SpeechDurationMs > 0 ? trimResult.SpeechDurationMs : result.DurationMs,
                CurrentTier);

            if (string.IsNullOrWhiteSpace(transcription.Text))
            {
                State = RecordingState.Failed("Empty transcription");
                return;
            }

            var insertionMode = _settings.Settings.InsertionMode == "where_started"
                ? InsertionMode.WhereStarted : InsertionMode.WhereCurrent;
            var insertResult = _textInsertion.InsertText(transcription.Text, insertionMode);

            State = insertResult switch
            {
                InsertionResult.DirectInsert => RecordingState.Inserted(transcription.Text),
                InsertionResult.ClipboardFallback => RecordingState.CopiedToClipboard(transcription.Text),
                _ => RecordingState.CopiedToClipboard(transcription.Text)
            };

            _lastAudioData = null;
        }
        catch (QuotaExceededException ex)
        {
            State = RecordingState.Failed(ex.Message);
        }
        catch (Exception ex)
        {
            State = RecordingState.Failed($"Transcription failed: {ex.Message}");
        }
    }

    public void SaveForLater()
    {
        if (_lastAudioData != null)
        {
            _pending.Save(_lastAudioData, CurrentTier.TierCode,
                RecordingDurationMs, State.Text ?? "Unknown error");
            _lastAudioData = null;
            State = RecordingState.Idle;
        }
    }

    public async Task RetryPendingAsync(PendingTranscription item)
    {
        var audioData = _pending.LoadAudioData(item);
        if (audioData == null) return;

        var tier = ModelTier.FromTierCode(item.TierCode);
        State = RecordingState.Processing;

        try
        {
            var transcription = await _api.TranscribeAsync(audioData, item.DurationMs, tier);

            if (!string.IsNullOrWhiteSpace(transcription.Text))
            {
                _pending.Delete(item);
                State = RecordingState.CopiedToClipboard(transcription.Text);
                System.Windows.Application.Current.Dispatcher.Invoke(() =>
                    System.Windows.Clipboard.SetText(transcription.Text));
            }
            else
            {
                State = RecordingState.Failed("Empty transcription");
            }
        }
        catch (Exception ex)
        {
            State = RecordingState.Failed(ex.Message);
        }
    }

    public void CycleModelNext()
    {
        CurrentTier = CurrentTier.Next();
        _settings.Settings.ModelTierCode = CurrentTier.TierCode;
        _settings.Save();
    }

    public void CycleModelPrevious()
    {
        CurrentTier = CurrentTier.Previous();
        _settings.Settings.ModelTierCode = CurrentTier.TierCode;
        _settings.Save();
    }
}
