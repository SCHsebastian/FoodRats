package es.schsebastian.foodrats.feature.mealai.di

import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.feature.mealai.data.MediaPipeMealClassifier
import org.koin.dsl.module
import platform.Foundation.NSData

/**
 * iOS-side Koin module that binds [MealClassifierPort] to the Swift-backed
 * [MediaPipeMealClassifier]. The Swift caller in iosApp supplies the [classifyNative]
 * lambda at startup — see `iosApp/ContentView.swift` and `MainViewController`.
 *
 * Bound per platform (no common binding) because the classifier is constructed
 * differently on each platform — the same reasoning that makes `CrashReporter` a
 * per-platform binding (`crashIosModule`).
 */
fun mealAiIosModule(
    classifyNative: (
        jpeg: NSData,
        completion: (labels: List<String>?, errorCode: String?) -> Unit,
    ) -> Unit,
) = module {
    single<MealClassifierPort> {
        MediaPipeMealClassifier(
            dispatchers = get(),
            crashReporter = get(),
            classifyNative = classifyNative,
        )
    }
}
