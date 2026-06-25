package es.schsebastian.foodrats.feature.meal.domain.test

import es.schsebastian.foodrats.core.domain.meal.DraftIngredients
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class FakeMealRepository : MealRepository {
    private val draftState = MutableStateFlow<MealDraft?>(null)
    var publishResultOverride: Result<Meal, MealError>? = null
    var saveDraftResultOverride: Result<Unit, MealError>? = null
    val publishedDrafts = mutableListOf<MealDraft>()
    data class RateCall(val crewId: CrewId, val mealId: MealId, val raterId: AccountId, val score: Score)
    val rateCalls = mutableListOf<RateCall>()
    var rateResultOverride: Result<Unit, RateError>? = null

    private val mealCounts = mutableMapOf<Pair<CrewId, MealDay>, Int>()

    /** Seed how many meals the author already has in a crew on a day (drives the daily-cap gate). */
    fun setMealCount(crewId: CrewId, day: MealDay, count: Int) {
        mealCounts[crewId to day] = count
    }

    override suspend fun mealCountsPerCrew(
        crewIds: Set<CrewId>, day: MealDay,
    ): Result<Map<CrewId, Int>, MealError.Read> =
        Result.success(crewIds.associateWith { mealCounts[it to day] ?: 0 })

    override suspend fun publish(draft: MealDraft): Result<Meal, MealError> {
        publishedDrafts += draft
        publishResultOverride?.let { return it }
        val crewId = draft.audienceCrewIds.firstOrNull()
            ?: return Result.failure(MealError.Publish.NoCrewSelected)
        return Result.success(
            Meal(
                id = (MealId.of("fake-id") as Result.Ok).value,
                author = MealAuthor(draft.authorId, "Fake", null),
                crewId = crewId,
                day = draft.day,
                slot = draft.slot,
                photoUrl = "fake://photo",
                dish = draft.dish!!,
                description = draft.description,
                publishedAt = Instant.parse("2026-05-16T00:00:00Z"),
            )
        )
    }

    val deleteCalls = mutableListOf<Pair<CrewId, MealId>>()
    var deleteResultOverride: Result<Unit, MealDeleteError>? = null
    override suspend fun delete(crewId: CrewId, mealId: MealId): Result<Unit, MealDeleteError> {
        deleteCalls += crewId to mealId
        return deleteResultOverride ?: Result.success(Unit)
    }

    val deleteFromAllCrewsCalls = mutableListOf<Set<CrewId>>()
    override suspend fun deleteFromAllCrews(
        crewIds: Set<CrewId>,
        authorId: AccountId,
        day: MealDay,
        token: String,
    ): Result<Unit, MealDeleteError> {
        deleteFromAllCrewsCalls += crewIds
        crewIds.forEach { deleteCalls += it to MealId.forDayToken(it, authorId, day, token) }
        return deleteResultOverride ?: Result.success(Unit)
    }
    override suspend fun saveDraft(draft: MealDraft): Result<Unit, MealError> {
        saveDraftResultOverride?.let { return it }
        draftState.value = draft; return Result.success(Unit)
    }
    override fun observeDraft(): Flow<MealDraft?> = draftState
    override suspend fun clearDraft() { draftState.value = null }

    override fun observeDraftIngredients(): Flow<DraftIngredients?> =
        draftState.map { d -> d?.let { DraftIngredients(it.ingredients, it.detectedIngredients) } }
    override suspend fun setIngredients(slugs: List<IngredientSlug>) {
        draftState.value = draftState.value?.copy(ingredients = slugs)
    }

    override fun observeFeed(crewId: CrewId, day: MealDay) =
        flowOf(Result.success<List<MealWithRatings>>(emptyList()) as Result<List<MealWithRatings>, MealReadError>)
    override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) =
        flowOf(Result.success<List<MealWithRatings>>(emptyList()) as Result<List<MealWithRatings>, MealReadError>)

    override suspend fun rate(
        crewId: CrewId,
        mealId: MealId,
        raterId: AccountId,
        score: Score,
    ): Result<Unit, RateError> {
        rateCalls += RateCall(crewId, mealId, raterId, score)
        return rateResultOverride ?: Result.success(Unit)
    }
}
