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
 * How the floor behaves across themes.
 *  - [Adaptive] — an atmospheric brush floor whose content uses the theme-aware `foreground`. It is
 *    dark in dark mode (so it keeps the dark dim/scrim for white type) and **light in light mode**, where
 *    the dark dim/scrim are DROPPED so the light floor shows and dark type reads.
 *  - [OnMedia] — a real photo ([painter]) or a `dish*` mood: dark-scrimmed in BOTH themes, with white
 *    `StructuralColors.onMedia` content over it. Always keeps the dim/scrim.
 */
enum class FrFloorTone { Adaptive, OnMedia }

/**
 * Z-Layer 0 — the **continuous edge-to-edge media floor**: the absolute structural foundation that
 * sits behind everything and never scrolls (the content plane scrolls over it). On photo screens
 * it's the [painter] (sharp on a hero, blurred on feed/stats); on chrome-only screens it's an
 * atmospheric Iron & Ember [brush] (`StructuralColors.fieldFloor` / a `dish*` mood).
 *
 * Frosted glass is KMP-safe here: the floor itself is blurred via [Modifier.blur] (real on
 * Android 31+ / iOS-Skia). Below API 31 [Modifier.blur] is a **no-op** (it's RenderEffect-backed),
 * so a requested blur silently leaves a SHARP, bright photo behind white [StructuralColors.foreground]
 * content — illegible. On those targets [structuralBlurSupported] is `false`, we skip the (useless)
 * blur entirely and raise the effective [dim] to [UNSUPPORTED_BLUR_DIM_FLOOR] so legibility no longer
 * depends on a blur that never ran. Strata placed above stay translucent tints — there's no per-tile
 * backdrop blur.
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
    tone: FrFloorTone = FrFloorTone.Adaptive,
) {
    // In light mode an Adaptive (atmospheric, no-photo) floor is LIGHT and its content is dark, so the
    // dark dim + scrim (which exist for white-on-photo legibility) would only muddy it — drop them.
    // Photos and dish moods ([OnMedia], or any [painter]) stay dark-scrimmed in both themes.
    val keepDarkWash = painter != null || tone == FrFloorTone.OnMedia || !StructuralColors.isLight
    // A blur was asked for but this platform's Modifier.blur is a no-op (Android < 31): the floor
    // would render sharp. Drop the dead blur and floor the dim so white content stays legible.
    val blurUnsupported = blur != StructuralBlur.None && !structuralBlurSupported()
    val applyBlur = blur != StructuralBlur.None && !blurUnsupported
    val effectiveDim = when {
        !keepDarkWash -> 0f
        blurUnsupported -> maxOf(dim, UNSUPPORTED_BLUR_DIM_FLOOR)
        else -> dim
    }
    val effectiveScrim = if (keepDarkWash) scrim else null
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
                    .then(if (applyBlur) Modifier.blur(blur.radius) else Modifier),
            )
        } else {
            Box(Modifier.matchParentSize().background(brush))
        }
        if (effectiveDim > 0f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = effectiveDim.coerceIn(0f, 1f))))
        }
        if (effectiveScrim != null) {
            FrScrim(style = effectiveScrim)
        }
    }
}

/**
 * Dim floor applied when a blur was requested but the platform can't render it (Android < 31, where
 * [Modifier.blur] is a no-op). Tuned so white [StructuralColors.foreground] content clears WCAG AA
 * over a sharp, bright photo without the missing frosted-glass softening.
 */
private const val UNSUPPORTED_BLUR_DIM_FLOOR = 0.6f

/**
 * Whether [Modifier.blur] actually renders on this platform. Android backs it with `RenderEffect`,
 * which is a **no-op below API 31** — so with `minSdk = 30` a requested floor blur silently does
 * nothing on Android 11. iOS (Compose-on-Skia) always supports it. [FrMediaFloor] uses this to fall
 * back to a darker [dim] instead of relying on a blur that never ran.
 */
internal expect fun structuralBlurSupported(): Boolean
