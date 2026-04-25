using System;
using System.Windows;
using System.Windows.Controls;
using Vozcribe.Services;

namespace Vozcribe.Views;

public partial class ReportIssueWindow : Window
{
    private readonly ApiClient _api;
    private readonly AuthService _auth;

    public ReportIssueWindow(ApiClient api, AuthService auth)
    {
        InitializeComponent();
        _api = api;
        _auth = auth;
    }

    private async void Submit_Click(object sender, RoutedEventArgs e)
    {
        var description = DescriptionBox.Text.Trim();
        if (string.IsNullOrEmpty(description))
        {
            ShowError("Please describe the issue.");
            return;
        }

        var category = CategoryBox.SelectedItem is ComboBoxItem item
            ? item.Tag?.ToString() ?? "other"
            : "other";

        SubmitButton.IsEnabled = false;
        SubmitButton.Content = "Submitting...";
        ErrorText.Visibility = Visibility.Collapsed;

        try
        {
            var uid = _auth.Uid ?? "unknown";
            await _api.SubmitIssueAsync(uid, _auth.Email, category, description);
            MessageBox.Show("Issue reported. Thank you!", "Vozcribe",
                MessageBoxButton.OK, MessageBoxImage.Information);
            Close();
        }
        catch (Exception)
        {
            ShowError("Failed to submit. Please try again.");
        }
        finally
        {
            SubmitButton.IsEnabled = true;
            SubmitButton.Content = "Submit";
        }
    }

    private void Cancel_Click(object sender, RoutedEventArgs e) => Close();

    private void ShowError(string message)
    {
        ErrorText.Text = message;
        ErrorText.Visibility = Visibility.Visible;
    }
}
