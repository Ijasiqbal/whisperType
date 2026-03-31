using Vozcribe.Models;
using Vozcribe.Services;

namespace Vozcribe.ViewModels;

public class OnboardingViewModel : ViewModelBase
{
    private readonly AuthService _auth;
    private readonly SettingsService _settings;
    private int _currentStep;
    private bool _isSigningIn;
    private bool _micPermissionGranted;
    private string? _signInError;

    public int CurrentStep { get => _currentStep; set => SetField(ref _currentStep, value); }
    public bool IsSigningIn { get => _isSigningIn; set => SetField(ref _isSigningIn, value); }
    public bool IsNotSigningIn => !_isSigningIn;
    public bool MicPermissionGranted { get => _micPermissionGranted; set => SetField(ref _micPermissionGranted, value); }
    public string? SignInError { get => _signInError; set => SetField(ref _signInError, value); }
    public bool HasSignInError => !string.IsNullOrEmpty(_signInError);
    public bool IsSignedIn => _auth.IsSignedIn;
    public string? UserEmail => _auth.Email;
    public HotkeyOption[] HotkeyPresets => HotkeyOption.Presets;

    public OnboardingViewModel(AuthService auth, SettingsService settings)
    {
        _auth = auth;
        _settings = settings;
    }

    public async Task SignInAsync(string googleClientId, string firebaseApiKey)
    {
        IsSigningIn = true;
        SignInError = null;
        OnPropertyChanged(nameof(IsNotSigningIn));
        OnPropertyChanged(nameof(HasSignInError));

        try
        {
            var success = await _auth.SignInWithGoogleAsync(googleClientId, firebaseApiKey);
            if (!success)
                SignInError = "Sign-in was cancelled or failed. Please try again.";
            else
                OnPropertyChanged(nameof(IsSignedIn));
        }
        catch (Exception ex)
        {
            SignInError = $"Sign-in error: {ex.Message}";
        }
        finally
        {
            IsSigningIn = false;
            OnPropertyChanged(nameof(IsNotSigningIn));
            OnPropertyChanged(nameof(HasSignInError));
        }
    }

    public void CheckMicPermission()
    {
        try
        {
            var devices = NAudio.Wave.WaveIn.DeviceCount;
            MicPermissionGranted = devices > 0;
        }
        catch
        {
            MicPermissionGranted = false;
        }
    }

    public void SelectHotkey(HotkeyOption hotkey)
    {
        _settings.Settings.HotkeyId = hotkey.Id;
        _settings.Save();
    }

    public void CompleteOnboarding()
    {
        _settings.Settings.HasCompletedOnboarding = true;
        _settings.Save();
    }
}
