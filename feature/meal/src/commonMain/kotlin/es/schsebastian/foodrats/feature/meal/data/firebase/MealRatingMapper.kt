package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealRating
import es.schsebastian.foodrats.core.domain.meal.MealValueObjectError
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import kotlin.time.Instant

fun MealRatingDto.toDomain(raterId: AccountId): Result<MealRating, MealValueObjectError> {
    val rawScore = score ?: return Result.failure(MealValueObjectError.ScoreOutOfRange)
    val ts = ratedAtEpochMs ?: return Result.failure(MealValueObjectError.ScoreOutOfRange)
    val s: Score = Score.of(rawScore).getOrElse { return Result.failure(it) }
    return Result.success(
        MealRating(
            raterId = raterId,
            raterDisplayName = raterName.orEmpty(),
            raterAvatarUrl = raterAvatarUrl,
            score = s,
            ratedAt = Instant.fromEpochMilliseconds(ts),
        ),
    )
}

fun MealRating.toDto(): MealRatingDto = MealRatingDto(
    score = score.value,
    ratedAtEpochMs = ratedAt.toEpochMilliseconds(),
    raterName = raterDisplayName,
    raterAvatarUrl = raterAvatarUrl,
)
