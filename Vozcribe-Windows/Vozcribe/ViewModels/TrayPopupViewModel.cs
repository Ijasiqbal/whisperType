using Vozcribe.Models;
using Vozcribe.Services;

namespace Vozcribe.ViewModels;

public class TrayPopupViewModel : ViewModelBase
{
    private readonly TranscriptionOrchestrator _orchestrator;
    private readonly AuthService _auth;
    private readonly ApiClient _api;
    private readonly PendingTranscriptionService _pendingService;
    private readonly SettingsService _settings;

    private int _creditsRemaining;
    private int _creditsLimit = Constants.DefaultFreeCredits;
    private string _planDisplay = "Free Trial";
    private List<PendingTranscription> _pendingItems = [];
    private bool _isLoading;

    public TranscriptionOrchestrator Orchestrator => _orchestrator;

    public string StatusText => _orchestrator.State.Type switch
    {
        RecordingStateType.Idle => "Ready",
        RecordingStateType.Recording => "Recording...",
        RecordingStateType.Processing => "Processing...",
        RecordingStateType.Success => "Done",
        RecordingStateType.Error => "Error",
        _ => "Ready"
    };

    public ModelTier CurrentTier => _orchestrator.CurrentTier;
    public ModelTier[] AllTiers => ModelTier.All;

    public int CreditsRemaining { get => _creditsRemaining; set => SetField(ref _creditsRemaining, value); }
    public int CreditsLimit { get => _creditsLimit; set => SetField(ref _creditsLimit, value); }
    public string CreditsDisplay => $"{CreditsRemaining} / {CreditsLimit}";
    public double CreditsProgress => CreditsLimit > 0 ? (double)CreditsRemaining / CreditsLimit : 0;
    public string PlanDisplay { get => _planDisplay; set => SetField(ref _planDisplay, value); }

    public string HotkeyDisplay
    {
        get
        {
            var id = _settings.Settings.HotkeyId;
            return HotkeyOption.FromId(id).DisplayName;
        }
    }

    public List<PendingTranscription> PendingItems { get => _pendingItems; set => SetField(ref _pendingItems, value); }
    public int PendingCount => PendingItems.Count;
    public bool HasPending => PendingItems.Count > 0;
    public bool IsLoading { get => _isLoading; set => SetField(ref _isLoading, value); }

    public TrayPopupViewModel(TranscriptionOrchestrator orchestrator, AuthService auth,
        ApiClient api, PendingTranscriptionService pendingService, SettingsService settings)
    {
        _orchestrator = orchestrator;
        _auth = auth;
        _api = api;
        _pendingService = pendingService;
        _settings = settings;

        _orchestrator.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(TranscriptionOrchestrator.State))
                OnPropertyChanged(nameof(StatusText));
            if (e.PropertyName == nameof(TranscriptionOrchestrator.CurrentTier))
                OnPropertyChanged(nameof(CurrentTier));
        };
    }

    public void SetModelTier(ModelTier tier)
    {
        _orchestrator.CurrentTier = tier;
        _settings.Settings.ModelTierCode = tier.TierCode;
        _settings.Save();
        OnPropertyChanged(nameof(CurrentTier));
    }

    public async Task RefreshStatusAsync()
    {
        IsLoading = true;
        try
        {
            var status = await _api.GetTrialStatusAsync();
            if (status.UserPlan == UserPlan.Pro)
            {
                CreditsRemaining = status.ProCreditsRemaining;
                CreditsLimit = status.ProCreditsLimit > 0 ? status.ProCreditsLimit : Constants.DefaultFreeCredits;
                PlanDisplay = "Pro";
            }
            else
            {
                CreditsRemaining = status.FreeCreditsRemaining;
                CreditsLimit = status.FreeTierCredits > 0 ? status.FreeTierCredits : Constants.DefaultFreeCredits;
                PlanDisplay = "Free Trial";
            }
            OnPropertyChanged(nameof(CreditsDisplay));
            OnPropertyChanged(nameof(CreditsProgress));
        }
        catch { }
        finally { IsLoading = false; }

        PendingItems = _pendingService.LoadAll();
        OnPropertyChanged(nameof(PendingCount));
        OnPropertyChanged(nameof(HasPending));
    }

    public async Task RetryPendingAsync(PendingTranscription item)
    {
        await _orchestrator.RetryPendingAsync(item);
        PendingItems = _pendingService.LoadAll();
        OnPropertyChanged(nameof(PendingCount));
        OnPropertyChanged(nameof(HasPending));
    }
}
