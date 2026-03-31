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
    private bool _isActive;

    public event Action? HotkeyTriggered;
    public event Action? ModelNextTriggered;
    public event Action? ModelPreviousTriggered;

    /// <summary>
    /// Set to true when recording/overlay is active so Shift+Arrow model cycling is enabled.
    /// When false, Shift+Arrow passes through to other apps normally.
    /// </summary>
    public bool IsActive
    {
        get => _isActive;
        set => _isActive = value;
    }

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

            if (msg == Win32Interop.WM_KEYDOWN || msg == Win32Interop.WM_SYSKEYDOWN)
            {
                if (_isActive)
                {
                    bool shiftHeld = (Win32Interop.GetAsyncKeyState(Win32Interop.VK_SHIFT) & 0x8000) != 0;
                    if (shiftHeld && hookStruct.vkCode == Win32Interop.VK_UP)
                    {
                        ModelNextTriggered?.Invoke();
                        return (IntPtr)1;
                    }
                    if (shiftHeld && hookStruct.vkCode == Win32Interop.VK_DOWN)
                    {
                        ModelPreviousTriggered?.Invoke();
                        return (IntPtr)1;
                    }
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
