package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealKind
import es.schsebastian.foodrats.core.domain.meal.MealPlate
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MealMapperTest {
    @Test fun toDomain_succeeds_on_complete_dto() {
        val dto = MealDto(
            id = "m-1", authorId = "a-1", authorName = "Sam",
            crewId = "c-1", dayKey = "2026-05-16", platePath = "crews/c-1/meals/m-1.jpg",
            dishName = "Pizza", description = "Margherita with basil",
            publishedAtEpochMs = 1731_000_000_000,
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals("Margherita with basil", r.value.description.value)
    }

    @Test fun toDomain_succeeds_with_empty_description() {
        val dto = MealDto(
            id = "m-2", authorId = "a-1", authorName = "Sam",
            crewId = "c-1", dayKey = "2026-05-16", platePath = "crews/c-1/meals/m-2.jpg",
            dishName = "Pizza",
            publishedAtEpochMs = 1731_000_000_000,
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals("", r.value.description.value)
    }

    @Test fun toDomain_reads_stamped_cuisine_slug() {
        val dto = MealDto(
            id = "m-3", authorId = "a-1", authorName = "Sam",
            crewId = "c-1", dayKey = "2026-05-16", platePath = "crews/c-1/meals/m-3.jpg",
            dishName = "Pizza", publishedAtEpochMs = 1731_000_000_000,
            cuisine = "italian",
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals("italian", r.value.cuisine?.value)
    }

    @Test fun toDomain_drops_blank_cuisine_to_null() {
        val dto = MealDto(
            id = "m-4", authorId = "a-1", authorName = "Sam",
            crewId = "c-1", dayKey = "2026-05-16", platePath = "crews/c-1/meals/m-4.jpg",
            dishName = "Pizza", publishedAtEpochMs = 1731_000_000_000,
            cuisine = "   ",
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(null, r.value.cuisine)
    }

    @Test fun toDomain_reads_solo_kind_discriminator() {
        val dto = soloDtoTemplate.copy(id = "m-5", kind = "solo")
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(MealKind.Solo, r.value.kind)
    }

    @Test fun toDomain_defaults_missing_kind_to_solo() {
        // Old/pre-seam doc: no `kind` field. MealDto's default is "solo".
        val dto = soloDtoTemplate.copy(id = "m-6")
        assertEquals("solo", dto.kind)
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(MealKind.Solo, r.value.kind)
    }

    @Test fun toDomain_maps_unknown_kind_to_solo() {
        // Forward-compat: a future "together" (or any unknown) value seen by a not-yet-updated
        // client collapses to Solo rather than failing the read.
        val dto = soloDtoTemplate.copy(id = "m-7", kind = "together")
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(MealKind.Solo, r.value.kind)
    }

    @Test fun from_writes_solo_discriminator() {
        val meal = (soloDtoTemplate.copy(id = "m-8").toDomain() as Result.Ok).value
        val dto = MealDto.from(meal)
        assertEquals("solo", dto.kind)
    }

    // ── plateSource provenance round-trip ────────────────────────────────

    @Test fun plate_source_camera_round_trips() {
        val dto = soloDtoTemplate.copy(id = "m-9", plateSource = "camera")
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(PlateSource.Camera, r.value.plateSource)
        assertEquals("camera", MealDto.from(r.value).plateSource)
    }

    @Test fun plate_source_gallery_round_trips() {
        val dto = soloDtoTemplate.copy(id = "m-10", plateSource = "gallery")
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(PlateSource.Gallery, r.value.plateSource)
        assertEquals("gallery", MealDto.from(r.value).plateSource)
    }

    @Test fun legacy_doc_without_plate_source_reads_as_camera() {
        // Docs published before the marker existed carry no plateSource — the DTO default is null.
        val dto = soloDtoTemplate.copy(id = "m-11")
        assertEquals(null, dto.plateSource)
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(PlateSource.Camera, r.value.plateSource)
    }

    @Test fun unknown_plate_source_value_collapses_to_camera() {
        // Forward-tolerance: an unrecognized key must never fail the read.
        val dto = soloDtoTemplate.copy(id = "m-12", plateSource = "hologram")
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(PlateSource.Camera, r.value.plateSource)
    }

    // ── plates (multi-photo) round-trip ──────────────────────────────────

    /** A well-formed multi-photo doc round-trips its FULL ordered `plates` list. */
    @Test fun plates_round_trip_in_order_with_mixed_sources() {
        // A well-formed doc keeps platePath/plateSource in sync with plates[0] (guaranteed by
        // construction on the write side — see FirebaseMealRepository.publish/MealDto.from);
        // model that here rather than relying on the read side to paper over drift.
        val dto = soloDtoTemplate.copy(
            id = "m-13",
            platePath = "crews/c-1/meals/m-13.jpg",
            plateSource = "camera",
            plates = listOf(
                PlateEntryDto(path = "crews/c-1/meals/m-13.jpg", source = "camera"),
                PlateEntryDto(path = "crews/c-1/meals/m-13_p1.jpg", source = "gallery"),
            ),
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(
            listOf(
                MealPlate("crews/c-1/meals/m-13.jpg", PlateSource.Camera),
                MealPlate("crews/c-1/meals/m-13_p1.jpg", PlateSource.Gallery),
            ),
            r.value.plates,
        )
        // plates[0] mirrors the legacy top-level fields.
        assertEquals(r.value.photoUrl, r.value.plates[0].photoUrl)
        assertEquals(r.value.plateSource, r.value.plates[0].source)

        // The inverse (`from`) rebuilds the SAME deterministic per-index paths from the ids.
        val back = MealDto.from(r.value)
        assertEquals(listOf("crews/c-1/meals/m-13.jpg", "crews/c-1/meals/m-13_p1.jpg"), back.plates.map { it.path })
        assertEquals(listOf("camera", "gallery"), back.plates.map { it.source })
    }

    /** Legacy fallback: `plates` empty/absent reads back as an empty `Meal.plates` — readers fall
     *  back to photoUrl/plateSource for the single photo, exactly like the pre-multi-photo shape. */
    @Test fun empty_plates_falls_back_to_legacy_single_photo_shape() {
        val dto = soloDtoTemplate.copy(id = "m-14", plateSource = "gallery") // plates defaults to emptyList()
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(emptyList<MealPlate>(), r.value.plates)
        assertEquals(1, r.value.photoCount)
        assertEquals(PlateSource.Gallery, r.value.plateSource)
    }

    /** An entry with a null/blank path is DROPPED, never surfaced as a blank-URL photo. */
    @Test fun plates_entry_with_null_or_blank_path_is_dropped() {
        val dto = soloDtoTemplate.copy(
            id = "m-15",
            plates = listOf(
                PlateEntryDto(path = "crews/c-1/meals/m-15.jpg", source = "camera"),
                PlateEntryDto(path = null, source = "gallery"),
                PlateEntryDto(path = "   ", source = "gallery"),
            ),
        )
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(listOf(MealPlate("crews/c-1/meals/m-15.jpg", PlateSource.Camera)), r.value.plates)
    }

    /** Every entry dropped (all null/blank paths) collapses to the legacy empty-list shape, not a
     *  read failure. */
    @Test fun plates_with_every_entry_blank_collapses_to_empty_list() {
        val dto = soloDtoTemplate.copy(id = "m-16", plates = listOf(PlateEntryDto(path = null), PlateEntryDto(path = "")))
        val r = dto.toDomain()
        assertTrue(r is Result.Ok)
        assertEquals(emptyList<MealPlate>(), r.value.plates)
    }

    /** `MealDto.from` rebuilds `plates` deterministically from ids/index — never from
     *  `meal.plates[i].photoUrl`, which may hold a (resolved, expiring) signed URL. */
    @Test fun from_rebuilds_plates_from_ids_never_from_signed_urls() {
        val enrichedMeal = (soloDtoTemplate.copy(id = "m-17").toDomain() as Result.Ok).value.copy(
            plates = listOf(
                MealPlate("https://signed.example/m-17.jpg?token=abc", PlateSource.Camera),
                MealPlate("https://signed.example/m-17_p1.jpg?token=xyz", PlateSource.Gallery),
            ),
        )
        val dto = MealDto.from(enrichedMeal)
        assertEquals(
            listOf("crews/c-1/meals/m-17.jpg", "crews/c-1/meals/m-17_p1.jpg"),
            dto.plates.map { it.path },
        )
        assertTrue(dto.plates.none { it.path?.contains("signed.example") == true })
    }

    private val soloDtoTemplate = MealDto(
        id = "m-0", authorId = "a-1", authorName = "Sam",
        crewId = "c-1", dayKey = "2026-05-16", platePath = "crews/c-1/meals/m-0.jpg",
        dishName = "Pizza", publishedAtEpochMs = 1731_000_000_000,
    )
}
