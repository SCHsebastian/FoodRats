package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import es.schsebastian.foodrats.core.domain.account.DataExportError
import es.schsebastian.foodrats.core.domain.account.DataExportPort
import es.schsebastian.foodrats.core.domain.account.ExportReady
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * [DataExportPort] over the `exportMyData` callable Cloud Function (region `europe-west3`).
 *
 * Mirrors [FirebaseAccountDeletionPort]: same `Firebase.functions(region).httpsCallable(NAME)
 * .invoke(req).data<Resp>()` call, the single [DispatcherProvider.io] boundary, and the
 * `runCatching { … }.fold(…)` error mapping by inspecting the [Throwable.message] for the
 * `HttpsError` code.
 *
 * The request carries **no** caller-supplied fields — the function derives the uid from
 * `request.auth.uid`; a client may only export its own data. The response is
 * `{ downloadUrl, expiresAtMs }` (a 15-minute V4 signed READ URL to a JSON archive). This is a
 * read-only operation (no `failed-precondition`/`aborted`): a server `unauthenticated` maps to the
 * re-auth-routed [DataExportError.Session.Unauthenticated], everything else to the retryable
 * [DataExportError.Backend.Unavailable]. See `docs/session/handoffs/w0-data-export-function.md`.
 */
class FirebaseDataExportPort(
    private val dispatchers: DispatcherProvider,
    private val region: String = "europe-west3",
) : DataExportPort {

    private val functions by lazy { Firebase.functions(region) }

    override suspend fun exportMyData(): Result<ExportReady, DataExportError> =
        withContext(dispatchers.io) {
            runCatching {
                functions.httpsCallable(CALLABLE)
                    .invoke(ExportMyDataRequest())
                    .data<ExportMyDataResponse>()
            }.fold(
                onSuccess = { resp ->
                    Result.success(
                        ExportReady(downloadUrl = resp.downloadUrl, expiresAtMs = resp.expiresAtMs),
                    )
                },
                onFailure = { t ->
                    FrLog.w("DataExport", t) { "exportMyData failed: ${t.message}" }
                    Result.failure(t.toDataExportError())
                },
            )
        }

    private companion object {
        const val CALLABLE = "exportMyData"
    }
}

/**
 * Maps the `HttpsError` code (string-matched on [Throwable.message], like
 * [Throwable.toAccountDeletionError]) to the typed export error tree: `unauthenticated → Session.
 * Unauthenticated` (route to re-auth); everything else → `Backend.Unavailable` (retryable). `internal`
 * so a unit test can exercise the mapping directly without a live `Firebase.functions`.
 */
internal fun Throwable.toDataExportError(): DataExportError {
    val msg = message?.lowercase() ?: ""
    return when {
        "unauthenticated" in msg -> DataExportError.Session.Unauthenticated
        else -> DataExportError.Backend.Unavailable
    }
}

/** No caller-supplied fields: the function derives the uid from `request.auth.uid`. */
@Serializable
private class ExportMyDataRequest

@Serializable
private data class ExportMyDataResponse(
    val downloadUrl: String,
    val expiresAtMs: Long,
)
