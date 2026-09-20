using System.Diagnostics;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace ZarexLoader;

public partial class Form1 : Form
{
    private const string FirebaseDb = "https://zarex-client-default-rtdb.firebaseio.com";
    private const string SignInBase = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";
    private const string FabricInstaller = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/fabric-installer-1.0.3.jar";
    private const string FabricApi = "https://cdn.modrinth.com/data/P7dR8mSH/versions/6qAuTtLR/fabric-api-0.141.6%2B1.21.11.jar";
    private const string ZarexMod = "https://cdn.discordapp.com/attachments/1547863897715908669/1551245396561371156/zarex-client-1.0.0.jar?ex=6ab1455b&is=6aaff3db&hm=78919bfca64b4aef8a7bb7d533442022e487ac58fd2de1a1569b0909db4539d3&";
    private const string MinecraftVersion = "1.21.11";
    private const string FabricLoader = "0.18.1";

    private readonly HttpClient http = new() { Timeout = TimeSpan.FromSeconds(45) };
    private CancellationTokenSource? cts;
    private bool dragging;
    private Point dragStart;
    private int screen;

    private static readonly Color Bg = Color.FromArgb(10, 11, 14);
    private static readonly Color Card = Color.FromArgb(23, 25, 30);
    private static readonly Color Field = Color.FromArgb(30, 32, 39);
    private static readonly Color White = Color.FromArgb(240, 241, 245);
    private static readonly Color Muted = Color.FromArgb(145, 149, 160);
    private static readonly Color Accent = Color.FromArgb(245, 225, 45);

    public Form1()
    {
        InitializeComponent();
        DoubleBuffered = true;
        SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer, true);
        ApplyRuntimeStyle();
        ShowLogin();
    }

    private void ApplyRuntimeStyle()
    {
        BackColor = Bg;
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(920, 570);
        Text = "Zarex Loader";

        emailTextBox.BackColor = Field;
        emailTextBox.ForeColor = White;
        passwordTextBox.BackColor = Field;
        passwordTextBox.ForeColor = White;

        signInButton.BackColor = Accent;
        signInButton.ForeColor = Color.FromArgb(20, 20, 20);
        loadButton.BackColor = Accent;
        loadButton.ForeColor = Color.FromArgb(20, 20, 20);

        closeButton.Click += (_, _) => Close();
        signInButton.Click += async (_, _) => await LoginAsync();
        loadButton.Click += async (_, _) => await LoadAsync();

        MouseDown += DragStart;
        MouseMove += DragMove;
        MouseUp += (_, _) => dragging = false;
        headerPanel.MouseDown += DragStart;
        headerPanel.MouseMove += DragMove;
        headerPanel.MouseUp += (_, _) => dragging = false;
    }

    private void ShowLogin()
    {
        screen = 0;
        titleLabel.Text = "ZAREX";
        subtitleLabel.Text = "CLIENT LOADER  •  FABRIC 1.21.11";
        accountLabel.Text = "ACCOUNT";
        helperLabel.Text = "Secure access to your client.";
        emailTextBox.Visible = passwordTextBox.Visible = signInButton.Visible = true;
        loadButton.Visible = false;
        progressBar.Visible = percentLabel.Visible = false;
        statusLabel.Text = "Sign in with your Zarex account.";
        statusLabel.ForeColor = Muted;
        Invalidate();
    }

    private void ShowDashboard()
    {
        screen = 1;
        titleLabel.Text = "WELCOME BACK";
        subtitleLabel.Text = "ZAREX CLIENT  •  READY TO LAUNCH";
        accountLabel.Text = "READY";
        helperLabel.Text = "Fabric environment is ready for launch.";
        emailTextBox.Visible = passwordTextBox.Visible = signInButton.Visible = false;
        loadButton.Visible = true;
        progressBar.Visible = percentLabel.Visible = false;
        statusLabel.Text = "Your account is verified. Prepare Minecraft.";
        statusLabel.ForeColor = Color.FromArgb(125, 210, 145);
        Invalidate();
    }

    private void ShowLoading()
    {
        screen = 2;
        titleLabel.Text = "LOADING";
        subtitleLabel.Text = "ZAREX CLIENT  •  INSTALLING";
        accountLabel.Text = "LOADING";
        helperLabel.Text = "Installing the client environment...";
        emailTextBox.Visible = passwordTextBox.Visible = signInButton.Visible = loadButton.Visible = false;
        progressBar.Visible = percentLabel.Visible = true;
        progressBar.Value = 0;
        Invalidate();
    }

    private async Task LoginAsync()
    {
        if (string.IsNullOrWhiteSpace(emailTextBox.Text) || string.IsNullOrWhiteSpace(passwordTextBox.Text))
        {
            SetStatus("Enter email and password.", Color.Salmon);
            return;
        }

        SetBusy(true);
        cts?.Dispose();
        cts = new CancellationTokenSource();

        try
        {
            SetStatus("Authenticating...", Muted);
            var key = Environment.GetEnvironmentVariable("ZAREX_FIREBASE_API_KEY");
            if (string.IsNullOrWhiteSpace(key))
            {
                SetStatus("Set ZAREX_FIREBASE_API_KEY before using login.", Color.Salmon);
                return;
            }

            var payload = JsonSerializer.Serialize(new { email = emailTextBox.Text.Trim(), password = passwordTextBox.Text, returnSecureToken = true });
            using var response = await http.PostAsync(SignInBase + key, new StringContent(payload, Encoding.UTF8, "application/json"), cts.Token);
            var body = await response.Content.ReadAsStringAsync(cts.Token);

            if (!response.IsSuccessStatusCode)
            {
                SetStatus(FirebaseError(body), Color.Salmon);
                return;
            }

            using var auth = JsonDocument.Parse(body);
            var root = auth.RootElement;
            var token = root.GetProperty("idToken").GetString() ?? "";
            var uid = root.GetProperty("localId").GetString() ?? "";

            using var profileResponse = await http.GetAsync($"{FirebaseDb}/users/{Uri.EscapeDataString(uid)}.json?auth={Uri.EscapeDataString(token)}", cts.Token);
            var profileBody = await profileResponse.Content.ReadAsStringAsync(cts.Token);

            if (!profileResponse.IsSuccessStatusCode || profileBody == "null")
            {
                SetStatus("ACCOUNT_PROFILE_NOT_FOUND", Color.Salmon);
                return;
            }

            using var profile = JsonDocument.Parse(profileBody);
            var p = profile.RootElement;

            if (!p.TryGetProperty("active", out var active) || !active.GetBoolean())
            {
                SetStatus("ACCOUNT_INACTIVE", Color.Salmon);
                return;
            }

            if (!p.TryGetProperty("licenseExpiresAt", out var expiry) ||
                !DateTimeOffset.TryParse(expiry.GetString(), out var expires) ||
                expires <= DateTimeOffset.UtcNow)
            {
                SetStatus("LICENSE_EXPIRED", Color.Salmon);
                return;
            }

            ShowDashboard();
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            SetStatus(ex.Message, Color.Salmon);
        }
        finally
        {
            SetBusy(false);
        }
    }

    private async Task LoadAsync()
    {
        ShowLoading();
        SetBusy(true);
        cts?.Dispose();
        cts = new CancellationTokenSource();

        try
        {
            var gameDir = Path.Combine(Path.GetTempPath(), "ZarexMinecraft", ".minecraft");
            Directory.CreateDirectory(gameDir);
            var installer = Path.Combine(Path.GetTempPath(), "ZarexMinecraft", "fabric-installer-1.0.3.jar");

            await DownloadAsync(FabricInstaller, installer, 5, 17, "Downloading Fabric installer");
            SetStatus("Installing Fabric...", Muted);

            var java = FindJava() ?? throw new InvalidOperationException("Java was not found.");
            var args = $"-jar \"{installer}\" client -dir \"{gameDir}\" -mcversion {MinecraftVersion} -loader {FabricLoader}";
            if (!await RunProcessAsync(java, args, gameDir, cts.Token))
                throw new InvalidOperationException("Fabric installation failed.");

            var mods = Path.Combine(gameDir, "mods");
            Directory.CreateDirectory(mods);

            await DownloadAsync(FabricApi, Path.Combine(mods, "fabric-api-0.141.6+1.21.11.jar"), 42, 30, "Downloading Fabric API");
            await DownloadAsync(ZarexMod, Path.Combine(mods, "zarex-client-1.0.0.jar"), 75, 20, "Downloading Zarex Client");

            CopyOptions(gameDir);
            progressBar.Value = 100;
            percentLabel.Text = "100%";
            SetStatus("Ready — launching Minecraft...", Color.FromArgb(125, 210, 145));

            if (!LaunchMinecraft())
                throw new InvalidOperationException("Minecraft Launcher.exe was not found.");

            await Task.Delay(350, cts.Token);
            Close();
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            ShowDashboard();
            SetStatus(ex.Message, Color.Salmon);
        }
        finally
        {
            SetBusy(false);
        }
    }

    private async Task DownloadAsync(string url, string destination, int start, int span, string status)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        using var response = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cts!.Token);
        response.EnsureSuccessStatusCode();

        var total = response.Content.Headers.ContentLength;
        await using var input = await response.Content.ReadAsStreamAsync(cts.Token);
        await using var output = File.Create(destination);

        var buffer = new byte[128 * 1024];
        long done = 0;
        int read;
        SetStatus(status, Muted);

        while ((read = await input.ReadAsync(buffer, cts.Token)) > 0)
        {
            await output.WriteAsync(buffer.AsMemory(0, read), cts.Token);
            done += read;

            if (total is > 0)
            {
                var local = (int)Math.Clamp(done * 100 / total.Value, 0, 100);
                var percent = Math.Clamp(start + local * span / 100, 0, 100);
                progressBar.Value = percent;
                percentLabel.Text = $"{percent}%";
            }
        }
    }

    private bool LaunchMinecraft()
    {
        var candidates = new[]
        {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Minecraft Launcher", "MinecraftLauncher.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Minecraft Launcher", "MinecraftLauncher.exe"),
            @"C:XboxGamesMinecraft LauncherContentMinecraft.exe"
        };

        var launcher = candidates.FirstOrDefault(File.Exists);
        if (launcher is null) return false;

        Process.Start(new ProcessStartInfo
        {
            FileName = launcher,
            WorkingDirectory = Path.GetDirectoryName(launcher),
            UseShellExecute = true
        });
        return true;
    }

    private static string? FindJava()
    {
        var home = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrWhiteSpace(home))
        {
            foreach (var name in new[] { "javaw.exe", "java.exe" })
            {
                var path = Path.Combine(home, "bin", name);
                if (File.Exists(path)) return path;
            }
        }

        foreach (var dir in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(';', StringSplitOptions.RemoveEmptyEntries))
        {
            foreach (var name in new[] { "javaw.exe", "java.exe" })
            {
                var path = Path.Combine(dir.Trim(), name);
                if (File.Exists(path)) return path;
            }
        }
        return null;
    }

    private static async Task<bool> RunProcessAsync(string executable, string arguments, string workingDirectory, CancellationToken token)
    {
        using var process = Process.Start(new ProcessStartInfo
        {
            FileName = executable,
            Arguments = arguments,
            WorkingDirectory = workingDirectory,
            UseShellExecute = false,
            CreateNoWindow = true
        });
        if (process is null) return false;
        await process.WaitForExitAsync(token);
        return process.ExitCode == 0;
    }

    private static void CopyOptions(string gameDir)
    {
        var publicDir = Path.Combine(Environment.GetEnvironmentVariable("PUBLIC") ?? @"C:UsersPublic", "Zarex");
        var source = Path.Combine(publicDir, "options.txt");
        if (File.Exists(source)) File.Copy(source, Path.Combine(gameDir, "options.txt"), true);
    }

    private static string FirebaseError(string json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            return doc.RootElement.GetProperty("error").GetProperty("message").GetString() ?? "AUTH_REQUEST_FAILED";
        }
        catch { return "AUTH_REQUEST_FAILED"; }
    }

    private void SetStatus(string text, Color color)
    {
        statusLabel.Text = text;
        statusLabel.ForeColor = color;
    }

    private void SetBusy(bool busy)
    {
        signInButton.Enabled = loadButton.Enabled = !busy;
        emailTextBox.Enabled = passwordTextBox.Enabled = !busy;
        Cursor = busy ? Cursors.WaitCursor : Cursors.Default;
    }

    private void DragStart(object? sender, MouseEventArgs e)
    {
        if (e.Button != MouseButtons.Left) return;
        dragging = true;
        dragStart = e.Location;
    }

    private void DragMove(object? sender, MouseEventArgs e)
    {
        if (!dragging || e.Button != MouseButtons.Left) return;
        var p = PointToScreen(e.Location);
        Location = new Point(p.X - dragStart.X, p.Y - dragStart.Y);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        base.OnPaint(e);
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

        using var bg = new LinearGradientBrush(ClientRectangle, Color.FromArgb(9, 10, 13), Color.FromArgb(25, 26, 32), 90f);
        g.FillRectangle(bg, ClientRectangle);

        using var glow = new SolidBrush(Color.FromArgb(18, Accent));
        g.FillEllipse(glow, new Rectangle(ClientSize.Width - 330, -120, 450, 330));

        var card = new Rectangle(48, 122, 700, 370);
        using var path = Rounded(card, 22);
        using var cardBrush = new SolidBrush(Card);
        using var border = new Pen(Color.FromArgb(70, Accent));
        g.FillPath(cardBrush, path);
        g.DrawPath(border, path);

        Draw(g, "●  ONLINE", new Rectangle(52, 44, 130, 22), 8, Muted, FontStyle.Bold);

        if (screen == 1)
        {
            Draw(g, "MINECRAFT", new Rectangle(92, 265, 180, 20), 9, Muted, FontStyle.Bold);
            Draw(g, "1.21.11", new Rectangle(92, 288, 180, 32), 16, White, FontStyle.Bold);
            Draw(g, "FABRIC", new Rectangle(310, 265, 180, 20), 9, Muted, FontStyle.Bold);
            Draw(g, "0.18.1", new Rectangle(310, 288, 180, 32), 16, White, FontStyle.Bold);
        }
    }

    private static void Draw(Graphics g, string text, Rectangle rect, float size, Color color, FontStyle style)
    {
        using var font = new Font("Segoe UI", size, style);
        using var brush = new SolidBrush(color);
        g.DrawString(text, font, brush, rect, new StringFormat { Alignment = StringAlignment.Near, LineAlignment = StringAlignment.Center });
    }

    private static GraphicsPath Rounded(Rectangle r, int radius)
    {
        var p = new GraphicsPath();
        var d = radius * 2;
        p.AddArc(r.X, r.Y, d, d, 180, 90);
        p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
        p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
        p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
        p.CloseFigure();
        return p;
    }

    protected override void OnFormClosed(FormClosedEventArgs e)
    {
        cts?.Cancel();
        cts?.Dispose();
        http.Dispose();
        base.OnFormClosed(e);
    }
}
