using System.Drawing.Drawing2D;
using System.Drawing.Text;

namespace ZarexLoader;

internal sealed class LoaderForm : Form
{
    private readonly LoaderService _service = new();
    private readonly Panel _root = new();
    private readonly TextBox _email = new();
    private readonly TextBox _password = new();
    private readonly Button _login = new();
    private readonly Button _load = new();
    private readonly Button _close = new();
    private readonly Label _title = new();
    private readonly Label _subtitle = new();
    private readonly Label _status = new();
    private readonly Label _percent = new();
    private readonly ProgressBar _progress = new();
    private CancellationTokenSource? _cts;
    private bool _dragging;
    private Point _dragStart;
    private int _screen;

    private static readonly Color Bg = Color.FromArgb(10, 11, 14);
    private static readonly Color Card = Color.FromArgb(23, 25, 30);
    private static readonly Color Field = Color.FromArgb(30, 32, 39);
    private static readonly Color White = Color.FromArgb(240, 241, 245);
    private static readonly Color Muted = Color.FromArgb(145, 149, 160);
    private static readonly Color Accent = Color.FromArgb(245, 225, 45);

    public LoaderForm()
    {
        Text = "Zarex Loader";
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(920, 570);
        BackColor = Bg;
        DoubleBuffered = true;
        BuildUi();
        ShowLogin();
    }

    private void BuildUi()
    {
        _root.Dock = DockStyle.Fill;
        _root.BackColor = Bg;
        _root.Paint += PaintRoot;
        _root.MouseDown += DragStart;
        _root.MouseMove += DragMove;
        Controls.Add(_root);

        _close.Text = "×";
        _close.FlatStyle = FlatStyle.Flat;
        _close.FlatAppearance.BorderSize = 0;
        _close.BackColor = Color.Transparent;
        _close.ForeColor = Muted;
        _close.Font = new Font("Segoe UI", 18);
        _close.Size = new Size(44, 44);
        _close.Location = new Point(ClientSize.Width - 58, 12);
        _close.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        _close.Click += (_, _) => Close();
        _root.Controls.Add(_close);

        _title.Font = new Font("Segoe UI Semibold", 24, FontStyle.Bold);
        _title.ForeColor = White;
        _title.AutoSize = true;
        _title.Location = new Point(52, 42);
        _root.Controls.Add(_title);

        _subtitle.Font = new Font("Segoe UI", 9, FontStyle.Bold);
        _subtitle.ForeColor = Accent;
        _subtitle.AutoSize = true;
        _subtitle.Location = new Point(55, 82);
        _root.Controls.Add(_subtitle);

        SetupBox(_email, "Email");
        SetupBox(_password, "Password");
        _password.UseSystemPasswordChar = true;
        _email.Bounds = new Rectangle(92, 196, 390, 46);
        _password.Bounds = new Rectangle(92, 262, 390, 46);
        _root.Controls.AddRange(new Control[] { _email, _password });

        SetupButton(_login, "SIGN IN");
        _login.Bounds = new Rectangle(92, 338, 390, 50);
        _login.Click += async (_, _) => await LoginAsync();
        _root.Controls.Add(_login);

        SetupButton(_load, "LOAD CLIENT");
        _load.Bounds = new Rectangle(92, 352, 390, 52);
        _load.Visible = false;
        _load.Click += async (_, _) => await LoadAsync();
        _root.Controls.Add(_load);

        _status.ForeColor = Muted;
        _status.Font = new Font("Segoe UI", 9);
        _status.Bounds = new Rectangle(92, 414, 600, 30);
        _root.Controls.Add(_status);

        _progress.Bounds = new Rectangle(92, 456, 600, 8);
        _progress.Minimum = 0;
        _progress.Maximum = 100;
        _progress.Visible = false;
        _root.Controls.Add(_progress);

        _percent.ForeColor = Muted;
        _percent.Font = new Font("Segoe UI", 9, FontStyle.Bold);
        _percent.AutoSize = true;
        _percent.Location = new Point(705, 448);
        _percent.Visible = false;
        _root.Controls.Add(_percent);
    }

    private static void SetupBox(TextBox box, string placeholder)
    {
        box.BorderStyle = BorderStyle.FixedSingle;
        box.BackColor = Field;
        box.ForeColor = White;
        box.Font = new Font("Segoe UI", 11);
        box.PlaceholderText = placeholder;
    }

    private static void SetupButton(Button b, string text)
    {
        b.Text = text;
        b.FlatStyle = FlatStyle.Flat;
        b.FlatAppearance.BorderSize = 0;
        b.BackColor = Accent;
        b.ForeColor = Color.FromArgb(20, 20, 20);
        b.Font = new Font("Segoe UI Semibold", 10, FontStyle.Bold);
        b.Cursor = Cursors.Hand;
    }

    private void ShowLogin()
    {
        _screen = 0;
        _title.Text = "ZAREX";
        _subtitle.Text = "CLIENT LOADER  •  FABRIC 1.21.11";
        _email.Visible = _password.Visible = _login.Visible = true;
        _load.Visible = false;
        _progress.Visible = _percent.Visible = false;
        _status.Text = "Sign in with your Zarex account.";
        _status.ForeColor = Muted;
        _root.Invalidate();
    }

    private void ShowDashboard()
    {
        _screen = 1;
        _title.Text = "WELCOME BACK";
        _subtitle.Text = "ZAREX CLIENT  •  READY TO LAUNCH";
        _email.Visible = _password.Visible = _login.Visible = false;
        _load.Visible = true;
        _progress.Visible = _percent.Visible = false;
        _status.Text = "Your account is verified. Prepare Minecraft.";
        _status.ForeColor = Color.FromArgb(125, 210, 145);
        _root.Invalidate();
    }

    private void ShowLoading()
    {
        _screen = 2;
        _email.Visible = _password.Visible = _login.Visible = _load.Visible = false;
        _progress.Visible = _percent.Visible = true;
        _progress.Value = 0;
        _root.Invalidate();
    }

    private async Task LoginAsync()
    {
        if (string.IsNullOrWhiteSpace(_email.Text) || string.IsNullOrWhiteSpace(_password.Text))
        {
            _status.Text = "Enter email and password.";
            _status.ForeColor = Color.Salmon;
            return;
        }

        SetBusy(true);
        _cts?.Dispose();
        _cts = new CancellationTokenSource();
        try
        {
            _status.Text = "Authenticating...";
            var result = await _service.LoginAsync(_email.Text.Trim(), _password.Text, _cts.Token);
            if (!result.Success)
            {
                _status.Text = result.Error;
                _status.ForeColor = Color.Salmon;
                return;
            }
            ShowDashboard();
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            _status.Text = ex.Message;
            _status.ForeColor = Color.Salmon;
        }
        finally { SetBusy(false); }
    }

    private async Task LoadAsync()
    {
        ShowLoading();
        SetBusy(true);
        _cts?.Dispose();
        _cts = new CancellationTokenSource();

        var progress = new Progress<LoadProgress>(p =>
        {
            _progress.Value = Math.Clamp(p.Percent, 0, 100);
            _percent.Text = $"{p.Percent}%";
            _status.Text = p.Status;
        });

        try
        {
            await _service.PrepareAsync(progress, _cts.Token);
            if (!_service.LaunchMinecraft())
                throw new InvalidOperationException("Minecraft Launcher.exe was not found.");
            Close();
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            ShowDashboard();
            _status.Text = ex.Message;
            _status.ForeColor = Color.Salmon;
        }
        finally { SetBusy(false); }
    }

    private void SetBusy(bool busy)
    {
        _login.Enabled = _load.Enabled = !busy;
        _email.Enabled = _password.Enabled = !busy;
        Cursor = busy ? Cursors.WaitCursor : Cursors.Default;
    }

    private void PaintRoot(object? sender, PaintEventArgs e)
    {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

        using var bg = new LinearGradientBrush(ClientRectangle,
            Color.FromArgb(9, 10, 13), Color.FromArgb(25, 26, 32), 90f);
        g.FillRectangle(bg, ClientRectangle);

        using var glow = new SolidBrush(Color.FromArgb(18, Accent));
        g.FillEllipse(glow, new Rectangle(ClientSize.Width - 330, -120, 450, 330));

        var card = new Rectangle(48, 122, 700, 370);
        using var path = Rounded(card, 22);
        using var cardBrush = new SolidBrush(Card);
        using var border = new Pen(Color.FromArgb(70, Accent), 1);
        g.FillPath(cardBrush, path);
        g.DrawPath(border, path);

        Draw(g, "●  ONLINE", new Rectangle(52, 44, 130, 22), 8, Muted, FontStyle.Bold);

        if (_screen == 0)
        {
            Draw(g, "ACCOUNT", new Rectangle(92, 154, 390, 24), 9, Muted, FontStyle.Bold);
            Draw(g, "Secure access to your client.", new Rectangle(92, 430, 520, 22), 9, Muted, FontStyle.Regular);
        }
        else if (_screen == 1)
        {
            Draw(g, "READY", new Rectangle(92, 164, 390, 32), 18, White, FontStyle.Bold);
            Draw(g, "Fabric environment is ready for launch.", new Rectangle(92, 202, 500, 25), 10, Muted, FontStyle.Regular);
            Draw(g, "MINECRAFT", new Rectangle(92, 265, 180, 20), 9, Muted, FontStyle.Bold);
            Draw(g, "1.21.11", new Rectangle(92, 288, 180, 32), 16, White, FontStyle.Bold);
            Draw(g, "FABRIC", new Rectangle(310, 265, 180, 20), 9, Muted, FontStyle.Bold);
            Draw(g, "0.18.1", new Rectangle(310, 288, 180, 32), 16, White, FontStyle.Bold);
        }
        else
        {
            Draw(g, "LOADING", new Rectangle(92, 164, 390, 32), 18, White, FontStyle.Bold);
            Draw(g, "Installing the client environment...", new Rectangle(92, 202, 500, 25), 10, Muted, FontStyle.Regular);
        }
    }

    private static void Draw(Graphics g, string text, Rectangle rect, float size, Color color, FontStyle style)
    {
        using var font = new Font("Segoe UI", size, style);
        using var brush = new SolidBrush(color);
        g.DrawString(text, font, brush, rect, new StringFormat
        {
            Alignment = StringAlignment.Near,
            LineAlignment = StringAlignment.Center
        });
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

    private void DragStart(object? sender, MouseEventArgs e)
    {
        if (e.Button != MouseButtons.Left) return;
        _dragging = true;
        _dragStart = e.Location;
    }

    private void DragMove(object? sender, MouseEventArgs e)
    {
        if (!_dragging || e.Button != MouseButtons.Left) return;
        var screen = PointToScreen(e.Location);
        Location = new Point(screen.X - _dragStart.X, screen.Y - _dragStart.Y);
    }

    protected override void OnMouseUp(MouseEventArgs e)
    {
        _dragging = false;
        base.OnMouseUp(e);
    }

    protected override void OnFormClosed(FormClosedEventArgs e)
    {
        _cts?.Cancel();
        _cts?.Dispose();
        base.OnFormClosed(e);
    }
}
