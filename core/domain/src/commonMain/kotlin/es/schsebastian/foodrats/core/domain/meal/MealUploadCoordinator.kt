package es.schsebastian.foodrats.core.domain.meal

/**
 * Write side of the meal-upload coordinator. Implementations enqueue the
 * upload on an application-scoped coroutine so the user can navigate away
 * from the composer without cancelling the work, then publish state
 * transitions through [MealUploadProgressPort].
 *
 * `enqueue` is fire-and-forget — it returns as soon as the work is scheduled.
 * The single in-flight upload model matches the product invariant of one
 * meal per slot per day; concurrent uploads would be a misuse.
 */
interface MealUploadCoordinator {
    fun enqueueDraftUpload()
}
