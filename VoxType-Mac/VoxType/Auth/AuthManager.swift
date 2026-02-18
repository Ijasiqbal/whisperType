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

    private var authStateHandle: AuthStateDidChangeListenerHandle?
    private var isConfigured = false

    private override init() {
        super.init()
        // Don't touch Firebase here — it may not be configured yet.
        // Call configure() after FirebaseApp.configure().
        print("[Auth] AuthManager created (Firebase not yet wired)")
    }

    /// Must be called once AFTER FirebaseApp.configure().
    func configure() {
        guard !isConfigured else { return }
        isConfigured = true

        // Read persisted session immediately
        let currentUser = Auth.auth().currentUser
        self.isSignedIn = currentUser != nil
        self.userEmail = currentUser?.email
        self.userName = currentUser?.displayName
        print("[Auth] configure() — current user: \(currentUser?.email ?? "none"), isSignedIn=\(currentUser != nil)")

        // Listen for future changes
        setupAuthStateListener()
    }

    // MARK: - Auth State

    private func setupAuthStateListener() {
        authStateHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            DispatchQueue.main.async {
                let wasSignedIn = self?.isSignedIn ?? false
                self?.isSignedIn = user != nil
                self?.userEmail = user?.email
                self?.userName = user?.displayName

                if wasSignedIn != (user != nil) {
                    print("[Auth] Auth state changed: \(user != nil ? "signed in as \(user?.email ?? "unknown")" : "signed out")")
                }
            }
        }
    }

    // MARK: - Token

    func getIDToken() async throws -> String {
        guard let user = Auth.auth().currentUser else {
            throw VoxTypeError.notAuthenticated
        }
        return try await user.getIDToken()
    }

    var currentUID: String? {
        Auth.auth().currentUser?.uid
    }

    // MARK: - Sign In with Apple

    func signInWithApple() {
        isLoading = true
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.performRequests()
    }

    // MARK: - Sign In with Google (OAuth + PKCE)

    func signInWithGoogle() {
        isLoading = true

        guard let clientID = FirebaseApp.app()?.options.clientID else {
            print("[Auth] Missing Firebase client ID")
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
            print("[Auth] Failed to create OAuth URL components")
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
            print("[Auth] Invalid Google OAuth URL")
            isLoading = false
            return
        }

        print("[Auth] Starting Google sign-in with URL: \(url.absoluteString)")

        let session = ASWebAuthenticationSession(
            url: url,
            callbackURLScheme: reversedClientID
        ) { [weak self] callbackURL, error in
            if let error {
                DispatchQueue.main.async { self?.isLoading = false }
                print("[Auth] Google sign-in cancelled/error: \(error.localizedDescription)")
                return
            }

            guard let callbackURL,
                  let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false),
                  let code = components.queryItems?.first(where: { $0.name == "code" })?.value else {
                DispatchQueue.main.async { self?.isLoading = false }
                print("[Auth] No auth code received from Google")
                return
            }

            print("[Auth] Received Google auth code, exchanging for tokens...")
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

    // MARK: - Google Code Exchange

    private func exchangeGoogleCode(code: String, codeVerifier: String, clientID: String, redirectURI: String) {
        guard let tokenURL = URL(string: "https://oauth2.googleapis.com/token") else {
            print("[Auth] Failed to create token exchange URL")
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
                print("[Auth] Token exchange error: \(error.localizedDescription)")
                return
            }

            guard let data else {
                DispatchQueue.main.async { self?.isLoading = false }
                print("[Auth] No data from token exchange")
                return
            }

            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let idToken = json["id_token"] as? String,
                  let accessToken = json["access_token"] as? String else {
                DispatchQueue.main.async { self?.isLoading = false }
                print("[Auth] Failed to parse token response")
                return
            }

            print("[Auth] Got Google tokens, signing into Firebase...")

            // Create Firebase Google credential and sign in
            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken)
            Auth.auth().signIn(with: credential) { [weak self] result, error in
                DispatchQueue.main.async {
                    self?.isLoading = false
                }

                if let error {
                    print("[Auth] Firebase Google sign-in error: \(error.localizedDescription)")
                    return
                }

                print("[Auth] ✅ Signed in with Google as: \(result?.user.uid ?? "unknown") (\(result?.user.email ?? "no email"))")
            }
        }.resume()
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

    // MARK: - Sign Out

    func signOut() {
        do {
            try Auth.auth().signOut()
        } catch {
            print("[Auth] Sign out error: \(error.localizedDescription)")
        }
    }

    deinit {
        if let handle = authStateHandle {
            Auth.auth().removeStateDidChangeListener(handle)
        }
    }
}

// MARK: - ASAuthorizationControllerDelegate

extension AuthManager: ASAuthorizationControllerDelegate {

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let identityToken = appleIDCredential.identityToken,
              let tokenString = String(data: identityToken, encoding: .utf8) else {
            print("[Auth] Failed to get Apple ID token")
            isLoading = false
            return
        }

        let credential = OAuthProvider.appleCredential(
            withIDToken: tokenString,
            rawNonce: nil,
            fullName: appleIDCredential.fullName
        )

        Auth.auth().signIn(with: credential) { [weak self] result, error in
            DispatchQueue.main.async {
                self?.isLoading = false
            }

            if let error {
                print("[Auth] Firebase sign-in error: \(error.localizedDescription)")
                return
            }

            print("[Auth] Signed in as: \(result?.user.uid ?? "unknown")")
        }
    }

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithError error: Error) {
        print("[Auth] Apple sign-in error: \(error.localizedDescription)")
        DispatchQueue.main.async {
            self.isLoading = false
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
