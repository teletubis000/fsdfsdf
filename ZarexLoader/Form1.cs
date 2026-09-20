using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace ZarexLoader
{
    public partial class Form1 : Form
    {
        private const string FirebaseDb = "https://zarex-client-default-rtdb.firebaseio.com";
        private const string SignInBase = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";
        private const string FabricInstaller = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/fabric-installer-1.0.3.jar";
        private const string FabricApi = "https://cdn.modrinth.com/data/P7dR8mSH/versions/6qAuTtLR/fabric-api-0.141.6%2B1.21.11.jar";
        private const string ZarexMod = "https://cdn.discordapp.com/attachments/1547863897715908669/1551245396561371156/zarex-client-1.0.0.jar?ex=6ab1455b&is=6aaff3db&hm=78919bfca64b4aef8a7bb7d533442022e487ac58fd2de1a1569b0909db4539d3&";
        private const string MinecraftVersion = "1.21.11";
        private const string FabricLoader = "0.18.1";

        private readonly HttpClient http = new HttpClient { Timeout = TimeSpan.FromSeconds(45) };
        private CancellationTokenSource cts;
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

            closeButton.Click += delegate { Close(); };
            signInButton.Click += async delegate { await LoginAsync(); };
            loadButton.Click += async delegate { await LoadAsync(); };

            MouseDown += DragStart;
            MouseMove += DragMove;
            MouseUp += delegate { dragging = false; };
            headerPanel.MouseDown += DragStart;
            headerPanel.MouseMove += DragMove;
            headerPanel.MouseUp += delegate { dragging = false; };
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
            percentLabel.Text = "0%";
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
            if (cts != null) cts.Dispose();
            cts = new CancellationTokenSource();

            try
            {
                SetStatus("Authenticating...", Muted);
                string key = Environment.GetEnvironmentVariable("ZAREX_FIREBASE_API_KEY");
                if (string.IsNullOrWhiteSpace(key))
                {
                    SetStatus("Set ZAREX_FIREBASE_API_KEY before using login.", Color.Salmon);
                    return;
                }

                string payload = "{"email":"" + JsonEscape(emailTextBox.Text.Trim()) + "","password":"" + JsonEscape(passwordTextBox.Text) + "","returnSecureToken":true}";
                HttpResponseMessage response = await http.PostAsync(SignInBase + key, new StringContent(payload, Encoding.UTF8, "application/json"), cts.Token);
                string body = await response.Content.ReadAsStringAsync();

                if (!response.IsSuccessStatusCode)
                {
                    SetStatus(FirebaseError(body), Color.Salmon);
                    return;
                }

                string token = JsonString(body, "idToken");
                string uid = JsonString(body, "localId");

                if (string.IsNullOrEmpty(token) || string.IsNullOrEmpty(uid))
                {
                    SetStatus("AUTH_RESPONSE_INVALID", Color.Salmon);
                    return;
                }

                HttpResponseMessage profileResponse = await http.GetAsync(FirebaseDb + "/users/" + Uri.EscapeDataString(uid) + ".json?auth=" + Uri.EscapeDataString(token), cts.Token);
                string profileBody = await profileResponse.Content.ReadAsStringAsync();

                if (!profileResponse.IsSuccessStatusCode || profileBody == "null")
                {
                    SetStatus("ACCOUNT_PROFILE_NOT_FOUND", Color.Salmon);
                    return;
                }

                if (!JsonBool(profileBody, "active", false))
                {
                    SetStatus("ACCOUNT_INACTIVE", Color.Salmon);
                    return;
                }

                string expiryText = JsonString(profileBody, "licenseExpiresAt");
                DateTimeOffset expires;
                if (string.IsNullOrEmpty(expiryText) || !DateTimeOffset.TryParse(expiryText, out expires) || expires <= DateTimeOffset.UtcNow)
                {
                    SetStatus("LICENSE_EXPIRED", Color.Salmon);
                    return;
                }

                ShowDashboard();
            }
            catch (OperationCanceledException)
            {
                SetStatus("Operation cancelled.", Color.Salmon);
            }
            catch (Exception ex)
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
            if (cts != null) cts.Dispose();
            cts = new CancellationTokenSource();

            try
            {
                string gameDir = Path.Combine(Path.GetTempPath(), "ZarexMinecraft", ".minecraft");
                Directory.CreateDirectory(gameDir);
                string installer = Path.Combine(Path.GetTempPath(), "ZarexMinecraft", "fabric-installer-1.0.3.jar");

                await DownloadAsync(FabricInstaller, installer, 5, 17, "Downloading Fabric installer");
                SetStatus("Installing Fabric...", Muted);

                string java = FindJava();
                if (java == null) throw new InvalidOperationException("Java was not found.");

                string args = "-jar "" + installer + "" client -dir "" + gameDir + "" -mcversion " + MinecraftVersion + " -loader " + FabricLoader;
                if (!await RunProcessAsync(java, args, gameDir, cts.Token))
                    throw new InvalidOperationException("Fabric installation failed.");

                string mods = Path.Combine(gameDir, "mods");
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
            catch (OperationCanceledException)
            {
                ShowDashboard();
                SetStatus("Operation cancelled.", Color.Salmon);
            }
            catch (Exception ex)
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
            string dir = Path.GetDirectoryName(destination);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);

            HttpResponseMessage response = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cts.Token);
            response.EnsureSuccessStatusCode();

            long? total = response.Content.Headers.ContentLength;
            using (Stream input = await response.Content.ReadAsStreamAsync())
            using (FileStream output = File.Create(destination))
            {
                byte[] buffer = new byte[128 * 1024];
                long done = 0;
                int read;
                SetStatus(status, Muted);

                while ((read = await input.ReadAsync(buffer, 0, buffer.Length, cts.Token)) > 0)
                {
                    await output.WriteAsync(buffer, 0, read, cts.Token);
                    done += read;

                    if (total.HasValue && total.Value > 0)
                    {
                        int local = ClampInt((int)(done * 100L / total.Value), 0, 100);
                        int percent = ClampInt(start + local * span / 100, 0, 100);
                        progressBar.Value = percent;
                        percentLabel.Text = percent + "%";
                    }
                }
            }
        }

        private bool LaunchMinecraft()
        {
            string[] candidates =
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Minecraft Launcher", "MinecraftLauncher.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Minecraft Launcher", "MinecraftLauncher.exe"),
                @"C:XboxGamesMinecraft LauncherContentMinecraft.exe"
            };

            string launcher = candidates.FirstOrDefault(File.Exists);
            if (launcher == null) return false;

            Process.Start(new ProcessStartInfo
            {
                FileName = launcher,
                WorkingDirectory = Path.GetDirectoryName(launcher),
                UseShellExecute = true
            });
            return true;
        }

        private static string FindJava()
        {
            string home = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrWhiteSpace(home))
            {
                string[] names = { "javaw.exe", "java.exe" };
                foreach (string name in names)
                {
                    string path = Path.Combine(home, "bin", name);
                    if (File.Exists(path)) return path;
                }
            }

            string pathEnv = Environment.GetEnvironmentVariable("PATH") ?? "";
            foreach (string dir in pathEnv.Split(new[] { ';' }, StringSplitOptions.RemoveEmptyEntries))
            {
                string trimmed = dir.Trim();
                foreach (string name in new[] { "javaw.exe", "java.exe" })
                {
                    string path = Path.Combine(trimmed, name);
                    if (File.Exists(path)) return path;
                }
            }
            return null;
        }

        private static async Task<bool> RunProcessAsync(string executable, string arguments, string workingDirectory, CancellationToken token)
        {
            using (Process process = Process.Start(new ProcessStartInfo
            {
                FileName = executable,
                Arguments = arguments,
                WorkingDirectory = workingDirectory,
                UseShellExecute = false,
                CreateNoWindow = true
            }))
            {
                if (process == null) return false;
                await Task.Run(delegate
                {
                    process.WaitForExit();
                }, token);
                return process.ExitCode == 0;
            }
        }

        private static void CopyOptions(string gameDir)
        {
            string publicDir = Path.Combine(Environment.GetEnvironmentVariable("PUBLIC") ?? @"C:UsersPublic", "Zarex");
            string source = Path.Combine(publicDir, "options.txt");
            if (File.Exists(source)) File.Copy(source, Path.Combine(gameDir, "options.txt"), true);
        }

        private static string JsonString(string json, string key)
        {
            string needle = """ + key + """;
            int p = json.IndexOf(needle, StringComparison.Ordinal);
            if (p < 0) return null;
            p = json.IndexOf(':', p + needle.Length);
            if (p < 0) return null;
            p++;
            while (p < json.Length && char.IsWhiteSpace(json[p])) p++;
            if (p >= json.Length || json[p] != '"') return null;
            p++;
            StringBuilder result = new StringBuilder();

            while (p < json.Length)
            {
                char ch = json[p++];
                if (ch == '"') break;
                if (ch == '\' && p < json.Length)
                {
                    char escaped = json[p++];
                    switch (escaped)
                    {
                        case '"': result.Append('"'); break;
                        case '\': result.Append('\'); break;
                        case '/': result.Append('/'); break;
                        case 'b': result.Append(''); break;
                        case 'f': result.Append(''); break;
                        case 'n': result.Append('
'); break;
                        case 'r': result.Append(''); break;
                        case 't': result.Append('	'); break;
                        default: result.Append(escaped); break;
                    }
                }
                else result.Append(ch);
            }
            return result.ToString();
        }

        private static bool JsonBool(string json, string key, bool fallback)
        {
            string needle = """ + key + """;
            int p = json.IndexOf(needle, StringComparison.Ordinal);
            if (p < 0) return fallback;
            p = json.IndexOf(':', p + needle.Length);
            if (p < 0) return fallback;
            p++;
            while (p < json.Length && char.IsWhiteSpace(json[p])) p++;
            if (json.IndexOf("true", p, StringComparison.Ordinal) == p) return true;
            if (json.IndexOf("false", p, StringComparison.Ordinal) == p) return false;
            return fallback;
        }

        private static string JsonEscape(string input)
        {
            return input.Replace("\", "\\").Replace(""", "\"");
        }

        private static string FirebaseError(string json)
        {
            string message = JsonString(json, "message");
            return string.IsNullOrEmpty(message) ? "AUTH_REQUEST_FAILED" : message;
        }

        private static int ClampInt(int value, int min, int max)
        {
            if (value < min) return min;
            if (value > max) return max;
            return value;
        }

        private void SetStatus(string text, Color color)
        {
            if (InvokeRequired)
            {
                BeginInvoke((MethodInvoker)delegate { SetStatus(text, color); });
                return;
            }
            statusLabel.Text = text;
            statusLabel.ForeColor = color;
        }

        private void SetBusy(bool busy)
        {
            signInButton.Enabled = loadButton.Enabled = !busy;
            emailTextBox.Enabled = passwordTextBox.Enabled = !busy;
            Cursor = busy ? Cursors.WaitCursor : Cursors.Default;
        }

        private void DragStart(object sender, MouseEventArgs e)
        {
            if (e.Button != MouseButtons.Left) return;
            dragging = true;
            dragStart = e.Location;
        }

        private void DragMove(object sender, MouseEventArgs e)
        {
            if (!dragging || e.Button != MouseButtons.Left) return;
            Point p = PointToScreen(e.Location);
            Location = new Point(p.X - dragStart.X, p.Y - dragStart.Y);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

            using (LinearGradientBrush bg = new LinearGradientBrush(ClientRectangle, Color.FromArgb(9, 10, 13), Color.FromArgb(25, 26, 32), 90f))
                g.FillRectangle(bg, ClientRectangle);

            using (SolidBrush glow = new SolidBrush(Color.FromArgb(18, Accent)))
                g.FillEllipse(glow, new Rectangle(ClientSize.Width - 330, -120, 450, 330));

            Rectangle card = new Rectangle(48, 122, 700, 370);
            using (GraphicsPath path = Rounded(card, 22))
            using (SolidBrush cardBrush = new SolidBrush(Card))
            using (Pen border = new Pen(Color.FromArgb(70, Accent)))
            {
                g.FillPath(cardBrush, path);
                g.DrawPath(border, path);
            }

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
            using (Font font = new Font("Segoe UI", size, style))
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.DrawString(text, font, brush, rect, new StringFormat { Alignment = StringAlignment.Near, LineAlignment = StringAlignment.Center });
            }
        }

        private static GraphicsPath Rounded(Rectangle r, int radius)
        {
            GraphicsPath p = new GraphicsPath();
            int d = radius * 2;
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            if (cts != null)
            {
                cts.Cancel();
                cts.Dispose();
            }
            http.Dispose();
            base.OnFormClosed(e);
        }
    }
}
