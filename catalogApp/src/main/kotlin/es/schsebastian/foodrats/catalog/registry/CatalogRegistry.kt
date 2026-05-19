package es.schsebastian.foodrats.catalog.registry

import es.schsebastian.foodrats.catalog.stories.atomStories
import es.schsebastian.foodrats.catalog.stories.foundationStories
import es.schsebastian.foodrats.catalog.stories.moleculeStories
import es.schsebastian.foodrats.catalog.stories.templateStories

/**
 * Single source of truth for every catalog entry. Add a new component? Add an entry here.
 *
 * The convention is documented in CLAUDE.md under "Adding a new design-system component".
 * Until enforced by a Konsist rule, keep this file in lockstep with `core/designsystem`:
 *  - one entry per public `Fr*` composable
 *  - one entry per design-system foundation (colors, typography, spacing tokens, …)
 *  - templates rendered inside the catalog at realistic sizes
 */
object CatalogRegistry {
    val entries: List<CatalogEntry> = buildList {
        addAll(foundationStories())
        addAll(atomStories())
        addAll(moleculeStories())
        addAll(templateStories())
    }

    fun byId(id: String): CatalogEntry? = entries.firstOrNull { it.id == id }

    fun search(query: String): List<CatalogEntry> {
        if (query.isBlank()) return entries
        val q = query.trim().lowercase()
        return entries.filter {
            it.title.lowercase().contains(q) ||
                (it.subtitle?.lowercase()?.contains(q) == true) ||
                it.group.label.lowercase().contains(q)
        }
    }
}
