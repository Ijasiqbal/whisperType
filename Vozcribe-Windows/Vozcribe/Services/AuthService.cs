using System.Diagnostics;
using System.Net;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Vozcribe.Models;
using Vozcribe.ViewModels;

namespace Vozcribe.Services;

public class AuthService : ViewModelBase
{
    private readonly HttpClient _http = new();
    private string? _idToken;
    private string? _refreshToken;
    private DateTime _tokenExpiry;
    private string? _email;
    private bool _isSignedIn;

    public bool IsSignedIn
    {
        get => _isSignedIn;
        private set => SetField(ref _isSignedIn, value);
    }

    public string? Email
    {
        get => _email;
        private set => SetField(ref _email, value);
    }

    public static string GenerateCodeVerifier()
    {
        var bytes = RandomNumberGenerator.GetBytes(32);
        return Convert.ToBase64String(bytes)
            .Replace("+", "-")
            .Replace("/", "_")
            .TrimEnd('=');
    }

    public static string GenerateCodeChallenge(string verifier)
    {
        var bytes = SHA256.HashData(Encoding.ASCII.GetBytes(verifier));
        return Convert.ToBase64String(bytes)
            .Replace("+", "-")
            .Replace("/", "_")
            .TrimEnd('=');
    }

    public static string BuildGoogleAuthUrl(string clientId, string codeChallenge, string redirectUri)
    {
        var scope = Uri.EscapeDataString("openid email profile");
        return "https://accounts.google.com/o/oauth2/v2/auth"
            + $"?client_id={Uri.EscapeDataString(clientId)}"
            + $"&redirect_uri={Uri.EscapeDataString(redirectUri)}"
            + "&response_type=code"
            + $"&scope={scope}"
            + $"&code_challenge={Uri.EscapeDataString(codeChallenge)}"
            + "&code_challenge_method=S256";
    }

    public async Task<bool> SignInWithGoogleAsync(string googleClientId, string firebaseApiKey)
    {
        var codeVerifier = GenerateCodeVerifier();
        var codeChallenge = GenerateCodeChallenge(codeVerifier);

        int port = GetAvailablePort();
        var redirectUri = $"http://localhost:{port}";
        var listener = new HttpListener();
        listener.Prefixes.Add(redirectUri + "/");
        listener.Start();

        var authUrl = BuildGoogleAuthUrl(googleClientId, codeChallenge, redirectUri);
        Process.Start(new ProcessStartInfo(authUrl) { UseShellExecute = true });

        var context = await listener.GetContextAsync();
        var code = context.Request.QueryString["code"];

        var responseHtml = "<html><body><h2>Sign-in successful!</h2><p>You can close this window.</p></body></html>";
        var buffer = Encoding.UTF8.GetBytes(responseHtml);
        context.Response.ContentLength64 = buffer.Length;
        context.Response.ContentType = "text/html";
        await context.Response.OutputStream.WriteAsync(buffer);
        context.Response.Close();
        listener.Stop();

        if (string.IsNullOrEmpty(code))
            return false;

        var googleTokens = await ExchangeCodeForGoogleTokensAsync(
            code, googleClientId, codeVerifier, redirectUri);

        if (googleTokens == null)
            return false;

        var firebaseResult = await ExchangeGoogleTokenForFirebaseAsync(
            googleTokens.IdToken, firebaseApiKey);

        if (firebaseResult == null)
            return false;

        _idToken = firebaseResult.IdToken;
        _refreshToken = firebaseResult.RefreshToken;
        _tokenExpiry = DateTime.UtcNow.AddSeconds(int.Parse(firebaseResult.ExpiresIn) - 300);
        Email = firebaseResult.Email;
        IsSignedIn = true;

        SaveTokensToCredentialManager();
        return true;
    }

    public async Task<string?> GetIdTokenAsync()
    {
        if (!IsSignedIn || _idToken == null)
            return null;

        if (DateTime.UtcNow >= _tokenExpiry && _refreshToken != null)
            await RefreshTokenAsync();

        return _idToken;
    }

    public void SignOut()
    {
        _idToken = null;
        _refreshToken = null;
        Email = null;
        IsSignedIn = false;
        DeleteTokensFromCredentialManager();
    }

    public bool TryRestoreSession()
    {
        var (idToken, refreshToken, email) = LoadTokensFromCredentialManager();
        if (idToken == null || refreshToken == null)
            return false;

        _idToken = idToken;
        _refreshToken = refreshToken;
        _tokenExpiry = DateTime.MinValue;
        Email = email;
        IsSignedIn = true;
        return true;
    }

    private async Task<GoogleTokenResponse?> ExchangeCodeForGoogleTokensAsync(
        string code, string clientId, string codeVerifier, string redirectUri)
    {
        var content = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["code"] = code,
            ["client_id"] = clientId,
            ["redirect_uri"] = redirectUri,
            ["grant_type"] = "authorization_code",
            ["code_verifier"] = codeVerifier
        });

        var response = await _http.PostAsync("https://oauth2.googleapis.com/token", content);
        if (!response.IsSuccessStatusCode) return null;

        var json = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<GoogleTokenResponse>(json);
    }

    private async Task<FirebaseSignInResponse?> ExchangeGoogleTokenForFirebaseAsync(
        string googleIdToken, string firebaseApiKey)
    {
        var url = $"https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key={firebaseApiKey}";
        var body = new
        {
            postBody = $"id_token={googleIdToken}&providerId=google.com",
            requestUri = "http://localhost",
            returnSecureToken = true,
            returnIdpCredential = true
        };

        var json = JsonSerializer.Serialize(body);
        var content = new StringContent(json, Encoding.UTF8, "application/json");
        var response = await _http.PostAsync(url, content);

        if (!response.IsSuccessStatusCode) return null;

        var responseJson = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<FirebaseSignInResponse>(responseJson);
    }

    private async Task RefreshTokenAsync()
    {
        var url = $"https://securetoken.googleapis.com/v1/token?key={Constants.FirebaseApiKey}";
        var content = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["grant_type"] = "refresh_token",
            ["refresh_token"] = _refreshToken!
        });

        var response = await _http.PostAsync(url, content);
        if (!response.IsSuccessStatusCode)
        {
            SignOut();
            return;
        }

        var json = await response.Content.ReadAsStringAsync();
        var result = JsonSerializer.Deserialize<FirebaseRefreshResponse>(json);
        if (result != null)
        {
            _idToken = result.IdToken;
            _refreshToken = result.RefreshToken;
            _tokenExpiry = DateTime.UtcNow.AddSeconds(int.Parse(result.ExpiresIn) - 300);
        }
    }

    private static int GetAvailablePort()
    {
        var listener = new System.Net.Sockets.TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        int port = ((System.Net.IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }

    private void SaveTokensToCredentialManager()
    {
        var data = JsonSerializer.Serialize(new StoredCredentials
        {
            IdToken = _idToken,
            RefreshToken = _refreshToken,
            Email = Email
        });
        var encrypted = System.Security.Cryptography.ProtectedData.Protect(
            Encoding.UTF8.GetBytes(data), null,
            System.Security.Cryptography.DataProtectionScope.CurrentUser);
        var path = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            Constants.AppDataFolder, ".auth");
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllBytes(path, encrypted);
    }

    private (string? idToken, string? refreshToken, string? email) LoadTokensFromCredentialManager()
    {
        var path = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            Constants.AppDataFolder, ".auth");
        if (!File.Exists(path))
            return (null, null, null);

        try
        {
            var encrypted = File.ReadAllBytes(path);
            var decrypted = System.Security.Cryptography.ProtectedData.Unprotect(
                encrypted, null,
                System.Security.Cryptography.DataProtectionScope.CurrentUser);
            var creds = JsonSerializer.Deserialize<StoredCredentials>(
                Encoding.UTF8.GetString(decrypted));
            return (creds?.IdToken, creds?.RefreshToken, creds?.Email);
        }
        catch
        {
            return (null, null, null);
        }
    }

    private void DeleteTokensFromCredentialManager()
    {
        var path = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            Constants.AppDataFolder, ".auth");
        if (File.Exists(path))
            File.Delete(path);
    }

    private class StoredCredentials
    {
        public string? IdToken { get; set; }
        public string? RefreshToken { get; set; }
        public string? Email { get; set; }
    }
}

public class GoogleTokenResponse
{
    [JsonPropertyName("id_token")]
    public string IdToken { get; set; } = "";

    [JsonPropertyName("access_token")]
    public string AccessToken { get; set; } = "";
}

public class FirebaseSignInResponse
{
    [JsonPropertyName("idToken")]
    public string IdToken { get; set; } = "";

    [JsonPropertyName("refreshToken")]
    public string RefreshToken { get; set; } = "";

    [JsonPropertyName("expiresIn")]
    public string ExpiresIn { get; set; } = "3600";

    [JsonPropertyName("email")]
    public string Email { get; set; } = "";
}

public class FirebaseRefreshResponse
{
    [JsonPropertyName("id_token")]
    public string IdToken { get; set; } = "";

    [JsonPropertyName("refresh_token")]
    public string RefreshToken { get; set; } = "";

    [JsonPropertyName("expires_in")]
    public string ExpiresIn { get; set; } = "3600";
}
