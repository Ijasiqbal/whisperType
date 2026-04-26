using System;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Automation;
using Vozcribe.Models;
using Vozcribe.Utilities;
using Log = Vozcribe.Utilities.AppLog;

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
            _capturedWindowHandle = Win32Interop.GetForegroundWindow();
            _capturedElement = AutomationElement.FocusedElement;

            var elementDesc = _capturedElement != null
                ? $"name='{_capturedElement.Current.Name}' class='{_capturedElement.Current.ClassName}' type='{_capturedElement.Current.ControlType.ProgrammaticName}'"
                : "null";
            Log.Write($"Capture: hwnd=0x{_capturedWindowHandle:X} element={elementDesc}");
        }
        catch (Exception ex)
        {
            Log.Write($"Capture: exception {ex.Message}");
            _capturedElement = null;
            _capturedWindowHandle = IntPtr.Zero;
        }
    }

    public InsertionResult InsertText(string text, InsertionMode mode)
    {
        Log.Write($"InsertText: mode={mode} hwnd=0x{_capturedWindowHandle:X} text='{text[..Math.Min(30, text.Length)]}'");

        AutomationElement? target;

        if (mode == InsertionMode.WhereStarted)
            target = _capturedElement;
        else
            target = GetCurrentFocusedElement();

        Log.Write($"InsertText: target={(target == null ? "null" : $"class='{target.Current.ClassName}'")}");

        bool isXtermTarget = target?.Current.ClassName?.Contains("xterm") == true;
        if (!isXtermTarget && target != null && TryDirectInsert(target, text))
        {
            Log.Write("InsertText: DirectInsert succeeded");
            return InsertionResult.DirectInsert;
        }

        // Always attempt clipboard paste — Terminal and Electron apps don't expose
        // UIAutomation elements but accept Ctrl+V via the captured window handle.
        if (TryClipboardPaste(text, isXtermTarget))
        {
            Log.Write("InsertText: ClipboardPaste succeeded");
            return InsertionResult.ClipboardFallback;
        }

        Log.Write("InsertText: all methods failed");
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
                Log.Write($"DirectInsert: ValuePattern found, isReadOnly={valuePattern.Current.IsReadOnly}");
                if (!valuePattern.Current.IsReadOnly)
                {
                    string existing = valuePattern.Current.Value ?? "";
                    string placeholder = element.Current.HelpText ?? "";
                    if (!string.IsNullOrEmpty(placeholder) && existing == placeholder)
                        existing = "";
                    string combined = existing + text;
                    valuePattern.SetValue(combined);
                    var inserted = valuePattern.Current.Value;
                    Log.Write($"DirectInsert: set value, match={inserted == combined}");
                    return inserted == combined;
                }
            }
            else
            {
                Log.Write("DirectInsert: no ValuePattern on element");
            }
        }
        catch (Exception ex)
        {
            Log.Write($"DirectInsert: exception {ex.Message}");
        }

        return false;
    }

    private bool TryClipboardPaste(string text, bool isXterm = false)
    {
        try
        {
            Log.Write($"ClipboardPaste: hwnd=0x{_capturedWindowHandle:X} isXterm={isXterm}");
            string? originalClipboard = null;
            Application.Current.Dispatcher.Invoke(() =>
            {
                if (Clipboard.ContainsText())
                    originalClipboard = Clipboard.GetText();
                Clipboard.SetText(text);
            });

            if (_capturedWindowHandle != IntPtr.Zero)
            {
                var before = Win32Interop.GetForegroundWindow();
                ForceForeground(_capturedWindowHandle);
                var after = Win32Interop.GetForegroundWindow();
                Log.Write($"ClipboardPaste: foreground before=0x{before:X} after=0x{after:X} target=0x{_capturedWindowHandle:X}");
            }
            else
            {
                Log.Write("ClipboardPaste: no window handle, skipping ForceForeground");
            }

            Thread.Sleep(120);

            var fgBeforePaste = Win32Interop.GetForegroundWindow();
            uint endSent = SimulateEndKey();
            uint vSent = isXterm ? SimulateCtrlShiftV() : SimulateCtrlV();
            var fgAfterPaste = Win32Interop.GetForegroundWindow();
            Log.Write($"ClipboardPaste: fgBeforePaste=0x{fgBeforePaste:X} fgAfterPaste=0x{fgAfterPaste:X} endSent={endSent} vSent={vSent} isXterm={isXterm}");

            Thread.Sleep(200);

            Application.Current.Dispatcher.Invoke(() =>
            {
                try
                {
                    if (originalClipboard != null)
                        Clipboard.SetText(originalClipboard);
                    else
                        Clipboard.Clear();
                }
                catch { }
            });

            return true;
        }
        catch
        {
            return false;
        }
    }

    private static void ForceForeground(IntPtr hWnd)
    {
        var foreground = Win32Interop.GetForegroundWindow();
        if (foreground == hWnd) return;

        uint foregroundThread = Win32Interop.GetWindowThreadProcessId(foreground, out _);
        uint targetThread = Win32Interop.GetWindowThreadProcessId(hWnd, out _);
        uint currentThread = Win32Interop.GetCurrentThreadId();

        bool attachedForeground = false, attachedTarget = false;
        try
        {
            if (foregroundThread != currentThread)
                attachedForeground = Win32Interop.AttachThreadInput(currentThread, foregroundThread, true);
            if (targetThread != currentThread)
                attachedTarget = Win32Interop.AttachThreadInput(currentThread, targetThread, true);

            Win32Interop.ShowWindow(hWnd, Win32Interop.SW_SHOW);
            Win32Interop.BringWindowToTop(hWnd);
            Win32Interop.SetForegroundWindow(hWnd);
        }
        finally
        {
            if (attachedForeground)
                Win32Interop.AttachThreadInput(currentThread, foregroundThread, false);
            if (attachedTarget)
                Win32Interop.AttachThreadInput(currentThread, targetThread, false);
        }
    }

    private static uint SendKeys(params ushort[] vkCodes)
    {
        var inputs = new Win32Interop.INPUT[vkCodes.Length * 2];
        int inputSize = Marshal.SizeOf<Win32Interop.INPUT>();
        for (int i = 0; i < vkCodes.Length; i++)
        {
            inputs[i].type = Win32Interop.INPUT_KEYBOARD;
            inputs[i].u.ki.wVk = vkCodes[i];
            inputs[vkCodes.Length * 2 - 1 - i].type = Win32Interop.INPUT_KEYBOARD;
            inputs[vkCodes.Length * 2 - 1 - i].u.ki.wVk = vkCodes[i];
            inputs[vkCodes.Length * 2 - 1 - i].u.ki.dwFlags = Win32Interop.KEYEVENTF_KEYUP;
        }
        return Win32Interop.SendInput((uint)inputs.Length, inputs, inputSize);
    }

    private static uint SimulateEndKey() =>
        SendKeys((ushort)Win32Interop.VK_END);

    private static uint SimulateCtrlV() =>
        SendKeys((ushort)Win32Interop.VK_CONTROL, 0x56);

    private static uint SimulateCtrlShiftV() =>
        SendKeys((ushort)Win32Interop.VK_CONTROL, (ushort)Win32Interop.VK_SHIFT, 0x56);

    public void ClearCapture()
    {
        _capturedElement = null;
        _capturedWindowHandle = IntPtr.Zero;
    }
}
