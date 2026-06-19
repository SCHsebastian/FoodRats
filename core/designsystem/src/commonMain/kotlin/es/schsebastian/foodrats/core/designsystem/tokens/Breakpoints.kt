package es.schsebastian.foodrats.core.designsystem.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive layout breakpoints + content-width caps.
 *
 * FoodRats is phone-first, but tablets, landscape, and foldables all hand a screen
 * far more width than a single column of cards/forms should consume. Rather than a
 * full adaptive-pane rework, the rule is: fill the width on a compact phone, and on
 * wider surfaces cap the content to a comfortable reading column and center it.
 *
 * Breakpoint values follow the Material window-size-class thresholds so they line up
 * with platform guidance (compact < 600dp, medium 600–840dp, expanded ≥ 840dp).
 */
object Breakpoints {
    /** Upper bound of a compact (phone-portrait) width window. */
    val compactMax: Dp = 600.dp

    /** Upper bound of a medium (large-phone-landscape / small-tablet) width window. */
    val mediumMax: Dp = 840.dp

    /** Comfortable max width for a single content column: feeds, lists, settings, detail. */
    val contentMax: Dp = 640.dp

    /** Tighter cap for focused forms (sign-in, crew create/join, compose, permission prompts). */
    val formMax: Dp = 480.dp
}
