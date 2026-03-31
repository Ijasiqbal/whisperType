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

    public static HotkeyOption FromSettings(string id, int? customModifiers, int? customKey)
    {
        if (id == "custom" && customModifiers.HasValue && customKey.HasValue)
            return Custom((ModifierKeys)customModifiers.Value, (Key)customKey.Value);
        return FromId(id);
    }

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
