package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Locks the settings-row a11y fix: a clickable FrStructuralRow (Profile/Crew-Settings drill-down
 * rows) must expose a single merged Role.Button node, matching the FrIngredientRow/FrReportSheet/
 * FrSettingsPicker precedent, instead of reading as several unmerged children.
 */
@RunWith(AndroidJUnit4::class)
class FrStructuralRowTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun clickableRow_exposesMergedButtonRole() {
        rule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrStructuralRow(onClick = {}) {
                    FrText("Notifications")
                }
            }
        }
        val node = rule.onNodeWithText("Notifications").fetchSemanticsNode()
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun clickableRow_firesOnClick() {
        var clicks = 0
        rule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrStructuralRow(onClick = { clicks++ }) {
                    FrText("Notifications")
                }
            }
        }
        rule.onNodeWithText("Notifications").performClick()
        assertEquals(1, clicks, "onClick should fire once when the row is tapped")
    }

    @Test
    fun nonClickableRow_hasNoButtonRole() {
        rule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrStructuralRow(onClick = null) {
                    FrText("Read only")
                }
            }
        }
        val node = rule.onNodeWithText("Read only").fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsProperties.Role))
    }
}
