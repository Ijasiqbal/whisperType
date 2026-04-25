using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using Vozcribe.Models;

namespace Vozcribe.Services;

public class ApiClient
{
    private static readonly HttpClient Http = new()
    {
        Timeout = Constants.ApiTimeout
    };

    private readonly SettingsService _settings;
    private Func<Task<string?>>? _getToken;

    public ApiClient(SettingsService settings)
    {
        _settings = settings;
    }

    public void SetTokenProvider(Func<Task<string?>> getToken)
    {
        _getToken = getToken;
    }

    public static string BuildBaseUrl(string region) =>
        string.Format(Constants.BaseUrlPattern, region);

    public static Dictionary<string, object> BuildTranscriptionBody(
        string audioBase64, string audioFormat, string tierCode,
        int durationMs, bool isTwoStage, string? llmTierCode, string? requestTier)
    {
        var body = new Dictionary<string, object>
        {
            ["audioBase64"] = audioBase64,
            ["audioFormat"] = audioFormat,
            ["model"] = tierCode,
            ["audioDurationMs"] = durationMs
        };

        if (isTwoStage)
        {
            if (llmTierCode != null) body["llmModel"] = llmTierCode;
            if (requestTier != null) body["tier"] = requestTier;
        }

        return body;
    }

    public async Task<TranscriptionResult> TranscribeAsync(
        byte[] opusAudio, int durationMs, ModelTier tier)
    {
        var token = _getToken != null ? await _getToken() : null;
        if (token == null)
            throw new InvalidOperationException("Not authenticated");

        var baseUrl = BuildBaseUrl(_settings.Settings.Region);
        var url = baseUrl + tier.Endpoint;

        var audioBase64 = Convert.ToBase64String(opusAudio);
        var body = BuildTranscriptionBody(
            audioBase64, "opus", tier.TierCode, durationMs,
            tier.IsTwoStage, tier.LlmTierCode, tier.RequestTier);

        var json = JsonSerializer.Serialize(body);
        return await PostWithRetryAsync<TranscriptionResult>(url, json, token);
    }

    public async Task<TrialStatus> GetTrialStatusAsync()
    {
        var token = _getToken != null ? await _getToken() : null;
        if (token == null)
            throw new InvalidOperationException("Not authenticated");

        var baseUrl = BuildBaseUrl(_settings.Settings.Region);
        var url = baseUrl + Constants.TrialStatusPath;

        var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

        var response = await Http.SendAsync(request);
        response.EnsureSuccessStatusCode();
        var responseJson = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<TrialStatus>(responseJson)
            ?? throw new InvalidOperationException("Invalid trial status response");
    }

    public async Task<VersionStatus> CheckVersionAsync()
    {
        var baseUrl = BuildBaseUrl(Constants.DetectedRegion);
        var url = $"{baseUrl}{Constants.VersionCheckPath}?version={Constants.AppVersion}";

        try
        {
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(8));
            var response = await Http.GetAsync(url, cts.Token);
            if (!response.IsSuccessStatusCode)
                return new VersionStatus { Status = "ok" };

            var json = await response.Content.ReadAsStringAsync(cts.Token);
            return JsonSerializer.Deserialize<VersionStatus>(json)
                ?? new VersionStatus { Status = "ok" };
        }
        catch
        {
            // Fail open — never block the user if the check can't reach the server.
            return new VersionStatus { Status = "ok" };
        }
    }

    public async Task SubmitIssueAsync(
        string userId, string? userEmail, string category, string description)
    {
        var token = _getToken != null ? await _getToken() : null;
        if (token == null)
            throw new InvalidOperationException("Not authenticated");

        var url = $"https://firestore.googleapis.com/v1/projects/{Constants.FirebaseProjectId}/databases/(default)/documents/issues";
        var now = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ");

        var body = new
        {
            fields = new Dictionary<string, object>
            {
                ["userId"]      = new { stringValue = userId },
                ["userEmail"]   = new { stringValue = userEmail ?? "unknown" },
                ["category"]    = new { stringValue = category },
                ["description"] = new { stringValue = description },
                ["osVersion"]   = new { stringValue = Environment.OSVersion.ToString() },
                ["appVersion"]  = new { stringValue = Constants.AppVersion },
                ["platform"]    = new { stringValue = "windows" },
                ["createdAt"]   = new { timestampValue = now },
                ["status"]      = new { stringValue = "open" }
            }
        };

        var json = JsonSerializer.Serialize(body);
        var request = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(json, Encoding.UTF8, "application/json")
        };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

        var response = await Http.SendAsync(request);
        if (!response.IsSuccessStatusCode)
        {
            var error = await response.Content.ReadAsStringAsync();
            throw new HttpRequestException($"HTTP {(int)response.StatusCode}: {error}");
        }
    }

    public async Task WarmupEndpointsAsync()
    {
        var baseUrl = BuildBaseUrl(_settings.Settings.Region);
        var endpoints = new[]
        {
            "/transcribeAuto",
            "/transcribeStandard",
            "/transcribePremium"
        };

        using var cts = new CancellationTokenSource(Constants.WarmupTimeout);
        var tasks = endpoints.Select(ep =>
            Http.GetAsync(baseUrl + ep, cts.Token).ContinueWith(_ => { }));
        await Task.WhenAll(tasks);
    }

    private async Task<T> PostWithRetryAsync<T>(string url, string json, string token)
    {
        int backoffMs = Constants.InitialBackoffMs;

        for (int attempt = 0; attempt <= Constants.MaxRetries; attempt++)
        {
            var request = new HttpRequestMessage(HttpMethod.Post, url)
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json")
            };
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

            var response = await Http.SendAsync(request);

            if (response.IsSuccessStatusCode)
            {
                var responseJson = await response.Content.ReadAsStringAsync();
                return JsonSerializer.Deserialize<T>(responseJson)
                    ?? throw new InvalidOperationException("Invalid response");
            }

            int statusCode = (int)response.StatusCode;

            if (statusCode == 403)
            {
                var errorJson = await response.Content.ReadAsStringAsync();
                var errorObj = JsonSerializer.Deserialize<JsonObject>(errorJson);
                var message = errorObj?["message"]?.ToString() ?? "Quota exceeded";
                throw new QuotaExceededException(message);
            }

            if (statusCode == 401)
                throw new InvalidOperationException("Not authenticated");

            if (attempt < Constants.MaxRetries && Constants.RetryableStatusCodes.Contains(statusCode))
            {
                await Task.Delay(backoffMs);
                backoffMs = Math.Min(backoffMs * Constants.BackoffMultiplier, Constants.MaxBackoffMs);
                continue;
            }

            var errorContent = await response.Content.ReadAsStringAsync();
            throw new HttpRequestException($"API error {statusCode}: {errorContent}");
        }

        throw new HttpRequestException("Max retries exceeded");
    }
}

public class QuotaExceededException : Exception
{
    public QuotaExceededException(string message) : base(message) { }
}
