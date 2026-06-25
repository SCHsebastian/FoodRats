package es.schsebastian.foodrats.core.designsystem.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints

/**
 * Caps a screen's content to a comfortable reading width and centers it horizontally.
 *
 * On a compact phone (parent narrower than [max]) this is effectively a `fillMaxWidth()`
 * — content fills the available space. On a wider surface (tablet, landscape, foldable)
 * the content stops growing at [max] and sits centered, so cards/forms/feeds never
 * stretch edge-to-edge into unreadable line lengths.
 *
 * Apply to the outermost content node of a screen body — the `LazyColumn`, scrolling
 * `Column`, or form. It is purely visual (no recomposition cost) and platform-agnostic.
 *
 * The recipe — `fillMaxWidth → wrapContentWidth(Center) → widthIn(max)` — is the standard
 * Compose idiom for a centered, capped container: `fillMaxWidth` claims the parent width,
 * `wrapContentWidth` relaxes the min constraint and centers, `widthIn` enforces the cap.
 *
 * @param max the maximum content width. Defaults to [Breakpoints.contentMax]; pass
 *   [Breakpoints.formMax] for focused forms.
 */
fun Modifier.frContentWidth(max: Dp = Breakpoints.contentMax): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = max)
