import Foundation
import UIKit
import GoogleSignIn

@objc public class GoogleSignInBridge: NSObject {
    /// completion(idToken, accessToken, errorCode) — exactly one of (idToken+accessToken) or errorCode is non-nil.
    @objc public static func signIn(presenter: UIViewController,
                                    completion: @escaping (_ idToken: String?, _ accessToken: String?, _ errorCode: String?) -> Void) {
        NSLog("[GoogleSignInBridge] signIn called, presenter=\(type(of: presenter))")
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let nsError = error as NSError? {
                NSLog("[GoogleSignInBridge] FAILED: domain=\(nsError.domain) code=\(nsError.code) localizedDescription=\(nsError.localizedDescription) userInfo=\(nsError.userInfo)")
                if nsError.code == GIDSignInError.canceled.rawValue {
                    completion(nil, nil, "cancelled"); return
                }
                if let underlying = nsError.userInfo[NSUnderlyingErrorKey] as? NSError {
                    NSLog("[GoogleSignInBridge] underlying: domain=\(underlying.domain) code=\(underlying.code)")
                    if underlying.domain == NSURLErrorDomain {
                        completion(nil, nil, "network"); return
                    }
                }
                if nsError.domain == NSURLErrorDomain {
                    completion(nil, nil, "network"); return
                }
                completion(nil, nil, "unknown"); return
            }
            guard let idToken = result?.user.idToken?.tokenString else {
                NSLog("[GoogleSignInBridge] FAILED: no id token in result")
                completion(nil, nil, "unknown"); return
            }
            let accessToken = result?.user.accessToken.tokenString
            NSLog("[GoogleSignInBridge] success, idToken length=\(idToken.count) accessToken length=\(accessToken?.count ?? 0)")
            completion(idToken, accessToken, nil)
        }
    }

    @objc public static func signOut() {
        NSLog("[GoogleSignInBridge] signOut called")
        GIDSignIn.sharedInstance.signOut()
    }
}
