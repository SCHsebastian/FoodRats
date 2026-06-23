package es.schsebastian.foodrats.feature.meal.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class RatingEntryDto(
    val score: Int = 0,
    val atMs: Long = 0L,
    // `true` once the rater has used their single allowed vote change. Default `false` so a
    // first vote (and any pre-edit-feature doc, where the field is absent) reads back as
    // not-yet-edited. The rate transaction sets this to `true` only on the one re-rate it permits.
    val edited: Boolean = false,
)
