using System.Diagnostics;
using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace ZarexLoader;

internal sealed class LoaderService
{
    private const string FirebaseDb = "https://zarex-client-default-rtdb.firebaseio.com";
    private const string SignInBase = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";
    private const string FabricInstaller = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/fabric-installer-1.0.3.jar";
    private const string FabricApi = "https://cdn.modrinth.com/data/P7dR8mSH/versions/6qAuTtLR/fabric-api-0.141.6%2B1.21.11.jar";
    private const string ZarexMod = "https://cdn.discordapp.com/attachments/1547863897715908669/1551245396561371156/zarex-client-1.0.0.jar?ex=6ab1455b&is=6aaff3db&hm=78919bfca64b4aef8a7bb7d533442022e487ac58fd2de1a1569b0909db4539d3&";
    private const string MinecraftVersion = "1.21.11";
    private const string FabricLoader = "0.18.1";

    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(45) };

    public async Task<LoginResult> LoginAsync(string email, string password, CancellationToken ct)
    {
        var key = Environment.GetEnvironmentVariable("ZAREX_FIREBASE_API_KEY");
        if (string.IsNullOrWhiteSpace(key))
            return LoginResult.Fail("Set ZAREX_FIREBASE_API_KEY before using login.");

        var json = JsonSerializer.Serialize(new { email, password, returnSecureToken = true });
        using var response = await _http.PostAsync(SignInBase + key,
            new StringContent(json, Encoding.UTF8, "application/json"), ct);
        var body = await response.Content.ReadAsStringAsync(ct);

        if (!response.IsSuccessStatusCode)
            return LoginResult.Fail(FirebaseError(body));

        using var auth = JsonDocument.Parse(body);
        var root = auth.RootElement;
        var token = root.GetProperty("idToken").GetString() ?? "";
        var uid = root.GetProperty("localId").GetString() ?? "";

        using var profileResponse = await _http.GetAsync(
            $"{FirebaseDb}/users/{Uri.EscapeDataString(uid)}.json?auth={Uri.EscapeDataString(token)}", ct);
        var profileBody = await profileResponse.Content.ReadAsStringAsync(ct);

        if (!profileResponse.IsSuccessStatusCode || profileBody == "null")
            return LoginResult.Fail("ACCOUNT_PROFILE_NOT_FOUND");

        using var profile = JsonDocument.Parse(profileBody);
        var p = profile.RootElement;

        if (!p.TryGetProperty("active", out var active) || !active.GetBoolean())
            return LoginResult.Fail("ACCOUNT_INACTIVE");

        if (!p.TryGetProperty("licenseExpiresAt", out var expiresElement))
            return LoginResult.Fail("LICENSE_REQUIRED");

        if (!DateTimeOffset.TryParse(expiresElement.GetString(), out var expires) ||
            expires <= DateTimeOffset.UtcNow)
            return LoginResult.Fail("LICENSE_EXPIRED");

        return LoginResult.Ok(expires);
    }

    public async Task PrepareAsync(IProgress<LoadProgress> progress, CancellationToken ct)
    {
        var gameDir = Path.Combine(Path.GetTempPath(), "ZarexMinecraft", ".minecraft");
        Directory.CreateDirectory(gameDir);
        var installer = Path.GetFullPath(Path.Combine(gameDir, "..", "fabric-installer-1.0.3.jar"));

        progress.Report(new(5, "Downloading Fabric installer"));
        await DownloadAsync(FabricInstaller, installer, progress, 5, 15, ct);

        progress.Report(new(22, "Installing Fabric"));
        var java = FindJava() ?? throw new InvalidOperationException("Java was not found.");
        var args = $"-jar \"{installer}\" client -dir \"{gameDir}\" -mcversion {MinecraftVersion} -loader {FabricLoader}";

        if (!await RunProcessAsync(java, args, gameDir, ct))
            throw new InvalidOperationException("Fabric installation failed.");

        var mods = Path.Combine(gameDir, "mods");
        Directory.CreateDirectory(mods);

        progress.Report(new(42, "Downloading Fabric API"));
        await DownloadAsync(FabricApi, Path.Combine(mods, "fabric-api-0.141.6+1.21.11.jar"), progress, 42, 30, ct);

        progress.Report(new(75, "Downloading Zarex Client"));
        await DownloadAsync(ZarexMod, Path.Combine(mods, "zarex-client-1.0.0.jar"), progress, 75, 20, ct);

        CopyOptions(gameDir);
        progress.Report(new(100, "Ready"));
    }

    public bool LaunchMinecraft()
    {
        var candidates = new[]
        {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Minecraft Launcher", "MinecraftLauncher.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Minecraft Launcher", "MinecraftLauncher.exe"),
            @"C:\XboxGames\Minecraft Launcher\Content\Minecraft.exe"
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

    private async Task DownloadAsync(string url, string destination, IProgress<LoadProgress> progress,
        int baseProgress, int span, CancellationToken ct)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        using var response = await _http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct);
        response.EnsureSuccessStatusCode();

        var total = response.Content.Headers.ContentLength;
        await using var input = await response.Content.ReadAsStreamAsync(ct);
        await using var output = File.Create(destination);

        var buffer = new byte[128 * 1024];
        long done = 0;
        int read;
        while ((read = await input.ReadAsync(buffer, ct)) > 0)
        {
            await output.WriteAsync(buffer.AsMemory(0, read), ct);
            done += read;
            if (total is > 0)
            {
                var local = (int)Math.Clamp(done * 100 / total.Value, 0, 100);
                progress.Report(new(baseProgress + local * span / 100, $"Downloading {Path.GetFileName(destination)}"));
            }
        }
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

        foreach (var dir in (Environment.GetEnvironmentVariable("PATH") ?? "")
            .Split(';', StringSplitOptions.RemoveEmptyEntries))
        {
            foreach (var name in new[] { "javaw.exe", "java.exe" })
            {
                var path = Path.Combine(dir.Trim(), name);
                if (File.Exists(path)) return path;
            }
        }
        return null;
    }

    private static async Task<bool> RunProcessAsync(string executable, string arguments, string workingDirectory, CancellationToken ct)
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
        await process.WaitForExitAsync(ct);
        return process.ExitCode == 0;
    }

    private static void CopyOptions(string gameDir)
    {
        var publicDir = Path.Combine(Environment.GetEnvironmentVariable("PUBLIC") ?? @"C:\Users\Public", "Zarex");
        var source = Path.Combine(publicDir, "options.txt");
        var destination = Path.Combine(gameDir, "options.txt");
        if (File.Exists(source)) File.Copy(source, destination, true);
    }
}

internal readonly record struct LoginResult(bool Success, string Error, DateTimeOffset? ExpiresAt)
{
    public static LoginResult Ok(DateTimeOffset expires) => new(true, "", expires);
    public static LoginResult Fail(string error) => new(false, error, null);
}

internal readonly record struct LoadProgress(int Percent, string Status);
