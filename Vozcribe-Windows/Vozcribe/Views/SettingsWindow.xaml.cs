using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;
using Vozcribe.Utilities;
using Vozcribe.ViewModels;

namespace Vozcribe.Views;

public partial class SettingsWindow : Window
{
    private SettingsViewModel ViewModel => (SettingsViewModel)DataContext;

    public SettingsWindow(SettingsViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
        SourceInitialized += (_, _) =>
            Win32Interop.EnableDarkTitleBar(new WindowInteropHelper(this).Handle);
    }

    private void CaptureHotkey_Click(object sender, RoutedEventArgs e) => ViewModel.StartCapturingHotkey();

    private void CaptureHotkey_KeyDown(object sender, KeyEventArgs e)
    {
        if (!ViewModel.IsCapturingHotkey) return;
        if (e.Key == Key.Escape)
        {
            ViewModel.IsCapturingHotkey = false;
            ViewModel.CaptureHotkeyText = "Record Custom Shortcut";
            return;
        }

        if (e.Key == Key.LeftCtrl || e.Key == Key.RightCtrl ||
            e.Key == Key.LeftAlt || e.Key == Key.RightAlt ||
            e.Key == Key.LeftShift || e.Key == Key.RightShift ||
            e.Key == Key.LWin || e.Key == Key.RWin)
            return;

        ViewModel.CaptureHotkey(Keyboard.Modifiers, e.Key);
        e.Handled = true;
    }

    private void ResetOverlay_Click(object sender, RoutedEventArgs e) => ViewModel.ResetOverlayPosition();
    private void SignOut_Click(object sender, RoutedEventArgs e) => ViewModel.SignOut();
}
