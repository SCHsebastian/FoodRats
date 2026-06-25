package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Structural" design-language color scheme — the **only** sanctioned home for raw `Color(0x…)` in this
 * variant (mirrors `theme/Colors.kt`). Everything downstream composes against these or against the
 * existing `MaterialTheme.colorScheme` / `LocalFrSemanticColors` brand roles.
 *
 * The look is **media-forward**: a continuous edge-to-edge media floor with floating frosted strata.
 * It ships both a dark ([structuralDarkColors], the original charcoal-olive `#1B1C19` floor) and a
 * light ([structuralLightColors], a warm-concrete `#E8E6DE` floor) variant, selected per [FoodRatsTheme]
 * and read through [LocalStructuralColors] / the [StructuralColors] accessor.
 *
 * Light-mode rule (the universal media-app convention — text over photos stays white-on-scrim in BOTH
 * themes): glass chrome + atmospheric floors flip light and [foreground] flips dark; text sitting
 * directly over a real photo or a `dish*` mood uses the theme-independent [StructuralColors.onMedia]
 * (always white) over the dark dim+scrim.
 *
 * Frosted glass is faked the KMP-safe way: the **media floor** is blurred ([FrMediaFloor]) and the
 * strata are translucent tints over it — there is no real per-tile backdrop blur (no shader on the
 * common classpath; same trade-off documented on `FrGlassPill`). [StructuralBlur] radii drive the
 * floor blur and document intent. Alpha is baked into the ARGB value (e.g. `0xA3` ≈ 64%).
 */
data class StructuralColorScheme(
    /** Foreground for type/content over glass strata and atmospheric floors. */
    val foreground: Color,
    // ---- Frosted strata fills (translucent over the media floor) ----
    val tile: Color,
    val tileDeep: Color,
    val tileNear: Color,
    val tileSolid: Color,
    // ---- Other glass surfaces ----
    val glassButton: Color,
    val chip: Color,
    val dock: Color,
    val sheet: Color,
    val dialog: Color,
    // ---- Edge lights & hairlines ----
    val topLight: Color,
    val hairline: Color,
    val dividerSoft: Color,
    // ---- Atmospheric media floors (used when there's no real photo) ----
    val fieldFloor: Brush,
    val oliveFloor: Brush,
    val stageFloor: Brush,
    /** True for the light scheme. Lets media-forward primitives drop the dark dim/scrim over a light floor. */
    val isLight: Boolean,
)

/** Dark scheme — the original Structural charcoal-olive `#1B1C19` floor, white-on-media type. */
fun structuralDarkColors(): StructuralColorScheme = StructuralColorScheme(
    foreground = Color(0xFFFFFFFF),
    tile = Color(0xA31B1C17),       // `.tile` `#1b1c17` @ 64%
    tileDeep = Color(0x7515160F),   // `.tile.deep` recedes `#15160f` @ 46%
    tileNear = Color(0xCC1D1E18),   // `.tile.near` advances `#1d1e18` @ 80%
    tileSolid = Color(0xFF1C1D17),  // `.tile.solid` opaque
    glassButton = Color(0x8015160F),// `.glass-btn` `#15160f` @ 50%
    chip = Color(0x8C1B1C17),       // `.chip` `#1b1c17` @ 55%
    dock = Color(0xB815160F),       // `.dock` `#15160f` @ 72%
    sheet = Color(0xE01C1D16),      // `.sheet` `#1c1d16` @ 88%
    dialog = Color(0xEB1D1E17),     // `.dialog` `#1d1e17` @ 92%
    topLight = Color(0x1AFFFFFF),   // glass edge-catch white @ 10%
    hairline = Color(0x0DFFFFFF),   // row separation white @ 5%
    dividerSoft = Color(0x0FFFFFFF),// soft divider white @ 6%
    fieldFloor = Brush.verticalGradient(
        listOf(Color(0xFF20231B), Color(0xFF15160F), Color(0xFF0E0F0A)),
    ),
    oliveFloor = Brush.verticalGradient(
        listOf(Color(0xFF1C1F16), Color(0xFF121309), Color(0xFF0D0E08)),
    ),
    stageFloor = Brush.verticalGradient(
        listOf(Color(0xFF23241F), Color(0xFF131410), Color(0xFF0C0D0A)),
    ),
    isLight = false,
)

/**
 * Light scheme — a warm-concrete `#E8E6DE` (Iron & Ember light surface) floor with near-black
 * `#16170F` ink. Glass strata become warm-white translucent so they read as frosted-light cards over
 * the light atmospheric floors AND keep dark-text legibility when floated over a dark photo. Edge
 * lights flip to a low-alpha black so the 1px catches/hairlines remain visible on light glass.
 * Tuned for WCAG AA: full [foreground] over any surface clears ~14–18:1.
 */
fun structuralLightColors(): StructuralColorScheme = StructuralColorScheme(
    foreground = Color(0xFF16170F),
    tile = Color(0xCCFBFAF5),       // warm-white @ 80%
    tileDeep = Color(0xB0FBFAF5),   // recedes @ 69%
    tileNear = Color(0xE6FCFBF8),   // advances @ 90%
    tileSolid = Color(0xFFF4F2EA),  // opaque warm white
    glassButton = Color(0xD9FFFFFF),// floating round chrome @ 85%
    chip = Color(0xCCFBFAF5),       // frosted pill @ 80%
    dock = Color(0xF0FBFAF5),       // floating nav @ 94% (opaque enough for dark icons over content)
    sheet = Color(0xF7F8F6EF),      // bottom sheet @ 97%
    dialog = Color(0xFAF8F6EF),     // dialog @ 98%
    topLight = Color(0x14000000),   // edge-catch black @ 8%
    hairline = Color(0x12000000),   // row separation black @ 7%
    dividerSoft = Color(0x1A000000),// soft divider black @ 10%
    fieldFloor = Brush.verticalGradient(
        listOf(Color(0xFFEFEEE7), Color(0xFFE6E3DA), Color(0xFFDAD7CD)),
    ),
    oliveFloor = Brush.verticalGradient(
        listOf(Color(0xFFECEEE4), Color(0xFFE1E3D7), Color(0xFFD5D8C9)),
    ),
    stageFloor = Brush.verticalGradient(
        listOf(Color(0xFFF1F0EB), Color(0xFFE7E5DE), Color(0xFFDBD9D1)),
    ),
    isLight = true,
)

/** The active Structural scheme — provided by [FoodRatsTheme]; defaults to dark. */
val LocalStructuralColors = staticCompositionLocalOf { structuralDarkColors() }

/**
 * Accessor facade over [LocalStructuralColors] (mirrors how `MaterialTheme.colorScheme` is read).
 * Theme-aware members are `@Composable` getters; [onMedia] and the `dish*` moods are
 * theme-INDEPENDENT plain `val`s (always vivid / always white), which also keeps them callable from
 * the non-composable `dishBrushFor` helpers.
 */
object StructuralColors {
    /** White foreground for type sitting directly on a real photo / `dish*` mood (always dark-scrimmed). */
    val onMedia: Color = Color(0xFFFFFFFF)

    /** Whether the active scheme is light — read by media-forward primitives (e.g. [FrMediaFloor]). */
    val isLight: Boolean
        @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.isLight

    /** Foreground for type/content over glass strata & atmospheric floors (theme-aware). */
    val foreground: Color
        @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.foreground

    val tile: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.tile
    val tileDeep: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.tileDeep
    val tileNear: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.tileNear
    val tileSolid: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.tileSolid
    val glassButton: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.glassButton
    val chip: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.chip
    val dock: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.dock
    val sheet: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.sheet
    val dialog: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.dialog
    val topLight: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.topLight
    val hairline: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.hairline
    val dividerSoft: Color @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.dividerSoft

    val fieldFloor: Brush @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.fieldFloor
    val oliveFloor: Brush @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.oliveFloor
    val stageFloor: Brush @Composable @ReadOnlyComposable get() = LocalStructuralColors.current.stageFloor

    // ---- Dish "moods" — appetizing gradient floors when a photo URL is absent (theme-independent) ----
    val dishRamen: Brush = Brush.linearGradient(listOf(Color(0xFFC97E1A), Color(0xFF7A3826), Color(0xFF3A160B)))
    val dishMackerel: Brush = Brush.linearGradient(listOf(Color(0xFF5C7A33), Color(0xFF34461F), Color(0xFF15160F)))
    val dishSalad: Brush = Brush.linearGradient(listOf(Color(0xFF6F8A3E), Color(0xFF3A4D22), Color(0xFF14160E)))
    val dishTacos: Brush = Brush.linearGradient(listOf(Color(0xFFC9772F), Color(0xFF7A3826), Color(0xFF2C1206)))
}

/**
 * Blur radii for the media floor (`structural.css` `.media.blur*`). Real backdrop blur is applied to
 * the **floor only** — strata are translucent tints, not blurred. `None` keeps the floor sharp
 * (meal-detail hero); `Soft`/`Heavy` push it back behind feed/stats strata.
 */
enum class StructuralBlur(val radius: Dp) {
    None(0.dp),
    Soft(34.dp),
    Heavy(56.dp),
}

/** Depth/blur radii documented for strata (intent only — no per-tile backdrop blur on KMP). */
object StructuralDepth {
    val tile: Dp = 26.dp
    val tileDeep: Dp = 40.dp
    val dock: Dp = 30.dp
}
