package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale

/**
 * Z-Layer 0 — the **continuous edge-to-edge media floor**: the absolute structural foundation that
 * sits behind everything and never scrolls (the content plane scrolls over it). On photo screens
 * it's the [painter] (sharp on a hero, blurred on feed/stats); on chrome-only screens it's an
 * atmospheric Iron & Ember [brush] (`StructuralColors.fieldFloor` / a `dish*` mood).
 *
 * Frosted glass is KMP-safe here: the floor itself is blurred via [Modifier.blur] (real on
 * Android 31+ / iOS-Skia; a no-op below, where the [dim] + [scrim] still carry the look). Strata
 * placed above stay translucent tints — there's no per-tile backdrop blur.
 *
 * @param painter optional photo; when null the [brush] paints the floor.
 * @param blur     how far to push the floor back behind the strata.
 * @param dim      additional black overlay (0f–1f) for on-media legibility (`.media.dim`/`brightness`).
 * @param scrim    optional legibility wash painted on top (null = none).
 */
@Composable
fun FrMediaFloor(
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    brush: Brush = StructuralColors.fieldFloor,
    blur: StructuralBlur = StructuralBlur.Soft,
    dim: Float = 0.38f,
    scrim: FrScrimStyle? = FrScrimStyle.Standard,
) {
    Box(modifier.fillMaxSize()) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    // slight over-scale so blurred edges never reveal the floor
                    .scale(1.06f)
                    .then(if (blur != StructuralBlur.None) Modifier.blur(blur.radius) else Modifier),
            )
        } else {
            Box(Modifier.matchParentSize().background(brush))
        }
        if (dim > 0f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim.coerceIn(0f, 1f))))
        }
        if (scrim != null) {
            FrScrim(style = scrim)
        }
    }
}
