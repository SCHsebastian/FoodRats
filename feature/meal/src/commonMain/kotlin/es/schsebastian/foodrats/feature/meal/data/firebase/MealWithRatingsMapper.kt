package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealRating
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.time.Instant

/**
 * Minimal projection of a crew member used to resolve rater display names without
 * pulling the full `MemberDto` (which lives in `:feature:crew`).
 */
data class CrewMemberLookup(
    val displayName: String,
    val avatarUrl: String?,
)

/**
 * Builds a `MealWithRatings` from a `MealDto` (with its denormalized `ratings` map)
 * and a snapshot of the crew's members. Ratings whose raterUid is no longer in the
 * crew document are kept (so `ratingSum` / `voterCount` stay accurate) but show a
 * placeholder display name.
 */
fun MealDto.toMealWithRatings(
    crewMembers: Map<String, CrewMemberLookup>,
): Result<MealWithRatings, MealReadError> {
    val mealResult = this.toDomain()
    if (mealResult !is Result.Ok) return Result.failure(MealReadError.Unavailable)
    val meal = mealResult.value
    val ratings = this.ratings.mapNotNull { (uid, entry) ->
        val raterId = (AccountId.of(uid) as? Result.Ok)?.value ?: return@mapNotNull null
        val score = (Score.of(entry.score) as? Result.Ok)?.value ?: return@mapNotNull null
        val member = crewMembers[uid]
        MealRating(
            raterId = raterId,
            raterDisplayName = member?.displayName ?: "—",
            raterAvatarUrl = member?.avatarUrl,
            score = score,
            ratedAt = Instant.fromEpochMilliseconds(entry.atMs),
        )
    }
    return Result.success(MealWithRatings(meal, ratings))
}
