import Foundation
import UIKit

/// Swift bridge over UIKit's `UIActivityViewController`, mirroring GoogleSignInBridge / CrashlyticsBridge.
///
/// `UIActivityViewController` must be presented from a live view controller, which the shared module
/// (ShareControllerIos) can't reach cleanly from Kotlin/Native — so the share sheet is built and
/// presented here, and wired into the iOS Koin graph via a lambda passed through
/// ContentView.swift -> MainViewController.
enum ShareBridge {

    /// Presents the native share sheet for the given text from the top-most view controller.
    static func shareText(_ text: String) {
        // Presentation must happen on the main thread; the call may originate from Kotlin code.
        DispatchQueue.main.async {
            guard let presenter = topViewController() else {
                NSLog("[ShareBridge] no view controller available to present the share sheet")
                return
            }
            let activityVC = UIActivityViewController(activityItems: [text], applicationActivities: nil)
            // iPad: anchor the popover to the presenter's view to avoid a runtime crash.
            if let popover = activityVC.popoverPresentationController {
                popover.sourceView = presenter.view
                popover.sourceRect = CGRect(
                    x: presenter.view.bounds.midX,
                    y: presenter.view.bounds.midY,
                    width: 0,
                    height: 0
                )
                popover.permittedArrowDirections = []
            }
            presenter.present(activityVC, animated: true)
        }
    }

    /// Walks the key window's view-controller chain to find the front-most presented controller.
    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes
        let windowScene = scenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene
            ?? scenes.compactMap { $0 as? UIWindowScene }.first
        let keyWindow = windowScene?.windows.first(where: { $0.isKeyWindow })
            ?? windowScene?.windows.first
        var top = keyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
