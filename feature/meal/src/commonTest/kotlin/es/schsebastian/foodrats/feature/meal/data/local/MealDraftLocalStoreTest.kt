package es.schsebastian.foodrats.feature.meal.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class MealDraftLocalStoreTest {
    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val newValue = transform(state.value)
            state.value = newValue
            return newValue
        }
    }

    @Test fun round_trips_ingredient_fields() = runTest {
        val store = MealDraftLocalStore(AppPreferences(FakeDataStore()))
        val draft = MealDraft(
            audienceCrewIds = setOf(
                (CrewId.of("crew-1") as Result.Ok).value,
                (CrewId.of("crew-2") as Result.Ok).value,
            ),
            authorId = (AccountId.of("acc-1") as Result.Ok).value,
            day = MealDay(LocalDate(2026, 5, 24), TimeZone.UTC),
            plate = null,
            dish = null,
            description = Description.EMPTY,
            ingredients = listOf(IngredientSlug.of("tomato").getOrNull()!!, IngredientSlug.of("pasta").getOrNull()!!),
            detectedIngredients = listOf(IngredientSlug.of("tomato").getOrNull()!!, IngredientSlug.of("cheese").getOrNull()!!),
            classifierVersion = "food101-v1",
        )

        store.save(draft)
        val restored = store.observe().first()!!

        assertEquals(
            setOf((CrewId.of("crew-1") as Result.Ok).value, (CrewId.of("crew-2") as Result.Ok).value),
            restored.audienceCrewIds,
        )
        assertEquals(listOf(IngredientSlug.of("tomato").getOrNull()!!, IngredientSlug.of("pasta").getOrNull()!!), restored.ingredients)
        assertEquals(listOf(IngredientSlug.of("tomato").getOrNull()!!, IngredientSlug.of("cheese").getOrNull()!!), restored.detectedIngredients)
        assertEquals("food101-v1", restored.classifierVersion)
    }
}
