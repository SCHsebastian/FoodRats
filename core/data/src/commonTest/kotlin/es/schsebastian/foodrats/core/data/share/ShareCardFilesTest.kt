package es.schsebastian.foodrats.core.data.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Host-testable pure parts of the share-card platform layer (spec §12). The real rasterization +
 * share sheet are platform-only and verified on device; these lock the file-naming / FileProvider
 * authority / MIME logic that the platform launchers build their content:// URIs from.
 */
class ShareCardFilesTest {

    @Test
    fun fileProvider_authority_is_appId_dot_fileprovider() {
        assertEquals(
            "es.schsebastian.foodrats.fileprovider",
            ShareCardFiles.fileProviderAuthority("es.schsebastian.foodrats"),
        )
    }

    @Test
    fun file_name_is_a_stable_png() {
        val name = ShareCardFiles.fileName()
        assertTrue(name.endsWith(".png"), "expected a .png suffix, got $name")
        // Stable name so the cache never grows unbounded — each share overwrites the previous PNG.
        assertEquals(ShareCardFiles.fileName(), name)
    }

    @Test
    fun png_mime_and_cache_subdir_are_the_documented_constants() {
        assertEquals("image/png", ShareCardFiles.PNG_MIME)
        assertEquals("share_cards", ShareCardFiles.CACHE_SUBDIR)
    }
}

/**
 * Locks the [StoryShareOutcome] taxonomy: three states, never a 4th, in the documented order the UI
 * toasts depend on (spec §6.3).
 */
class StoryShareOutcomeTest {

    @Test
    fun outcome_has_exactly_the_three_documented_states() {
        assertEquals(
            listOf("OpenedInstagram", "OpenedFallbackSheet", "Failed"),
            StoryShareOutcome.entries.map { it.name },
        )
    }
}
