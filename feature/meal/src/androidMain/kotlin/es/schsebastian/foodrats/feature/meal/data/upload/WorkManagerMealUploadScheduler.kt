package es.schsebastian.foodrats.feature.meal.data.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

/**
 * Enqueues a unique [MealUploadWorker] under [WORK_NAME] with:
 *   - `NetworkType.CONNECTED` constraint so the work waits for network instead
 *     of failing immediately on airplane mode.
 *   - Exponential backoff retry from the WorkManager minimum (10s) — covers
 *     transient Firestore/Storage failures without hammering the backend.
 *   - `KEEP` policy so multiple enqueues during the same pending session
 *     don't pile up workers; the existing one finishes first.
 */
class WorkManagerMealUploadScheduler(private val context: Context) : MealUploadScheduler {

    override fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<MealUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "meal-upload"
    }
}
