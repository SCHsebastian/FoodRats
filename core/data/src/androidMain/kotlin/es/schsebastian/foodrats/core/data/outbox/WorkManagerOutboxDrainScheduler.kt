package es.schsebastian.foodrats.core.data.outbox

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
 * Enqueues a unique [OutboxDrainWorker] under [WORK_NAME] with:
 *   - `NetworkType.CONNECTED` constraint — the worker waits for network, so it can
 *     never drain while offline (avoids pointless wakeups on airplane mode).
 *   - Exponential backoff from the WorkManager minimum (10 s) — covers transient
 *     Firestore failures without hammering the backend.
 *   - `KEEP` policy — multiple [schedule] calls while a drain is already pending or
 *     running are idempotent; only one worker lives at a time.
 *
 * The [Context] is passed in (not resolved via `androidContext()`) because
 * `:core:data` depends only on `koin-core`, not `koin-android` — mirroring the
 * pattern used by [es.schsebastian.foodrats.core.data.di.connectivityAndroidModule].
 */
class WorkManagerOutboxDrainScheduler(private val context: Context) : OutboxDrainScheduler {

    override fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<OutboxDrainWorker>()
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

    companion object {
        const val WORK_NAME = "outbox-drain"
    }
}
