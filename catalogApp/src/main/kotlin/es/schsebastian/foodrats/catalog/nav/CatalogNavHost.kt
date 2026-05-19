package es.schsebastian.foodrats.catalog.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import es.schsebastian.foodrats.catalog.registry.CatalogRegistry
import es.schsebastian.foodrats.catalog.screens.CatalogDetailScreen
import es.schsebastian.foodrats.catalog.screens.CatalogHomeScreen
import es.schsebastian.foodrats.catalog.theme.ThemeMode

@Composable
fun CatalogNavHost(
    navController: NavHostController,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
) {
    NavHost(navController = navController, startDestination = CatalogRoute.Home) {
        composable<CatalogRoute.Home> {
            CatalogHomeScreen(
                themeMode = themeMode,
                onCycleTheme = onCycleTheme,
                search = search,
                onSearchChange = onSearchChange,
                onEntryClick = { entry ->
                    navController.navigate(CatalogRoute.Story(entry.id))
                },
            )
        }
        composable<CatalogRoute.Story> { backStackEntry ->
            val story = backStackEntry.toRoute<CatalogRoute.Story>()
            val entry = CatalogRegistry.byId(story.entryId)
            CatalogDetailScreen(
                entry = entry,
                themeMode = themeMode,
                onCycleTheme = onCycleTheme,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
