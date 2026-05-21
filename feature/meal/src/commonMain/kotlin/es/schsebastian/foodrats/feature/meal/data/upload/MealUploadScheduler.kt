package es.schsebastian.foodrats.feature.meal.data.upload

/**
 * Platform port for scheduling a meal-upload that survives process death.
 *
 * - Android: implemented by `WorkManagerMealUploadScheduler` which enqueues a
 *   unique [MealUploadWorker]. WorkManager persists the request and retries
 *   on backoff even if the process is killed.
 * - iOS: implemented by `InProcessMealUploadScheduler` (a no-op). Real iOS
 *   background execution needs `BGTaskScheduler` + `Info.plist` /
 *   entitlements, which is out of scope here. The coordinator's
 *   auto-resume-on-launch path covers the "app got killed" case.
 *
 * Both [schedule] and [cancel] are idempotent. The coordinator always sets
 * the persistence flag before calling [schedule] so a missed schedule
 * (e.g. process killed between flag set and `schedule()` returning) still
 * triggers a resume on next launch.
 */
interface MealUploadScheduler {
    fun schedule()
    fun cancel()
}
