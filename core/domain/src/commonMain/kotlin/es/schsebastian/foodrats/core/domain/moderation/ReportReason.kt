package es.schsebastian.foodrats.core.domain.moderation

/**
 * Why a [ReportTarget] is being reported. Sealed (not an enum) per the project convention for closed
 * sets that may carry payloads later — e.g. [Other] may someday carry a free-text note. The leaves map
 * 1:1 to the wire `reason` strings the Firestore rules whitelist (`§8.2`).
 */
sealed interface ReportReason {
    data object Spam : ReportReason
    data object Harassment : ReportReason
    data object Hate : ReportReason
    data object Sexual : ReportReason
    data object Violence : ReportReason
    data object Other : ReportReason
}
