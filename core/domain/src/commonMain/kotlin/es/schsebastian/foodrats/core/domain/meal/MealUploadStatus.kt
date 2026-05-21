package es.schsebastian.foodrats.core.domain.meal

/**
 * Cross-feature snapshot of a meal-upload coordinator. The publisher feature
 * (`:feature:meal`) updates the state; consumers (Feed, Stats top bars) read
 * the same flow via [MealUploadProgressPort].
 *
 * Idle and Success/Failed are terminal snapshots; Uploading is the long-lived
 * "show the top bar" state. Failed carries a string token (`errorKey`) the
 * presentation layer translates via its own `StringKey` — domain doesn't know
 * about i18n.
 */
sealed interface MealUploadStatus {
    data object Idle : MealUploadStatus
    data object Uploading : MealUploadStatus
    data object Succeeded : MealUploadStatus
    data class Failed(val errorKey: String) : MealUploadStatus
}
