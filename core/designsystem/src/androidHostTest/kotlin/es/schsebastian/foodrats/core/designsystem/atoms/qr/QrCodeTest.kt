package es.schsebastian.foodrats.core.designsystem.atoms.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Structural correctness of the dependency-free QR encoder. Without bundling a decoder, the strongest
 * verification is the QR spec's *invariants*: a square module count of `4·version + 17`, the three
 * finder patterns at the corners, the timing tracks, the fixed dark module, and determinism. These
 * fail loudly if the placement or version-selection logic regresses.
 */
class QrCodeTest {

    @Test
    fun module_count_matches_version_formula() {
        // A short invite URL fits in a low version; assert the size is a legal QR side length.
        val qr = QrCode.encode("https://foodrats.app/invite/AB2K9P")
        assertTrue((qr.size - 17) % 4 == 0, "size must be 4*version+17, was ${qr.size}")
        val version = (qr.size - 17) / 4
        assertTrue(version in 1..40, "version out of range: $version")
    }

    @Test
    fun finder_patterns_present_at_three_corners() {
        val qr = QrCode.encode("https://foodrats.app/invite/AB2K9P")
        // The center module of each finder (offset 3 from the corner) is dark; the ring at offset 2 is light.
        assertTrue(qr.isDark(3, 3), "top-left finder center")
        assertTrue(qr.isDark(qr.size - 4, 3), "top-right finder center")
        assertTrue(qr.isDark(3, qr.size - 4), "bottom-left finder center")
        // The finder's inner separator ring (distance 2) is light.
        assertTrue(!qr.isDark(5, 3), "top-left finder ring should be light at distance 2")
    }

    @Test
    fun timing_pattern_alternates() {
        val qr = QrCode.encode("https://foodrats.app/invite/AB2K9P")
        // Row 6 / column 6 are the timing tracks: alternating dark/light starting dark at the
        // even coordinate. Check a stretch between the finder patterns.
        for (i in 8 until qr.size - 8) {
            assertEquals(i % 2 == 0, qr.isDark(i, 6), "timing row mismatch at x=$i")
            assertEquals(i % 2 == 0, qr.isDark(6, i), "timing col mismatch at y=$i")
        }
    }

    @Test
    fun dark_module_is_set() {
        val qr = QrCode.encode("https://foodrats.app/invite/AB2K9P")
        // The mandatory dark module at (8, 4*version+9) == (8, size-8).
        assertTrue(qr.isDark(8, qr.size - 8), "mandatory dark module")
    }

    @Test
    fun encoding_is_deterministic() {
        val a = QrCode.encode("https://foodrats.app/invite/AB2K9P")
        val b = QrCode.encode("https://foodrats.app/invite/AB2K9P")
        assertEquals(a.size, b.size)
        for (y in 0 until a.size) for (x in 0 until a.size) {
            assertEquals(a.isDark(x, y), b.isDark(x, y), "mismatch at ($x,$y)")
        }
    }

    @Test
    fun version_grows_with_payload() {
        val short = QrCode.encode("https://foodrats.app/invite/AB2K9P")
        val long = QrCode.encode("https://foodrats.app/invite/AB2K9P".repeat(10))
        assertTrue(long.size > short.size, "longer payload must need an equal-or-larger version")
    }

    @Test
    fun higher_ecc_uses_more_or_equal_space() {
        val low = QrCode.encode("https://foodrats.app/invite/AB2K9P", QrEcc.LOW)
        val high = QrCode.encode("https://foodrats.app/invite/AB2K9P", QrEcc.HIGH)
        assertTrue(high.size >= low.size, "HIGH ecc cannot fit in a smaller version than LOW")
    }

    @Test
    fun overlong_payload_throws() {
        assertFailsWith<IllegalArgumentException> {
            QrCode.encode("x".repeat(5000), QrEcc.HIGH)
        }
    }
}
