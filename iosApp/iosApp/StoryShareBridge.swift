import Foundation
import UIKit
import FoodRatsShared

extension KotlinByteArray {
    /// Copies a Kotlin/Native `ByteArray` into Swift `Data` (the framework exports `get(index:)` +
    /// `size`; there is no zero-copy bridge, but the PNGs here are small — ~hundreds of KB).
    func toData() -> Data {
        var bytes = [UInt8]()
        bytes.reserveCapacity(Int(size))
        for i in 0..<size {
            bytes.append(UInt8(bitPattern: self.get(index: i)))
        }
        return Data(bytes)
    }
}

/// Swift bridge for the shareable story-card PNG, mirroring `ShareBridge.swift`.
///
/// Hands a rendered card PNG to Instagram Stories (UIPasteboard background-image item +
/// `instagram-stories://share`), with a `UIActivityViewController` fallback when Instagram is
/// absent. Presentation must run from a live view controller on the main thread, which the shared
/// module (`StoryShareLauncherIos`) can't reach cleanly from Kotlin/Native — so it is built here and
/// wired into the iOS Koin graph via a lambda passed through ContentView.swift -> MainViewController.
///
/// The lambda contract returns a status code synchronously (the Kotlin `StoryShareLauncher` maps it
/// to a `StoryShareOutcome`): 0 = Instagram opened, 1 = fallback sheet, 2 = failed. The actual
/// open/present is dispatched async to the main thread; the code reflects which path was chosen.
///
/// NOTE: `instagram-stories` must be listed under `LSApplicationQueriesSchemes` in Info.plist, or
/// `canOpenURL` returns false and every share falls back to the system sheet (spec §6.2 / §15).
enum StoryShareBridge {

    /// Source application id, used by Instagram's Stories ingest to attribute the share.
    private static let sourceApplication = "es.schsebastian.foodrats"

    /// Presents the share for the given PNG bytes. Returns 0 (Instagram), 1 (sheet), or 2 (failed).
    static func shareToStories(_ png: Data) -> Int32 {
        let storiesURL = URL(string: "instagram-stories://share?source_application=\(sourceApplication)")

        if let url = storiesURL, UIApplication.shared.canOpenURL(url) {
            // Hand the background image to Instagram via the pasteboard with a short expiration.
            let item: [String: Any] = ["com.instagram.sharedSticker.backgroundImage": png]
            let options: [UIPasteboard.OptionsKey: Any] = [
                .expirationDate: Date().addingTimeInterval(60 * 5)
            ]
            UIPasteboard.general.setItems([item], options: options)
            DispatchQueue.main.async {
                UIApplication.shared.open(url, options: [:], completionHandler: nil)
            }
            return 0
        }

        // Fallback: the system share sheet with the PNG attached.
        guard topViewController() != nil else {
            NSLog("[StoryShareBridge] no view controller available to present the share sheet")
            return 2
        }
        DispatchQueue.main.async {
            // Re-fetch the presenter on the main thread so it is guaranteed live at present() time;
            // the controller captured above could have been dismissed/deallocated before this runs.
            guard let presenter = topViewController() else {
                NSLog("[StoryShareBridge] presenter unavailable on the main thread; share sheet skipped")
                return
            }
            guard let image = UIImage(data: png) else {
                NSLog("[StoryShareBridge] could not decode PNG for the fallback share sheet")
                return
            }
            let activityVC = UIActivityViewController(activityItems: [image], applicationActivities: nil)
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
        return 1
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
