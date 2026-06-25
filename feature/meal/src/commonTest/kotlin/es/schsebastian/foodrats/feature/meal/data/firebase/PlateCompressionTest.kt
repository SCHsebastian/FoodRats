package es.schsebastian.foodrats.feature.meal.data.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the PURE compression scaling math ([PlateCompression]). The actual bitmap
 * re-encode is platform IO (Android `Bitmap`, iOS `UIImage`) and is verified on-device (see the
 * report's manual checklist); here we lock the size policy.
 */
class PlateCompressionTest {

    @Test fun caps_longest_edge_preserving_aspect_ratio_landscape() {
        // 4000x3000 (4:3) → longest edge 4000 capped to 2048; height scales to 1536.
        val s = PlateCompression.scaledSize(4000, 3000)
        assertEquals(2048, s.width)
        assertEquals(1536, s.height)
    }

    @Test fun caps_longest_edge_preserving_aspect_ratio_portrait() {
        // 3000x4000 (3:4) → height 4000 capped to 2048; width scales to 1536.
        val s = PlateCompression.scaledSize(3000, 4000)
        assertEquals(2048, s.height)
        assertEquals(1536, s.width)
    }

    @Test fun does_not_upscale_small_images() {
        val s = PlateCompression.scaledSize(800, 600)
        assertEquals(800, s.width)
        assertEquals(600, s.height)
    }

    @Test fun image_exactly_at_cap_is_unchanged() {
        // 2048x1152 (16:9) sits exactly at the cap, so it passes through unchanged.
        val s = PlateCompression.scaledSize(2048, 1152)
        assertEquals(2048, s.width)
        assertEquals(1152, s.height)
    }

    @Test fun square_image_caps_both_edges() {
        val s = PlateCompression.scaledSize(2400, 2400)
        assertEquals(2048, s.width)
        assertEquals(2048, s.height)
    }

    @Test fun honors_explicit_max_edge() {
        val s = PlateCompression.scaledSize(2000, 1000, maxEdge = 1000)
        assertEquals(1000, s.width)
        assertEquals(500, s.height)
    }

    @Test fun non_positive_inputs_are_returned_unchanged() {
        assertEquals(PlateCompression.Size(0, 0), PlateCompression.scaledSize(0, 0))
        assertEquals(PlateCompression.Size(-5, 10), PlateCompression.scaledSize(-5, 10))
    }

    @Test fun defaults_follow_roadmap_5_1() {
        assertTrue(PlateCompression.MAX_EDGE_PX in 1440..2048)
        assertTrue(PlateCompression.JPEG_QUALITY in 80..90)
    }
}
