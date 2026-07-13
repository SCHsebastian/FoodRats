package es.schsebastian.foodrats.feature.meal.domain.model

data class Plate(
    val photoBytes: ByteArray,
    val overlayApplied: Boolean = false,
) {
    override fun equals(other: Any?): Boolean = other is Plate && photoBytes.contentEquals(other.photoBytes) && overlayApplied == other.overlayApplied
    override fun hashCode(): Int = photoBytes.contentHashCode() * 31 + overlayApplied.hashCode()

    // Size-only: Plate rides inside MVI states/intents whose toString() is logged; never
    // render photo bytes as text (a multi-MB byte dump OOM-crashed the crew banner flow).
    override fun toString(): String = "Plate(photoBytes=${photoBytes.size}B, overlayApplied=$overlayApplied)"
}
