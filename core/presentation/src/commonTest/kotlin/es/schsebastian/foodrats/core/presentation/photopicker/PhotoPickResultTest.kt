package es.schsebastian.foodrats.core.presentation.photopicker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PhotoPickResultTest {

    @Test
    fun pickedPhoto_equals_compares_bytes_by_content_not_reference() {
        val a = PickedPhoto(byteArrayOf(1, 2, 3), PhotoSource.Gallery)
        val b = PickedPhoto(byteArrayOf(1, 2, 3), PhotoSource.Gallery)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun pickedPhoto_equals_is_false_for_different_byte_content() {
        val a = PickedPhoto(byteArrayOf(1, 2, 3), PhotoSource.Gallery)
        val b = PickedPhoto(byteArrayOf(1, 2, 4), PhotoSource.Gallery)
        assertNotEquals(a, b)
    }

    @Test
    fun pickedPhoto_equals_is_false_for_different_source() {
        val a = PickedPhoto(byteArrayOf(1, 2, 3), PhotoSource.Gallery)
        val b = PickedPhoto(byteArrayOf(1, 2, 3), PhotoSource.Camera)
        assertNotEquals(a, b)
    }

    @Test
    fun pickedPhoto_toString_is_size_only_never_raw_bytes() {
        val photo = PickedPhoto(ByteArray(1234), PhotoSource.Gallery)
        assertEquals("PickedPhoto(bytes=1234b, source=Gallery, metadata=null)", photo.toString())
    }

    @Test
    fun pickedMultiple_is_part_of_the_photoPickResult_sealed_family() {
        val result: PhotoPickResult = PhotoPickResult.PickedMultiple(
            listOf(PickedPhoto(byteArrayOf(1), PhotoSource.Gallery)),
        )
        // Exhaustive without an `else` — locks PickedMultiple as a real sibling of
        // Picked/Cancelled/Failed, not an afterthought reachable only via `is`.
        val label = when (result) {
            is PhotoPickResult.Picked -> "picked"
            is PhotoPickResult.PickedMultiple -> "multiple"
            PhotoPickResult.Cancelled -> "cancelled"
            is PhotoPickResult.Failed -> "failed"
        }
        assertEquals("multiple", label)
    }

    @Test
    fun pickedMultiple_equality_follows_its_photos_list_content() {
        val a = PhotoPickResult.PickedMultiple(
            listOf(
                PickedPhoto(byteArrayOf(1, 2), PhotoSource.Camera),
                PickedPhoto(byteArrayOf(3, 4), PhotoSource.Gallery),
            ),
        )
        val b = PhotoPickResult.PickedMultiple(
            listOf(
                PickedPhoto(byteArrayOf(1, 2), PhotoSource.Camera),
                PickedPhoto(byteArrayOf(3, 4), PhotoSource.Gallery),
            ),
        )
        assertEquals(a, b)
    }
}
