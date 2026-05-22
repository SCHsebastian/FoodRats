package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Cross-context read port: "has this account already published any meal in this crew on this day?"
 * Consumed by :feature:notifications to skip the daily inactivity reminder when the user has
 * already posted. Implemented in :feature:meal/data/firebase. Best-effort — on read failure
 * callers should treat the result as "fire anyway" since a false negative beats a missed nudge.
 */
interface HasPostedTodayPort {
    suspend fun hasPosted(
        accountId: AccountId,
        crewId: CrewId,
        day: MealDay,
    ): Result<Boolean, MealReadError>
}
