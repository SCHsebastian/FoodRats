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
 * and a live identity lookup keyed by accountId. When the lookup resolves the
 * author or a rater, its values override the denormalized snapshots on the
 * meal document so a profile rename propagates without a republish.
 *
 * Ratings whose raterUid is missing from the lookup fall back to a placeholder.
 * The author falls back to the `MealDto.authorName` baked at publish time.
 */
fun MealDto.toMealWithRatings(
    crewMembers: Map<String, CrewMemberLookup>,
): Result<MealWithRatings, MealReadError> {
    val mealResult = this.toDomain()
    if (mealResult !is Result.Ok) return Result.failure(MealReadError.Unavailable)
    val baseMeal = mealResult.value
    val live = crewMembers[this.authorId]
    val meal = if (live != null) {
        baseMeal.copy(
            author = baseMeal.author.copy(
                displayName = live.displayName,
                avatarUrl = live.avatarUrl,
            ),
        )
    } else {
        baseMeal
    }
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
