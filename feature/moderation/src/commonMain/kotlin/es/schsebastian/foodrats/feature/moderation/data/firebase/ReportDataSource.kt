package es.schsebastian.foodrats.feature.moderation.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore

/**
 * Vendor-free seam for submitting reports, implemented by [ReportFirestoreDataSource]. The
 * [es.schsebastian.foodrats.feature.moderation.data.repository.ReportRepository] depends on this
 * abstraction so its error classification + dispatcher boundary stay host-testable; the Firestore impl
 * is the only place GitLive types appear.
 */
internal interface ReportDataSource {
    /**
     * Creates the report doc at `reports/{docId}`. The deterministic [docId] (`{reporter}|{targetKey}`)
     * makes a re-report by the same reporter an idempotent collision: throws [ReportAlreadyExistsException]
     * if the doc already exists, so the repository can map it to `AlreadyReported`.
     */
    suspend fun create(docId: String, dto: ReportDto)
}

/**
 * The only Firestore-touching adapter for the moderation report queue. Creates `reports/{docId}` inside
 * a transaction so a re-report (same deterministic id) deterministically fails with
 * [ReportAlreadyExistsException] rather than silently overwriting — the queue is append-only and
 * server-only-readable (UGC compliance §4 / §8.2).
 */
internal class ReportFirestoreDataSource(
    private val firestore: FirebaseFirestore,
) : ReportDataSource {

    private val reportsCol get() = firestore.collection("reports")

    override suspend fun create(docId: String, dto: ReportDto) {
        val ref = reportsCol.document(docId)
        firestore.runTransaction {
            val existing = get(ref)
            if (existing.exists) throw ReportAlreadyExistsException
            set(ref, dto)
        }
    }
}

/**
 * The deterministic report-doc id already exists — this reporter has already reported this exact
 * target. The repository maps it to [es.schsebastian.foodrats.core.domain.moderation.ReportError.Submit.AlreadyReported].
 */
internal object ReportAlreadyExistsException : RuntimeException() {
    private fun readResolve(): Any = ReportAlreadyExistsException
}
