# Vozcribe Windows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Windows system tray app (WPF / .NET 8) that records audio, sends it to the existing Firebase backend for transcription, and inserts the result into the user's text field.

**Architecture:** MVVM with service layer. Services are singletons handling audio, hotkeys, text insertion, API calls, and auth. Views are WPF windows/popups. Communication between services and views via INotifyPropertyChanged and events. Single-instance tray app with no main window.

**Tech Stack:** C# / WPF / .NET 8 LTS, NAudio (audio capture), Concentus (Opus encoding), Hardcodet.NotifyIcon.Wpf (tray icon), System.Text.Json, UIAutomationClient (.NET built-in)

**Spec:** `docs/superpowers/specs/2026-03-28-voxtype-windows-design.md`

---

## File Structure

```
Vozcribe-Windows/
├── Vozcribe.sln
├── Vozcribe/
│   ├── Vozcribe.csproj
│   ├── App.xaml
│   ├── App.xaml.cs
│   ├── Models/
│   │   ├── RecordingState.cs
│   │   ├── ModelTier.cs
│   │   ├── InsertionMode.cs
│   │   ├── TranscriptionResult.cs
│   │   ├── TrialStatus.cs
│   │   ├── UserPlan.cs
│   │   ├── PendingTranscription.cs
│   │   ├── AppSettings.cs
│   │   └── HotkeyOption.cs
│   ├── Services/
│   │   ├── AudioService.cs
│   │   ├── SilenceDetector.cs
│   │   ├── OpusEncoder.cs
│   │   ├── HotkeyService.cs
│   │   ├── TextInsertionService.cs
│   │   ├── TranscriptionOrchestrator.cs
│   │   ├── ApiClient.cs
│   │   ├── AuthService.cs
│   │   ├── SettingsService.cs
│   │   ├── PendingTranscriptionService.cs
│   │   └── StartupService.cs
│   ├── ViewModels/
│   │   ├── ViewModelBase.cs
│   │   ├── TrayPopupViewModel.cs
│   │   ├── RecordingOverlayViewModel.cs
│   │   ├── SettingsViewModel.cs
│   │   └── OnboardingViewModel.cs
│   ├── Views/
│   │   ├── TrayPopup.xaml / .xaml.cs
│   │   ├── RecordingOverlay.xaml / .xaml.cs
│   │   ├── SettingsWindow.xaml / .xaml.cs
│   │   └── OnboardingWindow.xaml / .xaml.cs
│   ├── Converters/
│   │   ├── BoolToVisibilityConverter.cs
│   │   └── RecordingStateConverters.cs
│   ├── Resources/
│   │   ├── Styles.xaml
│   │   └── Icons/
│   │       ├── tray-icon.ico
│   │       └── tray-icon-recording.ico
│   └── Utilities/
│       └── Win32Interop.cs
├── Vozcribe.Tests/
│   ├── Vozcribe.Tests.csproj
│   ├── Services/
│   │   ├── SilenceDetectorTests.cs
│   │   ├── OpusEncoderTests.cs
│   │   ├── ApiClientTests.cs
│   │   ├── SettingsServiceTests.cs
│   │   ├── PendingTranscriptionServiceTests.cs
│   │   └── AuthServiceTests.cs
│   └── Models/
│       └── ModelTierTests.cs
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `Vozcribe-Windows/Vozcribe.sln`
- Create: `Vozcribe-Windows/Vozcribe/Vozcribe.csproj`
- Create: `Vozcribe-Windows/Vozcribe/App.xaml`
- Create: `Vozcribe-Windows/Vozcribe/App.xaml.cs`
- Create: `Vozcribe-Windows/Vozcribe.Tests/Vozcribe.Tests.csproj`

- [ ] **Step 1: Create solution and projects**

```bash
cd /Users/ijas/Documents/whisperType
mkdir -p Vozcribe-Windows
cd Vozcribe-Windows
dotnet new sln -n Vozcribe
dotnet new wpf -n Vozcribe --framework net8.0
dotnet new xunit -n Vozcribe.Tests --framework net8.0
dotnet sln add Vozcribe/Vozcribe.csproj
dotnet sln add Vozcribe.Tests/Vozcribe.Tests.csproj
dotnet add Vozcribe.Tests reference Vozcribe/Vozcribe.csproj
```

- [ ] **Step 2: Add NuGet dependencies to Vozcribe.csproj**

Edit `Vozcribe-Windows/Vozcribe/Vozcribe.csproj` to add inside `<Project>`:

```xml
<PropertyGroup>
    <OutputType>WinExe</OutputType>
    <TargetFramework>net8.0-windows</TargetFramework>
    <Nullable>enable</Nullable>
    <UseWPF>true</UseWPF>
    <ApplicationIcon>Resources\Icons\tray-icon.ico</ApplicationIcon>
</PropertyGroup>

<ItemGroup>
    <PackageReference Include="NAudio" Version="2.2.1" />
    <PackageReference Include="Concentus" Version="2.2.0" />
    <PackageReference Include="Concentus.OggFile" Version="1.0.4" />
    <PackageReference Include="Hardcodet.NotifyIcon.Wpf" Version="1.1.0" />
</ItemGroup>
```

- [ ] **Step 3: Add test dependencies to Vozcribe.Tests.csproj**

Edit `Vozcribe-Windows/Vozcribe.Tests/Vozcribe.Tests.csproj`:

```xml
<ItemGroup>
    <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.9.0" />
    <PackageReference Include="xunit" Version="2.7.0" />
    <PackageReference Include="xunit.runner.visualstudio" Version="2.5.7" />
    <PackageReference Include="NSubstitute" Version="5.1.0" />
</ItemGroup>
```

- [ ] **Step 4: Create directory structure**

```bash
cd Vozcribe-Windows/Vozcribe
mkdir -p Models Services ViewModels Views Converters Resources/Icons Utilities
cd ../Vozcribe.Tests
mkdir -p Services Models
```

- [ ] **Step 5: Set up single-instance App.xaml.cs with tray icon**

Replace `Vozcribe-Windows/Vozcribe/App.xaml` with:

```xml
<Application x:Class="Vozcribe.App"
             xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
             xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
             ShutdownMode="OnExplicitShutdown">
    <Application.Resources>
        <ResourceDictionary>
            <ResourceDictionary.MergedDictionaries>
                <ResourceDictionary Source="Resources/Styles.xaml" />
            </ResourceDictionary.MergedDictionaries>
        </ResourceDictionary>
    </Application.Resources>
</Application>
```

Replace `Vozcribe-Windows/Vozcribe/App.xaml.cs` with:

```csharp
using System;
using System.Threading;
using System.Windows;

namespace Vozcribe;

public partial class App : Application
{
    private const string MutexName = "Vozcribe_SingleInstance";
    private Mutex? _mutex;

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
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _mutex?.ReleaseMutex();
        _mutex?.Dispose();
        base.OnExit(e);
    }
}
```

- [ ] **Step 6: Create empty Styles.xaml resource dictionary**

Create `Vozcribe-Windows/Vozcribe/Resources/Styles.xaml`:

```xml
<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
    <!-- Global styles will be added as UI components are built -->
</ResourceDictionary>
```

- [ ] **Step 7: Build and verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded. 0 errors.

- [ ] **Step 8: Run tests to verify test project works**

```bash
dotnet test
```

Expected: All default tests pass.

- [ ] **Step 9: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: scaffold Vozcribe Windows project with WPF, NAudio, Concentus, tray icon dependencies"
```

---

### Task 2: Models and Constants

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Models/RecordingState.cs`
- Create: `Vozcribe-Windows/Vozcribe/Models/ModelTier.cs`
- Create: `Vozcribe-Windows/Vozcribe/Models/InsertionMode.cs`
- Create: `Vozcribe-Windows/Vozcribe/Models/TranscriptionResult.cs`
- Create: `Vozcribe-Windows/Vozcribe/Models/TrialStatus.cs`
- Create: `Vozcribe-Windows/Vozcribe/Models/UserPlan.cs`
- Create: `Vozcribe-Windows/Vozcribe/Models/PendingTranscription.cs`
- Create: `Vozcribe-Windows/Vozcribe/Models/AppSettings.cs`
- Create: `Vozcribe-Windows/Vozcribe/Models/HotkeyOption.cs`
- Create: `Vozcribe-Windows/Vozcribe/Models/Constants.cs`
- Test: `Vozcribe-Windows/Vozcribe.Tests/Models/ModelTierTests.cs`

- [ ] **Step 1: Write ModelTier tests**

Create `Vozcribe-Windows/Vozcribe.Tests/Models/ModelTierTests.cs`:

```csharp
using Vozcribe.Models;

namespace Vozcribe.Tests.Models;

public class ModelTierTests
{
    [Fact]
    public void Auto_HasCorrectProperties()
    {
        var tier = ModelTier.Auto;
        Assert.Equal("auto", tier.TierCode);
        Assert.Equal("Auto (Free)", tier.DisplayName);
        Assert.Equal("/transcribeAuto", tier.Endpoint);
        Assert.True(tier.IsFree);
        Assert.False(tier.IsTwoStage);
    }

    [Fact]
    public void Standard_HasCorrectProperties()
    {
        var tier = ModelTier.Standard;
        Assert.Equal("standard", tier.TierCode);
        Assert.Equal("Standard (1x)", tier.DisplayName);
        Assert.Equal("/transcribeStandard", tier.Endpoint);
        Assert.False(tier.IsFree);
        Assert.True(tier.IsTwoStage);
    }

    [Fact]
    public void Premium_HasCorrectProperties()
    {
        var tier = ModelTier.Premium;
        Assert.Equal("premium", tier.TierCode);
        Assert.Equal("Premium (2x)", tier.DisplayName);
        Assert.Equal("/transcribePremium", tier.Endpoint);
        Assert.False(tier.IsFree);
        Assert.False(tier.IsTwoStage);
    }

    [Fact]
    public void All_ReturnsThreeTiers()
    {
        Assert.Equal(3, ModelTier.All.Length);
    }

    [Fact]
    public void Next_CyclesCorrectly()
    {
        Assert.Equal(ModelTier.Standard, ModelTier.Auto.Next());
        Assert.Equal(ModelTier.Premium, ModelTier.Standard.Next());
        Assert.Equal(ModelTier.Auto, ModelTier.Premium.Next());
    }

    [Fact]
    public void Previous_CyclesCorrectly()
    {
        Assert.Equal(ModelTier.Premium, ModelTier.Auto.Previous());
        Assert.Equal(ModelTier.Auto, ModelTier.Standard.Previous());
        Assert.Equal(ModelTier.Standard, ModelTier.Premium.Previous());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet test --filter "ModelTierTests"
```

Expected: FAIL — `ModelTier` not found.

- [ ] **Step 3: Implement all models**

Create `Vozcribe-Windows/Vozcribe/Models/ModelTier.cs`:

```csharp
namespace Vozcribe.Models;

public class ModelTier
{
    public string TierCode { get; }
    public string DisplayName { get; }
    public string ShortName { get; }
    public string Endpoint { get; }
    public bool IsFree { get; }
    public bool IsTwoStage { get; }
    public string? LlmTierCode { get; }
    public string? RequestTier { get; }

    private ModelTier(string tierCode, string displayName, string shortName,
        string endpoint, bool isFree, bool isTwoStage,
        string? llmTierCode = null, string? requestTier = null)
    {
        TierCode = tierCode;
        DisplayName = displayName;
        ShortName = shortName;
        Endpoint = endpoint;
        IsFree = isFree;
        IsTwoStage = isTwoStage;
        LlmTierCode = llmTierCode;
        RequestTier = requestTier;
    }

    public static readonly ModelTier Auto = new(
        "auto", "Auto (Free)", "Auto",
        "/transcribeAuto", isFree: true, isTwoStage: false);

    public static readonly ModelTier Standard = new(
        "standard", "Standard (1x)", "Standard",
        "/transcribeStandard", isFree: false, isTwoStage: true,
        llmTierCode: "standard_v2", requestTier: "STANDARD");

    public static readonly ModelTier Premium = new(
        "premium", "Premium (2x)", "Premium",
        "/transcribePremium", isFree: false, isTwoStage: false,
        requestTier: "PREMIUM");

    public static readonly ModelTier[] All = [Auto, Standard, Premium];

    public ModelTier Next()
    {
        int idx = Array.IndexOf(All, this);
        return All[(idx + 1) % All.Length];
    }

    public ModelTier Previous()
    {
        int idx = Array.IndexOf(All, this);
        return All[(idx - 1 + All.Length) % All.Length];
    }

    public static ModelTier FromTierCode(string code) =>
        All.FirstOrDefault(t => t.TierCode == code) ?? Auto;
}
```

Create `Vozcribe-Windows/Vozcribe/Models/RecordingState.cs`:

```csharp
namespace Vozcribe.Models;

public enum RecordingStateType
{
    Idle,
    Recording,
    Processing,
    Success,
    Error
}

public class RecordingState
{
    public RecordingStateType Type { get; }
    public string? Text { get; }
    public bool WasDirectInsert { get; }

    private RecordingState(RecordingStateType type, string? text = null, bool wasDirectInsert = false)
    {
        Type = type;
        Text = text;
        WasDirectInsert = wasDirectInsert;
    }

    public static RecordingState Idle => new(RecordingStateType.Idle);
    public static RecordingState Recording => new(RecordingStateType.Recording);
    public static RecordingState Processing => new(RecordingStateType.Processing);
    public static RecordingState Inserted(string text) => new(RecordingStateType.Success, text, wasDirectInsert: true);
    public static RecordingState CopiedToClipboard(string text) => new(RecordingStateType.Success, text, wasDirectInsert: false);
    public static RecordingState Failed(string error) => new(RecordingStateType.Error, error);
}
```

Create `Vozcribe-Windows/Vozcribe/Models/InsertionMode.cs`:

```csharp
namespace Vozcribe.Models;

public enum InsertionMode
{
    WhereStarted,
    WhereCurrent
}
```

Create `Vozcribe-Windows/Vozcribe/Models/UserPlan.cs`:

```csharp
namespace Vozcribe.Models;

public enum UserPlan
{
    Free,
    Pro
}
```

Create `Vozcribe-Windows/Vozcribe/Models/TrialStatus.cs`:

```csharp
using System.Text.Json.Serialization;

namespace Vozcribe.Models;

public class TrialStatus
{
    [JsonPropertyName("status")]
    public string Status { get; set; } = "";

    [JsonPropertyName("freeCreditsUsed")]
    public int FreeCreditsUsed { get; set; }

    [JsonPropertyName("freeCreditsRemaining")]
    public int FreeCreditsRemaining { get; set; }

    [JsonPropertyName("freeTierCredits")]
    public int FreeTierCredits { get; set; }

    [JsonPropertyName("trialExpiryDateMs")]
    public long TrialExpiryDateMs { get; set; }

    [JsonPropertyName("warningLevel")]
    public string WarningLevel { get; set; } = "none";
}
```

Create `Vozcribe-Windows/Vozcribe/Models/TranscriptionResult.cs`:

```csharp
using System.Text.Json.Serialization;

namespace Vozcribe.Models;

public class TranscriptionResult
{
    [JsonPropertyName("text")]
    public string Text { get; set; } = "";

    [JsonPropertyName("creditsUsed")]
    public int CreditsUsed { get; set; }

    [JsonPropertyName("totalCreditsThisMonth")]
    public int TotalCreditsThisMonth { get; set; }

    [JsonPropertyName("plan")]
    public string? Plan { get; set; }

    [JsonPropertyName("trialStatus")]
    public TrialStatus? TrialStatus { get; set; }

    [JsonPropertyName("proStatus")]
    public ProStatus? ProStatus { get; set; }
}

public class ProStatus
{
    [JsonPropertyName("isActive")]
    public bool IsActive { get; set; }

    [JsonPropertyName("proCreditsUsed")]
    public int ProCreditsUsed { get; set; }

    [JsonPropertyName("proCreditsRemaining")]
    public int ProCreditsRemaining { get; set; }

    [JsonPropertyName("proCreditsLimit")]
    public int ProCreditsLimit { get; set; }

    [JsonPropertyName("currentPeriodEndMs")]
    public long CurrentPeriodEndMs { get; set; }
}
```

Create `Vozcribe-Windows/Vozcribe/Models/PendingTranscription.cs`:

```csharp
using System.Text.Json.Serialization;

namespace Vozcribe.Models;

public class PendingTranscription
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [JsonPropertyName("timestamp")]
    public long Timestamp { get; set; }

    [JsonPropertyName("tierCode")]
    public string TierCode { get; set; } = "auto";

    [JsonPropertyName("durationMs")]
    public int DurationMs { get; set; }

    [JsonPropertyName("error")]
    public string Error { get; set; } = "";

    [JsonIgnore]
    public string AudioFilePath => $"{Id}.opus";
}
```

Create `Vozcribe-Windows/Vozcribe/Models/HotkeyOption.cs`:

```csharp
using System.Windows.Input;

namespace Vozcribe.Models;

public class HotkeyOption
{
    public string Id { get; }
    public string DisplayName { get; }
    public ModifierKeys Modifiers { get; }
    public Key Key { get; }
    public bool IsModifierOnly { get; }

    private HotkeyOption(string id, string displayName, ModifierKeys modifiers,
        Key key = Key.None, bool isModifierOnly = false)
    {
        Id = id;
        DisplayName = displayName;
        Modifiers = modifiers;
        Key = key;
        IsModifierOnly = isModifierOnly;
    }

    public static readonly HotkeyOption CtrlShift = new(
        "ctrl_shift", "Ctrl + Shift", ModifierKeys.Control | ModifierKeys.Shift,
        isModifierOnly: true);

    public static readonly HotkeyOption CtrlAlt = new(
        "ctrl_alt", "Ctrl + Alt", ModifierKeys.Control | ModifierKeys.Alt,
        isModifierOnly: true);

    public static readonly HotkeyOption WinShift = new(
        "win_shift", "Win + Shift", ModifierKeys.Windows | ModifierKeys.Shift,
        isModifierOnly: true);

    public static readonly HotkeyOption F5 = new(
        "f5", "F5", ModifierKeys.None, Key.F5);

    public static readonly HotkeyOption F8 = new(
        "f8", "F8", ModifierKeys.None, Key.F8);

    public static readonly HotkeyOption[] Presets = [CtrlShift, CtrlAlt, WinShift, F5, F8];

    public static HotkeyOption Custom(ModifierKeys modifiers, Key key) => new(
        "custom", FormatCustom(modifiers, key), modifiers, key);

    public static HotkeyOption FromId(string id) =>
        Presets.FirstOrDefault(p => p.Id == id) ?? CtrlShift;

    private static string FormatCustom(ModifierKeys mod, Key key)
    {
        var parts = new List<string>();
        if (mod.HasFlag(ModifierKeys.Control)) parts.Add("Ctrl");
        if (mod.HasFlag(ModifierKeys.Alt)) parts.Add("Alt");
        if (mod.HasFlag(ModifierKeys.Shift)) parts.Add("Shift");
        if (mod.HasFlag(ModifierKeys.Windows)) parts.Add("Win");
        if (key != Key.None) parts.Add(key.ToString());
        return string.Join(" + ", parts);
    }
}
```

Create `Vozcribe-Windows/Vozcribe/Models/AppSettings.cs`:

```csharp
using System.Text.Json.Serialization;

namespace Vozcribe.Models;

public class AppSettings
{
    [JsonPropertyName("hotkeyId")]
    public string HotkeyId { get; set; } = "ctrl_shift";

    [JsonPropertyName("customHotkeyModifiers")]
    public int? CustomHotkeyModifiers { get; set; }

    [JsonPropertyName("customHotkeyKey")]
    public int? CustomHotkeyKey { get; set; }

    [JsonPropertyName("modelTierCode")]
    public string ModelTierCode { get; set; } = "auto";

    [JsonPropertyName("region")]
    public string Region { get; set; } = "us-central1";

    [JsonPropertyName("insertionMode")]
    public string InsertionMode { get; set; } = "where_started";

    [JsonPropertyName("launchAtStartup")]
    public bool LaunchAtStartup { get; set; }

    [JsonPropertyName("overlayX")]
    public double? OverlayX { get; set; }

    [JsonPropertyName("overlayY")]
    public double? OverlayY { get; set; }

    [JsonPropertyName("hasCompletedOnboarding")]
    public bool HasCompletedOnboarding { get; set; }
}
```

Create `Vozcribe-Windows/Vozcribe/Models/Constants.cs`:

```csharp
namespace Vozcribe.Models;

public static class Constants
{
    // API
    public const string FirebaseProjectId = "whispertype-1de9f";
    public const string BaseUrlPattern = "https://{0}-whispertype-1de9f.cloudfunctions.net";
    public static readonly string[] Regions = ["us-central1", "asia-south1", "europe-west1"];
    public static readonly string[] RegionDisplayNames = ["US Central", "Asia South", "Europe West"];

    // Endpoints
    public const string TrialStatusPath = "/getTrialStatus";
    public const string SubscriptionStatusPath = "/getSubscriptionStatus";
    public const string HealthPath = "/health";

    // Timeouts
    public static readonly TimeSpan ApiTimeout = TimeSpan.FromSeconds(60);
    public static readonly TimeSpan WarmupTimeout = TimeSpan.FromSeconds(10);

    // Retry
    public const int MaxRetries = 3;
    public const int InitialBackoffMs = 1000;
    public const int BackoffMultiplier = 2;
    public const int MaxBackoffMs = 8000;
    public static readonly int[] RetryableStatusCodes = [408, 502, 503, 504];

    // Audio
    public const int SampleRate = 16000;
    public const int Channels = 1;
    public const int BitsPerSample = 16;
    public const int MinAudioSizeBytes = 500;
    public const int ShortAudioThresholdBytes = 1000;
    public const int OpusBitrate = 32000;

    // Silence Detection
    public const float SilenceThresholdDb = -40f;
    public const int MinSilenceDurationMs = 500;
    public const int AudioBufferBeforeMs = 150;
    public const int AudioBufferAfterMs = 200;
    public const int MinSavingsPercent = 10;

    // Amplitude Visualization
    public const float MaxAmplitude = 3000f;
    public const float AmplitudeSmoothingFactor = 0.6f;

    // UI Timings
    public const int DirectInsertDismissMs = 2000;
    public const int ClipboardFallbackDismissMs = 6000;

    // Billing
    public const int DefaultFreeCredits = 500;
    public const int CreditsStarter = 2000;
    public const int CreditsPro = 6000;

    // Persistence
    public const int MaxPendingTranscriptions = 50;
    public const string AppDataFolder = "Vozcribe";
    public const string SettingsFileName = "settings.json";
    public const string PendingFolder = "pending";

    // Auth
    public const string FirebaseApiKey = ""; // Set from config at build time
    public const string GoogleClientId = ""; // Set from config at build time
    public const string CredentialTarget = "Vozcribe_Auth";
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet test --filter "ModelTierTests"
```

Expected: All 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add all models, enums, and constants for Vozcribe Windows"
```

---

### Task 3: Settings Service

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Services/SettingsService.cs`
- Test: `Vozcribe-Windows/Vozcribe.Tests/Services/SettingsServiceTests.cs`

- [ ] **Step 1: Write SettingsService tests**

Create `Vozcribe-Windows/Vozcribe.Tests/Services/SettingsServiceTests.cs`:

```csharp
using System.IO;
using Vozcribe.Models;
using Vozcribe.Services;

namespace Vozcribe.Tests.Services;

public class SettingsServiceTests : IDisposable
{
    private readonly string _tempDir;
    private readonly SettingsService _service;

    public SettingsServiceTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"vozcribe_test_{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
        _service = new SettingsService(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    [Fact]
    public void Load_WhenNoFile_ReturnsDefaults()
    {
        var settings = _service.Settings;
        Assert.Equal("ctrl_shift", settings.HotkeyId);
        Assert.Equal("auto", settings.ModelTierCode);
        Assert.Equal("us-central1", settings.Region);
        Assert.Equal("where_started", settings.InsertionMode);
        Assert.False(settings.LaunchAtStartup);
        Assert.False(settings.HasCompletedOnboarding);
    }

    [Fact]
    public void Save_ThenLoad_RoundTrips()
    {
        _service.Settings.ModelTierCode = "premium";
        _service.Settings.Region = "asia-south1";
        _service.Save();

        var service2 = new SettingsService(_tempDir);
        Assert.Equal("premium", service2.Settings.ModelTierCode);
        Assert.Equal("asia-south1", service2.Settings.Region);
    }

    [Fact]
    public void Save_CreatesJsonFile()
    {
        _service.Save();
        var filePath = Path.Combine(_tempDir, Constants.SettingsFileName);
        Assert.True(File.Exists(filePath));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet test --filter "SettingsServiceTests"
```

Expected: FAIL — `SettingsService` not found.

- [ ] **Step 3: Implement SettingsService**

Create `Vozcribe-Windows/Vozcribe/Services/SettingsService.cs`:

```csharp
using System.IO;
using System.Text.Json;
using Vozcribe.Models;

namespace Vozcribe.Services;

public class SettingsService
{
    private readonly string _filePath;

    public AppSettings Settings { get; private set; }

    public SettingsService(string? basePath = null)
    {
        basePath ??= Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            Constants.AppDataFolder);
        Directory.CreateDirectory(basePath);
        _filePath = Path.Combine(basePath, Constants.SettingsFileName);
        Settings = Load();
    }

    private AppSettings Load()
    {
        if (!File.Exists(_filePath))
            return new AppSettings();

        var json = File.ReadAllText(_filePath);
        return JsonSerializer.Deserialize<AppSettings>(json) ?? new AppSettings();
    }

    public void Save()
    {
        var json = JsonSerializer.Serialize(Settings, new JsonSerializerOptions
        {
            WriteIndented = true
        });
        File.WriteAllText(_filePath, json);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
dotnet test --filter "SettingsServiceTests"
```

Expected: All 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add SettingsService with JSON persistence"
```

---

### Task 4: ViewModelBase

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/ViewModels/ViewModelBase.cs`

- [ ] **Step 1: Create ViewModelBase with INotifyPropertyChanged**

Create `Vozcribe-Windows/Vozcribe/ViewModels/ViewModelBase.cs`:

```csharp
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace Vozcribe.ViewModels;

public abstract class ViewModelBase : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler? PropertyChanged;

    protected void OnPropertyChanged([CallerMemberName] string? propertyName = null)
    {
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }

    protected bool SetField<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value))
            return false;
        field = value;
        OnPropertyChanged(propertyName);
        return true;
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded.

- [ ] **Step 3: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add ViewModelBase with INotifyPropertyChanged"
```

---

### Task 5: Silence Detector

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Services/SilenceDetector.cs`
- Test: `Vozcribe-Windows/Vozcribe.Tests/Services/SilenceDetectorTests.cs`

- [ ] **Step 1: Write SilenceDetector tests**

Create `Vozcribe-Windows/Vozcribe.Tests/Services/SilenceDetectorTests.cs`:

```csharp
using Vozcribe.Services;
using Vozcribe.Models;

namespace Vozcribe.Tests.Services;

public class SilenceDetectorTests
{
    [Fact]
    public void CalculateRmsDb_SilentSamples_ReturnsBelowThreshold()
    {
        // Very quiet samples
        var samples = new short[1600]; // 100ms at 16kHz
        for (int i = 0; i < samples.Length; i++)
            samples[i] = 1;

        var db = SilenceDetector.CalculateRmsDb(samples);
        Assert.True(db < Constants.SilenceThresholdDb);
    }

    [Fact]
    public void CalculateRmsDb_LoudSamples_ReturnsAboveThreshold()
    {
        // Loud samples (half max amplitude sine-like)
        var samples = new short[1600];
        for (int i = 0; i < samples.Length; i++)
            samples[i] = (short)(16000 * Math.Sin(2 * Math.PI * 440 * i / 16000));

        var db = SilenceDetector.CalculateRmsDb(samples);
        Assert.True(db > Constants.SilenceThresholdDb);
    }

    [Fact]
    public void DetectSpeechSegments_AllSilence_ReturnsEmpty()
    {
        // 2 seconds of silence
        var samples = new short[32000];
        var segments = SilenceDetector.DetectSpeechSegments(samples, Constants.SampleRate);
        Assert.Empty(segments);
    }

    [Fact]
    public void DetectSpeechSegments_AllSpeech_ReturnsSingleSegment()
    {
        // 1 second of loud audio
        var samples = new short[16000];
        for (int i = 0; i < samples.Length; i++)
            samples[i] = (short)(16000 * Math.Sin(2 * Math.PI * 440 * i / 16000));

        var segments = SilenceDetector.DetectSpeechSegments(samples, Constants.SampleRate);
        Assert.Single(segments);
    }

    [Fact]
    public void TrimAudio_WhenSavingsAboveThreshold_ReturnsTrimmed()
    {
        // 2 seconds: 0.5s silence + 0.5s speech + 1s silence
        var samples = new short[32000];
        // Speech at indices 8000-16000
        for (int i = 8000; i < 16000; i++)
            samples[i] = (short)(16000 * Math.Sin(2 * Math.PI * 440 * i / 16000));

        var result = SilenceDetector.TrimAudio(samples, Constants.SampleRate);
        Assert.True(result.WasTrimmed);
        Assert.True(result.Samples.Length < samples.Length);
        Assert.True(result.Samples.Length > 0);
    }

    [Fact]
    public void TrimAudio_WhenAllSpeech_ReturnsOriginal()
    {
        var samples = new short[16000];
        for (int i = 0; i < samples.Length; i++)
            samples[i] = (short)(16000 * Math.Sin(2 * Math.PI * 440 * i / 16000));

        var result = SilenceDetector.TrimAudio(samples, Constants.SampleRate);
        Assert.False(result.WasTrimmed);
        Assert.Equal(samples.Length, result.Samples.Length);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet test --filter "SilenceDetectorTests"
```

Expected: FAIL — `SilenceDetector` not found.

- [ ] **Step 3: Implement SilenceDetector**

Create `Vozcribe-Windows/Vozcribe/Services/SilenceDetector.cs`:

```csharp
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
                    // Check if gap from last segment is small enough to merge
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

        // Add padding
        int bufferBefore = sampleRate * Constants.AudioBufferBeforeMs / 1000;
        int bufferAfter = sampleRate * Constants.AudioBufferAfterMs / 1000;

        var padded = segments.Select(s => new SpeechSegment(
            Math.Max(0, s.StartIndex - bufferBefore),
            Math.Min(samples.Length, s.EndIndex + bufferAfter)
        )).ToList();

        // Merge overlapping
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

        // Extract trimmed samples
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
dotnet test --filter "SilenceDetectorTests"
```

Expected: All 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add SilenceDetector with RMS analysis and speech segment extraction"
```

---

### Task 6: Opus Encoder

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Services/OpusEncoder.cs`
- Test: `Vozcribe-Windows/Vozcribe.Tests/Services/OpusEncoderTests.cs`

- [ ] **Step 1: Write OpusEncoder tests**

Create `Vozcribe-Windows/Vozcribe.Tests/Services/OpusEncoderTests.cs`:

```csharp
using Vozcribe.Services;
using Vozcribe.Models;

namespace Vozcribe.Tests.Services;

public class OpusEncoderTests
{
    [Fact]
    public void Encode_ValidSamples_ReturnsNonEmptyBytes()
    {
        // 1 second of 440Hz tone
        var samples = new short[Constants.SampleRate];
        for (int i = 0; i < samples.Length; i++)
            samples[i] = (short)(16000 * Math.Sin(2 * Math.PI * 440 * i / Constants.SampleRate));

        var opus = OpusEncoderService.Encode(samples, Constants.SampleRate);
        Assert.NotNull(opus);
        Assert.True(opus.Length > 0);
        Assert.True(opus.Length < samples.Length * 2); // Should be compressed
    }

    [Fact]
    public void Encode_ShortSamples_StillWorks()
    {
        // 20ms of audio (one Opus frame)
        var samples = new short[320];
        for (int i = 0; i < samples.Length; i++)
            samples[i] = (short)(8000 * Math.Sin(2 * Math.PI * 440 * i / Constants.SampleRate));

        var opus = OpusEncoderService.Encode(samples, Constants.SampleRate);
        Assert.NotNull(opus);
        Assert.True(opus.Length > 0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet test --filter "OpusEncoderTests"
```

Expected: FAIL — `OpusEncoderService` not found.

- [ ] **Step 3: Implement OpusEncoderService**

Create `Vozcribe-Windows/Vozcribe/Services/OpusEncoder.cs`:

```csharp
using Concentus;
using Concentus.Enums;
using Concentus.Oggfile;
using Vozcribe.Models;

namespace Vozcribe.Services;

public static class OpusEncoderService
{
    private const int FrameSizeMs = 20;

    public static byte[] Encode(short[] samples, int sampleRate)
    {
        int frameSizeSamples = sampleRate * FrameSizeMs / 1000; // 320 for 16kHz
        var encoder = new OpusEncoder(sampleRate, Constants.Channels, OpusApplication.OPUS_APPLICATION_VOIP);
        encoder.Bitrate = Constants.OpusBitrate;

        using var outputStream = new MemoryStream();
        var oggWriter = new OpusOggWriteStream(encoder, outputStream, sampleRate, Constants.Channels);

        int offset = 0;
        while (offset + frameSizeSamples <= samples.Length)
        {
            var frame = new ReadOnlySpan<short>(samples, offset, frameSizeSamples);
            oggWriter.WriteSamples(frame);
            offset += frameSizeSamples;
        }

        // Handle remaining samples by padding with zeros
        if (offset < samples.Length)
        {
            var remaining = new short[frameSizeSamples];
            Array.Copy(samples, offset, remaining, 0, samples.Length - offset);
            oggWriter.WriteSamples(remaining.AsSpan());
        }

        oggWriter.Finish();
        return outputStream.ToArray();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
dotnet test --filter "OpusEncoderTests"
```

Expected: All 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add Opus encoder using Concentus for audio compression"
```

---

### Task 7: Audio Service

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Services/AudioService.cs`

- [ ] **Step 1: Implement AudioService**

Create `Vozcribe-Windows/Vozcribe/Services/AudioService.cs`:

```csharp
using System.Collections.Concurrent;
using NAudio.Wave;
using Vozcribe.Models;

namespace Vozcribe.Services;

public class AudioService : IDisposable
{
    private WasapiCapture? _capture;
    private WaveFormat? _captureFormat;
    private readonly ConcurrentBag<short[]> _chunks = [];
    private readonly object _lock = new();
    private bool _isRecording;

    public event Action<float>? AmplitudeUpdated;
    public bool IsRecording => _isRecording;

    public void StartRecording()
    {
        _chunks.Clear();

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

        // Combine all chunks
        var allSamples = _chunks.SelectMany(c => c).ToArray();
        int durationMs = allSamples.Length * 1000 / Constants.SampleRate;

        return new AudioCaptureResult(allSamples, durationMs);
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs e)
    {
        if (e.BytesRecorded == 0) return;

        int sampleCount = e.BytesRecorded / 2; // 16-bit = 2 bytes per sample
        var samples = new short[sampleCount];
        Buffer.BlockCopy(e.Buffer, 0, samples, 0, e.BytesRecorded);

        _chunks.Add(samples);

        // Calculate amplitude for visualization
        float rmsDb = SilenceDetector.CalculateRmsDb(samples);
        float rmsLinear = (float)Math.Pow(10, rmsDb / 20) * short.MaxValue;
        float normalized = Math.Min(rmsLinear / Constants.MaxAmplitude, 1f);
        AmplitudeUpdated?.Invoke(normalized);
    }

    private void OnRecordingStopped(object? sender, StoppedEventArgs e)
    {
        // Cleanup handled in Dispose
    }

    public void Dispose()
    {
        _capture?.Dispose();
        _capture = null;
    }
}

public record AudioCaptureResult(short[] Samples, int DurationMs);
```

- [ ] **Step 2: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded.

- [ ] **Step 3: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add AudioService with WASAPI capture and real-time amplitude"
```

---

### Task 8: API Client

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Services/ApiClient.cs`
- Test: `Vozcribe-Windows/Vozcribe.Tests/Services/ApiClientTests.cs`

- [ ] **Step 1: Write ApiClient tests**

Create `Vozcribe-Windows/Vozcribe.Tests/Services/ApiClientTests.cs`:

```csharp
using Vozcribe.Models;
using Vozcribe.Services;

namespace Vozcribe.Tests.Services;

public class ApiClientTests
{
    [Fact]
    public void BuildBaseUrl_UsesRegion()
    {
        var url = ApiClient.BuildBaseUrl("us-central1");
        Assert.Equal("https://us-central1-whispertype-1de9f.cloudfunctions.net", url);
    }

    [Fact]
    public void BuildBaseUrl_AsiaSouth()
    {
        var url = ApiClient.BuildBaseUrl("asia-south1");
        Assert.Equal("https://asia-south1-whispertype-1de9f.cloudfunctions.net", url);
    }

    [Fact]
    public void BuildTranscriptionRequest_SingleStage_HasCorrectFields()
    {
        var body = ApiClient.BuildTranscriptionBody(
            audioBase64: "dGVzdA==",
            audioFormat: "opus",
            tierCode: "auto",
            durationMs: 5000,
            isTwoStage: false,
            llmTierCode: null,
            requestTier: null);

        Assert.Equal("dGVzdA==", body["audioBase64"]?.ToString());
        Assert.Equal("opus", body["audioFormat"]?.ToString());
        Assert.Equal("auto", body["model"]?.ToString());
        Assert.Equal("5000", body["audioDurationMs"]?.ToString());
        Assert.False(body.ContainsKey("llmModel"));
        Assert.False(body.ContainsKey("tier"));
    }

    [Fact]
    public void BuildTranscriptionRequest_TwoStage_HasLlmFields()
    {
        var body = ApiClient.BuildTranscriptionBody(
            audioBase64: "dGVzdA==",
            audioFormat: "opus",
            tierCode: "standard",
            durationMs: 5000,
            isTwoStage: true,
            llmTierCode: "standard_v2",
            requestTier: "STANDARD");

        Assert.Equal("standard", body["model"]?.ToString());
        Assert.Equal("standard_v2", body["llmModel"]?.ToString());
        Assert.Equal("STANDARD", body["tier"]?.ToString());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet test --filter "ApiClientTests"
```

Expected: FAIL — `ApiClient` not found.

- [ ] **Step 3: Implement ApiClient**

Create `Vozcribe-Windows/Vozcribe/Services/ApiClient.cs`:

```csharp
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Vozcribe.Models;

namespace Vozcribe.Services;

public class ApiClient
{
    private static readonly HttpClient Http = new()
    {
        Timeout = Constants.ApiTimeout
    };

    private readonly SettingsService _settings;
    private Func<Task<string?>>? _getToken;

    public ApiClient(SettingsService settings)
    {
        _settings = settings;
    }

    public void SetTokenProvider(Func<Task<string?>> getToken)
    {
        _getToken = getToken;
    }

    public static string BuildBaseUrl(string region) =>
        string.Format(Constants.BaseUrlPattern, region);

    public static Dictionary<string, object> BuildTranscriptionBody(
        string audioBase64, string audioFormat, string tierCode,
        int durationMs, bool isTwoStage, string? llmTierCode, string? requestTier)
    {
        var body = new Dictionary<string, object>
        {
            ["audioBase64"] = audioBase64,
            ["audioFormat"] = audioFormat,
            ["model"] = tierCode,
            ["audioDurationMs"] = durationMs
        };

        if (isTwoStage)
        {
            if (llmTierCode != null) body["llmModel"] = llmTierCode;
            if (requestTier != null) body["tier"] = requestTier;
        }

        return body;
    }

    public async Task<TranscriptionResult> TranscribeAsync(
        byte[] opusAudio, int durationMs, ModelTier tier)
    {
        var token = _getToken != null ? await _getToken() : null;
        if (token == null)
            throw new InvalidOperationException("Not authenticated");

        var baseUrl = BuildBaseUrl(_settings.Settings.Region);
        var url = baseUrl + tier.Endpoint;

        var audioBase64 = Convert.ToBase64String(opusAudio);
        var body = BuildTranscriptionBody(
            audioBase64, "opus", tier.TierCode, durationMs,
            tier.IsTwoStage, tier.LlmTierCode, tier.RequestTier);

        var json = JsonSerializer.Serialize(body);
        return await PostWithRetryAsync<TranscriptionResult>(url, json, token);
    }

    public async Task<TrialStatus> GetTrialStatusAsync()
    {
        var token = _getToken != null ? await _getToken() : null;
        if (token == null)
            throw new InvalidOperationException("Not authenticated");

        var baseUrl = BuildBaseUrl(_settings.Settings.Region);
        var url = baseUrl + Constants.TrialStatusPath;

        var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

        var response = await Http.SendAsync(request);
        response.EnsureSuccessStatusCode();
        var responseJson = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<TrialStatus>(responseJson)
            ?? throw new InvalidOperationException("Invalid trial status response");
    }

    public async Task WarmupEndpointsAsync()
    {
        var baseUrl = BuildBaseUrl(_settings.Settings.Region);
        var endpoints = new[]
        {
            "/transcribeAuto",
            "/transcribeStandard",
            "/transcribePremium"
        };

        using var warmupClient = new HttpClient { Timeout = Constants.WarmupTimeout };
        var tasks = endpoints.Select(ep =>
            warmupClient.GetAsync(baseUrl + ep).ContinueWith(_ => { }));
        await Task.WhenAll(tasks);
    }

    private async Task<T> PostWithRetryAsync<T>(string url, string json, string token)
    {
        int backoffMs = Constants.InitialBackoffMs;

        for (int attempt = 0; attempt <= Constants.MaxRetries; attempt++)
        {
            var request = new HttpRequestMessage(HttpMethod.Post, url)
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json")
            };
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

            var response = await Http.SendAsync(request);

            if (response.IsSuccessStatusCode)
            {
                var responseJson = await response.Content.ReadAsStringAsync();
                return JsonSerializer.Deserialize<T>(responseJson)
                    ?? throw new InvalidOperationException("Invalid response");
            }

            int statusCode = (int)response.StatusCode;

            if (statusCode == 403)
            {
                var errorJson = await response.Content.ReadAsStringAsync();
                var errorObj = JsonSerializer.Deserialize<JsonObject>(errorJson);
                var message = errorObj?["message"]?.ToString() ?? "Quota exceeded";
                throw new QuotaExceededException(message);
            }

            if (statusCode == 401)
                throw new InvalidOperationException("Not authenticated");

            if (attempt < Constants.MaxRetries && Constants.RetryableStatusCodes.Contains(statusCode))
            {
                await Task.Delay(backoffMs);
                backoffMs = Math.Min(backoffMs * Constants.BackoffMultiplier, Constants.MaxBackoffMs);
                continue;
            }

            var errorContent = await response.Content.ReadAsStringAsync();
            throw new HttpRequestException($"API error {statusCode}: {errorContent}");
        }

        throw new HttpRequestException("Max retries exceeded");
    }
}

public class QuotaExceededException : Exception
{
    public QuotaExceededException(string message) : base(message) { }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
dotnet test --filter "ApiClientTests"
```

Expected: All 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add ApiClient with retry logic and endpoint warmup"
```

---

### Task 9: Auth Service

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Services/AuthService.cs`
- Test: `Vozcribe-Windows/Vozcribe.Tests/Services/AuthServiceTests.cs`

- [ ] **Step 1: Write AuthService tests**

Create `Vozcribe-Windows/Vozcribe.Tests/Services/AuthServiceTests.cs`:

```csharp
using Vozcribe.Services;

namespace Vozcribe.Tests.Services;

public class AuthServiceTests
{
    [Fact]
    public void GenerateCodeVerifier_Returns43CharString()
    {
        var verifier = AuthService.GenerateCodeVerifier();
        Assert.True(verifier.Length >= 43);
        // Must be URL-safe base64
        Assert.DoesNotContain("+", verifier);
        Assert.DoesNotContain("/", verifier);
        Assert.DoesNotContain("=", verifier);
    }

    [Fact]
    public void GenerateCodeChallenge_IsDeterministic()
    {
        var verifier = "test_verifier_value_12345678901234567890";
        var challenge1 = AuthService.GenerateCodeChallenge(verifier);
        var challenge2 = AuthService.GenerateCodeChallenge(verifier);
        Assert.Equal(challenge1, challenge2);
    }

    [Fact]
    public void GenerateCodeChallenge_IsUrlSafeBase64()
    {
        var verifier = AuthService.GenerateCodeVerifier();
        var challenge = AuthService.GenerateCodeChallenge(verifier);
        Assert.DoesNotContain("+", challenge);
        Assert.DoesNotContain("/", challenge);
        Assert.DoesNotContain("=", challenge);
    }

    [Fact]
    public void BuildAuthUrl_ContainsRequiredParams()
    {
        var url = AuthService.BuildGoogleAuthUrl("test_client_id", "test_challenge", "http://localhost:1234");
        Assert.Contains("client_id=test_client_id", url);
        Assert.Contains("code_challenge=test_challenge", url);
        Assert.Contains("redirect_uri=", url);
        Assert.Contains("response_type=code", url);
        Assert.Contains("scope=", url);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet test --filter "AuthServiceTests"
```

Expected: FAIL — `AuthService` not found.

- [ ] **Step 3: Implement AuthService**

Create `Vozcribe-Windows/Vozcribe/Services/AuthService.cs`:

```csharp
using System.Diagnostics;
using System.Net;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Vozcribe.Models;
using Vozcribe.ViewModels;

namespace Vozcribe.Services;

public class AuthService : ViewModelBase
{
    private readonly HttpClient _http = new();
    private string? _idToken;
    private string? _refreshToken;
    private DateTime _tokenExpiry;
    private string? _email;
    private bool _isSignedIn;

    public bool IsSignedIn
    {
        get => _isSignedIn;
        private set => SetField(ref _isSignedIn, value);
    }

    public string? Email
    {
        get => _email;
        private set => SetField(ref _email, value);
    }

    public static string GenerateCodeVerifier()
    {
        var bytes = RandomNumberGenerator.GetBytes(32);
        return Convert.ToBase64String(bytes)
            .Replace("+", "-")
            .Replace("/", "_")
            .TrimEnd('=');
    }

    public static string GenerateCodeChallenge(string verifier)
    {
        var bytes = SHA256.HashData(Encoding.ASCII.GetBytes(verifier));
        return Convert.ToBase64String(bytes)
            .Replace("+", "-")
            .Replace("/", "_")
            .TrimEnd('=');
    }

    public static string BuildGoogleAuthUrl(string clientId, string codeChallenge, string redirectUri)
    {
        var scope = Uri.EscapeDataString("openid email profile");
        return "https://accounts.google.com/o/oauth2/v2/auth"
            + $"?client_id={Uri.EscapeDataString(clientId)}"
            + $"&redirect_uri={Uri.EscapeDataString(redirectUri)}"
            + "&response_type=code"
            + $"&scope={scope}"
            + $"&code_challenge={Uri.EscapeDataString(codeChallenge)}"
            + "&code_challenge_method=S256";
    }

    public async Task<bool> SignInWithGoogleAsync(string googleClientId, string firebaseApiKey)
    {
        var codeVerifier = GenerateCodeVerifier();
        var codeChallenge = GenerateCodeChallenge(codeVerifier);

        // Start local HTTP listener for OAuth redirect
        int port = GetAvailablePort();
        var redirectUri = $"http://localhost:{port}";
        var listener = new HttpListener();
        listener.Prefixes.Add(redirectUri + "/");
        listener.Start();

        // Open browser
        var authUrl = BuildGoogleAuthUrl(googleClientId, codeChallenge, redirectUri);
        Process.Start(new ProcessStartInfo(authUrl) { UseShellExecute = true });

        // Wait for callback
        var context = await listener.GetContextAsync();
        var code = context.Request.QueryString["code"];

        // Send success response to browser
        var responseHtml = "<html><body><h2>Sign-in successful!</h2><p>You can close this window.</p></body></html>";
        var buffer = Encoding.UTF8.GetBytes(responseHtml);
        context.Response.ContentLength64 = buffer.Length;
        context.Response.ContentType = "text/html";
        await context.Response.OutputStream.WriteAsync(buffer);
        context.Response.Close();
        listener.Stop();

        if (string.IsNullOrEmpty(code))
            return false;

        // Exchange code for Google tokens
        var googleTokens = await ExchangeCodeForGoogleTokensAsync(
            code, googleClientId, codeVerifier, redirectUri);

        if (googleTokens == null)
            return false;

        // Exchange Google ID token for Firebase token
        var firebaseResult = await ExchangeGoogleTokenForFirebaseAsync(
            googleTokens.IdToken, firebaseApiKey);

        if (firebaseResult == null)
            return false;

        _idToken = firebaseResult.IdToken;
        _refreshToken = firebaseResult.RefreshToken;
        _tokenExpiry = DateTime.UtcNow.AddSeconds(int.Parse(firebaseResult.ExpiresIn) - 300);
        Email = firebaseResult.Email;
        IsSignedIn = true;

        SaveTokensToCredentialManager();
        return true;
    }

    public async Task<string?> GetIdTokenAsync()
    {
        if (!IsSignedIn || _idToken == null)
            return null;

        if (DateTime.UtcNow >= _tokenExpiry && _refreshToken != null)
            await RefreshTokenAsync();

        return _idToken;
    }

    public void SignOut()
    {
        _idToken = null;
        _refreshToken = null;
        Email = null;
        IsSignedIn = false;
        DeleteTokensFromCredentialManager();
    }

    public bool TryRestoreSession()
    {
        var (idToken, refreshToken, email) = LoadTokensFromCredentialManager();
        if (idToken == null || refreshToken == null)
            return false;

        _idToken = idToken;
        _refreshToken = refreshToken;
        _tokenExpiry = DateTime.MinValue; // Force refresh on next use
        Email = email;
        IsSignedIn = true;
        return true;
    }

    private async Task<GoogleTokenResponse?> ExchangeCodeForGoogleTokensAsync(
        string code, string clientId, string codeVerifier, string redirectUri)
    {
        var content = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["code"] = code,
            ["client_id"] = clientId,
            ["redirect_uri"] = redirectUri,
            ["grant_type"] = "authorization_code",
            ["code_verifier"] = codeVerifier
        });

        var response = await _http.PostAsync("https://oauth2.googleapis.com/token", content);
        if (!response.IsSuccessStatusCode) return null;

        var json = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<GoogleTokenResponse>(json);
    }

    private async Task<FirebaseSignInResponse?> ExchangeGoogleTokenForFirebaseAsync(
        string googleIdToken, string firebaseApiKey)
    {
        var url = $"https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key={firebaseApiKey}";
        var body = new
        {
            postBody = $"id_token={googleIdToken}&providerId=google.com",
            requestUri = "http://localhost",
            returnSecureToken = true,
            returnIdpCredential = true
        };

        var json = JsonSerializer.Serialize(body);
        var content = new StringContent(json, Encoding.UTF8, "application/json");
        var response = await _http.PostAsync(url, content);

        if (!response.IsSuccessStatusCode) return null;

        var responseJson = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<FirebaseSignInResponse>(responseJson);
    }

    private async Task RefreshTokenAsync()
    {
        var url = $"https://securetoken.googleapis.com/v1/token?key={Constants.FirebaseApiKey}";
        var content = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["grant_type"] = "refresh_token",
            ["refresh_token"] = _refreshToken!
        });

        var response = await _http.PostAsync(url, content);
        if (!response.IsSuccessStatusCode)
        {
            SignOut();
            return;
        }

        var json = await response.Content.ReadAsStringAsync();
        var result = JsonSerializer.Deserialize<FirebaseRefreshResponse>(json);
        if (result != null)
        {
            _idToken = result.IdToken;
            _refreshToken = result.RefreshToken;
            _tokenExpiry = DateTime.UtcNow.AddSeconds(int.Parse(result.ExpiresIn) - 300);
        }
    }

    private static int GetAvailablePort()
    {
        var listener = new System.Net.Sockets.TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        int port = ((System.Net.IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }

    // Windows Credential Manager integration
    private void SaveTokensToCredentialManager()
    {
        var data = JsonSerializer.Serialize(new StoredCredentials
        {
            IdToken = _idToken,
            RefreshToken = _refreshToken,
            Email = Email
        });
        // Use DPAPI for encryption via ProtectedData
        var encrypted = System.Security.Cryptography.ProtectedData.Protect(
            Encoding.UTF8.GetBytes(data), null,
            System.Security.Cryptography.DataProtectionScope.CurrentUser);
        var path = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            Constants.AppDataFolder, ".auth");
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllBytes(path, encrypted);
    }

    private (string? idToken, string? refreshToken, string? email) LoadTokensFromCredentialManager()
    {
        var path = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            Constants.AppDataFolder, ".auth");
        if (!File.Exists(path))
            return (null, null, null);

        try
        {
            var encrypted = File.ReadAllBytes(path);
            var decrypted = System.Security.Cryptography.ProtectedData.Unprotect(
                encrypted, null,
                System.Security.Cryptography.DataProtectionScope.CurrentUser);
            var creds = JsonSerializer.Deserialize<StoredCredentials>(
                Encoding.UTF8.GetString(decrypted));
            return (creds?.IdToken, creds?.RefreshToken, creds?.Email);
        }
        catch
        {
            return (null, null, null);
        }
    }

    private void DeleteTokensFromCredentialManager()
    {
        var path = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            Constants.AppDataFolder, ".auth");
        if (File.Exists(path))
            File.Delete(path);
    }

    private class StoredCredentials
    {
        public string? IdToken { get; set; }
        public string? RefreshToken { get; set; }
        public string? Email { get; set; }
    }
}

public class GoogleTokenResponse
{
    [JsonPropertyName("id_token")]
    public string IdToken { get; set; } = "";

    [JsonPropertyName("access_token")]
    public string AccessToken { get; set; } = "";
}

public class FirebaseSignInResponse
{
    [JsonPropertyName("idToken")]
    public string IdToken { get; set; } = "";

    [JsonPropertyName("refreshToken")]
    public string RefreshToken { get; set; } = "";

    [JsonPropertyName("expiresIn")]
    public string ExpiresIn { get; set; } = "3600";

    [JsonPropertyName("email")]
    public string Email { get; set; } = "";
}

public class FirebaseRefreshResponse
{
    [JsonPropertyName("id_token")]
    public string IdToken { get; set; } = "";

    [JsonPropertyName("refresh_token")]
    public string RefreshToken { get; set; } = "";

    [JsonPropertyName("expires_in")]
    public string ExpiresIn { get; set; } = "3600";
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
dotnet test --filter "AuthServiceTests"
```

Expected: All 4 tests pass.

- [ ] **Step 5: Add System.Security.Cryptography.ProtectedData NuGet package**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows/Vozcribe
dotnet add package System.Security.Cryptography.ProtectedData
```

- [ ] **Step 6: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded.

- [ ] **Step 7: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add AuthService with Google OAuth PKCE flow and DPAPI token storage"
```

---

### Task 10: Pending Transcription Service

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Services/PendingTranscriptionService.cs`
- Test: `Vozcribe-Windows/Vozcribe.Tests/Services/PendingTranscriptionServiceTests.cs`

- [ ] **Step 1: Write PendingTranscriptionService tests**

Create `Vozcribe-Windows/Vozcribe.Tests/Services/PendingTranscriptionServiceTests.cs`:

```csharp
using System.IO;
using Vozcribe.Models;
using Vozcribe.Services;

namespace Vozcribe.Tests.Services;

public class PendingTranscriptionServiceTests : IDisposable
{
    private readonly string _tempDir;
    private readonly PendingTranscriptionService _service;

    public PendingTranscriptionServiceTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"vozcribe_pending_test_{Guid.NewGuid()}");
        _service = new PendingTranscriptionService(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    [Fact]
    public void Save_CreatesAudioAndMetadataFiles()
    {
        var audio = new byte[] { 1, 2, 3, 4 };
        _service.Save(audio, "auto", 5000, "test error");

        var items = _service.LoadAll();
        Assert.Single(items);
        Assert.Equal("auto", items[0].TierCode);
        Assert.Equal(5000, items[0].DurationMs);
        Assert.Equal("test error", items[0].Error);
    }

    [Fact]
    public void LoadAudioData_ReturnsCorrectBytes()
    {
        var audio = new byte[] { 10, 20, 30, 40, 50 };
        _service.Save(audio, "premium", 3000, "error");

        var items = _service.LoadAll();
        var loaded = _service.LoadAudioData(items[0]);
        Assert.Equal(audio, loaded);
    }

    [Fact]
    public void Delete_RemovesFiles()
    {
        var audio = new byte[] { 1, 2, 3 };
        _service.Save(audio, "auto", 1000, "err");

        var items = _service.LoadAll();
        _service.Delete(items[0]);

        Assert.Empty(_service.LoadAll());
    }

    [Fact]
    public void Save_PurgesOldestWhenOverLimit()
    {
        for (int i = 0; i < Constants.MaxPendingTranscriptions + 5; i++)
        {
            _service.Save(new byte[] { (byte)i }, "auto", 1000, "err");
        }

        var items = _service.LoadAll();
        Assert.Equal(Constants.MaxPendingTranscriptions, items.Count);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet test --filter "PendingTranscriptionServiceTests"
```

Expected: FAIL — `PendingTranscriptionService` not found.

- [ ] **Step 3: Implement PendingTranscriptionService**

Create `Vozcribe-Windows/Vozcribe/Services/PendingTranscriptionService.cs`:

```csharp
using System.IO;
using System.Text.Json;
using Vozcribe.Models;

namespace Vozcribe.Services;

public class PendingTranscriptionService
{
    private readonly string _pendingDir;

    public PendingTranscriptionService(string? basePath = null)
    {
        basePath ??= Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            Constants.AppDataFolder);
        _pendingDir = Path.Combine(basePath, Constants.PendingFolder);
        Directory.CreateDirectory(_pendingDir);
    }

    public void Save(byte[] audioData, string tierCode, int durationMs, string error)
    {
        var pending = new PendingTranscription
        {
            Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            TierCode = tierCode,
            DurationMs = durationMs,
            Error = error
        };

        var audioPath = Path.Combine(_pendingDir, pending.AudioFilePath);
        var metaPath = Path.Combine(_pendingDir, $"{pending.Id}.json");

        File.WriteAllBytes(audioPath, audioData);
        File.WriteAllText(metaPath, JsonSerializer.Serialize(pending));

        PurgeOldest();
    }

    public List<PendingTranscription> LoadAll()
    {
        var items = new List<PendingTranscription>();
        foreach (var file in Directory.GetFiles(_pendingDir, "*.json"))
        {
            try
            {
                var json = File.ReadAllText(file);
                var item = JsonSerializer.Deserialize<PendingTranscription>(json);
                if (item != null)
                    items.Add(item);
            }
            catch { /* skip corrupt files */ }
        }
        return items.OrderByDescending(i => i.Timestamp).ToList();
    }

    public byte[]? LoadAudioData(PendingTranscription item)
    {
        var path = Path.Combine(_pendingDir, item.AudioFilePath);
        return File.Exists(path) ? File.ReadAllBytes(path) : null;
    }

    public void Delete(PendingTranscription item)
    {
        var audioPath = Path.Combine(_pendingDir, item.AudioFilePath);
        var metaPath = Path.Combine(_pendingDir, $"{item.Id}.json");
        if (File.Exists(audioPath)) File.Delete(audioPath);
        if (File.Exists(metaPath)) File.Delete(metaPath);
    }

    private void PurgeOldest()
    {
        var items = LoadAll();
        while (items.Count > Constants.MaxPendingTranscriptions)
        {
            var oldest = items[^1];
            Delete(oldest);
            items.RemoveAt(items.Count - 1);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
dotnet test --filter "PendingTranscriptionServiceTests"
```

Expected: All 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add PendingTranscriptionService for local failure recovery"
```

---

### Task 11: Hotkey Service

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Services/HotkeyService.cs`
- Create: `Vozcribe-Windows/Vozcribe/Utilities/Win32Interop.cs`

- [ ] **Step 1: Implement Win32 interop declarations**

Create `Vozcribe-Windows/Vozcribe/Utilities/Win32Interop.cs`:

```csharp
using System.Runtime.InteropServices;

namespace Vozcribe.Utilities;

public static class Win32Interop
{
    public const int WH_KEYBOARD_LL = 13;
    public const int WM_KEYDOWN = 0x0100;
    public const int WM_KEYUP = 0x0101;
    public const int WM_SYSKEYDOWN = 0x0104;
    public const int WM_SYSKEYUP = 0x0105;

    public const int VK_SHIFT = 0x10;
    public const int VK_CONTROL = 0x11;
    public const int VK_MENU = 0x12; // Alt
    public const int VK_LWIN = 0x5B;
    public const int VK_RWIN = 0x5C;
    public const int VK_UP = 0x26;
    public const int VK_DOWN = 0x28;

    public delegate IntPtr LowLevelKeyboardProc(int nCode, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern IntPtr SetWindowsHookEx(int idHook, LowLevelKeyboardProc lpfn, IntPtr hMod, uint dwThreadId);

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool UnhookWindowsHookEx(IntPtr hhk);

    [DllImport("user32.dll")]
    public static extern IntPtr CallNextHookEx(IntPtr hhk, int nCode, IntPtr wParam, IntPtr lParam);

    [DllImport("kernel32.dll")]
    public static extern IntPtr GetModuleHandle(string? lpModuleName);

    [DllImport("user32.dll")]
    public static extern short GetAsyncKeyState(int vKey);

    [StructLayout(LayoutKind.Sequential)]
    public struct KBDLLHOOKSTRUCT
    {
        public uint vkCode;
        public uint scanCode;
        public uint flags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    // For text insertion: SendInput
    public const int INPUT_KEYBOARD = 1;
    public const int KEYEVENTF_KEYUP = 0x0002;

    [StructLayout(LayoutKind.Sequential)]
    public struct INPUT
    {
        public int type;
        public INPUTUNION u;
    }

    [StructLayout(LayoutKind.Explicit)]
    public struct INPUTUNION
    {
        [FieldOffset(0)]
        public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [DllImport("user32.dll", SetLastError = true)]
    public static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool SetForegroundWindow(IntPtr hWnd);
}
```

- [ ] **Step 2: Implement HotkeyService**

Create `Vozcribe-Windows/Vozcribe/Services/HotkeyService.cs`:

```csharp
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Windows.Input;
using Vozcribe.Models;
using Vozcribe.Utilities;

namespace Vozcribe.Services;

public class HotkeyService : IDisposable
{
    private IntPtr _hookId = IntPtr.Zero;
    private Win32Interop.LowLevelKeyboardProc? _hookProc;
    private HotkeyOption _currentHotkey;
    private bool _modifiersPressed;

    public event Action? HotkeyTriggered;
    public event Action? ModelNextTriggered;
    public event Action? ModelPreviousTriggered;

    public HotkeyService(HotkeyOption hotkey)
    {
        _currentHotkey = hotkey;
    }

    public void Start()
    {
        _hookProc = HookCallback;
        using var process = Process.GetCurrentProcess();
        using var module = process.MainModule!;
        _hookId = Win32Interop.SetWindowsHookEx(
            Win32Interop.WH_KEYBOARD_LL, _hookProc,
            Win32Interop.GetModuleHandle(module.ModuleName), 0);
    }

    public void ChangeHotkey(HotkeyOption hotkey)
    {
        _currentHotkey = hotkey;
        _modifiersPressed = false;
    }

    private IntPtr HookCallback(int nCode, IntPtr wParam, IntPtr lParam)
    {
        if (nCode >= 0)
        {
            var hookStruct = Marshal.PtrToStructure<Win32Interop.KBDLLHOOKSTRUCT>(lParam);
            int msg = wParam.ToInt32();

            // Check for model cycling: Shift + Up/Down
            if (msg == Win32Interop.WM_KEYDOWN || msg == Win32Interop.WM_SYSKEYDOWN)
            {
                bool shiftHeld = (Win32Interop.GetAsyncKeyState(Win32Interop.VK_SHIFT) & 0x8000) != 0;
                if (shiftHeld && hookStruct.vkCode == Win32Interop.VK_UP)
                {
                    ModelNextTriggered?.Invoke();
                    return (IntPtr)1; // Consume the key
                }
                if (shiftHeld && hookStruct.vkCode == Win32Interop.VK_DOWN)
                {
                    ModelPreviousTriggered?.Invoke();
                    return (IntPtr)1;
                }
            }

            if (_currentHotkey.IsModifierOnly)
                HandleModifierOnlyHotkey(msg, hookStruct);
            else
                HandleKeyBasedHotkey(msg, hookStruct);
        }

        return Win32Interop.CallNextHookEx(_hookId, nCode, wParam, lParam);
    }

    private void HandleModifierOnlyHotkey(int msg, Win32Interop.KBDLLHOOKSTRUCT hookStruct)
    {
        bool isKeyDown = msg == Win32Interop.WM_KEYDOWN || msg == Win32Interop.WM_SYSKEYDOWN;
        bool isKeyUp = msg == Win32Interop.WM_KEYUP || msg == Win32Interop.WM_SYSKEYUP;

        if (isKeyDown && AreModifiersPressed())
        {
            if (!_modifiersPressed)
            {
                _modifiersPressed = true;
                HotkeyTriggered?.Invoke();
            }
        }
        else if (isKeyUp)
        {
            _modifiersPressed = false;
        }
    }

    private void HandleKeyBasedHotkey(int msg, Win32Interop.KBDLLHOOKSTRUCT hookStruct)
    {
        if (msg != Win32Interop.WM_KEYDOWN && msg != Win32Interop.WM_SYSKEYDOWN)
            return;

        int wpfKey = KeyInterop.KeyFromVirtualKey((int)hookStruct.vkCode);
        if ((Key)wpfKey != _currentHotkey.Key)
            return;

        if (_currentHotkey.Modifiers != ModifierKeys.None && !AreModifiersPressed())
            return;

        HotkeyTriggered?.Invoke();
    }

    private bool AreModifiersPressed()
    {
        var required = _currentHotkey.Modifiers;

        if (required.HasFlag(ModifierKeys.Control) &&
            (Win32Interop.GetAsyncKeyState(Win32Interop.VK_CONTROL) & 0x8000) == 0)
            return false;

        if (required.HasFlag(ModifierKeys.Alt) &&
            (Win32Interop.GetAsyncKeyState(Win32Interop.VK_MENU) & 0x8000) == 0)
            return false;

        if (required.HasFlag(ModifierKeys.Shift) &&
            (Win32Interop.GetAsyncKeyState(Win32Interop.VK_SHIFT) & 0x8000) == 0)
            return false;

        if (required.HasFlag(ModifierKeys.Windows) &&
            ((Win32Interop.GetAsyncKeyState(Win32Interop.VK_LWIN) & 0x8000) == 0 &&
             (Win32Interop.GetAsyncKeyState(Win32Interop.VK_RWIN) & 0x8000) == 0))
            return false;

        return true;
    }

    public void Dispose()
    {
        if (_hookId != IntPtr.Zero)
        {
            Win32Interop.UnhookWindowsHookEx(_hookId);
            _hookId = IntPtr.Zero;
        }
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded.

- [ ] **Step 4: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add HotkeyService with low-level keyboard hooks and model cycling"
```

---

### Task 12: Text Insertion Service

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Services/TextInsertionService.cs`

- [ ] **Step 1: Implement TextInsertionService**

Create `Vozcribe-Windows/Vozcribe/Services/TextInsertionService.cs`:

```csharp
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Automation;
using Vozcribe.Models;
using Vozcribe.Utilities;

namespace Vozcribe.Services;

public enum InsertionResult
{
    DirectInsert,
    ClipboardFallback,
    NoTextField
}

public class TextInsertionService
{
    private AutomationElement? _capturedElement;
    private IntPtr _capturedWindowHandle;

    public void CaptureCurrentTextField()
    {
        try
        {
            _capturedElement = AutomationElement.FocusedElement;
            if (_capturedElement != null)
            {
                // Walk up to find the window handle for SetForegroundWindow
                var walker = TreeWalker.ControlViewWalker;
                var current = _capturedElement;
                while (current != null)
                {
                    var handle = current.Current.NativeWindowHandle;
                    if (handle != 0)
                    {
                        _capturedWindowHandle = new IntPtr(handle);
                        break;
                    }
                    current = walker.GetParent(current);
                }
            }
        }
        catch
        {
            _capturedElement = null;
        }
    }

    public InsertionResult InsertText(string text, InsertionMode mode)
    {
        AutomationElement? target;

        if (mode == InsertionMode.WhereStarted)
            target = _capturedElement;
        else
            target = GetCurrentFocusedElement();

        if (target == null)
            return InsertionResult.NoTextField;

        // Try direct insertion via ValuePattern
        if (TryDirectInsert(target, text))
            return InsertionResult.DirectInsert;

        // Fallback: clipboard paste
        if (TryClipboardPaste(target, text))
            return InsertionResult.ClipboardFallback;

        return InsertionResult.NoTextField;
    }

    private static AutomationElement? GetCurrentFocusedElement()
    {
        try { return AutomationElement.FocusedElement; }
        catch { return null; }
    }

    private bool TryDirectInsert(AutomationElement element, string text)
    {
        try
        {
            if (element.TryGetCurrentPattern(ValuePattern.Pattern, out object? pattern))
            {
                var valuePattern = (ValuePattern)pattern;
                if (!valuePattern.Current.IsReadOnly)
                {
                    valuePattern.SetValue(text);

                    // Verify insertion
                    var inserted = valuePattern.Current.Value;
                    return inserted == text;
                }
            }
        }
        catch { /* Fall through to clipboard */ }

        return false;
    }

    private bool TryClipboardPaste(AutomationElement element, string text)
    {
        try
        {
            // Save original clipboard
            string? originalClipboard = null;
            Application.Current.Dispatcher.Invoke(() =>
            {
                if (Clipboard.ContainsText())
                    originalClipboard = Clipboard.GetText();
                Clipboard.SetText(text);
            });

            // Bring target window to front
            if (_capturedWindowHandle != IntPtr.Zero)
                Win32Interop.SetForegroundWindow(_capturedWindowHandle);

            // Try to focus the element
            try { element.SetFocus(); }
            catch { /* Best effort */ }

            Thread.Sleep(50);

            // Simulate Ctrl+V
            SimulateCtrlV();

            Thread.Sleep(100);

            // Restore original clipboard
            Application.Current.Dispatcher.Invoke(() =>
            {
                if (originalClipboard != null)
                    Clipboard.SetText(originalClipboard);
                else
                    Clipboard.Clear();
            });

            return true;
        }
        catch
        {
            return false;
        }
    }

    private static void SimulateCtrlV()
    {
        var inputs = new Win32Interop.INPUT[4];

        // Ctrl down
        inputs[0].type = Win32Interop.INPUT_KEYBOARD;
        inputs[0].u.ki.wVk = (ushort)Win32Interop.VK_CONTROL;

        // V down
        inputs[1].type = Win32Interop.INPUT_KEYBOARD;
        inputs[1].u.ki.wVk = 0x56; // V key

        // V up
        inputs[2].type = Win32Interop.INPUT_KEYBOARD;
        inputs[2].u.ki.wVk = 0x56;
        inputs[2].u.ki.dwFlags = Win32Interop.KEYEVENTF_KEYUP;

        // Ctrl up
        inputs[3].type = Win32Interop.INPUT_KEYBOARD;
        inputs[3].u.ki.wVk = (ushort)Win32Interop.VK_CONTROL;
        inputs[3].u.ki.dwFlags = Win32Interop.KEYEVENTF_KEYUP;

        Win32Interop.SendInput(4, inputs, Marshal.SizeOf<Win32Interop.INPUT>());
    }

    public void ClearCapture()
    {
        _capturedElement = null;
        _capturedWindowHandle = IntPtr.Zero;
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded.

- [ ] **Step 3: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add TextInsertionService with UI Automation direct insert and clipboard fallback"
```

---

### Task 13: Transcription Orchestrator

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Services/TranscriptionOrchestrator.cs`
- Create: `Vozcribe-Windows/Vozcribe/Services/StartupService.cs`

- [ ] **Step 1: Implement TranscriptionOrchestrator**

Create `Vozcribe-Windows/Vozcribe/Services/TranscriptionOrchestrator.cs`:

```csharp
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

    private RecordingState _state = RecordingState.Idle;
    private ModelTier _currentTier;
    private float _currentAmplitude;
    private int _recordingDurationMs;
    private byte[]? _lastAudioData;
    private System.Timers.Timer? _durationTimer;

    public RecordingState State
    {
        get => _state;
        private set => SetField(ref _state, value);
    }

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
            _ = StopAndTranscribeAsync();
        else if (State.Type == RecordingStateType.Idle || State.Type == RecordingStateType.Success
            || State.Type == RecordingStateType.Error)
            StartRecording();
    }

    private void StartRecording()
    {
        // Capture text field before anything else
        _textInsertion.CaptureCurrentTextField();

        State = RecordingState.Recording;
        RecordingDurationMs = 0;
        CurrentAmplitude = 0;

        _audio.StartRecording();

        // Start duration timer
        _durationTimer = new System.Timers.Timer(100);
        _durationTimer.Elapsed += (_, _) => RecordingDurationMs += 100;
        _durationTimer.Start();

        // Warmup endpoints in background
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

        // Trim silence
        var trimResult = SilenceDetector.TrimAudio(result.Samples, Constants.SampleRate);

        // Encode to Opus
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

            // Insert text
            var insertionMode = _settings.Settings.InsertionMode == "where_started"
                ? InsertionMode.WhereStarted : InsertionMode.WhereCurrent;
            var insertResult = _textInsertion.InsertText(transcription.Text, insertionMode);

            State = insertResult switch
            {
                InsertionResult.DirectInsert => RecordingState.Inserted(transcription.Text),
                InsertionResult.ClipboardFallback => RecordingState.CopiedToClipboard(transcription.Text),
                _ => RecordingState.CopiedToClipboard(transcription.Text) // Show text for manual copy
            };

            _lastAudioData = null; // Clear on success
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
```

- [ ] **Step 2: Implement StartupService**

Create `Vozcribe-Windows/Vozcribe/Services/StartupService.cs`:

```csharp
using Microsoft.Win32;

namespace Vozcribe.Services;

public static class StartupService
{
    private const string RegistryKey = @"SOFTWARE\Microsoft\Windows\CurrentVersion\Run";
    private const string AppName = "Vozcribe";

    public static bool IsEnabled
    {
        get
        {
            using var key = Registry.CurrentUser.OpenSubKey(RegistryKey, false);
            return key?.GetValue(AppName) != null;
        }
    }

    public static void SetEnabled(bool enabled)
    {
        using var key = Registry.CurrentUser.OpenSubKey(RegistryKey, true);
        if (key == null) return;

        if (enabled)
        {
            var exePath = Environment.ProcessPath ?? "";
            key.SetValue(AppName, $"\"{exePath}\"");
        }
        else
        {
            key.DeleteValue(AppName, false);
        }
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded.

- [ ] **Step 4: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add TranscriptionOrchestrator and StartupService"
```

---

### Task 14: Recording Overlay UI

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/ViewModels/RecordingOverlayViewModel.cs`
- Create: `Vozcribe-Windows/Vozcribe/Views/RecordingOverlay.xaml`
- Create: `Vozcribe-Windows/Vozcribe/Views/RecordingOverlay.xaml.cs`
- Create: `Vozcribe-Windows/Vozcribe/Converters/BoolToVisibilityConverter.cs`
- Create: `Vozcribe-Windows/Vozcribe/Converters/RecordingStateConverters.cs`

- [ ] **Step 1: Create converters**

Create `Vozcribe-Windows/Vozcribe/Converters/BoolToVisibilityConverter.cs`:

```csharp
using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace Vozcribe.Converters;

public class BoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is true ? Visibility.Visible : Visibility.Collapsed;

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is Visibility.Visible;
}

public class InverseBoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is true ? Visibility.Collapsed : Visibility.Visible;

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is Visibility.Collapsed;
}
```

Create `Vozcribe-Windows/Vozcribe/Converters/RecordingStateConverters.cs`:

```csharp
using System.Globalization;
using System.Windows;
using System.Windows.Data;
using Vozcribe.Models;

namespace Vozcribe.Converters;

public class StateToVisibilityConverter : IValueConverter
{
    public RecordingStateType TargetState { get; set; }

    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is RecordingStateType state)
            return state == TargetState ? Visibility.Visible : Visibility.Collapsed;
        return Visibility.Collapsed;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}
```

- [ ] **Step 2: Create RecordingOverlayViewModel**

Create `Vozcribe-Windows/Vozcribe/ViewModels/RecordingOverlayViewModel.cs`:

```csharp
using System.Windows;
using System.Windows.Input;
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

    // Amplitude bars (8 bars)
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
                    OnPropertyChanged(nameof(Orchestrator));
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
            _orchestrator.ToggleRecording(); // Reset to idle if needed
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
            // Each bar has slightly different height for visual variety
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
        // Default: bottom-right above taskbar
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
```

- [ ] **Step 3: Create RecordingOverlay XAML**

Create `Vozcribe-Windows/Vozcribe/Views/RecordingOverlay.xaml`:

```xml
<Window x:Class="Vozcribe.Views.RecordingOverlay"
        xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="Vozcribe"
        WindowStyle="None"
        AllowsTransparency="True"
        Background="Transparent"
        Topmost="True"
        ShowInTaskbar="False"
        SizeToContent="WidthAndHeight"
        ResizeMode="NoResize"
        MouseLeftButtonDown="Window_MouseLeftButtonDown"
        MouseLeftButtonUp="Window_MouseLeftButtonUp">

    <Border CornerRadius="24" Padding="16,10"
            MinWidth="240" MinHeight="48">
        <Border.Background>
            <SolidColorBrush Color="#1E1E2E" Opacity="0.95"/>
        </Border.Background>
        <Border.Effect>
            <DropShadowEffect BlurRadius="20" Opacity="0.3" ShadowDepth="4"/>
        </Border.Effect>

        <Grid>
            <!-- Recording State -->
            <StackPanel Orientation="Horizontal"
                        Visibility="{Binding IsRecording, Converter={StaticResource BoolToVis}}">
                <!-- Pulsing red dot -->
                <Ellipse Width="10" Height="10" Fill="#FF4444" Margin="0,0,10,0">
                    <Ellipse.Triggers>
                        <EventTrigger RoutedEvent="Loaded">
                            <BeginStoryboard>
                                <Storyboard RepeatBehavior="Forever">
                                    <DoubleAnimation Storyboard.TargetProperty="Opacity"
                                                     From="1" To="0.3" Duration="0:0:0.8"
                                                     AutoReverse="True"/>
                                </Storyboard>
                            </BeginStoryboard>
                        </EventTrigger>
                    </Ellipse.Triggers>
                </Ellipse>

                <!-- Amplitude bars -->
                <ItemsControl x:Name="AmplitudeBarsControl" Margin="0,0,10,0">
                    <ItemsControl.ItemsPanel>
                        <ItemsPanelTemplate>
                            <StackPanel Orientation="Horizontal"/>
                        </ItemsPanelTemplate>
                    </ItemsControl.ItemsPanel>
                </ItemsControl>

                <!-- Duration -->
                <TextBlock Text="{Binding DurationText}" Foreground="White"
                           FontSize="14" VerticalAlignment="Center" Margin="0,0,10,0"/>

                <!-- Model label -->
                <Border CornerRadius="8" Background="#333355" Padding="8,3">
                    <TextBlock Text="{Binding Orchestrator.CurrentTier.ShortName}"
                               Foreground="#AAAACC" FontSize="11"/>
                </Border>
            </StackPanel>

            <!-- Processing State -->
            <StackPanel Orientation="Horizontal"
                        Visibility="{Binding IsProcessing, Converter={StaticResource BoolToVis}}">
                <ProgressBar IsIndeterminate="True" Width="20" Height="20"
                             Style="{StaticResource CircularProgress}" Margin="0,0,10,0"/>
                <TextBlock Text="Transcribing..." Foreground="White" FontSize="14"
                           VerticalAlignment="Center"/>
            </StackPanel>

            <!-- Success State -->
            <StackPanel Visibility="{Binding IsSuccess, Converter={StaticResource BoolToVis}}">
                <StackPanel Orientation="Horizontal">
                    <TextBlock Text="&#x2713;" Foreground="#44BB44" FontSize="16"
                               VerticalAlignment="Center" Margin="0,0,8,0"/>
                    <TextBlock Text="{Binding Orchestrator.State.Text}"
                               Foreground="White" FontSize="13"
                               MaxWidth="280" TextTrimming="CharacterEllipsis"
                               VerticalAlignment="Center"/>
                </StackPanel>
                <Button Content="Copy" Click="CopyButton_Click"
                        Visibility="{Binding ShowCopyButton, Converter={StaticResource BoolToVis}}"
                        Style="{StaticResource OverlayButton}" Margin="0,6,0,0"/>
            </StackPanel>

            <!-- Error State -->
            <StackPanel Visibility="{Binding IsError, Converter={StaticResource BoolToVis}}">
                <TextBlock Text="{Binding Orchestrator.State.Text}"
                           Foreground="#FF6666" FontSize="13"
                           MaxWidth="280" TextWrapping="Wrap"/>
                <StackPanel Orientation="Horizontal" Margin="0,6,0,0">
                    <Button Content="Retry" Click="RetryButton_Click"
                            Style="{StaticResource OverlayButton}" Margin="0,0,8,0"/>
                    <Button Content="Save" Click="SaveButton_Click"
                            Style="{StaticResource OverlayButton}"/>
                </StackPanel>
            </StackPanel>
        </Grid>
    </Border>
</Window>
```

- [ ] **Step 4: Create RecordingOverlay code-behind**

Create `Vozcribe-Windows/Vozcribe/Views/RecordingOverlay.xaml.cs`:

```csharp
using System.Windows;
using System.Windows.Input;
using Vozcribe.ViewModels;

namespace Vozcribe.Views;

public partial class RecordingOverlay : Window
{
    private RecordingOverlayViewModel ViewModel => (RecordingOverlayViewModel)DataContext;

    public RecordingOverlay()
    {
        InitializeComponent();
    }

    public void Initialize(RecordingOverlayViewModel viewModel)
    {
        DataContext = viewModel;

        var (x, y) = viewModel.GetSavedPosition();
        Left = x;
        Top = y;

        viewModel.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(RecordingOverlayViewModel.IsVisible))
            {
                Dispatcher.Invoke(() =>
                {
                    if (viewModel.IsVisible) Show();
                    else Hide();
                });
            }
        };
    }

    private void Window_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        DragMove();
    }

    private void Window_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        ViewModel.SavePosition(Left, Top);
    }

    private void CopyButton_Click(object sender, RoutedEventArgs e)
    {
        if (ViewModel.Orchestrator.State.Text != null)
            Clipboard.SetText(ViewModel.Orchestrator.State.Text);
    }

    private void RetryButton_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.Orchestrator.ToggleRecording();
    }

    private void SaveButton_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.SaveForLater();
    }
}
```

- [ ] **Step 5: Update Styles.xaml with overlay styles**

Replace `Vozcribe-Windows/Vozcribe/Resources/Styles.xaml`:

```xml
<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
                    xmlns:converters="clr-namespace:Vozcribe.Converters">

    <converters:BoolToVisibilityConverter x:Key="BoolToVis"/>
    <converters:InverseBoolToVisibilityConverter x:Key="InverseBoolToVis"/>

    <!-- Overlay Button -->
    <Style x:Key="OverlayButton" TargetType="Button">
        <Setter Property="Background" Value="#444466"/>
        <Setter Property="Foreground" Value="White"/>
        <Setter Property="BorderThickness" Value="0"/>
        <Setter Property="Padding" Value="12,4"/>
        <Setter Property="Cursor" Value="Hand"/>
        <Setter Property="Template">
            <Setter.Value>
                <ControlTemplate TargetType="Button">
                    <Border Background="{TemplateBinding Background}"
                            CornerRadius="8" Padding="{TemplateBinding Padding}">
                        <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center"/>
                    </Border>
                    <ControlTemplate.Triggers>
                        <Trigger Property="IsMouseOver" Value="True">
                            <Setter Property="Background" Value="#555577"/>
                        </Trigger>
                    </ControlTemplate.Triggers>
                </ControlTemplate>
            </Setter.Value>
        </Setter>
    </Style>

    <!-- Circular Progress (simple) -->
    <Style x:Key="CircularProgress" TargetType="ProgressBar">
        <Setter Property="Foreground" Value="#7777FF"/>
        <Setter Property="IsIndeterminate" Value="True"/>
    </Style>
</ResourceDictionary>
```

- [ ] **Step 6: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded (may have XAML binding warnings, acceptable).

- [ ] **Step 7: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add RecordingOverlay with draggable pill UI and auto-dismiss"
```

---

### Task 15: System Tray Popup UI

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/ViewModels/TrayPopupViewModel.cs`
- Create: `Vozcribe-Windows/Vozcribe/Views/TrayPopup.xaml`
- Create: `Vozcribe-Windows/Vozcribe/Views/TrayPopup.xaml.cs`

- [ ] **Step 1: Create TrayPopupViewModel**

Create `Vozcribe-Windows/Vozcribe/ViewModels/TrayPopupViewModel.cs`:

```csharp
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

    public int CreditsRemaining
    {
        get => _creditsRemaining;
        set => SetField(ref _creditsRemaining, value);
    }

    public int CreditsLimit
    {
        get => _creditsLimit;
        set => SetField(ref _creditsLimit, value);
    }

    public string CreditsDisplay => $"{CreditsRemaining} / {CreditsLimit}";

    public double CreditsProgress => CreditsLimit > 0
        ? (double)CreditsRemaining / CreditsLimit : 0;

    public string PlanDisplay
    {
        get => _planDisplay;
        set => SetField(ref _planDisplay, value);
    }

    public string HotkeyDisplay
    {
        get
        {
            var id = _settings.Settings.HotkeyId;
            return HotkeyOption.FromId(id).DisplayName;
        }
    }

    public List<PendingTranscription> PendingItems
    {
        get => _pendingItems;
        set => SetField(ref _pendingItems, value);
    }

    public int PendingCount => PendingItems.Count;

    public bool IsLoading
    {
        get => _isLoading;
        set => SetField(ref _isLoading, value);
    }

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
            CreditsRemaining = status.FreeCreditsRemaining;
            CreditsLimit = status.FreeTierCredits > 0 ? status.FreeTierCredits : Constants.DefaultFreeCredits;
            OnPropertyChanged(nameof(CreditsDisplay));
            OnPropertyChanged(nameof(CreditsProgress));
        }
        catch { /* Silent fail — show cached values */ }
        finally { IsLoading = false; }

        PendingItems = _pendingService.LoadAll();
        OnPropertyChanged(nameof(PendingCount));
    }

    public async Task RetryPendingAsync(PendingTranscription item)
    {
        await _orchestrator.RetryPendingAsync(item);
        PendingItems = _pendingService.LoadAll();
        OnPropertyChanged(nameof(PendingCount));
    }
}
```

- [ ] **Step 2: Create TrayPopup XAML**

Create `Vozcribe-Windows/Vozcribe/Views/TrayPopup.xaml`:

```xml
<UserControl x:Class="Vozcribe.Views.TrayPopup"
             xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
             xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
             Width="300">

    <Border Background="#1E1E2E" CornerRadius="12" Padding="16"
            BorderBrush="#333355" BorderThickness="1">
        <Border.Effect>
            <DropShadowEffect BlurRadius="20" Opacity="0.4" ShadowDepth="4"/>
        </Border.Effect>

        <StackPanel>
            <!-- Status -->
            <StackPanel Orientation="Horizontal" Margin="0,0,0,12">
                <Ellipse Width="8" Height="8" Margin="0,0,8,0">
                    <Ellipse.Style>
                        <Style TargetType="Ellipse">
                            <Setter Property="Fill" Value="#44BB44"/>
                        </Style>
                    </Ellipse.Style>
                </Ellipse>
                <TextBlock Text="{Binding StatusText}" Foreground="White" FontSize="14" FontWeight="SemiBold"/>
            </StackPanel>

            <!-- Model Selection -->
            <TextBlock Text="Model" Foreground="#888899" FontSize="11" Margin="0,0,0,4"/>
            <ComboBox ItemsSource="{Binding AllTiers}"
                      SelectedItem="{Binding CurrentTier}"
                      DisplayMemberPath="DisplayName"
                      SelectionChanged="ModelComboBox_SelectionChanged"
                      Margin="0,0,0,12"
                      Background="#2A2A3E" Foreground="White" BorderBrush="#444466"/>

            <!-- Credits -->
            <TextBlock Text="Credits" Foreground="#888899" FontSize="11" Margin="0,0,0,4"/>
            <ProgressBar Value="{Binding CreditsProgress}" Maximum="1"
                         Height="6" Margin="0,0,0,4"
                         Foreground="#7777FF" Background="#333355"/>
            <TextBlock Text="{Binding CreditsDisplay}" Foreground="#AAAACC"
                       FontSize="12" Margin="0,0,0,12"/>

            <!-- Hotkey Reminder -->
            <Border Background="#2A2A3E" CornerRadius="8" Padding="10,6" Margin="0,0,0,12">
                <StackPanel Orientation="Horizontal">
                    <TextBlock Text="Hotkey: " Foreground="#888899" FontSize="12"/>
                    <TextBlock Text="{Binding HotkeyDisplay}" Foreground="White" FontSize="12" FontWeight="SemiBold"/>
                </StackPanel>
            </Border>

            <!-- Pending Transcriptions -->
            <StackPanel Visibility="{Binding HasPending, Converter={StaticResource BoolToVis}}"
                        Margin="0,0,0,12">
                <TextBlock Foreground="#FFAA44" FontSize="12" Margin="0,0,0,6">
                    <Run Text="{Binding PendingCount, Mode=OneWay}"/>
                    <Run Text=" pending transcription(s)"/>
                </TextBlock>
                <ItemsControl ItemsSource="{Binding PendingItems}" MaxHeight="120">
                    <ItemsControl.ItemTemplate>
                        <DataTemplate>
                            <Border Background="#2A2A3E" CornerRadius="6" Padding="8,4" Margin="0,2">
                                <StackPanel Orientation="Horizontal">
                                    <TextBlock Text="{Binding Error}" Foreground="#AAAACC"
                                               FontSize="11" MaxWidth="180" TextTrimming="CharacterEllipsis"
                                               VerticalAlignment="Center"/>
                                    <Button Content="Retry" Click="RetryPending_Click"
                                            Tag="{Binding}" Style="{StaticResource OverlayButton}"
                                            FontSize="11" Margin="8,0,0,0" Padding="8,2"/>
                                </StackPanel>
                            </Border>
                        </DataTemplate>
                    </ItemsControl.ItemTemplate>
                </ItemsControl>
            </StackPanel>

            <!-- Separator -->
            <Separator Background="#333355" Margin="0,0,0,8"/>

            <!-- Actions -->
            <StackPanel Orientation="Horizontal" HorizontalAlignment="Right">
                <Button Content="Settings" Click="Settings_Click"
                        Style="{StaticResource OverlayButton}" Margin="0,0,8,0"/>
                <Button Content="Quit" Click="Quit_Click"
                        Style="{StaticResource OverlayButton}"/>
            </StackPanel>
        </StackPanel>
    </Border>
</UserControl>
```

- [ ] **Step 3: Create TrayPopup code-behind**

Create `Vozcribe-Windows/Vozcribe/Views/TrayPopup.xaml.cs`:

```csharp
using System.Windows;
using System.Windows.Controls;
using Vozcribe.Models;
using Vozcribe.ViewModels;

namespace Vozcribe.Views;

public partial class TrayPopup : UserControl
{
    public event Action? SettingsRequested;
    public event Action? QuitRequested;

    private TrayPopupViewModel ViewModel => (TrayPopupViewModel)DataContext;

    public TrayPopup()
    {
        InitializeComponent();
    }

    private void ModelComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (e.AddedItems.Count > 0 && e.AddedItems[0] is ModelTier tier)
            ViewModel.SetModelTier(tier);
    }

    private async void RetryPending_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is PendingTranscription item)
            await ViewModel.RetryPendingAsync(item);
    }

    private void Settings_Click(object sender, RoutedEventArgs e)
    {
        SettingsRequested?.Invoke();
    }

    private void Quit_Click(object sender, RoutedEventArgs e)
    {
        QuitRequested?.Invoke();
    }
}
```

- [ ] **Step 4: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded.

- [ ] **Step 5: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add system tray popup with status, model picker, credits, and pending list"
```

---

### Task 16: Settings Window UI

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/ViewModels/SettingsViewModel.cs`
- Create: `Vozcribe-Windows/Vozcribe/Views/SettingsWindow.xaml`
- Create: `Vozcribe-Windows/Vozcribe/Views/SettingsWindow.xaml.cs`

- [ ] **Step 1: Create SettingsViewModel**

Create `Vozcribe-Windows/Vozcribe/ViewModels/SettingsViewModel.cs`:

```csharp
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
    public string[] Regions => Constants.Regions;
    public string[] RegionDisplayNames => Constants.RegionDisplayNames;

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

    public bool IsCapturingHotkey
    {
        get => _isCapturingHotkey;
        set => SetField(ref _isCapturingHotkey, value);
    }

    public string CaptureHotkeyText
    {
        get => _captureHotkeyText;
        set => SetField(ref _captureHotkeyText, value);
    }

    public InsertionMode InsertionMode
    {
        get => _settings.Settings.InsertionMode == "where_started"
            ? InsertionMode.WhereStarted : InsertionMode.WhereCurrent;
        set
        {
            _settings.Settings.InsertionMode = value == InsertionMode.WhereStarted
                ? "where_started" : "where_current";
            _settings.Save();
            OnPropertyChanged();
        }
    }

    public ModelTier SelectedModelTier
    {
        get => ModelTier.FromTierCode(_settings.Settings.ModelTierCode);
        set
        {
            _settings.Settings.ModelTierCode = value.TierCode;
            _settings.Save();
            OnPropertyChanged();
        }
    }

    public int SelectedRegionIndex
    {
        get => Array.IndexOf(Constants.Regions, _settings.Settings.Region);
        set
        {
            if (value >= 0 && value < Constants.Regions.Length)
            {
                _settings.Settings.Region = Constants.Regions[value];
                _settings.Save();
                OnPropertyChanged();
            }
        }
    }

    public bool LaunchAtStartup
    {
        get => _settings.Settings.LaunchAtStartup;
        set
        {
            _settings.Settings.LaunchAtStartup = value;
            StartupService.SetEnabled(value);
            _settings.Save();
            OnPropertyChanged();
        }
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
```

- [ ] **Step 2: Create SettingsWindow XAML**

Create `Vozcribe-Windows/Vozcribe/Views/SettingsWindow.xaml`:

```xml
<Window x:Class="Vozcribe.Views.SettingsWindow"
        xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="Vozcribe Settings"
        Width="420" Height="520"
        WindowStartupLocation="CenterScreen"
        ResizeMode="NoResize"
        Background="#1E1E2E">

    <ScrollViewer VerticalScrollBarVisibility="Auto" Padding="24">
        <StackPanel>
            <!-- Header -->
            <TextBlock Text="Settings" Foreground="White" FontSize="22"
                       FontWeight="Bold" Margin="0,0,0,24"/>

            <!-- Shortcuts Section -->
            <TextBlock Text="SHORTCUTS" Foreground="#7777FF" FontSize="11"
                       FontWeight="Bold" Margin="0,0,0,8"/>

            <TextBlock Text="Hotkey" Foreground="#AAAACC" FontSize="13" Margin="0,0,0,4"/>
            <ComboBox ItemsSource="{Binding HotkeyPresets}"
                      SelectedItem="{Binding SelectedHotkey}"
                      DisplayMemberPath="DisplayName"
                      Background="#2A2A3E" Foreground="White" BorderBrush="#444466"
                      Margin="0,0,0,8"/>
            <Button Content="{Binding CaptureHotkeyText}"
                    Click="CaptureHotkey_Click"
                    KeyDown="CaptureHotkey_KeyDown"
                    Style="{StaticResource OverlayButton}" Margin="0,0,0,12"/>

            <TextBlock Text="Text Insertion" Foreground="#AAAACC" FontSize="13" Margin="0,0,0,4"/>
            <RadioButton Content="Paste where I started recording"
                         IsChecked="{Binding IsWhereStarted}"
                         Foreground="White" Margin="0,0,0,4"
                         GroupName="InsertionMode"/>
            <RadioButton Content="Paste in currently active field"
                         IsChecked="{Binding IsWhereCurrent}"
                         Foreground="White" Margin="0,0,0,16"
                         GroupName="InsertionMode"/>

            <!-- Transcription Section -->
            <TextBlock Text="TRANSCRIPTION" Foreground="#7777FF" FontSize="11"
                       FontWeight="Bold" Margin="0,0,0,8"/>

            <TextBlock Text="Model" Foreground="#AAAACC" FontSize="13" Margin="0,0,0,4"/>
            <ComboBox ItemsSource="{Binding ModelTiers}"
                      SelectedItem="{Binding SelectedModelTier}"
                      DisplayMemberPath="DisplayName"
                      Background="#2A2A3E" Foreground="White" BorderBrush="#444466"
                      Margin="0,0,0,8"/>

            <TextBlock Text="Region" Foreground="#AAAACC" FontSize="13" Margin="0,0,0,4"/>
            <ComboBox ItemsSource="{Binding RegionDisplayNames}"
                      SelectedIndex="{Binding SelectedRegionIndex}"
                      Background="#2A2A3E" Foreground="White" BorderBrush="#444466"
                      Margin="0,0,0,16"/>

            <!-- General Section -->
            <TextBlock Text="GENERAL" Foreground="#7777FF" FontSize="11"
                       FontWeight="Bold" Margin="0,0,0,8"/>

            <CheckBox Content="Launch at startup"
                      IsChecked="{Binding LaunchAtStartup}"
                      Foreground="White" Margin="0,0,0,8"/>
            <Button Content="Reset Overlay Position" Click="ResetOverlay_Click"
                    Style="{StaticResource OverlayButton}" Margin="0,0,0,16"/>

            <!-- Account Section -->
            <TextBlock Text="ACCOUNT" Foreground="#7777FF" FontSize="11"
                       FontWeight="Bold" Margin="0,0,0,8"/>

            <TextBlock Text="{Binding UserEmail}" Foreground="White" FontSize="13" Margin="0,0,0,8"/>
            <Button Content="Sign Out" Click="SignOut_Click"
                    Style="{StaticResource OverlayButton}"
                    Visibility="{Binding IsSignedIn, Converter={StaticResource BoolToVis}}"/>
        </StackPanel>
    </ScrollViewer>
</Window>
```

- [ ] **Step 3: Create SettingsWindow code-behind**

Create `Vozcribe-Windows/Vozcribe/Views/SettingsWindow.xaml.cs`:

```csharp
using System.Windows;
using System.Windows.Input;
using Vozcribe.ViewModels;

namespace Vozcribe.Views;

public partial class SettingsWindow : Window
{
    private SettingsViewModel ViewModel => (SettingsViewModel)DataContext;

    public SettingsWindow(SettingsViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }

    private void CaptureHotkey_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.StartCapturingHotkey();
    }

    private void CaptureHotkey_KeyDown(object sender, KeyEventArgs e)
    {
        if (!ViewModel.IsCapturingHotkey) return;
        if (e.Key == Key.Escape)
        {
            ViewModel.IsCapturingHotkey = false;
            ViewModel.CaptureHotkeyText = "Record Custom Shortcut";
            return;
        }

        // Ignore modifier-only presses
        if (e.Key == Key.LeftCtrl || e.Key == Key.RightCtrl ||
            e.Key == Key.LeftAlt || e.Key == Key.RightAlt ||
            e.Key == Key.LeftShift || e.Key == Key.RightShift ||
            e.Key == Key.LWin || e.Key == Key.RWin)
            return;

        ViewModel.CaptureHotkey(Keyboard.Modifiers, e.Key);
        e.Handled = true;
    }

    private void ResetOverlay_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.ResetOverlayPosition();
    }

    private void SignOut_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.SignOut();
    }
}
```

- [ ] **Step 4: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded.

- [ ] **Step 5: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add SettingsWindow with hotkey, model, region, insertion, and account controls"
```

---

### Task 17: Onboarding Window

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/ViewModels/OnboardingViewModel.cs`
- Create: `Vozcribe-Windows/Vozcribe/Views/OnboardingWindow.xaml`
- Create: `Vozcribe-Windows/Vozcribe/Views/OnboardingWindow.xaml.cs`

- [ ] **Step 1: Create OnboardingViewModel**

Create `Vozcribe-Windows/Vozcribe/ViewModels/OnboardingViewModel.cs`:

```csharp
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

    public int CurrentStep
    {
        get => _currentStep;
        set => SetField(ref _currentStep, value);
    }

    public bool IsSigningIn
    {
        get => _isSigningIn;
        set => SetField(ref _isSigningIn, value);
    }

    public bool MicPermissionGranted
    {
        get => _micPermissionGranted;
        set => SetField(ref _micPermissionGranted, value);
    }

    public string? SignInError
    {
        get => _signInError;
        set => SetField(ref _signInError, value);
    }

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
        }
    }

    public void CheckMicPermission()
    {
        // On Windows, mic access is granted per-app on first use
        // We just check if any audio capture device is available
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
```

- [ ] **Step 2: Create OnboardingWindow XAML**

Create `Vozcribe-Windows/Vozcribe/Views/OnboardingWindow.xaml`:

```xml
<Window x:Class="Vozcribe.Views.OnboardingWindow"
        xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="Welcome to Vozcribe"
        Width="480" Height="420"
        WindowStartupLocation="CenterScreen"
        ResizeMode="NoResize"
        Background="#1E1E2E">

    <Grid Margin="32">
        <!-- Step 1: Welcome + Sign In -->
        <StackPanel x:Name="Step1" VerticalAlignment="Center">
            <TextBlock Text="Welcome to Vozcribe" Foreground="White"
                       FontSize="26" FontWeight="Bold" HorizontalAlignment="Center"
                       Margin="0,0,0,8"/>
            <TextBlock Text="Voice to text, everywhere."
                       Foreground="#AAAACC" FontSize="14" HorizontalAlignment="Center"
                       Margin="0,0,0,32"/>

            <Button Content="Sign in with Google" Click="SignIn_Click"
                    HorizontalAlignment="Center" Padding="24,10"
                    FontSize="15" IsEnabled="{Binding IsNotSigningIn}">
                <Button.Style>
                    <Style TargetType="Button">
                        <Setter Property="Background" Value="#4285F4"/>
                        <Setter Property="Foreground" Value="White"/>
                        <Setter Property="BorderThickness" Value="0"/>
                        <Setter Property="Cursor" Value="Hand"/>
                        <Setter Property="Template">
                            <Setter.Value>
                                <ControlTemplate TargetType="Button">
                                    <Border Background="{TemplateBinding Background}"
                                            CornerRadius="8" Padding="{TemplateBinding Padding}">
                                        <ContentPresenter HorizontalAlignment="Center"/>
                                    </Border>
                                </ControlTemplate>
                            </Setter.Value>
                        </Setter>
                    </Style>
                </Button.Style>
            </Button>

            <TextBlock Text="{Binding SignInError}" Foreground="#FF6666"
                       FontSize="12" HorizontalAlignment="Center" Margin="0,12,0,0"
                       Visibility="{Binding HasSignInError, Converter={StaticResource BoolToVis}}"/>
        </StackPanel>

        <!-- Step 2: Microphone Permission -->
        <StackPanel x:Name="Step2" VerticalAlignment="Center" Visibility="Collapsed">
            <TextBlock Text="Microphone Access" Foreground="White"
                       FontSize="22" FontWeight="Bold" HorizontalAlignment="Center"
                       Margin="0,0,0,8"/>
            <TextBlock Text="Vozcribe needs microphone access to record your voice."
                       Foreground="#AAAACC" FontSize="14" HorizontalAlignment="Center"
                       TextWrapping="Wrap" TextAlignment="Center" Margin="0,0,0,24"/>

            <TextBlock x:Name="MicStatus" HorizontalAlignment="Center"
                       FontSize="14" Margin="0,0,0,24"/>

            <Button Content="Next" Click="MicNext_Click"
                    HorizontalAlignment="Center" Style="{StaticResource OverlayButton}"
                    Padding="32,10" FontSize="15"/>
        </StackPanel>

        <!-- Step 3: Hotkey + Done -->
        <StackPanel x:Name="Step3" VerticalAlignment="Center" Visibility="Collapsed">
            <TextBlock Text="Choose Your Hotkey" Foreground="White"
                       FontSize="22" FontWeight="Bold" HorizontalAlignment="Center"
                       Margin="0,0,0,8"/>
            <TextBlock Text="Press this shortcut to start and stop recording."
                       Foreground="#AAAACC" FontSize="14" HorizontalAlignment="Center"
                       Margin="0,0,0,24"/>

            <ComboBox ItemsSource="{Binding HotkeyPresets}"
                      DisplayMemberPath="DisplayName"
                      SelectionChanged="Hotkey_SelectionChanged"
                      SelectedIndex="0"
                      HorizontalAlignment="Center" Width="200"
                      Background="#2A2A3E" Foreground="White" BorderBrush="#444466"
                      Margin="0,0,0,24"/>

            <Button Content="Start Using Vozcribe" Click="Complete_Click"
                    HorizontalAlignment="Center" Padding="24,10" FontSize="15">
                <Button.Style>
                    <Style TargetType="Button">
                        <Setter Property="Background" Value="#44BB44"/>
                        <Setter Property="Foreground" Value="White"/>
                        <Setter Property="BorderThickness" Value="0"/>
                        <Setter Property="Cursor" Value="Hand"/>
                        <Setter Property="Template">
                            <Setter.Value>
                                <ControlTemplate TargetType="Button">
                                    <Border Background="{TemplateBinding Background}"
                                            CornerRadius="8" Padding="{TemplateBinding Padding}">
                                        <ContentPresenter HorizontalAlignment="Center"/>
                                    </Border>
                                </ControlTemplate>
                            </Setter.Value>
                        </Setter>
                    </Style>
                </Button.Style>
            </Button>
        </StackPanel>
    </Grid>
</Window>
```

- [ ] **Step 3: Create OnboardingWindow code-behind**

Create `Vozcribe-Windows/Vozcribe/Views/OnboardingWindow.xaml.cs`:

```csharp
using System.Windows;
using System.Windows.Controls;
using Vozcribe.Models;
using Vozcribe.ViewModels;

namespace Vozcribe.Views;

public partial class OnboardingWindow : Window
{
    private OnboardingViewModel ViewModel => (OnboardingViewModel)DataContext;

    public bool Completed { get; private set; }

    public OnboardingWindow(OnboardingViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }

    private async void SignIn_Click(object sender, RoutedEventArgs e)
    {
        await ViewModel.SignInAsync(Constants.GoogleClientId, Constants.FirebaseApiKey);
        if (ViewModel.IsSignedIn)
        {
            Step1.Visibility = Visibility.Collapsed;
            Step2.Visibility = Visibility.Visible;
            ViewModel.CheckMicPermission();
            UpdateMicStatus();
        }
    }

    private void UpdateMicStatus()
    {
        if (ViewModel.MicPermissionGranted)
        {
            MicStatus.Text = "Microphone detected";
            MicStatus.Foreground = new System.Windows.Media.SolidColorBrush(
                System.Windows.Media.Color.FromRgb(0x44, 0xBB, 0x44));
        }
        else
        {
            MicStatus.Text = "No microphone found. Please connect one.";
            MicStatus.Foreground = new System.Windows.Media.SolidColorBrush(
                System.Windows.Media.Color.FromRgb(0xFF, 0x66, 0x66));
        }
    }

    private void MicNext_Click(object sender, RoutedEventArgs e)
    {
        Step2.Visibility = Visibility.Collapsed;
        Step3.Visibility = Visibility.Visible;
    }

    private void Hotkey_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (e.AddedItems.Count > 0 && e.AddedItems[0] is HotkeyOption hotkey)
            ViewModel.SelectHotkey(hotkey);
    }

    private void Complete_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.CompleteOnboarding();
        Completed = true;
        Close();
    }
}
```

- [ ] **Step 4: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded.

- [ ] **Step 5: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add OnboardingWindow with 3-step wizard"
```

---

### Task 18: Wire Everything Together in App.xaml.cs

**Files:**
- Modify: `Vozcribe-Windows/Vozcribe/App.xaml.cs`

- [ ] **Step 1: Update App.xaml.cs to bootstrap all services and UI**

Replace `Vozcribe-Windows/Vozcribe/App.xaml.cs`:

```csharp
using System;
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

    // Services
    private SettingsService _settings = null!;
    private AuthService _auth = null!;
    private ApiClient _api = null!;
    private AudioService _audio = null!;
    private HotkeyService _hotkey = null!;
    private TextInsertionService _textInsertion = null!;
    private PendingTranscriptionService _pending = null!;
    private TranscriptionOrchestrator _orchestrator = null!;

    // Views
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
        InitializeServices();

        // Try to restore previous session
        _auth.TryRestoreSession();

        if (!_settings.Settings.HasCompletedOnboarding || !_auth.IsSignedIn)
        {
            ShowOnboarding();
        }
        else
        {
            StartApp();
        }
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

        var hotkeyOption = HotkeyOption.FromId(_settings.Settings.HotkeyId);
        _hotkey = new HotkeyService(hotkeyOption);

        _orchestrator = new TranscriptionOrchestrator(
            _audio, _api, _textInsertion, _pending, _settings);
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
        // Set up hotkey events
        _hotkey.HotkeyTriggered += () =>
            Dispatcher.Invoke(() => _orchestrator.ToggleRecording());
        _hotkey.ModelNextTriggered += () =>
            Dispatcher.Invoke(() => _orchestrator.CycleModelNext());
        _hotkey.ModelPreviousTriggered += () =>
            Dispatcher.Invoke(() => _orchestrator.CycleModelPrevious());
        _hotkey.Start();

        // Create overlay
        var overlayVm = new RecordingOverlayViewModel(_orchestrator, _settings);
        _overlay = new RecordingOverlay();
        _overlay.Initialize(overlayVm);

        // Create tray popup
        var trayVm = new TrayPopupViewModel(_orchestrator, _auth, _api, _pending, _settings);
        _trayPopup = new TrayPopup { DataContext = trayVm };
        _trayPopup.SettingsRequested += ShowSettings;
        _trayPopup.QuitRequested += () => Shutdown();

        // Create tray icon
        _trayIcon = new TaskbarIcon
        {
            ToolTipText = "Vozcribe",
            TrayPopup = _trayPopup
        };

        // TODO: Set icon from resources once icon files are added
        // _trayIcon.IconSource = new BitmapImage(new Uri("pack://application:,,,/Resources/Icons/tray-icon.ico"));

        // Refresh status on popup open
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
```

- [ ] **Step 2: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded (may have warnings about nullable references, acceptable).

- [ ] **Step 3: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: wire all services and UI together in App.xaml.cs"
```

---

### Task 19: Add Tray Icon Assets

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/Resources/Icons/tray-icon.ico`
- Modify: `Vozcribe-Windows/Vozcribe/App.xaml.cs` (uncomment icon line)

- [ ] **Step 1: Create a placeholder tray icon**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows/Vozcribe/Resources/Icons
# Create a simple 16x16 ICO from a solid color using ImageMagick if available,
# or just note that real icons need to be designed
# For now, create a minimal valid .ico file
python3 -c "
import struct
# Minimal 16x16 32-bit ICO
width, height = 16, 16
pixels = b'\\xff\\x77\\x77\\xff' * width * height  # BGRA coral color
and_mask = b'\\x00\\x00' * height * 2
bmp_size = 40 + len(pixels) + len(and_mask)
ico = struct.pack('<HHH', 0, 1, 1)  # ICO header
ico += struct.pack('<BBBBHHII', width, height, 0, 0, 1, 32, bmp_size, 22)
ico += struct.pack('<IiiHH', 40, width, height*2, 1, 32, ) + b'\\x00'*8 + struct.pack('<ii', 0, 0)
ico += pixels + and_mask
with open('tray-icon.ico', 'wb') as f: f.write(ico)
" 2>/dev/null || echo "Icon generation skipped - add real icons manually"
```

Note: Real icons should be designed and added. For now the app will work without a custom icon (uses default).

- [ ] **Step 2: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "chore: add placeholder tray icon assets"
```

---

### Task 20: Integration Build and Smoke Test

**Files:** None new — verification only.

- [ ] **Step 1: Full build**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build --configuration Release
```

Expected: Build succeeded.

- [ ] **Step 2: Run all tests**

```bash
dotnet test
```

Expected: All tests pass.

- [ ] **Step 3: Fix any build errors or test failures**

Address any issues found in steps 1-2.

- [ ] **Step 4: Verify project structure**

```bash
ls -R Vozcribe/Models/ Vozcribe/Services/ Vozcribe/ViewModels/ Vozcribe/Views/
```

Expected: All planned files exist.

- [ ] **Step 5: Commit any fixes**

```bash
git add Vozcribe-Windows/
git commit -m "fix: resolve build errors and ensure all tests pass"
```

---

### Task 21: Configuration Setup

**Files:**
- Create: `Vozcribe-Windows/Vozcribe/appsettings.json`
- Modify: `Vozcribe-Windows/Vozcribe/Models/Constants.cs`

- [ ] **Step 1: Create appsettings.json for Firebase config**

Create `Vozcribe-Windows/Vozcribe/appsettings.json`:

```json
{
  "Firebase": {
    "ApiKey": "",
    "GoogleClientId": ""
  }
}
```

Note: Values must be filled in from the Firebase console before the app can authenticate. Add to `.gitignore`.

- [ ] **Step 2: Add appsettings.json to .gitignore**

Append to `Vozcribe-Windows/.gitignore`:

```
appsettings.json
```

- [ ] **Step 3: Create appsettings.example.json**

Create `Vozcribe-Windows/Vozcribe/appsettings.example.json`:

```json
{
  "Firebase": {
    "ApiKey": "your-firebase-web-api-key",
    "GoogleClientId": "your-google-oauth-client-id"
  }
}
```

- [ ] **Step 4: Update Constants.cs to load from config**

Update `Vozcribe-Windows/Vozcribe/Models/Constants.cs` — replace the hardcoded Firebase constants:

```csharp
// Auth — loaded from appsettings.json at runtime
public static string FirebaseApiKey { get; set; } = "";
public static string GoogleClientId { get; set; } = "";
```

- [ ] **Step 5: Load config in App.xaml.cs**

Add at the start of `InitializeServices()` in `App.xaml.cs`:

```csharp
// Load Firebase config
var configPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "appsettings.json");
if (File.Exists(configPath))
{
    var configJson = File.ReadAllText(configPath);
    var config = JsonSerializer.Deserialize<JsonElement>(configJson);
    if (config.TryGetProperty("Firebase", out var firebase))
    {
        Constants.FirebaseApiKey = firebase.GetProperty("ApiKey").GetString() ?? "";
        Constants.GoogleClientId = firebase.GetProperty("GoogleClientId").GetString() ?? "";
    }
}
```

- [ ] **Step 6: Build to verify**

```bash
cd /Users/ijas/Documents/whisperType/Vozcribe-Windows
dotnet build
```

Expected: Build succeeded.

- [ ] **Step 7: Commit**

```bash
git add Vozcribe-Windows/
git commit -m "feat: add appsettings.json config for Firebase API key and Google client ID"
```

---

## Summary

| Task | Component | Tests |
|------|-----------|-------|
| 1 | Project scaffolding | - |
| 2 | Models & Constants | 6 |
| 3 | Settings Service | 3 |
| 4 | ViewModelBase | - |
| 5 | Silence Detector | 6 |
| 6 | Opus Encoder | 2 |
| 7 | Audio Service | - |
| 8 | API Client | 4 |
| 9 | Auth Service | 4 |
| 10 | Pending Transcription Service | 4 |
| 11 | Hotkey Service | - |
| 12 | Text Insertion Service | - |
| 13 | Transcription Orchestrator + Startup Service | - |
| 14 | Recording Overlay UI | - |
| 15 | System Tray Popup UI | - |
| 16 | Settings Window UI | - |
| 17 | Onboarding Window | - |
| 18 | App bootstrap wiring | - |
| 19 | Tray icon assets | - |
| 20 | Integration build + smoke test | All |
| 21 | Configuration setup | - |

**Total: 21 tasks, ~29 unit tests, full end-to-end app.**
