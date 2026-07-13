package es.schsebastian.foodrats.feature.meal.domain.model

import es.schsebastian.foodrats.core.domain.meal.PlateSource

data class Plate(
    val photoBytes: ByteArray,
    val overlayApplied: Boolean = false,
    /**
     * How this plate photo was captured (camera vs gallery pick) — see [PlateSource]. Mandatory,
     * permanent provenance: threaded from `PhotoPickResult.Picked.source` at capture time, stamped
     * onto the published [es.schsebastian.foodrats.core.domain.meal.Meal.plateSource], and rendered
     * as a non-removable marker on the composer preview / feed tile / detail screen. Defaults to
     * [PlateSource.Camera] so every existing construction site compiles unchanged.
     */
    val source: PlateSource = PlateSource.Camera,
) {
    override fun equals(other: Any?): Boolean =
        other is Plate &&
            photoBytes.contentEquals(other.photoBytes) &&
            overlayApplied == other.overlayApplied &&
            source == other.source
    override fun hashCode(): Int = (photoBytes.contentHashCode() * 31 + overlayApplied.hashCode()) * 31 + source.hashCode()

    // Size-only: Plate rides inside MVI states/intents whose toString() is logged; never
    // render photo bytes as text (a multi-MB byte dump OOM-crashed the crew banner flow).
    override fun toString(): String = "Plate(photoBytes=${photoBytes.size}B, overlayApplied=$overlayApplied, source=$source)"
}
