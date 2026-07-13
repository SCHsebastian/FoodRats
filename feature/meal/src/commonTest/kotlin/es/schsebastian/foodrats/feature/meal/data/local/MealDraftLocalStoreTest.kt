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
            plates = listOf(Plate(byteArrayOf(1, 2, 3), source = PlateSource.Gallery)),
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

    @Test fun round_trips_multiple_photos_in_order_with_mixed_sources() = runTest {
        val store = MealDraftLocalStore(AppPreferences(FakeDataStore()))
        val draft = MealDraft(
            audienceCrewIds = setOf((CrewId.of("crew-1") as Result.Ok).value),
            authorId = (AccountId.of("acc-1") as Result.Ok).value,
            day = MealDay(LocalDate(2026, 7, 13), TimeZone.UTC),
            plates = listOf(
                Plate(byteArrayOf(1, 2, 3), source = PlateSource.Camera),
                Plate(byteArrayOf(4, 5, 6), source = PlateSource.Gallery),
                Plate(byteArrayOf(7, 8, 9), source = PlateSource.Camera),
            ),
            dish = null,
            description = Description.EMPTY,
        )

        store.save(draft)
        val restored = store.observe().first()!!

        assertEquals(3, restored.plates.size)
        assertContentEquals(byteArrayOf(1, 2, 3), restored.plates[0].photoBytes)
        assertContentEquals(byteArrayOf(4, 5, 6), restored.plates[1].photoBytes)
        assertContentEquals(byteArrayOf(7, 8, 9), restored.plates[2].photoBytes)
        assertEquals(
            listOf(PlateSource.Camera, PlateSource.Gallery, PlateSource.Camera),
            restored.plates.map { it.source },
        )
        // The primary convenience derivation mirrors plates[0].
        assertContentEquals(byteArrayOf(1, 2, 3), restored.plate?.photoBytes)
    }

    @Test fun legacy_persisted_draft_without_plates_array_reads_as_a_one_item_list() = runTest {
        // A draft persisted before the `plates` array existed: raw JSON with only the legacy
        // single-photo fields, no `plates` key at all. Must read back as a 1-item list.
        val prefs = AppPreferences(FakeDataStore())
        prefs.set(
            Keys.MealDraftJson,
            """
            {"audienceCrewIds":["crew-1"],"authorId":"acc-1","dayIso":"2026-07-13","zoneId":"UTC",
             "photoBase64":"AQID","overlayApplied":false,"plateSource":"gallery","dish":null}
            """.trimIndent(),
        )
        val store = MealDraftLocalStore(prefs)

        val restored = store.observe().first()!!

        assertEquals(1, restored.plates.size)
        assertEquals(PlateSource.Gallery, restored.plates.single().source)
        assertContentEquals(byteArrayOf(1, 2, 3), restored.plates.single().photoBytes)
    }
}
