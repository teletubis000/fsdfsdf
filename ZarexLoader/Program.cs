using System;
using System.Windows.Forms;

namespace ZarexLoader;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new LoaderForm());
    }
}
