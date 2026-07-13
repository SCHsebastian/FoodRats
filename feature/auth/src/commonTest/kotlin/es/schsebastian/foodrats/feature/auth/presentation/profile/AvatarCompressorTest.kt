package es.schsebastian.foodrats.feature.auth.presentation.profile

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Ladder + contract tests for [compressAvatarForUpload], driven through the injectable `encode`
 * lambda (the platform codec is irrelevant to the walk logic). The load-bearing guarantee: the
 * ORIGINAL picked bytes can never reach the upload path — the old contract's "return `this` on
 * failure" is exactly the bug that tripped the 1 MB avatars/{accountId} Storage cap.
 */
class AvatarCompressorTest {

    private val original = ByteArray(5 * 1024 * 1024) { it.toByte() }

    @Test fun first_rung_that_fits_wins_without_walking_further() {
        val encoded = byteArrayOf(1, 2, 3)
        val attempts = mutableListOf<Pair<Int, Int>>()

        val result = original.compressAvatarForUpload { _, maxDimension, quality ->
            attempts += maxDimension to quality
            encoded
        }

        val fit = assertIs<AvatarCompression.Fit>(result)
        assertContentEquals(encoded, fit.bytes)
        assertEquals(listOf(256 to 80), attempts)
    }

    @Test fun ladder_walks_quality_inner_then_dimension_outer() {
        val attempts = mutableListOf<Pair<Int, Int>>()
        // Nothing fits until the 6th rung — the 2nd quality of the 2nd dimension.
        val fitAt = 192 to 65

        val result = original.compressAvatarForUpload { _, maxDimension, quality ->
            attempts += maxDimension to quality
            if (maxDimension to quality == fitAt) byteArrayOf(9) else ByteArray(AVATAR_UPLOAD_BYTE_CAP)
        }

        assertIs<AvatarCompression.Fit>(result)
        assertEquals(
            listOf(256 to 80, 256 to 65, 256 to 50, 256 to 35, 192 to 80, 192 to 65),
            attempts,
        )
    }

    @Test fun encode_failure_is_unreadable_not_the_original_bytes() {
        val result = original.compressAvatarForUpload { _, _, _ -> null }
        assertEquals(AvatarCompression.Unreadable, result)
    }

    @Test fun exhausted_ladder_is_too_large_not_the_original_bytes() {
        val attempts = mutableListOf<Pair<Int, Int>>()

        // Every rung lands exactly AT the cap — the rule is strict `<`, so nothing fits.
        val result = original.compressAvatarForUpload { _, maxDimension, quality ->
            attempts += maxDimension to quality
            ByteArray(AVATAR_UPLOAD_BYTE_CAP)
        }

        assertEquals(AvatarCompression.TooLarge, result)
        // All 4 dimensions x 4 qualities were tried before giving up.
        assertEquals(16, attempts.size)
        assertEquals(96 to 35, attempts.last())
    }

    @Test fun fit_returns_the_encoded_bytes_never_the_receiver() {
        val encoded = ByteArray(10) { 42 }
        val result = original.compressAvatarForUpload { _, _, _ -> encoded }

        val fit = assertIs<AvatarCompression.Fit>(result)
        assertTrue(fit.bytes === encoded, "Fit must carry the encoder's output")
        assertTrue(fit.bytes !== original, "Fit must never pass the original picked bytes through")
    }

    @Test fun byte_cap_boundary_is_strictly_below_the_storage_rule() {
        // One byte under the cap fits; at the cap does not (the rule is `size < 1 MB`).
        val justUnder = original.compressAvatarForUpload { _, _, _ -> ByteArray(AVATAR_UPLOAD_BYTE_CAP - 1) }
        assertIs<AvatarCompression.Fit>(justUnder)

        val atCap = original.compressAvatarForUpload { _, _, _ -> ByteArray(AVATAR_UPLOAD_BYTE_CAP) }
        assertEquals(AvatarCompression.TooLarge, atCap)
    }
}
