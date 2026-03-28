using System.Threading;
using System.Windows;

namespace Vozcribe;

public partial class App : Application
{
    private const string MutexName = "Vozcribe_SingleInstance";
    private Mutex? _mutex;

    protected override void OnStartup(StartupEventArgs e)
    {
        _mutex = new Mutex(initiallyOwned: true, MutexName, out bool createdNew);

        if (!createdNew)
        {
            MessageBox.Show(
                "Vozcribe is already running. Check the system tray.",
                "Vozcribe",
                MessageBoxButton.OK,
                MessageBoxImage.Information);
            _mutex.Dispose();
            _mutex = null;
            Shutdown();
            return;
        }

        base.OnStartup(e);
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _mutex?.ReleaseMutex();
        _mutex?.Dispose();
        _mutex = null;
        base.OnExit(e);
    }
}
