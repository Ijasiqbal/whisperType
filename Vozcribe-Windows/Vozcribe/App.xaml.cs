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

    protected override async void OnStartup(StartupEventArgs e)
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

        var versionStatus = await _api.CheckVersionAsync();
        if (versionStatus.IsBlocked)
        {
            ShowVersionBlockedDialog(versionStatus);
            Shutdown();
            return;
        }

        _auth.TryRestoreSession();

        if (!_settings.Settings.HasCompletedOnboarding || !_auth.IsSignedIn)
            ShowOnboarding();
        else
            StartApp();
    }

    private static void ShowVersionBlockedDialog(VersionStatus status)
    {
        var message = status.Message
            ?? "This version of Vozcribe is no longer supported.";
        var downloadUrl = status.DownloadUrl ?? Constants.CheckForUpdateUrl;

        var result = ShowOwnedMessageBox(
            $"{message}\n\nClick OK to open the download page.",
            "Update Required",
            MessageBoxButton.OKCancel,
            MessageBoxImage.Warning);

        if (result == MessageBoxResult.OK)
            System.Diagnostics.Process.Start(
                new System.Diagnostics.ProcessStartInfo(downloadUrl)
                { UseShellExecute = true });
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
                if (firebase.TryGetProperty("GoogleClientSecret", out var secret))
                    Constants.GoogleClientSecret = secret.GetString() ?? "";
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
        _hotkey.ChangeHotkey(HotkeyOption.FromSettings(
            _settings.Settings.HotkeyId,
            _settings.Settings.CustomHotkeyModifiers,
            _settings.Settings.CustomHotkeyKey));

        _hotkey.HotkeyTriggered += () =>
            Dispatcher.Invoke(() => _orchestrator.ToggleRecording());
        _hotkey.ModelNextTriggered += () =>
            Dispatcher.Invoke(() => _orchestrator.CycleModelNext());
        _hotkey.ModelPreviousTriggered += () =>
            Dispatcher.Invoke(() => _orchestrator.CycleModelPrevious());
        _hotkey.EscapeTriggered += () =>
            Dispatcher.Invoke(() => _orchestrator.Cancel());
        _hotkey.Start();

        var overlayVm = new RecordingOverlayViewModel(_orchestrator, _settings);
        _overlay = new RecordingOverlay();
        _overlay.Initialize(overlayVm);

        var trayVm = new TrayPopupViewModel(_orchestrator, _auth, _api, _pending, _settings);
        _trayPopup = new TrayPopup { DataContext = trayVm };
        _trayPopup.SettingsRequested += ShowSettings;
        _trayPopup.ReportIssueRequested += ShowReportIssue;
        _trayPopup.CheckForUpdateRequested += () => _ = SafeCheckForUpdateAsync();
        _trayPopup.QuitRequested += () => Shutdown();

        var iconUri = new Uri(
            "pack://application:,,,/Resources/Icons/tray-icon.ico",
            UriKind.Absolute);
        using var iconStream = System.Windows.Application
            .GetResourceStream(iconUri)!.Stream;
        _trayIcon = new TaskbarIcon
        {
            ToolTipText = "Vozcribe",
            Icon = new System.Drawing.Icon(iconStream),
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

    private void ShowReportIssue()
    {
        var window = new ReportIssueWindow(_api, _auth);
        window.ShowDialog();
    }

    private async Task SafeCheckForUpdateAsync()
    {
        try
        {
            await CheckForUpdateAsync();
        }
        catch (Exception ex)
        {
            await Dispatcher.InvokeAsync(() => ShowOwnedMessageBox(
                $"Update check failed:\n{ex.Message}",
                "Vozcribe",
                MessageBoxButton.OK,
                MessageBoxImage.Warning));
        }
    }

    private async Task CheckForUpdateAsync()
    {
        var status = await _api.CheckVersionAsync();
        await Dispatcher.InvokeAsync(() =>
        {
            if (status.IsUpdateAvailable)
            {
                var msg = status.Message
                    ?? $"Version {status.LatestVersion} is available.";
                var downloadUrl = status.DownloadUrl
                    ?? Constants.CheckForUpdateUrl;
                var result = ShowOwnedMessageBox(
                    $"{msg}\n\nClick OK to download.",
                    "Update Available",
                    MessageBoxButton.OKCancel,
                    MessageBoxImage.Information);
                if (result == MessageBoxResult.OK)
                {
                    try
                    {
                        System.Diagnostics.Process.Start(
                            new System.Diagnostics.ProcessStartInfo(downloadUrl)
                            { UseShellExecute = true });
                    }
                    catch (Exception ex)
                    {
                        ShowOwnedMessageBox(
                            $"Couldn't open the download page:\n{ex.Message}",
                            "Vozcribe",
                            MessageBoxButton.OK,
                            MessageBoxImage.Warning);
                    }
                }
            }
            else if (status.IsBlocked)
            {
                ShowVersionBlockedDialog(status);
            }
            else
            {
                ShowOwnedMessageBox(
                    $"You're up to date! (v{Constants.AppVersion})",
                    "Vozcribe",
                    MessageBoxButton.OK,
                    MessageBoxImage.Information);
            }
        });
    }

    /// <summary>
    /// Shows a MessageBox with a transient invisible owner window so the
    /// dialog has a stable top-level parent. Without this, dialogs invoked
    /// from the tray popup can be torn down (or hidden behind other
    /// windows) the moment the popup loses focus and closes — making the
    /// dialog flash on screen and disappear before the user can read it.
    /// </summary>
    private static MessageBoxResult ShowOwnedMessageBox(
        string message,
        string title,
        MessageBoxButton buttons,
        MessageBoxImage image)
    {
        var owner = new Window
        {
            Width = 1,
            Height = 1,
            WindowStyle = WindowStyle.None,
            ShowInTaskbar = false,
            ShowActivated = false,
            Opacity = 0,
            Topmost = true,
            Left = -32000,
            Top = -32000,
            AllowsTransparency = true,
            Background = System.Windows.Media.Brushes.Transparent
        };
        try
        {
            owner.Show();
            return MessageBox.Show(owner, message, title, buttons, image);
        }
        finally
        {
            owner.Close();
        }
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
