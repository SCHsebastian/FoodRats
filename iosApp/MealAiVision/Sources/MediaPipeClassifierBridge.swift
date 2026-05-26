import Foundation
import UIKit
// Implementation-only: MediaPipe is an internal detail of this framework. Hiding it from the
// module interface means clients (the app) don't need MediaPipeTasksVision's module on their
// search paths — and, combined with this being a dynamic framework, keeps MediaPipe's vendored
// symbols out of the app binary entirely.
@_implementationOnly import MediaPipeTasksVision

/// Swift bridge over MediaPipe Tasks Vision `ImageClassifier`, mirroring GoogleSignInBridge /
/// CrashlyticsBridge.
///
/// This lives in the **MealAiVision dynamic framework**, NOT the app target, on purpose:
/// MediaPipe Tasks Vision is distributed as a *static* vendored xcframework that bundles its own
/// copies of GTMSessionFetcher, gRPC and RE2. Firebase (via SPM) bundles those same libraries, so
/// linking MediaPipe directly into the app binary collides with Firebase ("duplicate symbols").
/// By linking MediaPipe statically *inside this dynamic framework* and having the app link only the
/// framework dynamically, MediaPipe's vendored symbols stay encapsulated in this dylib and never
/// reach the app-binary link stage.
///
/// The shared module's `MediaPipeMealClassifier` (iosMain) calls `classify(...)` through a lambda
/// wired in ContentView.swift -> MainViewController -> mealAiIosModule.
///
/// The bundled model `food101.tflite` is part of the **app** target's "Copy Bundle Resources", so
/// it resolves via `Bundle.main` (the app bundle) even though this code runs inside the framework.
public enum MediaPipeClassifierBridge {

    /// Lazily-created classifier. `nil` means the model failed to load (missing/corrupt asset);
    /// callers surface that as the "load" error code.
    private static let classifier: ImageClassifier? = {
        guard let modelPath = Bundle.main.path(forResource: "food101", ofType: "tflite") else {
            NSLog("[MediaPipeClassifierBridge] food101.tflite not found in bundle")
            return nil
        }
        do {
            let options = ImageClassifierOptions()
            options.baseOptions.modelAssetPath = modelPath
            options.maxResults = 5
            return try ImageClassifier(options: options)
        } catch {
            NSLog("[MediaPipeClassifierBridge] failed to create ImageClassifier: \(error)")
            return nil
        }
    }()

    /// Classifies a JPEG into dish labels (sorted by descending confidence by MediaPipe).
    /// - Parameters:
    ///   - jpeg: encoded JPEG bytes from the Kotlin `MealClassifierPort.classify` call.
    ///   - completion: invoked with `(labels, errorCode)` — exactly one side is non-nil.
    ///     Each label is a primitive `"<dishSlug>|<confidence>"` string so the Kotlin
    ///     boundary needs no exported domain type. errorCode is "load" | "decode" | "inference".
    public static func classify(jpeg: Data,
                                completion: @escaping (_ labels: [String]?, _ errorCode: String?) -> Void) {
        guard let classifier = classifier else {
            completion(nil, "load"); return
        }
        guard let uiImage = UIImage(data: jpeg) else {
            completion(nil, "decode"); return
        }
        do {
            let mpImage = try MPImage(uiImage: uiImage)
            let result = try classifier.classify(image: mpImage)
            let categories = result.classificationResult.classifications.first?.categories ?? []
            let labels = categories.map { category in
                "\(category.categoryName ?? "")|\(category.score)"
            }
            completion(labels, nil)
        } catch {
            NSLog("[MediaPipeClassifierBridge] inference failed: \(error)")
            completion(nil, "inference")
        }
    }
}
