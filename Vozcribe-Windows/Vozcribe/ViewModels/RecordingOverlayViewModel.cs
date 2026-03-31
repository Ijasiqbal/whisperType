using System.Windows;
using System.Windows.Threading;
using Vozcribe.Models;
using Vozcribe.Services;

namespace Vozcribe.ViewModels;

public class RecordingOverlayViewModel : ViewModelBase
{
    private readonly TranscriptionOrchestrator _orchestrator;
    private readonly SettingsService _settings;
    private DispatcherTimer? _autoDismissTimer;
    private bool _isVisible;

    public bool IsVisible
    {
        get => _isVisible;
        set => SetField(ref _isVisible, value);
    }

    public TranscriptionOrchestrator Orchestrator => _orchestrator;

    public bool IsRecording => _orchestrator.State.Type == RecordingStateType.Recording;
    public bool IsProcessing => _orchestrator.State.Type == RecordingStateType.Processing;
    public bool IsSuccess => _orchestrator.State.Type == RecordingStateType.Success;
    public bool IsError => _orchestrator.State.Type == RecordingStateType.Error;
    public bool ShowCopyButton => IsSuccess && !_orchestrator.State.WasDirectInsert;

    public string DurationText
    {
        get
        {
            var ms = _orchestrator.RecordingDurationMs;
            var sec = ms / 1000;
            var tenths = (ms % 1000) / 100;
            return $"{sec}.{tenths}s";
        }
    }

    public double[] AmplitudeBars { get; } = new double[8];

    public RecordingOverlayViewModel(TranscriptionOrchestrator orchestrator, SettingsService settings)
    {
        _orchestrator = orchestrator;
        _settings = settings;

        _orchestrator.PropertyChanged += (_, e) =>
        {
            switch (e.PropertyName)
            {
                case nameof(TranscriptionOrchestrator.State):
                    OnPropertyChanged(nameof(IsRecording));
                    OnPropertyChanged(nameof(IsProcessing));
                    OnPropertyChanged(nameof(IsSuccess));
                    OnPropertyChanged(nameof(IsError));
                    OnPropertyChanged(nameof(ShowCopyButton));
                    HandleStateChange();
                    break;
                case nameof(TranscriptionOrchestrator.RecordingDurationMs):
                    OnPropertyChanged(nameof(DurationText));
                    break;
                case nameof(TranscriptionOrchestrator.CurrentAmplitude):
                    UpdateAmplitudeBars();
                    break;
                case nameof(TranscriptionOrchestrator.CurrentTier):
                    OnPropertyChanged(nameof(Orchestrator));
                    break;
            }
        };
    }

    private void HandleStateChange()
    {
        var state = _orchestrator.State;

        switch (state.Type)
        {
            case RecordingStateType.Recording:
            case RecordingStateType.Processing:
            case RecordingStateType.Error:
                IsVisible = true;
                CancelAutoDismiss();
                break;

            case RecordingStateType.Success:
                IsVisible = true;
                int delay = state.WasDirectInsert
                    ? Constants.DirectInsertDismissMs
                    : Constants.ClipboardFallbackDismissMs;
                StartAutoDismiss(delay);
                break;

            case RecordingStateType.Idle:
                IsVisible = false;
                CancelAutoDismiss();
                break;
        }
    }

    private void StartAutoDismiss(int delayMs)
    {
        CancelAutoDismiss();
        _autoDismissTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(delayMs) };
        _autoDismissTimer.Tick += (_, _) =>
        {
            _autoDismissTimer.Stop();
            IsVisible = false;
        };
        _autoDismissTimer.Start();
    }

    private void CancelAutoDismiss()
    {
        _autoDismissTimer?.Stop();
        _autoDismissTimer = null;
    }

    private void UpdateAmplitudeBars()
    {
        var amp = _orchestrator.CurrentAmplitude;
        var random = new Random();
        for (int i = 0; i < 8; i++)
        {
            double variation = 0.7 + random.NextDouble() * 0.6;
            AmplitudeBars[i] = Math.Max(0.15, Math.Min(1.0, amp * variation));
        }
        OnPropertyChanged(nameof(AmplitudeBars));
    }

    public void SaveForLater() => _orchestrator.SaveForLater();
    public void Dismiss() => IsVisible = false;

    public (double x, double y) GetSavedPosition()
    {
        var s = _settings.Settings;
        if (s.OverlayX.HasValue && s.OverlayY.HasValue)
            return (s.OverlayX.Value, s.OverlayY.Value);
        var screen = SystemParameters.WorkArea;
        return (screen.Right - 320, screen.Bottom - 120);
    }

    public void SavePosition(double x, double y)
    {
        _settings.Settings.OverlayX = x;
        _settings.Settings.OverlayY = y;
        _settings.Save();
    }
}
