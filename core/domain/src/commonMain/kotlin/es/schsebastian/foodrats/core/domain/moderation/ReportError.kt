package es.schsebastian.foodrats.core.domain.moderation

/**
 * Typed failures for submitting a content/user report (UGC compliance §4). Sealed with a nested
 * `Submit` group (reports are write-only from the client — the queue is server-only readable, so there
 * is no read error). No `Unknown` leaf.
 */
sealed interface ReportError {

    /** Failures while submitting a report. */
    sealed interface Submit : ReportError {
        /** No authenticated user. */
        data object NotSignedIn : Submit

        /** A reporter cannot report their own content/account. */
        data object SelfReport : Submit

        /** This reporter has already reported this exact target (idempotent create denied). */
        data object AlreadyReported : Submit

        /** Network / backend unavailable. */
        data object Unavailable : Submit
    }
}
