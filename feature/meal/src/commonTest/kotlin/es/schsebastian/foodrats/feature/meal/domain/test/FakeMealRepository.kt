package es.schsebastian.foodrats.feature.meal.domain.test

import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

class FakeMealRepository : MealRepository {
    private val draftState = MutableStateFlow<MealDraft?>(null)
    var publishResultOverride: Result<Meal, MealError>? = null
    val publishedDrafts = mutableListOf<MealDraft>()

    private val takenSlots = mutableMapOf<Triple<CrewId, MealDay, MealSlot>, Boolean>()

    fun markSlotTaken(crewId: CrewId, day: MealDay, slot: MealSlot) {
        takenSlots[Triple(crewId, day, slot)] = true
    }

    override suspend fun hasMealForSlot(
        crewId: CrewId,
        day: MealDay,
        slot: MealSlot,
    ): Result<Boolean, MealError.Read> =
        Result.success(takenSlots[Triple(crewId, day, slot)] == true)

    override suspend fun takenSlotsFor(
        crewId: CrewId,
        day: MealDay,
    ): Result<Set<MealSlot>, MealError.Read> = Result.success(
        takenSlots.entries
            .filter { (key, value) -> value && key.first == crewId && key.second == day }
            .map { it.key.third }
            .toSet()
    )

    override suspend fun publish(draft: MealDraft): Result<Meal, MealError> {
        publishedDrafts += draft
        return publishResultOverride ?: Result.success(
            Meal(
                id = (MealId.of("fake-id") as Result.Ok).value,
                author = MealAuthor(draft.authorId, "Fake", null),
                crewId = draft.crewId,
                day = draft.day,
                slot = MealSlot.Lunch,
                photoUrl = "fake://photo",
                score = draft.score!!,
                dish = draft.dish!!,
                tags = draft.tags,
                publishedAt = Instant.parse("2026-05-16T00:00:00Z"),
            )
        )
    }
    override suspend fun delete(id: MealId) = Result.success(Unit)
    override suspend fun saveDraft(draft: MealDraft): Result<Unit, MealError> {
        draftState.value = draft; return Result.success(Unit)
    }
    override fun observeDraft(): Flow<MealDraft?> = draftState
    override suspend fun clearDraft() { draftState.value = null }
    override fun observeFeed(crewId: CrewId, day: MealDay) =
        flowOf(Result.success<List<Meal>>(emptyList()) as Result<List<Meal>, MealReadError>)
    override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) =
        flowOf(Result.success<List<Meal>>(emptyList()) as Result<List<Meal>, MealReadError>)
}
