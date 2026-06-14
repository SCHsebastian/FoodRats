package es.schsebastian.foodrats.app.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavGraph as NavGraphNode
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural coverage for the imperative navigation glue the AAA review flagged as untested
 * (`navigateTopLevel` + back-stack math), plus the `launchSingleTop` hardening on tap-driven pushes.
 *
 * It exercises a stub graph that mirrors the real shape — an OUTER NavHost whose `Main` destination
 * hosts an INNER NavHost (Feed/Stats), exactly the nested-host configuration implicated in the
 * intermittent "gray / nothing shown" Profile navigation. The destinations are stubs (plain `Text`)
 * so the test needs no Koin/Firebase graph; the functions under test (`navigateTopLevel`, the typed
 * `Route` graph, `launchSingleTop`) are the real ones.
 */
@RunWith(AndroidJUnit4::class)
class NavGraphBackStackTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var controller: NavHostController

    private fun setRootGraph() {
        rule.setContent {
            controller = rememberNavController()
            FoodRatsTheme {
                NavHost(navController = controller, startDestination = Route.Splash) {
                    composable<Route.Splash> { Text("Splash") }
                    composable<Route.SignIn> { Text("SignIn") }
                    composable<Route.CrewPicker> { Text("CrewPicker") }
                    composable<Route.Profile> { Text("Profile") }
                    composable<Route.Main> {
                        // Mirror MainScaffold: an inner NavHost nested inside the Main destination.
                        val inner = rememberNavController()
                        NavHost(navController = inner, startDestination = MainTab.Feed) {
                            composable<MainTab.Feed> { Text("Feed") }
                            composable<MainTab.Stats> { Text("Stats") }
                        }
                    }
                }
            }
        }
    }

    /** Simple-name routes of the real (non-graph) destinations on the OUTER back stack, bottom→top. */
    private fun realBackStackRoutes(): List<String> =
        controller.currentBackStack.value
            .map { it.destination }
            .filter { it !is NavGraphNode }
            .mapNotNull { it.route?.substringAfterLast('.')?.substringBefore('/') }

    @Test
    fun navigating_to_profile_renders_profile_content() {
        setRootGraph()
        rule.runOnIdle { controller.navigate(Route.Main) }
        rule.runOnIdle { controller.navigate(Route.Profile) { launchSingleTop = true } }
        rule.onNodeWithText("Profile").assertExists()
    }

    @Test
    fun double_tap_profile_does_not_stack_a_duplicate() {
        setRootGraph()
        rule.runOnIdle { controller.navigate(Route.Main) }
        // Both pushes in one frame — models a rapid double-tap on the avatar.
        rule.runOnIdle {
            controller.navigate(Route.Profile) { launchSingleTop = true }
            controller.navigate(Route.Profile) { launchSingleTop = true }
        }
        val profileEntries = rule.runOnIdle { realBackStackRoutes().count { it == "Profile" } }
        assertEquals(1, profileEntries)
    }

    @Test
    fun navigateTopLevel_from_a_deep_stack_collapses_to_a_single_entry() {
        setRootGraph()
        rule.runOnIdle { controller.navigate(Route.Main) }
        rule.runOnIdle { controller.navigate(Route.Profile) }
        // Sign-out-style reset from a deep screen: must clear everything to a single entry.
        rule.runOnIdle { controller.navigateTopLevel(Route.SignIn) }
        val routes = rule.runOnIdle { realBackStackRoutes() }
        assertEquals(listOf("SignIn"), routes)
        rule.onNodeWithText("SignIn").assertExists()
    }

    @Test
    fun navigateTopLevel_to_the_current_sole_destination_does_not_empty_the_stack() {
        setRootGraph()
        rule.runOnIdle { controller.navigateTopLevel(Route.Main) }
        // Re-issuing the same top-level target must NOT leave an empty back stack — an empty outer
        // stack renders nothing (the "nothing shown later" failure mode this guards against).
        rule.runOnIdle { controller.navigateTopLevel(Route.Main) }
        val routes = rule.runOnIdle { realBackStackRoutes() }
        assertTrue(routes.isNotEmpty(), "outer back stack must not be empty after re-navigation")
        rule.onNodeWithText("Feed").assertExists()
    }

    @Test
    fun back_from_profile_returns_to_main_and_renders_feed() {
        setRootGraph()
        rule.runOnIdle { controller.navigate(Route.Main) }
        rule.runOnIdle { controller.navigate(Route.Profile) }
        rule.runOnIdle { controller.popBackStack() }
        rule.onNodeWithText("Feed").assertExists()
    }
}
