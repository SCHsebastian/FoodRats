package es.schsebastian.foodrats.feature.meal.domain.model

import es.schsebastian.foodrats.core.domain.meal.PlateSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlateTest {

    @Test fun default_source_is_camera() {
        val plate = Plate(byteArrayOf(1, 2, 3))
        assertEquals(PlateSource.Camera, plate.source)
    }

    @Test fun equals_and_hashCode_include_source_not_just_bytes_and_overlay() {
        val camera = Plate(byteArrayOf(1, 2, 3), overlayApplied = true, source = PlateSource.Camera)
        val gallery = Plate(byteArrayOf(1, 2, 3), overlayApplied = true, source = PlateSource.Gallery)

        assertFalse(camera == gallery, "plates differing only by source must not be equal")
        assertFalse(camera.hashCode() == gallery.hashCode(), "hashCode must vary with source too")
    }

    @Test fun equals_is_true_for_identical_bytes_overlay_and_source() {
        val a = Plate(byteArrayOf(1, 2, 3), overlayApplied = false, source = PlateSource.Gallery)
        val b = Plate(byteArrayOf(1, 2, 3), overlayApplied = false, source = PlateSource.Gallery)

        assertTrue(a == b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test fun toString_never_dumps_raw_bytes_but_reports_size_and_source() {
        // Byte-carrier convention (94e4368): toString must stay size-only for the photo payload —
        // a multi-MB byte dump OOM-crashed the crew banner flow previously.
        val plate = Plate(byteArrayOf(1, 2, 3, 4, 5), source = PlateSource.Gallery)

        val text = plate.toString()

        assertTrue(text.contains("5B"), "must report byte COUNT, not the bytes themselves: $text")
        assertTrue(text.contains("Gallery"), "must surface the provenance: $text")
        assertFalse(text.contains("[B@"), "must never leak a raw ByteArray toString(): $text")
    }
}
