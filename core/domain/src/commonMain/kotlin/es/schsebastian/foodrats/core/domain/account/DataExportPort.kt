package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Initiates a GDPR Art. 20 data-portability export of the caller's own data.
 *
 * [exportMyData] invokes the server-side `exportMyData` callable **synchronously** and returns
 * [Result.success] with an [ExportReady] only when the function reports the archive assembled and
 * uploaded. The archive is an `application/json` document containing the caller's own PII
 * (account, consent, devices, crews, meals, comments, votes, plates with signed image URLs) — no
 * other members' data. This is **read-only**: nothing is created or destroyed, so a failure is
 * always retryable.
 *
 * The request carries **no** caller-supplied fields — the implementing adapter derives the
 * caller's identity server-side (`request.auth.uid`); a client may only export its own data.
 *
 * See `docs/session/handoffs/w0-data-export-function.md`. The adapter lives in the feature's
 * data layer; this domain port is vendor-free.
 */
interface DataExportPort {
    suspend fun exportMyData(): Result<ExportReady, DataExportError>
}

/**
 * A ready-to-download data export.
 *
 * @property downloadUrl a short-lived (15-minute) signed READ URL to the JSON archive. The UI
 *   opens this directly in a browser / share sheet.
 * @property expiresAtMs epoch-ms after which [downloadUrl] (and every plate image URL inside the
 *   archive) is expired; surface a "link valid for ~15 min" hint or re-request on expiry.
 */
data class ExportReady(
    val downloadUrl: String,
    val expiresAtMs: Long,
)

sealed interface DataExportError {
    sealed interface Backend : DataExportError {
        /**
         * The export could not be assembled/signed/uploaded (server `internal`), or the caller's
         * session could not be authenticated (server `unauthenticated` — shouldn't happen behind
         * the auth gate). Nothing was destroyed, so the user can retry.
         */
        data object Unavailable : Backend
    }
}
