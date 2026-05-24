package es.schsebastian.foodrats.feature.mealai.data

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.DishLabel
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter

/**
 * iOS stub. The real implementation (plan T16) wraps the `MediaPipeTasksVision`
 * cocoapod via cinterop — that needs the `kotlin.native.cocoapods` plugin plus
 * `pod install`, which can't run in this environment. Until it's wired on a Mac,
 * iOS reports the model as unavailable; `ClassifyPlateUseCase` surfaces that as
 * `ClassifierBannerLoadFailed` and the compose flow degrades to manual entry.
 *
 * TODO(T16, Mac/Xcode): replace with the cocoapod-backed `MPPImageClassifier`
 * implementation; add the cocoapods config to `build.gradle.kts`.
 */
internal actual class MediaPipeMealClassifier actual constructor(
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
) : MealClassifierPort {
    override suspend fun classify(jpeg: ByteArray): Result<List<DishLabel>, ClassifierError> =
        Result.Err(ClassifierError.Load.ModelMissing)
}
