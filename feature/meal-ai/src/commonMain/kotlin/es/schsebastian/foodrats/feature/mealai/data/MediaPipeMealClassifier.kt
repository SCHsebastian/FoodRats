package es.schsebastian.foodrats.feature.mealai.data

import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort

/**
 * On-device food classifier backed by MediaPipe Tasks Vision. The model asset
 * (`files/food101.tflite`) is loaded inside the data layer only.
 *
 * The seam declares no constructor on purpose (mirrors `GoogleAuthClient`): each
 * platform actual takes the dependencies it actually needs.
 *
 * - **Android:** `ImageClassifier` from `com.google.mediapipe:tasks-vision`, plus a
 *   `(DispatcherProvider, CrashReporter)` constructor and an `init(Context)` hook.
 * - **iOS:** delegates to a Swift `MediaPipeClassifierBridge` lambda (MediaPipe iOS
 *   is distributed as a CocoaPod / XCFramework, integrated in the Xcode project, so
 *   inference runs in Swift and is bridged into Kotlin — the same pattern used for
 *   GoogleSignIn and Crashlytics).
 */
internal expect class MediaPipeMealClassifier : MealClassifierPort
