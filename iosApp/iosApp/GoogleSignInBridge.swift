import Foundation
import UIKit
import GoogleSignIn

@objc public class GoogleSignInBridge: NSObject {
    @objc public static func signIn(presenter: UIViewController,
                                    completion: @escaping (_ idToken: String?, _ errorCode: String?) -> Void) {
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let nsError = error as NSError?, nsError.code == GIDSignInError.canceled.rawValue {
                completion(nil, "cancelled"); return
            }
            if error != nil {
                completion(nil, "unknown"); return
            }
            guard let token = result?.user.idToken?.tokenString else {
                completion(nil, "unknown"); return
            }
            completion(token, nil)
        }
    }

    @objc public static func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }
}
