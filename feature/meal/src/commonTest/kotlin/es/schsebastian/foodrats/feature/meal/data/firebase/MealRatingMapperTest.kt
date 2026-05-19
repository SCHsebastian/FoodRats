package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealRating
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class MealRatingMapperTest {
    private val raterId = (AccountId.of("u-rater") as Result.Ok).value

    @Test fun dto_to_domain_happy_path() {
        val dto = MealRatingDto(
            score = 4,
            ratedAtEpochMs = 1747681200000L,
            raterName = "Maria",
            raterAvatarUrl = "https://x/a.jpg",
        )
        val r = dto.toDomain(raterId)
        assertIs<Result.Ok<MealRating>>(r)
        assertEquals(
            MealRating(
                raterId = raterId,
                raterDisplayName = "Maria",
                raterAvatarUrl = "https://x/a.jpg",
                score = (Score.of(4) as Result.Ok).value,
                ratedAt = Instant.fromEpochMilliseconds(1747681200000L),
            ),
            r.value,
        )
    }

    @Test fun dto_with_invalid_score_fails() {
        val dto = MealRatingDto(score = 99, ratedAtEpochMs = 0L, raterName = "x")
        val r = dto.toDomain(raterId)
        assertIs<Result.Err<*>>(r)
    }

    @Test fun missing_score_or_timestamp_fails() {
        val r1 = MealRatingDto(score = null, ratedAtEpochMs = 0L, raterName = "x").toDomain(raterId)
        val r2 = MealRatingDto(score = 3, ratedAtEpochMs = null, raterName = "x").toDomain(raterId)
        assertIs<Result.Err<*>>(r1)
        assertIs<Result.Err<*>>(r2)
    }

    @Test fun domain_to_dto_round_trip() {
        val r = MealRating(
            raterId = raterId,
            raterDisplayName = "Maria",
            raterAvatarUrl = null,
            score = (Score.of(5) as Result.Ok).value,
            ratedAt = Instant.fromEpochMilliseconds(1L),
        )
        val dto = r.toDto()
        assertEquals(5, dto.score)
        assertEquals(1L, dto.ratedAtEpochMs)
        assertEquals("Maria", dto.raterName)
        assertNull(dto.raterAvatarUrl)
    }
}
