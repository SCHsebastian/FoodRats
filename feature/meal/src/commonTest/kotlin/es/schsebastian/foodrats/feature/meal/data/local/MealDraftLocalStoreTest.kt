package es.schsebastian.foodrats.feature.meal.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    @Test fun round_trips_gallery_plate_source() = runTest {
        val store = MealDraftLocalStore(AppPreferences(FakeDataStore()))
        val draft = MealDraft(
            audienceCrewIds = setOf((CrewId.of("crew-1") as Result.Ok).value),
            authorId = (AccountId.of("acc-1") as Result.Ok).value,
            day = MealDay(LocalDate(2026, 7, 13), TimeZone.UTC),
            plate = Plate(byteArrayOf(1, 2, 3), source = PlateSource.Gallery),
            dish = null,
            description = Description.EMPTY,
        )

        store.save(draft)
        val restored = store.observe().first()!!

        assertEquals(PlateSource.Gallery, restored.plate?.source)
        assertContentEquals(byteArrayOf(1, 2, 3), restored.plate?.photoBytes)
    }

    @Test fun legacy_persisted_draft_without_plate_source_reads_as_camera() = runTest {
        // A draft persisted by a pre-marker build: raw JSON with no `plateSource` field. The
        // decoder must fill the null default and the Plate must read back as camera-sourced.
        val prefs = AppPreferences(FakeDataStore())
        prefs.set(
            Keys.MealDraftJson,
            """
            {"audienceCrewIds":["crew-1"],"authorId":"acc-1","dayIso":"2026-07-13","zoneId":"UTC",
             "photoBase64":"AQID","overlayApplied":false,"dish":null}
            """.trimIndent(),
        )
        val store = MealDraftLocalStore(prefs)

        val restored = store.observe().first()!!

        assertEquals(PlateSource.Camera, restored.plate?.source)
        assertContentEquals(byteArrayOf(1, 2, 3), restored.plate?.photoBytes)
    }
}
