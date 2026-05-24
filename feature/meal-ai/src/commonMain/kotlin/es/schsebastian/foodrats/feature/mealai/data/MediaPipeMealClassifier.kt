package es.schsebastian.foodrats.feature.mealai.data

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter

/**
 * On-device food classifier backed by MediaPipe Tasks Vision. The model asset
 * (`files/food101.tflite`) is loaded inside the data layer only.
 *
 * Android: real `ImageClassifier`. iOS: stub pending the MediaPipeTasksVision
 * cocoapod wiring (plan T16 — requires a Mac + `pod install`).
 */
internal expect class MediaPipeMealClassifier(
    dispatchers: DispatcherProvider,
    crashReporter: CrashReporter,
) : MealClassifierPort
