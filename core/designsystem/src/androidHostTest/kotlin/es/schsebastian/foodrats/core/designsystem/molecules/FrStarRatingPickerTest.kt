package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FrStarRatingPickerTest {
    @get:Rule val rule = createComposeRule()

    @Test fun clicking_a_star_emits_its_value() {
        var clicked: Int? = null
        rule.setContent {
            FrStarRatingPicker(onSelect = { clicked = it })
        }
        rule.onNodeWithContentDescription("4").performClick()
        assertEquals(4, clicked)
    }

    @Test fun emits_lowest_and_highest() {
        var clicked: Int? = null
        rule.setContent { FrStarRatingPicker(onSelect = { clicked = it }) }
        rule.onNodeWithContentDescription("1").performClick()
        assertEquals(1, clicked)
        rule.onNodeWithContentDescription("5").performClick()
        assertEquals(5, clicked)
    }
}
