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

    private bool _ctrlDown;
    private bool _altDown;
    private bool _shiftDown;
    private bool _winDown;

    public event Action? HotkeyTriggered;
    public event Action? ModelNextTriggered;
    public event Action? ModelPreviousTriggered;
    public event Action? EscapeTriggered;

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

            bool isKeyDown = msg == Win32Interop.WM_KEYDOWN || msg == Win32Interop.WM_SYSKEYDOWN;
            bool isKeyUp = msg == Win32Interop.WM_KEYUP || msg == Win32Interop.WM_SYSKEYUP;

            UpdateModifierState(hookStruct.vkCode, isKeyDown, isKeyUp);

            if (isKeyDown && hookStruct.vkCode == Win32Interop.VK_ESCAPE)
            {
                EscapeTriggered?.Invoke();
            }

            if (isKeyDown && _isActive)
            {
                if (_shiftDown && hookStruct.vkCode == Win32Interop.VK_UP)
                {
                    ModelNextTriggered?.Invoke();
                    return (IntPtr)1;
                }
                if (_shiftDown && hookStruct.vkCode == Win32Interop.VK_DOWN)
                {
                    ModelPreviousTriggered?.Invoke();
                    return (IntPtr)1;
                }
            }

            if (_currentHotkey.IsModifierOnly)
                HandleModifierOnlyHotkey(isKeyDown, isKeyUp);
            else
                HandleKeyBasedHotkey(isKeyDown, hookStruct);
        }

        return Win32Interop.CallNextHookEx(_hookId, nCode, wParam, lParam);
    }

    private void UpdateModifierState(uint vkCode, bool isKeyDown, bool isKeyUp)
    {
        if (!isKeyDown && !isKeyUp) return;
        bool pressed = isKeyDown;

        switch (vkCode)
        {
            case Win32Interop.VK_CONTROL:
            case Win32Interop.VK_LCONTROL:
            case Win32Interop.VK_RCONTROL: _ctrlDown = pressed; break;
            case Win32Interop.VK_MENU:
            case Win32Interop.VK_LMENU:
            case Win32Interop.VK_RMENU: _altDown = pressed; break;
            case Win32Interop.VK_SHIFT:
            case Win32Interop.VK_LSHIFT:
            case Win32Interop.VK_RSHIFT: _shiftDown = pressed; break;
            case Win32Interop.VK_LWIN:
            case Win32Interop.VK_RWIN: _winDown = pressed; break;
        }
    }

    private void HandleModifierOnlyHotkey(bool isKeyDown, bool isKeyUp)
    {
        if (isKeyDown && AreRequiredModifiersDown())
        {
            if (!_modifiersPressed)
            {
                _modifiersPressed = true;
                HotkeyTriggered?.Invoke();
            }
        }
        else if (isKeyUp && !AreRequiredModifiersDown())
        {
            _modifiersPressed = false;
        }
    }

    private void HandleKeyBasedHotkey(bool isKeyDown, Win32Interop.KBDLLHOOKSTRUCT hookStruct)
    {
        if (!isKeyDown) return;

        Key wpfKey = KeyInterop.KeyFromVirtualKey((int)hookStruct.vkCode);
        if (wpfKey != _currentHotkey.Key)
            return;

        if (_currentHotkey.Modifiers != ModifierKeys.None && !AreRequiredModifiersDown())
            return;

        HotkeyTriggered?.Invoke();
    }

    private bool AreRequiredModifiersDown()
    {
        var required = _currentHotkey.Modifiers;
        if (required.HasFlag(ModifierKeys.Control) && !_ctrlDown) return false;
        if (required.HasFlag(ModifierKeys.Alt) && !_altDown) return false;
        if (required.HasFlag(ModifierKeys.Shift) && !_shiftDown) return false;
        if (required.HasFlag(ModifierKeys.Windows) && !_winDown) return false;
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
