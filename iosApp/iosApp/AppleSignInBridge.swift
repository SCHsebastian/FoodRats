import Foundation
import UIKit
import AuthenticationServices
import CryptoKit

/// Native Sign-in-with-Apple, the mirror of `GoogleSignInBridge`. Runs an
/// `ASAuthorizationController` request with a SHA-256-hashed nonce and returns the identity-token
/// JWT plus the RAW nonce to Kotlin (`AppleAuthClient.ios`), which exchanges them with Firebase via
/// `OAuthProvider("apple.com")`. Firebase re-hashes the raw nonce and matches it against the nonce
/// Apple signed into the token — replay protection.
@objc public class AppleSignInBridge: NSObject {

    /// Retains the in-flight coordinator for the duration of the async ASAuthorization flow —
    /// without a strong reference the delegate is deallocated before Apple calls back.
    private static var activeCoordinator: Coordinator?

    /// completion(identityToken, rawNonce, authorizationCode, email, fullName, errorCode).
    /// Exactly one of (identityToken+rawNonce) or errorCode is non-nil. errorCode ∈
    /// {"cancelled", "network", "invalid", "unknown"} (mapped in AppleAuthClient.ios.kt).
    @objc public static func signIn(
        presenter: UIViewController,
        completion: @escaping (
            _ identityToken: String?,
            _ rawNonce: String?,
            _ authorizationCode: String?,
            _ email: String?,
            _ fullName: String?,
            _ errorCode: String?
        ) -> Void
    ) {
        NSLog("[AppleSignInBridge] signIn called, presenter=\(type(of: presenter))")
        let rawNonce = randomNonceString()
        let coordinator = Coordinator(presenter: presenter, rawNonce: rawNonce) { idToken, raw, code, email, name, err in
            // Release the retained coordinator once the flow terminates (success or error).
            activeCoordinator = nil
            completion(idToken, raw, code, email, name, err)
        }
        activeCoordinator = coordinator

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(rawNonce)

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = coordinator
        controller.presentationContextProvider = coordinator
        controller.performRequests()
    }

    @objc public static func signOut() {
        // Firebase owns the session; Apple keeps no client-side token to clear. No-op.
        NSLog("[AppleSignInBridge] signOut called (no-op)")
    }

    // MARK: - Coordinator (delegate + presentation anchor)

    private final class Coordinator: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
        private let presenter: UIViewController
        private let rawNonce: String
        private let completion: (String?, String?, String?, String?, String?, String?) -> Void

        init(
            presenter: UIViewController,
            rawNonce: String,
            completion: @escaping (String?, String?, String?, String?, String?, String?) -> Void
        ) {
            self.presenter = presenter
            self.rawNonce = rawNonce
            self.completion = completion
        }

        func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
            presenter.view.window ?? ASPresentationAnchor()
        }

        func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let tokenData = credential.identityToken,
                  let idToken = String(data: tokenData, encoding: .utf8) else {
                NSLog("[AppleSignInBridge] FAILED: missing/undecodable identity token")
                completion(nil, nil, nil, nil, nil, "invalid")
                return
            }
            let authCode = credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
            // Apple returns email/fullName ONLY on the first authorization for this app — capture
            // them when present; they are nil on every subsequent sign-in.
            let email = credential.email
            let fullName: String? = {
                guard let nameComponents = credential.fullName else { return nil }
                let formatted = PersonNameComponentsFormatter().string(from: nameComponents)
                return formatted.isEmpty ? nil : formatted
            }()
            NSLog("[AppleSignInBridge] success, idToken length=\(idToken.count)")
            completion(idToken, rawNonce, authCode, email, fullName, nil)
        }

        func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
            let code: String
            if let authError = error as? ASAuthorizationError {
                switch authError.code {
                case .canceled: code = "cancelled"
                case .failed, .invalidResponse, .notHandled: code = "invalid"
                default: code = "unknown"
                }
            } else if (error as NSError).domain == NSURLErrorDomain {
                code = "network"
            } else {
                code = "unknown"
            }
            NSLog("[AppleSignInBridge] FAILED: \((error as NSError).localizedDescription) -> \(code)")
            completion(nil, nil, nil, nil, nil, code)
        }
    }

    // MARK: - Nonce helpers

    /// Cryptographically-random nonce (Apple/Firebase canonical helper). The RAW value is returned
    /// to Firebase; its SHA-256 is what we put in the ASAuthorization request.
    private static func randomNonceString(length: Int = 32) -> String {
        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            var randoms = [UInt8](repeating: 0, count: 16)
            let status = SecRandomCopyBytes(kSecRandomDefault, randoms.count, &randoms)
            if status != errSecSuccess {
                fatalError("[AppleSignInBridge] SecRandomCopyBytes failed with status \(status)")
            }
            randoms.forEach { random in
                if remaining == 0 { return }
                if Int(random) < charset.count {
                    result.append(charset[Int(random)])
                    remaining -= 1
                }
            }
        }
        return result
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
