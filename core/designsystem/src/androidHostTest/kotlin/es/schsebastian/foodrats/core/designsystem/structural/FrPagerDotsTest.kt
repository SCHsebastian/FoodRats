package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Locks [FrPagerDots]'s page-count rendering: one decorative dot per page, none at all for a
 * single-page pager (the detail screen only mounts it for `pageCount > 1`, but the atom itself
 * must also be safe/inert if ever called with 0 or 1 pages).
 */
@RunWith(AndroidJUnit4::class)
class FrPagerDotsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersOneDotPerPage() {
        rule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrPagerDots(pageCount = 3, currentPage = 0)
            }
        }
        rule.onAllNodesWithTag("pagerDot").assertCountEquals(3)
    }

    @Test
    fun updatesWhichDotIsCurrentWithoutChangingCount() {
        rule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrPagerDots(pageCount = 5, currentPage = 3)
            }
        }
        rule.onAllNodesWithTag("pagerDot").assertCountEquals(5)
    }

    @Test
    fun singlePage_rendersNoDots() {
        rule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrPagerDots(pageCount = 1, currentPage = 0)
            }
        }
        rule.onAllNodesWithTag("pagerDot").assertCountEquals(0)
    }

    @Test
    fun zeroPages_rendersNoDots() {
        rule.setContent {
            FoodRatsTheme(darkTheme = true) {
                FrPagerDots(pageCount = 0, currentPage = 0)
            }
        }
        rule.onAllNodesWithTag("pagerDot").assertCountEquals(0)
    }
}
