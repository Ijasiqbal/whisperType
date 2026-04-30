using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Media;
using Vozcribe.Models;
using Vozcribe.Utilities;
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
        SourceInitialized += (_, _) =>
            Win32Interop.EnableDarkTitleBar(new WindowInteropHelper(this).Handle);
    }

    private void SetActiveStep(int n)
    {
        var amber = (SolidColorBrush)FindResource("AmberBrush");
        var ink = (SolidColorBrush)FindResource("InkBrush");
        var muted = (SolidColorBrush)FindResource("InkMutedBrush");
        var dim = (SolidColorBrush)FindResource("InkDimBrush");

        void Apply(StackPanel row, int rowStep)
        {
            bool active = rowStep == n;
            bool done = rowStep < n;
            if (row.Children[0] is TextBlock num)
                num.Foreground = active ? amber : (done ? muted : dim);
            if (row.Children[1] is TextBlock label)
            {
                label.Foreground = active ? ink : (done ? muted : dim);
                label.TextDecorations = done ? TextDecorations.Strikethrough : null;
            }
        }
        Apply(StepRow1, 1);
        Apply(StepRow2, 2);
        Apply(StepRow3, 3);
        Apply(StepRow4, 4);
    }

    private async void SignIn_Click(object sender, RoutedEventArgs e)
    {
        await ViewModel.SignInAsync(Constants.GoogleClientId, Constants.FirebaseApiKey);
        if (ViewModel.IsSignedIn)
        {
            Step1.Visibility = Visibility.Collapsed;
            Step2.Visibility = Visibility.Visible;
            SetActiveStep(2);
            ViewModel.CheckMicPermission();
            UpdateMicStatus();
        }
    }

    private void UpdateMicStatus()
    {
        if (ViewModel.MicPermissionGranted)
        {
            MicStatus.Text = "● Microphone detected";
            MicStatus.Foreground = new SolidColorBrush(Color.FromRgb(0x4F, 0xB8, 0xA8));
        }
        else
        {
            MicStatus.Text = "● No microphone found. Please connect one.";
            MicStatus.Foreground = new SolidColorBrush(Color.FromRgb(0xE0, 0x5D, 0x5D));
        }
    }

    private void MicNext_Click(object sender, RoutedEventArgs e)
    {
        Step2.Visibility = Visibility.Collapsed;
        Step3.Visibility = Visibility.Visible;
        SetActiveStep(3);
    }

    private void Hotkey_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (e.AddedItems.Count > 0 && e.AddedItems[0] is HotkeyOption hotkey)
            ViewModel.SelectHotkey(hotkey);
    }

    private void HotkeyNext_Click(object sender, RoutedEventArgs e)
    {
        Step3.Visibility = Visibility.Collapsed;
        Step4.Visibility = Visibility.Visible;
        SetActiveStep(4);
    }

    private void Step4Scroll_ScrollChanged(object sender, ScrollChangedEventArgs e)
    {
        var sv = (ScrollViewer)sender;
        Step4ScrollFade.Visibility = sv.VerticalOffset >= sv.ScrollableHeight - 1
            ? Visibility.Collapsed
            : Visibility.Visible;
    }

    private void TrayGotIt_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.CompleteOnboarding();
        Completed = true;
        Close();
    }

    private void TraySkip_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.CompleteOnboarding();
        Completed = true;
        Close();
    }
}
