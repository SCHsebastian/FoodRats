package es.schsebastian.foodrats.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import es.schsebastian.foodrats.catalog.nav.CatalogNavHost
import es.schsebastian.foodrats.catalog.theme.LocalThemeMode
import es.schsebastian.foodrats.catalog.theme.ThemeMode
import es.schsebastian.foodrats.catalog.theme.isDark
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme

@Composable
fun CatalogApp() {
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    var search by remember { mutableStateOf("") }
    val controller = rememberNavController()
    CompositionLocalProvider(LocalThemeMode provides themeMode) {
        FoodRatsTheme(darkTheme = themeMode.isDark()) {
            CatalogNavHost(
                navController = controller,
                themeMode = themeMode,
                onCycleTheme = { themeMode = themeMode.cycle() },
                search = search,
                onSearchChange = { search = it },
            )
        }
    }
}

private fun ThemeMode.cycle(): ThemeMode = when (this) {
    ThemeMode.System -> ThemeMode.Light
    ThemeMode.Light  -> ThemeMode.Dark
    ThemeMode.Dark   -> ThemeMode.System
}
