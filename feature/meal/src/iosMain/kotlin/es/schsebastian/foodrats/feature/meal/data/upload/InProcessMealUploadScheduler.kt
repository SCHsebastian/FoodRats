package es.schsebastian.foodrats.feature.meal.data.upload

/**
 * iOS scheduler: a no-op. The actual in-process coroutine is started by
 * `BackgroundMealUploadCoordinator.enqueueDraftUpload()` already; persistence
 * of the pending flag + the coordinator's `init { … }` auto-resume covers the
 * "app got killed mid-upload" case on the next launch.
 *
 * A future improvement would be to register a `BGProcessingTask` via
 * `BGTaskScheduler` so the OS can grant brief background execution windows.
 * That needs `Info.plist` (`BGTaskSchedulerPermittedIdentifiers`) +
 * background-mode entitlements wired in Xcode, which is out of scope here.
 */
class InProcessMealUploadScheduler : MealUploadScheduler {
    override fun schedule() = Unit
    override fun cancel() = Unit
}
