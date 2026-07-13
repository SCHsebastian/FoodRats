package es.schsebastian.foodrats.core.domain.meal

import kotlin.test.Test
import kotlin.test.assertEquals

class MealPlateTest {

    @Test
    fun default_source_is_camera() {
        val plate = MealPlate(photoUrl = "https://example.test/plate.jpg")
        assertEquals(PlateSource.Camera, plate.source)
    }

    @Test
    fun source_is_settable_to_gallery() {
        val plate = MealPlate(photoUrl = "https://example.test/plate.jpg", source = PlateSource.Gallery)
        assertEquals(PlateSource.Gallery, plate.source)
    }
}
