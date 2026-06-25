package es.schsebastian.foodrats.core.designsystem.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Horizontal safe-area padding for the edge-to-edge "structural" screens.
 *
 * The structural screens let their [es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor]
 * bleed full-bleed under every system inset (that's correct — the media should run edge to edge,
 * including *under* the camera cutout). Only the **content/chrome layer** drawn on top must clear the
 * cutout. In portrait the camera notch sits at the top and is covered by `statusBarsPadding()`; in
 * **landscape** it moves to a side, and a fixed `padding(horizontal = …)` gutter does NOT account for
 * it, so text/buttons slide under the camera. This applies the horizontal portion of
 * [WindowInsets.safeDrawing] (display cutout + any side system bar) so content always clears the
 * camera and a side gesture/3-button nav bar in landscape.
 *
 * Apply it on the same node that owns the screen's `padding(horizontal = …)` gutter, *before* the
 * gutter, so the cutout is inset first and the fixed gutter is added inside the safe area:
 * `Modifier.fillMaxSize().frSafeHorizontalPadding().padding(horizontal = Spacing.lg)`.
 *
 * Pairs with the screen's existing top (`statusBarsPadding()`) and bottom (dock / nav-bar clearance)
 * handling — it intentionally touches only the horizontal axis so it never double-pads the top or
 * fights the IME / manual dock clearance at the bottom.
 */
@Composable
fun Modifier.frSafeHorizontalPadding(): Modifier =
    this.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))

/**
 * Horizontal + bottom safe-area padding for floating bottom chrome (the [FrDock], a bottom action
 * bar). Keeps the chrome above the bottom navigation bar *and* clear of a side camera cutout / side
 * nav bar in landscape — without pulling in the IME inset (which would make the dock jump when a
 * keyboard opens elsewhere). Uses navigation-bar + display-cutout insets only.
 */
@Composable
fun Modifier.frSafeBottomBarPadding(): Modifier =
    this.windowInsetsPadding(
        WindowInsets.navigationBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    )
