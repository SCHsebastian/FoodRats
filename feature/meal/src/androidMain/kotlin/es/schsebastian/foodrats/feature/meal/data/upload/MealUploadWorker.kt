package es.schsebastian.foodrats.feature.meal.data.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background-survivable wrapper around [BackgroundMealUploadCoordinator].
 *
 * WorkManager keeps the request alive across process death, network drops,
 * and reboots (with the `KEEP` unique-work policy so a re-enqueue can't pile
 * up workers). The actual publish runs through the coordinator's
 * `resumeFromBackgroundWorker()` so the in-process flow and this one share
 * a single mutex — we never run the publish twice in parallel.
 *
 * Resolves the coordinator at runtime via Koin (`KoinComponent`) because
 * WorkManager instantiates workers via reflection and we can't pass it in.
 */
class MealUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val coordinator: BackgroundMealUploadCoordinator by inject()

    override suspend fun doWork(): Result =
        runCatching { coordinator.resumeFromBackgroundWorker() }.fold(
            onSuccess = { succeeded ->
                if (succeeded) Result.success() else Result.retry()
            },
            onFailure = { t ->
                FrLog.w("MealUpload", t) { "worker failed: ${t.message}" }
                Result.retry()
            },
        )
}
