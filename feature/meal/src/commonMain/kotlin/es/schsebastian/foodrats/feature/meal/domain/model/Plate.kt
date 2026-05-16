package es.schsebastian.foodrats.feature.meal.domain.model

data class Plate(
    val photoBytes: ByteArray,
    val overlayApplied: Boolean = false,
) {
    override fun equals(other: Any?): Boolean = other is Plate && photoBytes.contentEquals(other.photoBytes) && overlayApplied == other.overlayApplied
    override fun hashCode(): Int = photoBytes.contentHashCode() * 31 + overlayApplied.hashCode()
}
