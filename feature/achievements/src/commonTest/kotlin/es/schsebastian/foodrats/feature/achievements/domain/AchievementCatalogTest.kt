package es.schsebastian.foodrats.feature.achievements.domain

import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementCriterion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementCatalogTest {

    @Test
    fun ships_fifteen_rows() {
        assertEquals(15, AchievementCatalog.all.size)
    }

    @Test
    fun every_id_is_unique() {
        val ids = AchievementCatalog.all.map { it.id.value }
        assertEquals(ids.size, ids.distinct().size, "duplicate AchievementId in catalog")
    }

    @Test
    fun every_title_and_description_key_is_distinct() {
        // Catches copy-paste catalog rows that reuse another row's i18n keys.
        val keys = AchievementCatalog.all.flatMap { listOf(it.titleKey, it.descriptionKey) }
        assertEquals(keys.size, keys.distinct().size, "duplicate StringKey across catalog rows")
    }

    @Test
    fun cuisineVariety_is_not_shipped() {
        // Forward-hook criterion only — must not appear in the shipped catalog (spec §9, §15).
        assertTrue(AchievementCatalog.all.none { it.criterion is AchievementCriterion.CuisineVariety })
    }
}
