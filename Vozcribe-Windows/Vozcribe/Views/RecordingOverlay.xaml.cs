using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using Vozcribe.Models;
using Vozcribe.ViewModels;

namespace Vozcribe.Views;

public partial class RecordingOverlay : Window
{
    private RecordingOverlayViewModel ViewModel => (RecordingOverlayViewModel)DataContext;

    public RecordingOverlay()
    {
        InitializeComponent();
    }

    public void Initialize(RecordingOverlayViewModel viewModel)
    {
        DataContext = viewModel;

        var (x, y) = viewModel.GetSavedPosition();
        Left = x;
        Top = y;

        viewModel.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(RecordingOverlayViewModel.IsVisible))
            {
                Dispatcher.Invoke(() =>
                {
                    if (viewModel.IsVisible) Show();
                    else Hide();
                });
            }
        };
    }

    private void Window_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        DragMove();
    }

    private void Window_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        ViewModel.SavePosition(Left, Top);
    }

    private void CopyButton_Click(object sender, RoutedEventArgs e)
    {
        if (ViewModel.Orchestrator.State.Text != null)
            Clipboard.SetText(ViewModel.Orchestrator.State.Text);
    }

    private void RetryButton_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.Retry();
    }

    private void RetryWithButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.ContextMenu != null)
        {
            btn.ContextMenu.PlacementTarget = btn;
            btn.ContextMenu.IsOpen = true;
        }
    }

    private void RetryWithTier_Click(object sender, RoutedEventArgs e)
    {
        if (sender is MenuItem mi && mi.DataContext is ModelTier tier)
            ViewModel.Retry(tier);
    }

    private void SaveButton_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.SaveForLater();
    }

    private void CloseButton_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.Orchestrator.Cancel();
    }
}
