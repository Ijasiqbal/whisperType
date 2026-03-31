using System;
using System.IO;
using System.Text.Json;
using System.Threading;
using System.Windows;
using Hardcodet.Wpf.TaskbarNotification;
using Vozcribe.Models;
using Vozcribe.Services;
using Vozcribe.ViewModels;
using Vozcribe.Views;

namespace Vozcribe;

public partial class App : Application
{
    private const string MutexName = "Vozcribe_SingleInstance";
    private Mutex? _mutex;
    private TaskbarIcon? _trayIcon;

    private SettingsService _settings = null!;
    private AuthService _auth = null!;
    private ApiClient _api = null!;
    private AudioService _audio = null!;
    private HotkeyService _hotkey = null!;
    private TextInsertionService _textInsertion = null!;
    private PendingTranscriptionService _pending = null!;
    private TranscriptionOrchestrator _orchestrator = null!;

    private RecordingOverlay _overlay = null!;
    private TrayPopup _trayPopup = null!;

    protected override void OnStartup(StartupEventArgs e)
    {
        _mutex = new Mutex(true, MutexName, out bool isNewInstance);
        if (!isNewInstance)
        {
            MessageBox.Show("Vozcribe is already running.", "Vozcribe",
                MessageBoxButton.OK, MessageBoxImage.Information);
            Shutdown();
            return;
        }

        base.OnStartup(e);
        LoadConfig();
        InitializeServices();

        _auth.TryRestoreSession();

        if (!_settings.Settings.HasCompletedOnboarding || !_auth.IsSignedIn)
            ShowOnboarding();
        else
            StartApp();
    }

    private void LoadConfig()
    {
        var configPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "appsettings.json");
        if (!File.Exists(configPath)) return;

        try
        {
            var configJson = File.ReadAllText(configPath);
            var config = JsonSerializer.Deserialize<JsonElement>(configJson);
            if (config.TryGetProperty("Firebase", out var firebase))
            {
                Constants.FirebaseApiKey = firebase.GetProperty("ApiKey").GetString() ?? "";
                Constants.GoogleClientId = firebase.GetProperty("GoogleClientId").GetString() ?? "";
            }
        }
        catch { }
    }

    private void InitializeServices()
    {
        _settings = new SettingsService();
        _auth = new AuthService();
        _api = new ApiClient(_settings);
        _api.SetTokenProvider(() => _auth.GetIdTokenAsync());
        _audio = new AudioService();
        _textInsertion = new TextInsertionService();
        _pending = new PendingTranscriptionService();

        var hotkeyOption = HotkeyOption.FromSettings(
            _settings.Settings.HotkeyId,
            _settings.Settings.CustomHotkeyModifiers,
            _settings.Settings.CustomHotkeyKey);
        _hotkey = new HotkeyService(hotkeyOption);

        _orchestrator = new TranscriptionOrchestrator(
            _audio, _api, _textInsertion, _pending, _settings);
        _orchestrator.SetHotkeyService(_hotkey);
    }

    private void ShowOnboarding()
    {
        var vm = new OnboardingViewModel(_auth, _settings);
        var onboarding = new OnboardingWindow(vm);
        onboarding.Closed += (_, _) =>
        {
            if (onboarding.Completed)
                StartApp();
            else
                Shutdown();
        };
        onboarding.Show();
    }

    private void StartApp()
    {
        _hotkey.HotkeyTriggered += () =>
            Dispatcher.Invoke(() => _orchestrator.ToggleRecording());
        _hotkey.ModelNextTriggered += () =>
            Dispatcher.Invoke(() => _orchestrator.CycleModelNext());
        _hotkey.ModelPreviousTriggered += () =>
            Dispatcher.Invoke(() => _orchestrator.CycleModelPrevious());
        _hotkey.Start();

        var overlayVm = new RecordingOverlayViewModel(_orchestrator, _settings);
        _overlay = new RecordingOverlay();
        _overlay.Initialize(overlayVm);

        var trayVm = new TrayPopupViewModel(_orchestrator, _auth, _api, _pending, _settings);
        _trayPopup = new TrayPopup { DataContext = trayVm };
        _trayPopup.SettingsRequested += ShowSettings;
        _trayPopup.QuitRequested += () => Shutdown();

        _trayIcon = new TaskbarIcon
        {
            ToolTipText = "Vozcribe",
            TrayPopup = _trayPopup
        };

        _trayIcon.TrayPopupOpen += async (_, _) => await trayVm.RefreshStatusAsync();
    }

    private void ShowSettings()
    {
        var vm = new SettingsViewModel(_settings, _hotkey, _auth);
        var window = new SettingsWindow(vm);
        window.ShowDialog();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _hotkey?.Dispose();
        _audio?.Dispose();
        _trayIcon?.Dispose();
        _mutex?.ReleaseMutex();
        _mutex?.Dispose();
        base.OnExit(e);
    }
}
