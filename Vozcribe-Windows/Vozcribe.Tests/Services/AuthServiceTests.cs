using Vozcribe.Services;

namespace Vozcribe.Tests.Services;

public class AuthServiceTests
{
    [Fact]
    public void GenerateCodeVerifier_Returns43CharString()
    {
        var verifier = AuthService.GenerateCodeVerifier();
        Assert.True(verifier.Length >= 43);
        Assert.DoesNotContain("+", verifier);
        Assert.DoesNotContain("/", verifier);
        Assert.DoesNotContain("=", verifier);
    }

    [Fact]
    public void GenerateCodeChallenge_IsDeterministic()
    {
        var verifier = "test_verifier_value_12345678901234567890";
        var challenge1 = AuthService.GenerateCodeChallenge(verifier);
        var challenge2 = AuthService.GenerateCodeChallenge(verifier);
        Assert.Equal(challenge1, challenge2);
    }

    [Fact]
    public void GenerateCodeChallenge_IsUrlSafeBase64()
    {
        var verifier = AuthService.GenerateCodeVerifier();
        var challenge = AuthService.GenerateCodeChallenge(verifier);
        Assert.DoesNotContain("+", challenge);
        Assert.DoesNotContain("/", challenge);
        Assert.DoesNotContain("=", challenge);
    }

    [Fact]
    public void BuildAuthUrl_ContainsRequiredParams()
    {
        var url = AuthService.BuildGoogleAuthUrl("test_client_id", "test_challenge", "http://localhost:1234");
        Assert.Contains("client_id=test_client_id", url);
        Assert.Contains("code_challenge=test_challenge", url);
        Assert.Contains("redirect_uri=", url);
        Assert.Contains("response_type=code", url);
        Assert.Contains("scope=", url);
    }
}
