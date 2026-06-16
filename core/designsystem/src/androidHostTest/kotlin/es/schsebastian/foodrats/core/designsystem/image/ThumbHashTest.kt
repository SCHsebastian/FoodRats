package es.schsebastian.foodrats.core.designsystem.image

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

// AndroidJUnit4 + Robolectric so the expect/actual ImageBitmap step (android.graphics.Bitmap) runs.
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalEncodingApi::class)
class ThumbHashTest {

    // Canonical ThumbHash from the reference project (evanw/thumbhash) — the sunset sample.
    private val sunsetBase64 = "1QcSHQRnh493V4dIh4eXh1h4kJUI"

    @Test
    fun decodes_reference_hash_to_a_small_rgba_image() {
        val bytes = Base64.decode(sunsetBase64)
        val decoded = ThumbHash.decode(bytes)
        assertNotNull(decoded, "reference hash should decode")
        // ThumbHash reconstructs into at most a 32px box, preserving aspect ratio.
        assertTrue(decoded.width in 1..32, "width=${decoded.width}")
        assertTrue(decoded.height in 1..32, "height=${decoded.height}")
        // RGBA: 4 bytes per pixel, exactly width*height pixels.
        assertEquals(decoded.width * decoded.height * 4, decoded.rgba.size)
    }

    @Test
    fun approximate_aspect_ratio_is_positive_and_finite() {
        val bytes = Base64.decode(sunsetBase64)
        val ratio = ThumbHash.approximateAspectRatio(bytes)
        assertNotNull(ratio)
        assertTrue(ratio > 0f && ratio.isFinite(), "ratio=$ratio")
    }

    @Test
    fun decoded_dimensions_match_aspect_ratio_orientation() {
        val bytes = Base64.decode(sunsetBase64)
        val ratio = ThumbHash.approximateAspectRatio(bytes)!!
        val decoded = ThumbHash.decode(bytes)!!
        // Landscape hashes decode wider than tall (and vice versa); square stays square-ish.
        if (ratio > 1f) assertTrue(decoded.width >= decoded.height)
        if (ratio < 1f) assertTrue(decoded.height >= decoded.width)
    }

    @Test
    fun too_short_hash_returns_null_not_crash() {
        assertNull(ThumbHash.decode(byteArrayOf(0, 1)))
        assertNull(ThumbHash.decode(ByteArray(0)))
        assertNull(ThumbHash.approximateAspectRatio(byteArrayOf(1)))
    }

    @Test
    fun decode_helper_handles_blank_and_garbage_base64() {
        assertNull(decodeThumbHash(null))
        assertNull(decodeThumbHash(""))
        assertNull(decodeThumbHash("   "))
        // Not valid base64 → caught, returns null (never throws into the feed).
        assertNull(decodeThumbHash("!!!not-base64!!!"))
    }

    @Test
    fun decode_helper_produces_a_bitmap_for_a_valid_hash() {
        val bitmap = decodeThumbHash(sunsetBase64)
        assertNotNull(bitmap, "valid hash should yield an ImageBitmap")
        assertTrue(bitmap.width in 1..32)
        assertTrue(bitmap.height in 1..32)
    }
}
