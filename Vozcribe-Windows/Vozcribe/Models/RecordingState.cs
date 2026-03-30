namespace Vozcribe.Models;

public enum RecordingStateType
{
    Idle, Recording, Processing, Success, Error
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
