package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Which legibility wash to lay over the media floor. No color — pure black/ink, just contrast. */
enum class FrScrimStyle {
    /** `.scrim` — dark at the top (status row) and heavier at the bottom (dock), clear in the middle. */
    Standard,

    /** `.scrim.even` — a flat top-to-bottom darkening. */
    Even,

    /** `.photo-scrim` — bottom-heavy, for white text sitting on an in-tile/hero photo. */
    Photo,

    /** `.photo-scrim.even` — a softer bottom-heavy wash. */
    PhotoEven,
}

/**
 * Z-Layer 1 — a pure-legibility wash painted over the [FrMediaFloor] so frosted strata and
 * on-media type keep contrast. Carries no brand color; it's only black at varying alpha.
 * Decorative and non-interactive (no semantics, lets touches through is the caller's concern).
 */
@Composable
fun FrScrim(
    modifier: Modifier = Modifier,
    style: FrScrimStyle = FrScrimStyle.Standard,
) {
    val brush: Brush = remember(style) {
        when (style) {
            FrScrimStyle.Standard -> Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color(0x8C080907),
                    0.16f to Color(0x00080907),
                    0.74f to Color(0x00080907),
                    1.0f to Color(0xC7080907),
                ),
            )
            FrScrimStyle.Even -> Brush.verticalGradient(
                listOf(Color(0x570A0B08), Color(0x800A0B08)),
            )
            FrScrimStyle.Photo -> Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color(0x57070805),
                    0.54f to Color(0x1F070805),
                    1.0f to Color(0xE6070805),
                ),
            )
            FrScrimStyle.PhotoEven -> Brush.verticalGradient(
                listOf(Color(0x59070805), Color(0xB2070805)),
            )
        }
    }
    Box(modifier.fillMaxSize().background(brush))
}
