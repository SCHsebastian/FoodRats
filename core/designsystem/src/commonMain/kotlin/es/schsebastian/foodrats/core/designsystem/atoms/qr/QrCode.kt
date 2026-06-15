package es.schsebastian.foodrats.core.designsystem.atoms.qr

/**
 * Minimal, dependency-free QR Code (Model 2) encoder — pure Kotlin, so it renders identically on
 * Android and iOS. Encodes a UTF-8 byte-mode payload at the smallest version (1..40) that fits the
 * requested error-correction [QrEcc] level, applies the lowest-penalty mask, and exposes the result
 * as a square boolean [matrix] (`true` = dark module). Rendering is the caller's job — [FrQrCode]
 * paints it on a Compose Canvas.
 *
 * This is a faithful, compact implementation of the public ISO/IEC 18004 algorithm (the same one the
 * reference Nayuki/ZXing encoders implement); it is self-contained on purpose so the project takes on
 * no third-party QR dependency for a single small use (sharing a crew invite link).
 */
class QrCode internal constructor(
    /** Number of modules per side. */
    val size: Int,
    private val modules: Array<BooleanArray>,
) {
    /** `matrix[y][x]` — `true` is a dark module. */
    val matrix: Array<BooleanArray> get() = modules

    fun isDark(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        /**
         * Encodes [text] as bytes (UTF-8) into a QR Code at the given [ecc] level. Throws
         * [IllegalArgumentException] if the data is too long for the highest version (40) — for the
         * project's invite URLs this never happens.
         */
        fun encode(text: String, ecc: QrEcc = QrEcc.MEDIUM): QrCode {
            val data = text.encodeToByteArray()
            val version = smallestVersionFor(data.size, ecc)
            val bits = buildDataBits(data, version, ecc)
            val codewords = bitsToCodewords(bits, version, ecc)
            val finalCodewords = interleaveWithEcc(codewords, version, ecc)
            return drawMatrix(version, ecc, finalCodewords)
        }
    }
}

/** Error-correction level. The numeric [ordinalForMaskInfo] is the value used in the format bits. */
enum class QrEcc(val ordinalForMaskInfo: Int) {
    LOW(1),       // L — ~7%
    MEDIUM(0),    // M — ~15%
    QUARTILE(3),  // Q — ~25%
    HIGH(2),      // H — ~30%
}
