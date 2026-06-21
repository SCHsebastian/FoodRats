package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Structural" design-language tokens — the **only** sanctioned home for raw `Color(0x…)` in this
 * variant (mirrors `theme/Colors.kt`). Everything downstream composes against these or against the
 * existing `MaterialTheme.colorScheme` / `LocalFrSemanticColors` brand roles.
 *
 * The Structural look is **dark-first** (charcoal-olive `#1B1C19` floor, white-on-media type, frosted
 * translucent strata). These values are the Compose port of `structural.css`'s glass fills, scrims,
 * dish "moods" and atmospheric floors. Alpha is baked into the ARGB value (e.g. `0xA3` ≈ 64%).
 *
 * Frosted glass is faked the KMP-safe way: the **media floor** is blurred ([FrMediaFloor]) and the
 * strata are translucent tints over it — there is no real per-tile backdrop blur (no shader on the
 * common classpath; same trade-off documented on `FrGlassPill`). [StructuralBlur] radii drive the
 * floor blur and document intent.
 */
object StructuralColors {
    /** White foreground for on-media type and glass content. */
    val foreground = Color(0xFFFFFFFF)

    // ---- Frosted strata fills (translucent over the blurred media floor) --------------------------
    /** `.tile` — default stratum, `#1b1c17` @ 64%. */
    val tile = Color(0xA31B1C17)

    /** `.tile.deep` — recedes (more blur, lower opacity), `#15160f` @ 46%. */
    val tileDeep = Color(0x7515160F)

    /** `.tile.near` — advances (more opaque, bigger shadow), `#1d1e18` @ 80%. */
    val tileNear = Color(0xCC1D1E18)

    /** `.tile.solid` — opaque, no translucency. */
    val tileSolid = Color(0xFF1C1D17)

    // ---- Other glass surfaces ---------------------------------------------------------------------
    /** `.glass-btn` — floating round chrome over media, `#15160f` @ 50%. */
    val glassButton = Color(0x8015160F)

    /** `.chip` — frosted pill, `#1b1c17` @ 55%. */
    val chip = Color(0x8C1B1C17)

    /** `.dock` — floating nav bar, `#15160f` @ 72%. */
    val dock = Color(0xB815160F)

    /** `.sheet` — bottom sheet, `#1c1d16` @ 88%. */
    val sheet = Color(0xE01C1D16)

    /** `.dialog` — centered dialog, `#1d1e17` @ 92%. */
    val dialog = Color(0xEB1D1E17)

    // ---- Edge lights & hairlines ------------------------------------------------------------------
    /** Glass edge-catch: 1px inner top-light, white @ 10%. Never a box outline. */
    val topLight = Color(0x1AFFFFFF)

    /** Row separation: a 1px *light*, white @ 5% — never a solid divider. */
    val hairline = Color(0x0DFFFFFF)

    /** Soft inline divider, white @ 6%. */
    val dividerSoft = Color(0x0FFFFFFFF)

    // ---- Atmospheric media floors (used when there's no real photo) -------------------------------
    /** `.media.field` — warm Iron & Ember still-life floor for chrome-only screens (sign-in, settings). */
    val fieldFloor: Brush = Brush.verticalGradient(
        listOf(Color(0xFF20231B), Color(0xFF15160F), Color(0xFF0E0F0A)),
    )

    /** `.media.field.olive` — cooler olive variant. */
    val oliveFloor: Brush = Brush.verticalGradient(
        listOf(Color(0xFF1C1F16), Color(0xFF121309), Color(0xFF0D0E08)),
    )

    /** `.stage` — neutral gallery backdrop behind a device shell (catalog / standalone). */
    val stageFloor: Brush = Brush.verticalGradient(
        listOf(Color(0xFF23241F), Color(0xFF131410), Color(0xFF0C0D0A)),
    )

    // ---- Dish "moods" — appetizing gradient floors when a photo URL is absent ----------------------
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
