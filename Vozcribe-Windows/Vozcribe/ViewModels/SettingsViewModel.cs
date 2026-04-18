using System.Windows.Input;
using Vozcribe.Models;
using Vozcribe.Services;

namespace Vozcribe.ViewModels;

public class SettingsViewModel : ViewModelBase
{
    private readonly SettingsService _settings;
    private readonly HotkeyService _hotkeyService;
    private readonly AuthService _auth;
    private bool _isCapturingHotkey;
    private string _captureHotkeyText = "Record Custom Shortcut";

    public HotkeyOption[] HotkeyPresets => HotkeyOption.Presets;
    public ModelTier[] ModelTiers => ModelTier.All;

    public HotkeyOption SelectedHotkey
    {
        get => HotkeyOption.FromId(_settings.Settings.HotkeyId);
        set
        {
            _settings.Settings.HotkeyId = value.Id;
            _hotkeyService.ChangeHotkey(value);
            _settings.Save();
            OnPropertyChanged();
        }
    }

    public bool IsCapturingHotkey { get => _isCapturingHotkey; set => SetField(ref _isCapturingHotkey, value); }
    public string CaptureHotkeyText { get => _captureHotkeyText; set => SetField(ref _captureHotkeyText, value); }

    public bool IsWhereStarted
    {
        get => _settings.Settings.InsertionMode == "where_started";
        set { if (value) { _settings.Settings.InsertionMode = "where_started"; _settings.Save(); OnPropertyChanged(); OnPropertyChanged(nameof(IsWhereCurrent)); } }
    }

    public bool IsWhereCurrent
    {
        get => _settings.Settings.InsertionMode == "where_current";
        set { if (value) { _settings.Settings.InsertionMode = "where_current"; _settings.Save(); OnPropertyChanged(); OnPropertyChanged(nameof(IsWhereStarted)); } }
    }

    public ModelTier SelectedModelTier
    {
        get => ModelTier.FromTierCode(_settings.Settings.ModelTierCode);
        set { _settings.Settings.ModelTierCode = value.TierCode; _settings.Save(); OnPropertyChanged(); }
    }

    public bool LaunchAtStartup
    {
        get => _settings.Settings.LaunchAtStartup;
        set { _settings.Settings.LaunchAtStartup = value; StartupService.SetEnabled(value); _settings.Save(); OnPropertyChanged(); }
    }

    public string? UserEmail => _auth.Email;
    public bool IsSignedIn => _auth.IsSignedIn;

    public SettingsViewModel(SettingsService settings, HotkeyService hotkeyService, AuthService auth)
    {
        _settings = settings;
        _hotkeyService = hotkeyService;
        _auth = auth;
    }

    public void StartCapturingHotkey()
    {
        IsCapturingHotkey = true;
        CaptureHotkeyText = "Press a key combination...";
    }

    public void CaptureHotkey(ModifierKeys modifiers, Key key)
    {
        if (!IsCapturingHotkey) return;

        var custom = HotkeyOption.Custom(modifiers, key);
        _settings.Settings.HotkeyId = "custom";
        _settings.Settings.CustomHotkeyModifiers = (int)modifiers;
        _settings.Settings.CustomHotkeyKey = (int)key;
        _hotkeyService.ChangeHotkey(custom);
        _settings.Save();

        IsCapturingHotkey = false;
        CaptureHotkeyText = $"Custom: {custom.DisplayName}";
        OnPropertyChanged(nameof(SelectedHotkey));
    }

    public void ResetOverlayPosition()
    {
        _settings.Settings.OverlayX = null;
        _settings.Settings.OverlayY = null;
        _settings.Save();
    }

    public void SignOut()
    {
        _auth.SignOut();
        OnPropertyChanged(nameof(IsSignedIn));
        OnPropertyChanged(nameof(UserEmail));
    }
}
