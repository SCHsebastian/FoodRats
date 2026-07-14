package es.schsebastian.foodrats.core.domain.cuisine

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.jvm.JvmInline

/**
 * The stable identifier of a cuisine in the closed cuisine catalog (e.g. `italian`,
 * `middle_eastern`). It is the document id of `cuisines/{slug}` AND the value the
 * `dishCuisineMap` resolves a Food-101 dish to. Mirrors [IngredientSlug][es.schsebastian.foodrats.core.domain.meal.IngredientSlug].
 *
 * The catalog is a closed set of 14 slugs; [of] does not check membership (the catalog
 * is the source of truth at read time), only the shape (non-blank, length-bounded).
 */
@JvmInline
value class CuisineSlug internal constructor(val value: String) {
    companion object {
        const val MAX_LEN = 64

        /**
         * Validating factory — the only public way to build a [CuisineSlug].
         * Blank → [CuisineValueObjectError.CuisineSlugBlank]; over [MAX_LEN] chars →
         * [CuisineValueObjectError.CuisineSlugTooLong]. Mirrors `IngredientSlug.of`.
         */
        fun of(raw: String): Result<CuisineSlug, CuisineValueObjectError> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty()        -> Result.failure(CuisineValueObjectError.CuisineSlugBlank)
                trimmed.length > MAX_LEN -> Result.failure(CuisineValueObjectError.CuisineSlugTooLong)
                else                     -> Result.success(CuisineSlug(trimmed))
            }
        }
    }
}

/**
 * A cuisine catalog cell — one of the passport grid's collectible cuisines. A cuisine is
 * deliberately leaner than an `Ingredient`: just a slug, a language-resolved display name,
 * and an `iconKey` (always == the slug). No category/aliases — those are ingredient-only.
 *
 * @property displayName already resolved for the active language by the adapter (the
 *   repository re-maps names off a `language: Flow<String>`, exactly as `IngredientRepository`
 *   does); falls back to [CuisineSlug.humanized] when a localized name is missing.
 */
data class Cuisine(
    val slug: CuisineSlug,
    val displayName: String,
    val iconKey: String,
)

/**
 * Display fallback for a slug absent from the localized catalog (catalog still loading,
 * or an unknown slug): `middle_eastern` -> `Middle eastern`. The real localized name
 * replaces this as soon as the catalog emits. Mirrors `IngredientSlug.humanized`.
 */
fun CuisineSlug.humanized(): String =
    value.replace('_', ' ').replace('-', ' ').trim()
        .replaceFirstChar { it.uppercaseChar() }

/**
 * Typed shape failures for [CuisineSlug]. Sealed interface with `data object` leaves — never
 * an enum — per the project error convention (keeps the door open to payloads later).
 */
sealed interface CuisineValueObjectError {
    data object CuisineSlugBlank   : CuisineValueObjectError
    data object CuisineSlugTooLong : CuisineValueObjectError
}
