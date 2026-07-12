package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Locks the bottom-dock a11y fix: each nav item's icon + label must merge into a single
 * selected/unselected tab node (WCAG 1.3.1/4.1.2), not two unmerged TalkBack stops.
 */
@RunWith(AndroidJUnit4::class)
class FrDockTest {

    @get:Rule
    val rule = createComposeRule()

    private val items = listOf(
        FrDockItem(icon = Icons.Filled.Home, label = "Feed"),
        FrDockItem(icon = Icons.Filled.Person, label = "Crew"),
    )

    @Test
    fun selectedNavItem_mergesIconAndLabel_andAnnouncesSelected() {
        rule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrDock(
                    items = items,
                    selectedIndex = 0,
                    onSelect = {},
                    onFabClick = {},
                    fabIcon = Icons.Filled.Add,
                    fabContentDescription = "Compose",
                )
            }
        }
        // One merged node carries the label text + selected=true; the icon must not surface its own
        // separate contentDescription node ("Feed") once merged into the tab.
        rule.onNodeWithText("Feed").assertIsSelected()
        rule.onNodeWithContentDescription("Feed").assertDoesNotExist()
    }

    @Test
    fun unselectedNavItem_announcesNotSelected() {
        rule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrDock(
                    items = items,
                    selectedIndex = 0,
                    onSelect = {},
                    onFabClick = {},
                    fabIcon = Icons.Filled.Add,
                    fabContentDescription = "Compose",
                )
            }
        }
        rule.onNodeWithText("Crew").assertIsNotSelected()
    }

    @Test
    fun tappingNavItem_firesOnSelect_withTappedIndex() {
        var selected = -1
        rule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrDock(
                    items = items,
                    selectedIndex = 0,
                    onSelect = { selected = it },
                    onFabClick = {},
                    fabIcon = Icons.Filled.Add,
                    fabContentDescription = "Compose",
                )
            }
        }
        rule.onNodeWithText("Crew").performClick()
        assertEquals(1, selected, "tapping the second dock item should report index 1")
    }
}
