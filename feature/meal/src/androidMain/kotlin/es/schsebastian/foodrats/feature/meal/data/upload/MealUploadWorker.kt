package es.schsebastian.foodrats.feature.meal.data.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.meal.data.queue.DraftRetryRunner
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background-survivable wrapper around [BackgroundMealUploadCoordinator] +
 * the offline-first durable queue (roadmap §5.2).
 *
 * WorkManager keeps the request alive across process death, network drops,
 * and reboots (with the `KEEP` unique-work policy so a re-enqueue can't pile
 * up workers, and a `NetworkType.CONNECTED` constraint so it fires on reconnect).
 * The worker:
 *  1. calls `resumeFromBackgroundWorker()` — a no-op that reports "done" in durable mode (the
 *     queue is the single executor), or runs the in-process upload in the no-queue fallback; and
 *  2. drains the durable [DraftRetryRunner] queue ([DraftRetryRunner.runOnce] with
 *     no scope, so it relies on WorkManager's backoff rather than scheduling its
 *     own in-process delay — the worker process may die between attempts).
 * In durable mode (2) is the only publisher, so the worker can never duplicate a meal.
 *
 * `Result.retry` (→ WorkManager exponential backoff) iff anything is still
 * undrained; `Result.success` once both paths report done.
 *
 * Resolves dependencies at runtime via Koin (`KoinComponent`) because
 * WorkManager instantiates workers via reflection and we can't pass them in.
 */
class MealUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val coordinator: BackgroundMealUploadCoordinator by inject()
    private val retryRunner: DraftRetryRunner by inject()

    override suspend fun doWork(): Result =
        runCatching {
            val singleDone = coordinator.resumeFromBackgroundWorker()
            // No scope → no in-process backoff delay; WorkManager backoff drives retries.
            val queueDrained = retryRunner.runOnce(scope = null)
            singleDone && queueDrained
        }.fold(
            onSuccess = { done ->
                if (done) Result.success() else Result.retry()
            },
            onFailure = { t ->
                FrLog.w("MealUpload", t) { "worker failed: ${t.message}" }
                Result.retry()
            },
        )
}
