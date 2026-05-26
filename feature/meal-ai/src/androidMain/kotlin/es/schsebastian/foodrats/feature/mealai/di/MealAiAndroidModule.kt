package es.schsebastian.foodrats.feature.mealai.di

import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.feature.mealai.data.MediaPipeMealClassifier
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android-side Koin module binding [MealClassifierPort] to the MediaPipe-backed
 * [MediaPipeMealClassifier]. Bound per platform (no common binding) — each platform
 * constructs the classifier differently, mirroring `mealAiIosModule` and the
 * per-platform `CrashReporter`.
 *
 * The MediaPipe `ImageClassifier` is built lazily on the first `classify()` call (on the
 * IO dispatcher, reading the model through Compose Resources) — not at injection time, so
 * resolving the port never blocks or crashes the composition that triggers it. No
 * `Application.onCreate` hook is required (and `MediaPipeMealClassifier` is `internal`, so
 * the app module couldn't reach it anyway).
 */
val mealAiAndroidModule = module {
    single<MealClassifierPort> {
        MediaPipeMealClassifier(
            context = androidContext(),
            dispatchers = get(),
            crashReporter = get(),
        )
    }
}
