package es.schsebastian.foodrats.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.molecules.FrScorePicker
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FrScorePickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun picker_rendersAllTenScoreChips() {
        composeTestRule.setContent {
            FoodRatsTheme {
                FrScorePicker(value = null, onChange = {})
            }
        }
        // All 10 chips must be present.
        (1..10).forEach { i ->
            composeTestRule.onNodeWithText(i.toString()).assertExists()
        }
    }

    @Test
    fun picker_callsOnChange_withCorrectScore() {
        var selected: Int? = null
        composeTestRule.setContent {
            FoodRatsTheme {
                FrScorePicker(value = null, onChange = { selected = it })
            }
        }
        composeTestRule.onNodeWithText("8").performClick()
        assertEquals(8, selected, "onChange should be called with 8 when chip '8' is tapped")
    }

    @Test
    fun picker_callsOnChange_whenDifferentChipTapped() {
        var selected: Int? = null
        composeTestRule.setContent {
            FoodRatsTheme {
                FrScorePicker(value = 3, onChange = { selected = it })
            }
        }
        composeTestRule.onNodeWithText("5").performClick()
        assertEquals(5, selected, "onChange should be called with 5 when chip '5' is tapped")
    }

    @Test
    fun picker_updatesSelectedChip_onStateChange() {
        var pickerValue by mutableStateOf<Int?>(null)
        composeTestRule.setContent {
            FoodRatsTheme {
                FrScorePicker(value = pickerValue, onChange = { pickerValue = it })
            }
        }
        // Tap chip "3"
        composeTestRule.onNodeWithText("3").performClick()
        assertEquals(3, pickerValue)

        // Tap chip "9" — value should update
        composeTestRule.onNodeWithText("9").performClick()
        assertEquals(9, pickerValue)
    }
}
