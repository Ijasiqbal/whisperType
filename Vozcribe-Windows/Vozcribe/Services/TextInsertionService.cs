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

        if (TryDirectInsert(target, text))
            return InsertionResult.DirectInsert;

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
                    var inserted = valuePattern.Current.Value;
                    return inserted == text;
                }
            }
        }
        catch { }

        return false;
    }

    private bool TryClipboardPaste(AutomationElement element, string text)
    {
        try
        {
            string? originalClipboard = null;
            Application.Current.Dispatcher.Invoke(() =>
            {
                if (Clipboard.ContainsText())
                    originalClipboard = Clipboard.GetText();
                Clipboard.SetText(text);
            });

            if (_capturedWindowHandle != IntPtr.Zero)
                Win32Interop.SetForegroundWindow(_capturedWindowHandle);

            try { element.SetFocus(); }
            catch { }

            Thread.Sleep(50);

            SimulateCtrlV();

            Thread.Sleep(100);

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

        inputs[0].type = Win32Interop.INPUT_KEYBOARD;
        inputs[0].u.ki.wVk = (ushort)Win32Interop.VK_CONTROL;

        inputs[1].type = Win32Interop.INPUT_KEYBOARD;
        inputs[1].u.ki.wVk = 0x56;

        inputs[2].type = Win32Interop.INPUT_KEYBOARD;
        inputs[2].u.ki.wVk = 0x56;
        inputs[2].u.ki.dwFlags = Win32Interop.KEYEVENTF_KEYUP;

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
