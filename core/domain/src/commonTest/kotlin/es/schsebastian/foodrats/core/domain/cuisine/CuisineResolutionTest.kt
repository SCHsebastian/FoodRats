package es.schsebastian.foodrats.core.domain.cuisine

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals

class CuisineResolutionTest {
    private fun slug(raw: String): CuisineSlug = (CuisineSlug.of(raw) as Result.Ok).value

    @Test fun humanized_replaces_underscores_and_capitalizes() {
        assertEquals("Middle eastern", slug("middle_eastern").humanized())
        assertEquals("Italian", slug("italian").humanized())
    }

    @Test fun resolver_uses_catalog_display_name_when_present() {
        val italian = slug("italian")
        val catalog = mapOf(italian to Cuisine(italian, "Italiana", "italian"))
        val resolve = cuisineNameResolver(catalog)
        assertEquals("Italiana", resolve(italian))
    }

    @Test fun resolver_falls_back_to_humanized_for_unknown_slug() {
        val resolve = cuisineNameResolver(emptyMap())
        assertEquals("Middle eastern", resolve(slug("middle_eastern")))
    }
}
