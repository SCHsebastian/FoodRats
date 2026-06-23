package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/** Depth on the Z-axis. Zones read by transparency + shadow, never by a border. */
enum class FrTileDepth { Default, Deep, Near, Solid }

/** Tile fill. `Glass` is the default frosted tint; `Ember`/`Olive` are the loud celebration tiles. */
enum class FrTileTone { Glass, Ember, Olive }

/**
 * A floating frosted **stratum** — the core Structural building block. Border-less: it's read by a
 * translucent tint over the blurred [FrMediaFloor], a soft drop shadow, and a 1px inner top-light
 * (the glass edge-catch) — never a box outline or divider.
 *
 * Translucency is faked the KMP-safe way (a tinted fill over the already-blurred floor); there's no
 * per-tile backdrop blur. [FrTileDepth] trades opacity + shadow to recede ([FrTileDepth.Deep]) or
 * advance ([FrTileDepth.Near]). [FrTileTone.Ember]/[FrTileTone.Olive] swap the tint for a brand
 * gradient (streak / celebration only — keep them rare).
 *
 * @param onClick when non-null the tile gets a press-scale physics cue and `Role.Button` semantics.
 */
@Composable
fun FrGlassTile(
    modifier: Modifier = Modifier,
    depth: FrTileDepth = FrTileDepth.Default,
    tone: FrTileTone = FrTileTone.Glass,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Radius.lg),
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val semantic = LocalFrSemanticColors.current
    val scheme = MaterialTheme.colorScheme

    val fill: Brush = when (tone) {
        FrTileTone.Ember -> Brush.linearGradient(
            listOf(semantic.streakHot, scheme.secondary, scheme.tertiary),
        )
        FrTileTone.Olive -> Brush.linearGradient(
            listOf(scheme.primary, semantic.success),
        )
        FrTileTone.Glass -> {
            val glass = when (depth) {
                FrTileDepth.Default -> StructuralColors.tile
                FrTileDepth.Deep -> StructuralColors.tileDeep
                FrTileDepth.Near -> StructuralColors.tileNear
                FrTileDepth.Solid -> StructuralColors.tileSolid
            }
            // In light mode the tint is a TRANSLUCENT warm-white. `Modifier.shadow` over a translucent
            // fill renders a hard double-edge ("white square with a wrong fade", user report 2026-06-23)
            // — and the frosted see-through effect is imperceptible light-over-light anyway. Use an
            // OPAQUE warm-white in light so the drop shadow renders as a clean soft lift. Dark keeps the
            // translucent tint (the shadow vanishes into the dark floor there, so no artifact).
            SolidColor(if (StructuralColors.isLight) glass.copy(alpha = 1f) else glass)
        }
    }

    // Drop-shadow depth. The original radii were tuned dark-first, where a big black shadow vanishes
    // into the dark floor. On the warm-white light floor that same shadow becomes a heavy grey halo, so
    // light mode uses much shallower elevations for a soft, believable lift (with the opaque fill above).
    val elevation = if (StructuralColors.isLight) {
        when (depth) {
            FrTileDepth.Near -> 10.dp
            FrTileDepth.Deep -> 3.dp
            else -> 6.dp
        }
    } else {
        when (depth) {
            FrTileDepth.Near -> 30.dp
            FrTileDepth.Deep -> 14.dp
            else -> 22.dp
        }
    }

    // Read the theme-aware edge-light here — DrawScope lambdas are not @Composable.
    val edgeLight = StructuralColors.topLight

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && pressed) 0.98f else 1f,
        animationSpec = tween(Motion.quick, easing = Motion.Standard),
        label = "tilePress",
    )

    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.graphicsLayer { scaleX = scale; scaleY = scale } else Modifier)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(fill, shape)
            .drawWithContent {
                drawContent()
                val y = 0.5.dp.toPx()
                drawLine(
                    color = edgeLight,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
    ) {
        if (tone == FrTileTone.Glass) {
            content()
        } else {
            // Ember/Olive are saturated, dark-ish brand gradients in BOTH themes — force the dark
            // structural scheme so `foreground` content stays white (in light mode it would otherwise
            // flip to dark ink and disappear into the gradient). Mirrors the onMedia rule for photos.
            val columnScope = this
            CompositionLocalProvider(LocalStructuralColors provides structuralDarkColors()) {
                with(columnScope) { content() }
            }
        }
    }
}
