package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.cuisine.CuisineSlug
import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlin.time.Instant

data class Meal(
    val id: MealId,
    val author: MealAuthor,
    val crewId: CrewId,
    val day: MealDay,
    /** Optional "meal moment" label — `null` when the author didn't tag one (slot is no longer required). */
    val slot: MealSlot?,
    /**
     * Signed URL of the FULL plate image (used by the detail screen). At the data layer this
     * transiently carries the Storage PATH until the feed enrichment resolves it to a signed URL.
     */
    val photoUrl: String,
    /**
     * Signed URL of the lightweight server-generated THUMBNAIL (used by feed cards — smaller +
     * faster). Empty when the image pipeline hasn't produced a thumbnail yet (or pre-pipeline
     * meals); callers fall back to [photoUrl]. Resolved by the feed enrichment, like [photoUrl].
     */
    val thumbnailUrl: String = "",
    /**
     * Base64-encoded ThumbHash (the ~21–25 byte blur preview) written by the server pipeline.
     * Decoded client-side into the instant placeholder behind the plate image. `null` until the
     * pipeline processes the meal (a few seconds post-publish) — callers show a flat placeholder.
     */
    val thumbHash: String? = null,
    val dish: DishName,
    val description: Description,
    val publishedAt: Instant,
    val coordinates: Coordinates? = null,
    val ingredients: List<IngredientSlug> = emptyList(),
    val detectedIngredients: List<IngredientSlug> = emptyList(),
    val classifierVersion: String? = null,
    /**
     * The cuisine STAMPED at publish from the detected dish via
     * [CuisineReadPort.loadDishCuisine][es.schsebastian.foodrats.core.domain.cuisine.CuisineReadPort.loadDishCuisine]
     * (roadmap §2.2: stamp-at-publish, stable across future `dishCuisineMap` changes). `null`
     * when the dish wasn't classified or isn't in the cuisine map. The passport derivation
     * ([deriveCuisinePassport][es.schsebastian.foodrats.core.domain.cuisine.deriveCuisinePassport])
     * reads this field; it never re-derives cuisine from ingredients/AI detections.
     */
    val cuisine: CuisineSlug? = null,
    /**
     * How this meal is authored — see [MealKind]. Defaults to [MealKind.Solo], so every existing
     * construction site compiles unchanged and feed/stats read `Solo` for every meal (pre-launch:
     * no migration; old data with no `kind` reads as `Solo`). [author] stays the single
     * authoritative author for `Solo`. This is a behaviorally-inert seam for the deferred
     * multi-author kind (spec §4.2 / §5).
     */
    val kind: MealKind = MealKind.Solo,
    /**
     * How the plate photo was captured — see [PlateSource]. Defaults to [PlateSource.Camera] so
     * every existing construction site compiles unchanged and legacy meals (published before this
     * marker existed) read as camera-sourced.
     */
    val plateSource: PlateSource = PlateSource.Camera,
    /**
     * Ordered photos for this meal, up to [MealPublishPolicy.MAX_PHOTOS_PER_MEAL]. Contract:
     * - **Empty** — the legacy single-photo shape. Every meal published before this field existed
     *   (and any construction site that hasn't been migrated) has an empty list; readers fall back
     *   to [photoUrl]/[thumbnailUrl]/[plateSource] for the one photo.
     * - **Non-empty** — the full ordered list of this meal's photos. `plates[0]` always mirrors
     *   [photoUrl]/[plateSource] (the first photo doubles as the "cover" photo those fields
     *   surface), so single-photo readers that only know about [photoUrl] still see the right
     *   cover image.
     *
     * Defaults to empty so every existing construction site compiles unchanged.
     */
    val plates: List<MealPlate> = emptyList(),
) {
    /**
     * Total photo count for this meal. Legacy/empty [plates] is always exactly 1 (the single
     * [photoUrl] every meal has); non-empty [plates] reports its own size. Never 0.
     */
    val photoCount: Int get() = if (plates.isEmpty()) 1 else plates.size
}
