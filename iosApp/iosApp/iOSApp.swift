import SwiftUI
import FoodRatsShared

@main
struct iOSApp: App {
    init() {
        // Firebase + GoogleSignIn require SPM packages added per SETUP.md.
        // Once SPM is configured, uncomment the imports and these calls:
        //   FirebaseApp.configure()
        // and the .onOpenURL handler below.
    }
    var body: some Scene {
        WindowGroup {
            ComposeViewController()
                .ignoresSafeArea(.keyboard)
                // .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }
        }
    }
}

struct ComposeViewController: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
