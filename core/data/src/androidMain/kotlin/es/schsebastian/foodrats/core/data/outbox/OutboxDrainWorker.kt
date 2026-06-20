package es.schsebastian.foodrats.core.data.outbox

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * WorkManager-survivable wrapper around [OutboxRunner.runOnce].
 *
 * WorkManager keeps the request alive across process death, network drops, and
 * reboots (via the `NetworkType.CONNECTED` constraint + `KEEP` unique-work policy in
 * [WorkManagerOutboxDrainScheduler], so at most one worker lives at a time and it
 * fires automatically on reconnect).
 *
 * Dependencies are resolved at runtime via Koin [KoinComponent] because WorkManager
 * instantiates workers via reflection and we cannot pass constructor arguments.
 * The resolved [OutboxRunner] is the **same Koin singleton** started by `outboxModule`
 * (with its [kotlinx.coroutines.sync.Mutex]), so this worker and the in-process
 * drain triggered by connectivity/enqueue events can never run a concurrent drain
 * pass — the Mutex serialises them.
 *
 * Worker path: [OutboxRunner.runOnce] is called with `scope = null` (no in-process
 * per-entry backoff delay — WorkManager's own exponential backoff drives spacing
 * between worker runs). Return contract:
 *   - `true`  → [Result.success] (outbox is fully drained, WorkManager stops)
 *   - `false` → [Result.retry]   (work remains; WM re-runs after backoff)
 *   - exception → [Result.retry]
 */
class OutboxDrainWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val runner: OutboxRunner by inject()

    override suspend fun doWork(): Result =
        runCatching {
            runner.runOnce(scope = null)
        }.fold(
            onSuccess = { drained ->
                if (drained) Result.success() else Result.retry()
            },
            onFailure = { t ->
                FrLog.w("OutboxDrain", t) { "worker failed: ${t.message}" }
                Result.retry()
            },
        )
}
