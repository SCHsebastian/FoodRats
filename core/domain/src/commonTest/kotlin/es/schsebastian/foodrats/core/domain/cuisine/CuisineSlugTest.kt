package es.schsebastian.foodrats.core.domain.cuisine

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals

class CuisineSlugTest {
    private fun slug(raw: String): CuisineSlug = (CuisineSlug.of(raw) as Result.Ok).value

    @Test fun humanized_replaces_underscores_and_capitalizes() {
        assertEquals("Middle eastern", slug("middle_eastern").humanized())
        assertEquals("Italian", slug("italian").humanized())
    }
}
