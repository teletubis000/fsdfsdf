using System.Drawing;
using System.Windows.Forms;

namespace ZarexLoader
{
    partial class Form1
    {
        private Panel headerPanel;
        private Label titleLabel;
        private Label subtitleLabel;
        private Label accountLabel;
        private Label helperLabel;
        private Label statusLabel;
        private Label percentLabel;
        private TextBox emailTextBox;
        private TextBox passwordTextBox;
        private Button signInButton;
        private Button loadButton;
        private Button closeButton;
        private ProgressBar progressBar;

        private void InitializeComponent()
        {
            headerPanel = new Panel();
            titleLabel = new Label();
            subtitleLabel = new Label();
            accountLabel = new Label();
            helperLabel = new Label();
            statusLabel = new Label();
            percentLabel = new Label();
            emailTextBox = new TextBox();
            passwordTextBox = new TextBox();
            signInButton = new Button();
            loadButton = new Button();
            closeButton = new Button();
            progressBar = new ProgressBar();
            SuspendLayout();

            headerPanel.BackColor = Color.Transparent;
            headerPanel.Location = new Point(0, 0);
            headerPanel.Name = "headerPanel";
            headerPanel.Size = new Size(920, 115);
            headerPanel.TabIndex = 0;

            titleLabel.AutoSize = true;
            titleLabel.BackColor = Color.Transparent;
            titleLabel.Font = new Font("Segoe UI Semibold", 24F, FontStyle.Bold);
            titleLabel.ForeColor = Color.FromArgb(240, 241, 245);
            titleLabel.Location = new Point(52, 42);
            titleLabel.Name = "titleLabel";
            titleLabel.Size = new Size(115, 45);
            titleLabel.TabIndex = 0;
            titleLabel.Text = "ZAREX";

            subtitleLabel.AutoSize = true;
            subtitleLabel.BackColor = Color.Transparent;
            subtitleLabel.Font = new Font("Segoe UI", 9F, FontStyle.Bold);
            subtitleLabel.ForeColor = Color.FromArgb(245, 225, 45);
            subtitleLabel.Location = new Point(55, 82);
            subtitleLabel.Name = "subtitleLabel";
            subtitleLabel.Size = new Size(210, 15);
            subtitleLabel.TabIndex = 1;

            closeButton.FlatAppearance.BorderSize = 0;
            closeButton.FlatStyle = FlatStyle.Flat;
            closeButton.Font = new Font("Segoe UI", 18F);
            closeButton.ForeColor = Color.FromArgb(145, 149, 160);
            closeButton.Location = new Point(862, 12);
            closeButton.Name = "closeButton";
            closeButton.Size = new Size(44, 44);
            closeButton.TabIndex = 2;
            closeButton.Text = "×";
            closeButton.UseVisualStyleBackColor = false;
            closeButton.Anchor = AnchorStyles.Top | AnchorStyles.Right;

            accountLabel.AutoSize = true;
            accountLabel.BackColor = Color.Transparent;
            accountLabel.Font = new Font("Segoe UI", 9F, FontStyle.Bold);
            accountLabel.ForeColor = Color.FromArgb(145, 149, 160);
            accountLabel.Location = new Point(92, 154);
            accountLabel.Name = "accountLabel";
            accountLabel.Size = new Size(60, 15);
            accountLabel.TabIndex = 3;

            emailTextBox.BorderStyle = BorderStyle.FixedSingle;
            emailTextBox.Font = new Font("Segoe UI", 11F);
            emailTextBox.Location = new Point(92, 196);
            emailTextBox.Name = "emailTextBox";
            emailTextBox.Size = new Size(390, 27);
            emailTextBox.TabIndex = 4;

            passwordTextBox.BorderStyle = BorderStyle.FixedSingle;
            passwordTextBox.Font = new Font("Segoe UI", 11F);
            passwordTextBox.Location = new Point(92, 262);
            passwordTextBox.Name = "passwordTextBox";
            passwordTextBox.Size = new Size(390, 27);
            passwordTextBox.TabIndex = 5;
            passwordTextBox.UseSystemPasswordChar = true;

            signInButton.FlatAppearance.BorderSize = 0;
            signInButton.FlatStyle = FlatStyle.Flat;
            signInButton.Font = new Font("Segoe UI Semibold", 10F, FontStyle.Bold);
            signInButton.Location = new Point(92, 338);
            signInButton.Name = "signInButton";
            signInButton.Size = new Size(390, 50);
            signInButton.TabIndex = 6;
            signInButton.Text = "SIGN IN";
            signInButton.UseVisualStyleBackColor = false;

            loadButton.FlatAppearance.BorderSize = 0;
            loadButton.FlatStyle = FlatStyle.Flat;
            loadButton.Font = new Font("Segoe UI Semibold", 10F, FontStyle.Bold);
            loadButton.Location = new Point(92, 352);
            loadButton.Name = "loadButton";
            loadButton.Size = new Size(390, 52);
            loadButton.TabIndex = 7;
            loadButton.Text = "LOAD CLIENT";
            loadButton.UseVisualStyleBackColor = false;
            loadButton.Visible = false;

            helperLabel.AutoSize = true;
            helperLabel.BackColor = Color.Transparent;
            helperLabel.Font = new Font("Segoe UI", 9F);
            helperLabel.ForeColor = Color.FromArgb(145, 149, 160);
            helperLabel.Location = new Point(92, 430);
            helperLabel.Name = "helperLabel";
            helperLabel.Size = new Size(190, 15);
            helperLabel.TabIndex = 8;

            statusLabel.AutoEllipsis = true;
            statusLabel.BackColor = Color.Transparent;
            statusLabel.Font = new Font("Segoe UI", 9F);
            statusLabel.ForeColor = Color.FromArgb(145, 149, 160);
            statusLabel.Location = new Point(92, 414);
            statusLabel.Name = "statusLabel";
            statusLabel.Size = new Size(600, 24);
            statusLabel.TabIndex = 9;
            statusLabel.Text = "Sign in with your Zarex account.";

            progressBar.Location = new Point(92, 456);
            progressBar.Name = "progressBar";
            progressBar.Size = new Size(600, 8);
            progressBar.Style = ProgressBarStyle.Continuous;
            progressBar.TabIndex = 10;
            progressBar.Visible = false;

            percentLabel.AutoSize = true;
            percentLabel.Font = new Font("Segoe UI", 9F, FontStyle.Bold);
            percentLabel.ForeColor = Color.FromArgb(145, 149, 160);
            percentLabel.Location = new Point(705, 448);
            percentLabel.Name = "percentLabel";
            percentLabel.Size = new Size(31, 15);
            percentLabel.TabIndex = 11;
            percentLabel.Text = "0%";
            percentLabel.Visible = false;

            Controls.Add(headerPanel);
            Controls.Add(closeButton);
            Controls.Add(titleLabel);
            Controls.Add(subtitleLabel);
            Controls.Add(accountLabel);
            Controls.Add(emailTextBox);
            Controls.Add(passwordTextBox);
            Controls.Add(signInButton);
            Controls.Add(loadButton);
            Controls.Add(helperLabel);
            Controls.Add(statusLabel);
            Controls.Add(progressBar);
            Controls.Add(percentLabel);

            AutoScaleDimensions = new SizeF(7F, 15F);
            AutoScaleMode = AutoScaleMode.Font;
            ClientSize = new Size(920, 570);
            Name = "Form1";
            Text = "Zarex Loader";
            ResumeLayout(false);
            PerformLayout();
        }
    }
}
