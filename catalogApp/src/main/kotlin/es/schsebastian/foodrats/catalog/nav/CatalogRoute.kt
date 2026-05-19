package es.schsebastian.foodrats.catalog.nav

import kotlinx.serialization.Serializable

sealed interface CatalogRoute {
    @Serializable data object Home : CatalogRoute
    @Serializable data class Story(val entryId: String) : CatalogRoute
}
