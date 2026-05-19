package es.schsebastian.foodrats.catalog.registry

import androidx.compose.runtime.Composable

enum class CatalogGroup(val label: String, val order: Int) {
    FOUNDATIONS("Foundations", 0),
    ATOMS("Atoms", 1),
    MOLECULES("Molecules", 2),
    TEMPLATES("Templates", 3),
}

data class CatalogEntry(
    val id: String,
    val group: CatalogGroup,
    val title: String,
    val subtitle: String? = null,
    val content: @Composable () -> Unit,
)
