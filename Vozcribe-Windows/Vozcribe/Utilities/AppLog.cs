using System;
using System.Diagnostics;
using System.IO;

namespace Vozcribe.Utilities;

public static class AppLog
{
    private static readonly string LogPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "Vozcribe", "debug.log");

    static AppLog()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(LogPath)!);
    }

    [Conditional("DEBUG")]
    public static void Write(string message)
    {
        try
        {
            File.AppendAllText(LogPath, $"[{DateTime.Now:HH:mm:ss.fff}] {message}{Environment.NewLine}");
        }
        catch { }
    }
}
