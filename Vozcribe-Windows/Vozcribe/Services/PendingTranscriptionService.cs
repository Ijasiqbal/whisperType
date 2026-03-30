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
            catch { }
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
