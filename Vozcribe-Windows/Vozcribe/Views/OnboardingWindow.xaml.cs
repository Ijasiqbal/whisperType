using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Vozcribe.Models;
using Vozcribe.ViewModels;

namespace Vozcribe.Views;

public partial class OnboardingWindow : Window
{
    private OnboardingViewModel ViewModel => (OnboardingViewModel)DataContext;
    public bool Completed { get; private set; }

    public OnboardingWindow(OnboardingViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }

    private async void SignIn_Click(object sender, RoutedEventArgs e)
    {
        await ViewModel.SignInAsync(Constants.GoogleClientId, Constants.FirebaseApiKey);
        if (ViewModel.IsSignedIn)
        {
            Step1.Visibility = Visibility.Collapsed;
            Step2.Visibility = Visibility.Visible;
            ViewModel.CheckMicPermission();
            UpdateMicStatus();
        }
    }

    private void UpdateMicStatus()
    {
        if (ViewModel.MicPermissionGranted)
        {
            MicStatus.Text = "Microphone detected";
            MicStatus.Foreground = new SolidColorBrush(Color.FromRgb(0x44, 0xBB, 0x44));
        }
        else
        {
            MicStatus.Text = "No microphone found. Please connect one.";
            MicStatus.Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x66, 0x66));
        }
    }

    private void MicNext_Click(object sender, RoutedEventArgs e)
    {
        Step2.Visibility = Visibility.Collapsed;
        Step3.Visibility = Visibility.Visible;
    }

    private void Hotkey_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (e.AddedItems.Count > 0 && e.AddedItems[0] is HotkeyOption hotkey)
            ViewModel.SelectHotkey(hotkey);
    }

    private void Complete_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.CompleteOnboarding();
        Completed = true;
        Close();
    }
}
