package es.schsebastian.foodrats.core.domain.meal

import kotlin.test.Test
import kotlin.test.assertEquals

class PlateSourceTest {

    @Test
    fun keys_are_stable_lowercase_strings() {
        assertEquals("camera", PlateSource.Camera.key())
        assertEquals("gallery", PlateSource.Gallery.key())
    }

    @Test
    fun fromKey_round_trips_known_keys() {
        PlateSource.entries.forEach { source ->
            assertEquals(source, PlateSource.fromKey(source.key()))
        }
    }

    @Test
    fun fromKey_defaults_to_camera_for_unknown_or_missing_key() {
        assertEquals(PlateSource.Camera, PlateSource.fromKey("polaroid"))
        assertEquals(PlateSource.Camera, PlateSource.fromKey(""))
        assertEquals(PlateSource.Camera, PlateSource.fromKey(null))
    }
}
