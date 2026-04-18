using System.Globalization;
using System.Windows.Data;

namespace Vozcribe.Converters;

public class AmpToHeightConverter : IValueConverter
{
    public double MinHeight { get; set; } = 3;
    public double MaxHeight { get; set; } = 22;

    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        double amp = value switch
        {
            double d => d,
            float f => f,
            _ => 0
        };
        amp = Math.Max(0, Math.Min(1, amp));
        return MinHeight + (MaxHeight - MinHeight) * amp;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        throw new NotImplementedException();
}
