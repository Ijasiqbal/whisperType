import AuthenticationServices
import CryptoKit
import FirebaseAuth
import FirebaseCore
import Foundation

final class AuthManager: NSObject, ObservableObject {

    static let shared = AuthManager()

    @Published var isSignedIn = false
    @Published var userEmail: String?
    @Published var userName: String?
    @Published var isLoading = false

    var userUID: String? {
        if let uid = Auth.auth().currentUser?.uid { return uid }
        let uid = UserDefaults.standard.string(forKey: Constants.restUserUID) ?? ""
        return uid.isEmpty ? nil : uid
    }

    private var authStateHandle: AuthStateDidChangeListenerHandle?
    private var isConfigured = false
    private var pendingCodeVerifier: String?
    private var pendingRedirectURI: String?

    private override init() {
        super.init()
        // Don't touch Firebase here — it may not be configured yet.
        // Call configure() after FirebaseApp.configure().
        debugLog("[Auth] AuthManager created (Firebase not yet wired)")
    }

    /// Must be called once AFTER FirebaseApp.configure().
    func configure() {
        guard !isConfigured else { return }
        isConfigured = true

        // Use the app's default keychain instead of a shared access group.
        do {
            try Auth.auth().useUserAccessGroup(nil)
        } catch {
            debugLog("[Auth] Failed to set keychain access group: \(error.localizedDescription)")
        }

        // Read persisted session — try SDK first, then REST fallback
        let currentUser = Auth.auth().currentUser
        if let user = currentUser {
            self.isSignedIn = true
            self.userEmail = user.email
            self.userName = user.displayName
            debugLog("[Auth] configure() — SDK user: \(user.email ?? "none")")
            // Update lastSeen on every app launch for existing SDK sessions
            user.getIDToken { [weak self] token, _ in
                if let token { self?.updatePlatformPresence(token: token) }
            }
        } else {
            let defaults = UserDefaults.standard
            let uid = defaults.string(forKey: Constants.restUserUID) ?? ""
            if !uid.isEmpty {
                self.isSignedIn = true
                self.userEmail = defaults.string(forKey: Constants.restUserEmail)
                self.userName = defaults.string(forKey: Constants.restUserName)
                debugLog("[Auth] configure() — REST user: \(self.userEmail ?? "none")")
                // Update lastSeen on every app launch for existing REST sessions
                Task { [weak self] in
                    if let token = try? await self?.getIDToken() {
                        self?.updatePlatformPresence(token: token)
                    }
                }
            } else {
                self.isSignedIn = false
                debugLog("[Auth] configure() — no user session found")
            }
        }

        // Listen for future changes
        setupAuthStateListener()
    }

    // MARK: - Auth State

    private func setupAuthStateListener() {
        authStateHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            DispatchQueue.main.async {
                guard let self else { return }
                if let user {
                    self.isSignedIn = true
                    self.userEmail = user.email
                    self.userName = user.displayName
                    debugLog("[Auth] Auth state changed: signed in as \(user.email ?? "unknown")")
                } else {
                    // Only clear state if there is no REST session — otherwise the SDK
                    // firing with user=nil on startup would override a valid REST session.
                    let hasRESTSession = !(UserDefaults.standard.string(forKey: Constants.restUserUID) ?? "").isEmpty
                    if !hasRESTSession {
                        self.clearAuthState()
                        debugLog("[Auth] Auth state changed: signed out")
                    }
                }
            }
        }
    }

    // MARK: - Token

    func getIDToken() async throws -> String {
        // Prefer SDK user if available
        if let user = Auth.auth().currentUser {
            return try await user.getIDToken()
        }

        // Fall back to REST-persisted token
        let defaults = UserDefaults.standard
        guard let storedToken = defaults.string(forKey: Constants.restFirebaseIDToken),
              defaults.string(forKey: Constants.restUserUID) != nil else {
            throw VoxTypeError.notAuthenticated
        }

        let expiry = defaults.double(forKey: Constants.restFirebaseTokenExpiry)
        if expiry == 0 || Date().timeIntervalSince1970 > expiry - 300 {
            return try await refreshFirebaseToken()
        }

        return storedToken
    }

    var currentUID: String? {
        Auth.auth().currentUser?.uid ?? UserDefaults.standard.string(forKey: Constants.restUserUID)
    }

    // MARK: - Sign In with Google (OAuth + PKCE)

    func signInWithGoogle() {
        isLoading = true

        guard let clientID = FirebaseApp.app()?.options.clientID else {
            debugLog("[Auth] Missing Firebase client ID")
            isLoading = false
            return
        }

        // PKCE: Generate code verifier and challenge
        let codeVerifier = generateCodeVerifier()
        let codeChallenge = generateCodeChallenge(from: codeVerifier)

        // Use REVERSED_CLIENT_ID as the callback scheme (Google's standard for iOS/macOS)
        let reversedClientID = clientID.components(separatedBy: ".").reversed().joined(separator: ".")
        let redirectURI = "\(reversedClientID):/oauthredirect"
        let scope = "openid email profile"

        // Build OAuth URL with PKCE
        guard var components = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth") else {
            debugLog("[Auth] Failed to create OAuth URL components")
            isLoading = false
            return
        }

        components.queryItems = [
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: scope),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
        ]

        guard let url = components.url else {
            debugLog("[Auth] Invalid Google OAuth URL")
            isLoading = false
            return
        }

        pendingCodeVerifier = codeVerifier
        pendingRedirectURI = redirectURI
        debugLog("[Auth] Starting Google sign-in flow")

        let session = ASWebAuthenticationSession(
            url: url,
            callbackURLScheme: reversedClientID
        ) { [weak self] callbackURL, error in
            if let error {
                DispatchQueue.main.async { self?.isLoading = false }
                debugLog("[Auth] Google sign-in cancelled/error: \(error.localizedDescription)")
                return
            }

            guard let callbackURL,
                  let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false),
                  let code = components.queryItems?.first(where: { $0.name == "code" })?.value else {
                DispatchQueue.main.async { self?.isLoading = false }
                debugLog("[Auth] No auth code received from Google")
                return
            }

            self?.pendingCodeVerifier = nil
            self?.pendingRedirectURI = nil
            debugLog("[Auth] Received Google auth code, exchanging for tokens...")
            self?.exchangeGoogleCode(
                code: code,
                codeVerifier: codeVerifier,
                clientID: clientID,
                redirectURI: redirectURI
            )
        }

        session.prefersEphemeralWebBrowserSession = false
        session.presentationContextProvider = self
        session.start()
    }

    // MARK: - URL Handler Fallback

    func handleCallbackURL(_ url: URL) {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let code = components.queryItems?.first(where: { $0.name == "code" })?.value,
              let clientID = FirebaseApp.app()?.options.clientID,
              let codeVerifier = pendingCodeVerifier,
              let redirectURI = pendingRedirectURI else { return }
        pendingCodeVerifier = nil
        pendingRedirectURI = nil
        exchangeGoogleCode(code: code, codeVerifier: codeVerifier, clientID: clientID, redirectURI: redirectURI)
    }

    // MARK: - Google Code Exchange

    private func exchangeGoogleCode(code: String, codeVerifier: String, clientID: String, redirectURI: String) {
        guard let tokenURL = URL(string: "https://oauth2.googleapis.com/token") else {
            debugLog("[Auth] Failed to create token exchange URL")
            DispatchQueue.main.async { self.isLoading = false }
            return
        }
        var request = URLRequest(url: tokenURL)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        let bodyParams = [
            "code": code,
            "client_id": clientID,
            "redirect_uri": redirectURI,
            "grant_type": "authorization_code",
            "code_verifier": codeVerifier,
        ]

        request.httpBody = bodyParams
            .map { key, value in
                "\(key)=\(value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value)"
            }
            .joined(separator: "&")
            .data(using: .utf8)

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            if let error {
                DispatchQueue.main.async { self?.isLoading = false }
                debugLog("[Auth] Token exchange error: \(error.localizedDescription)")
                return
            }

            guard let data else {
                DispatchQueue.main.async { self?.isLoading = false }
                debugLog("[Auth] No data from token exchange")
                return
            }

            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let idToken = json["id_token"] as? String,
                  let accessToken = json["access_token"] as? String else {
                DispatchQueue.main.async { self?.isLoading = false }
                debugLog("[Auth] Failed to parse token response")
                return
            }

            debugLog("[Auth] Got Google tokens, signing into Firebase...")

            // Try SDK sign-in first; fall back to REST API on keychain error
            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken)
            Auth.auth().signIn(with: credential) { [weak self] result, error in
                if let error {
                    debugLog("[Auth] SDK sign-in failed (keychain?): \(error.localizedDescription)")
                    // Fall back to REST API — bypasses Keychain entirely
                    self?.signInViaFirebaseREST(googleIDToken: idToken)
                    return
                }

                DispatchQueue.main.async {
                    self?.isLoading = false
                }
                debugLog("[Auth] SDK sign-in succeeded: \(result?.user.email ?? "no email")")
                if let user = result?.user {
                    user.getIDToken { token, _ in
                        if let token {
                            self?.updatePlatformPresence(token: token)
                        }
                    }
                }
            }
        }.resume()
    }

    // MARK: - Firebase REST API Fallback

    private var firebaseAPIKey: String? { FirebaseApp.app()?.options.apiKey }

    /// Signs in via Firebase REST API, bypassing the SDK's Keychain usage.
    /// Used when SDK sign-in fails due to missing provisioning profile (ad-hoc builds).
    private func signInViaFirebaseREST(googleIDToken: String) {
        guard let apiKey = firebaseAPIKey,
              let url = URL(string: "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=\(apiKey)") else {
            debugLog("[Auth] No Firebase API key available for REST fallback")
            DispatchQueue.main.async { self.isLoading = false }
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "postBody": "id_token=\(googleIDToken)&providerId=google.com",
            "requestUri": "http://localhost",
            "returnSecureToken": true,
            "returnIdpCredential": true,
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { [weak self] data, _, error in
            DispatchQueue.main.async { self?.isLoading = false }

            if let error {
                debugLog("[Auth] REST sign-in error: \(error.localizedDescription)")
                return
            }
            let json = try? JSONSerialization.jsonObject(with: data ?? Data()) as? [String: Any]
            guard let data,
                  let firebaseIDToken = json?["idToken"] as? String,
                  let refreshToken = json?["refreshToken"] as? String else {
                let errorMsg = (json?["error"] as? [String: Any])?["message"] as? String ?? "unexpected response"
                debugLog("[Auth] REST sign-in failed: \(errorMsg)")
                return
            }

            let email = json?["email"] as? String
            let displayName = json?["displayName"] as? String
            let uid = json?["localId"] as? String ?? ""

            let defaults = UserDefaults.standard
            defaults.set(firebaseIDToken, forKey: Constants.restFirebaseIDToken)
            defaults.set(refreshToken, forKey: Constants.restFirebaseRefreshToken)
            defaults.set(Date().timeIntervalSince1970 + Constants.firebaseTokenLifetime, forKey: Constants.restFirebaseTokenExpiry)
            defaults.set(email, forKey: Constants.restUserEmail)
            defaults.set(displayName, forKey: Constants.restUserName)
            defaults.set(uid, forKey: Constants.restUserUID)

            debugLog("[Auth] REST sign-in succeeded: \(email ?? "no email"), uid=\(uid)")
            self?.updatePlatformPresence(token: firebaseIDToken)

            DispatchQueue.main.async {
                self?.isSignedIn = true
                self?.userEmail = email
                self?.userName = displayName
            }
        }.resume()
    }

    /// Refreshes the Firebase ID token using the stored refresh token.
    private func refreshFirebaseToken() async throws -> String {
        guard let apiKey = firebaseAPIKey,
              let refreshToken = UserDefaults.standard.string(forKey: Constants.restFirebaseRefreshToken) else {
            throw VoxTypeError.notAuthenticated
        }

        guard let url = URL(string: "https://securetoken.googleapis.com/v1/token?key=\(apiKey)") else {
            throw VoxTypeError.notAuthenticated
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = "grant_type=refresh_token&refresh_token=\(refreshToken)".data(using: .utf8)

        let (data, response) = try await URLSession.shared.data(for: request)

        if let http = response as? HTTPURLResponse, http.statusCode != 200 {
            let errorMsg = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])
                .flatMap { ($0["error"] as? [String: Any])?["message"] as? String }
                ?? "HTTP \(http.statusCode)"
            debugLog("[Auth] Token refresh failed: \(errorMsg)")
            // Refresh token is expired or revoked — clear the session so user is prompted to sign in
            clearRESTSession()
            await MainActor.run { self.isSignedIn = false }
            throw VoxTypeError.notAuthenticated
        }

        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let newIDToken = json["id_token"] as? String else {
            debugLog("[Auth] Token refresh failed: unexpected response format")
            throw VoxTypeError.notAuthenticated
        }

        let defaults = UserDefaults.standard
        defaults.set(newIDToken, forKey: Constants.restFirebaseIDToken)
        defaults.set(Date().timeIntervalSince1970 + Constants.firebaseTokenLifetime, forKey: Constants.restFirebaseTokenExpiry)

        if let newRefresh = json["refresh_token"] as? String {
            defaults.set(newRefresh, forKey: Constants.restFirebaseRefreshToken)
        }

        debugLog("[Auth] Token refreshed successfully")
        return newIDToken
    }

    // MARK: - PKCE Helpers

    private func generateCodeVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private func generateCodeChallenge(from verifier: String) -> String {
        let data = Data(verifier.utf8)
        let hash = SHA256.hash(data: data)
        return Data(hash).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    // MARK: - Platform Presence

    /// Records that this user is active on macOS through the backend.
    private func updatePlatformPresence(token: String) {
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        let osVersion = ProcessInfo.processInfo.operatingSystemVersionString
        let urlString = Constants.baseURL(for: "us-central1") + Constants.platformPresencePath
        guard let url = URL(string: urlString) else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        let body: [String: Any] = [
            "platform": "mac",
            "appVersion": appVersion,
            "osVersion": osVersion
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { _, response, error in
            if let error {
                debugLog("[Auth] Platform presence update failed: \(error.localizedDescription)")
            } else if let httpResponse = response as? HTTPURLResponse,
                      !(200...299).contains(httpResponse.statusCode) {
                debugLog("[Auth] Platform presence update failed: HTTP \(httpResponse.statusCode)")
            } else {
                debugLog("[Auth] Platform presence updated (mac \(appVersion))")
            }
        }.resume()
    }

    // MARK: - Sign Out

    func signOut() {
        do {
            try Auth.auth().signOut()
        } catch {
            debugLog("[Auth] Sign out error: \(error.localizedDescription)")
        }
        clearRESTSession()
        DispatchQueue.main.async {
            self.clearAuthState()
            debugLog("[Auth] Signed out")
        }
    }

    private func clearAuthState() {
        isSignedIn = false
        userEmail = nil
        userName = nil
    }

    private func clearRESTSession() {
        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: Constants.restFirebaseIDToken)
        defaults.removeObject(forKey: Constants.restFirebaseRefreshToken)
        defaults.removeObject(forKey: Constants.restFirebaseTokenExpiry)
        defaults.removeObject(forKey: Constants.restUserEmail)
        defaults.removeObject(forKey: Constants.restUserName)
        defaults.removeObject(forKey: Constants.restUserUID)
    }

    deinit {
        if let handle = authStateHandle {
            Auth.auth().removeStateDidChangeListener(handle)
        }
    }
}

// MARK: - ASWebAuthenticationPresentationContextProviding

extension AuthManager: ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        // Return any available window, or create one for the auth sheet
        if let window = NSApp.windows.first(where: { $0.isVisible }) {
            return window
        }
        // Fallback: create a temporary invisible window as anchor
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 1, height: 1),
            styleMask: [],
            backing: .buffered,
            defer: false
        )
        window.center()
        window.makeKeyAndOrderFront(nil)
        return window
    }
}
