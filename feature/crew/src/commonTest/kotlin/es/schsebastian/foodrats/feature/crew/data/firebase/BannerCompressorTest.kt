package es.schsebastian.foodrats.feature.crew.data.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The shrink-until-fits ladder is pure logic over an injected `encode` lambda, so it is tested
 * without any platform codec. The load-bearing contract: [compressBannerForUpload] NEVER returns
 * the original bytes — an undecodable or un-shrinkable image is a typed failure, not a silent
 * oversized upload (the old best-effort compressor fell back to the originals, which sailed
 * through to a storage-rules deny that read as a generic failure).
 */
class BannerCompressorTest {

    /** Well over [BANNER_UPLOAD_BYTE_CAP] so a leaked original could never accidentally "fit". */
    private val oversizedOriginal = ByteArray(3 * 1024 * 1024) { it.toByte() }

    @Test
    fun first_encode_under_the_cap_wins_at_the_top_rung() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val fit = ByteArray(100) { 7 }

        val r = oversizedOriginal.compressBannerForUpload(byteCap = 1024) { _, dim, quality ->
            calls += dim to quality
            fit
        }

        assertEquals(BannerCompression.Fit(fit), r)
        // One attempt only, at the top of the ladder: 1280 px @ quality 80.
        assertEquals(listOf(1280 to 80), calls)
    }

    @Test
    fun ladder_drops_quality_then_dimension_until_a_rung_fits() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val fit = ByteArray(512) { 3 }

        val r = oversizedOriginal.compressBannerForUpload(byteCap = 1024) { _, dim, quality ->
            calls += dim to quality
            if (dim == 1024 && quality == 50) fit else ByteArray(2048)
        }

        assertEquals(BannerCompression.Fit(fit), r)
        // Quality exhausts within a dimension rung before the dimension drops (dimension outer,
        // quality inner), and the walk stops at the first fitting rung.
        assertEquals(
            listOf(
                1280 to 80, 1280 to 65, 1280 to 50, 1280 to 35,
                1024 to 80, 1024 to 65, 1024 to 50,
            ),
            calls,
        )
    }

    @Test
    fun null_encode_is_Unreadable_immediately() {
        var callCount = 0

        val r = oversizedOriginal.compressBannerForUpload(byteCap = 1024) { _, _, _ ->
            callCount++
            null
        }

        assertEquals(BannerCompression.Unreadable, r)
        // Fail fast: an undecodable image cannot succeed at a smaller rung either.
        assertEquals(1, callCount)
    }

    @Test
    fun exhausted_ladder_is_TooLarge() {
        var callCount = 0

        val r = oversizedOriginal.compressBannerForUpload(byteCap = 1024) { _, _, _ ->
            callCount++
            // `size < byteCap` is strict — exactly-at-cap must NOT fit (mirrors the Storage rule).
            ByteArray(1024)
        }

        assertEquals(BannerCompression.TooLarge, r)
        // 6 dimensions x 4 qualities.
        assertEquals(24, callCount)
    }

    @Test
    fun encode_returning_the_original_oversized_bytes_never_yields_Fit() {
        // The pathological old behavior: an "encoder" that hands back the untouched originals.
        // With the default cap those bytes can never fit, so the result MUST be a typed failure —
        // the originals must never reach Storage.
        val r = oversizedOriginal.compressBannerForUpload { bytes, _, _ -> bytes }

        assertEquals(BannerCompression.TooLarge, r)
    }

    @Test
    fun fit_bytes_are_the_re_encoded_candidate_not_the_original() {
        val reEncoded = ByteArray(64) { 1 }

        val r = oversizedOriginal.compressBannerForUpload { _, _, _ -> reEncoded }

        assertIs<BannerCompression.Fit>(r)
        assertTrue(r.bytes.contentEquals(reEncoded))
        assertTrue(!r.bytes.contentEquals(oversizedOriginal))
        assertTrue(r.bytes.size < BANNER_UPLOAD_BYTE_CAP)
    }
}
