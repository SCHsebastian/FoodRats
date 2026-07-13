package es.schsebastian.foodrats.core.domain.meal

/**
 * How a [Meal]'s plate photo was captured — live camera shot or picked from the device's photo
 * library. This provenance marker is mandatory and permanent: once a plate is gallery-sourced, the
 * marker renders on the composer preview, the feed tile, and the meal detail so every crew member
 * can tell it wasn't shot live. It is never user-removable.
 *
 * The persisted key is a stable lowercase string (`"camera"`/`"gallery"`), mirroring the
 * [MealSlot.key]/[MealSlot.fromKey] idiom in this package. Legacy documents (published before this
 * marker existed) have no stored value; [fromKey] defaults a missing/unknown key to [Camera] since
 * every pre-existing meal was captured live.
 */
enum class PlateSource {
    Camera, Gallery;

    fun key(): String = name.lowercase()

    companion object {
        fun fromKey(key: String?): PlateSource = entries.firstOrNull { it.key() == key } ?: Camera
    }
}
