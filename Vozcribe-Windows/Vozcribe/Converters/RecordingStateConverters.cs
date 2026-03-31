using System.Globalization;
using System.Windows;
using System.Windows.Data;
using Vozcribe.Models;

namespace Vozcribe.Converters;

public class StateToVisibilityConverter : IValueConverter
{
    public RecordingStateType TargetState { get; set; }

    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is RecordingStateType state)
            return state == TargetState ? Visibility.Visible : Visibility.Collapsed;
        return Visibility.Collapsed;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}
