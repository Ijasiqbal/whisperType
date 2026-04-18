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
        AppSettings settings;
        if (!File.Exists(_filePath))
            settings = new AppSettings();
        else
        {
            var json = File.ReadAllText(_filePath);
            settings = JsonSerializer.Deserialize<AppSettings>(json) ?? new AppSettings();
        }
        settings.Region = Constants.DetectedRegion;
        return settings;
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
