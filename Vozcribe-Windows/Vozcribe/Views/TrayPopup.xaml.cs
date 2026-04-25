using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using Vozcribe.Models;
using Vozcribe.ViewModels;

namespace Vozcribe.Views;

public partial class TrayPopup : UserControl
{
    public event Action? SettingsRequested;
    public event Action? QuitRequested;
    public event Action? ReportIssueRequested;
    public event Action? CheckForUpdateRequested;

    private TrayPopupViewModel ViewModel => (TrayPopupViewModel)DataContext;

    public TrayPopup()
    {
        InitializeComponent();
    }

    private void ModelComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (e.AddedItems.Count > 0 && e.AddedItems[0] is ModelTier tier)
            ViewModel.SetModelTier(tier);
    }

    private async void RetryPending_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is PendingTranscription item)
            await ViewModel.RetryPendingAsync(item);
    }

    private void Settings_Click(object sender, RoutedEventArgs e) => SettingsRequested?.Invoke();
    private void Quit_Click(object sender, RoutedEventArgs e) => QuitRequested?.Invoke();

    private void CheckForUpdate_Click(object sender, RoutedEventArgs e)
        => CheckForUpdateRequested?.Invoke();

    private void ReportIssue_Click(object sender, RoutedEventArgs e)
        => ReportIssueRequested?.Invoke();
}
