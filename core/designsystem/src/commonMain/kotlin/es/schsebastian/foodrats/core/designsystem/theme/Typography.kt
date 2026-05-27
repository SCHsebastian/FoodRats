package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import foodrats.core.designsystem.generated.resources.Res
import foodrats.core.designsystem.generated.resources.plus_jakarta_sans_bold
import foodrats.core.designsystem.generated.resources.plus_jakarta_sans_extrabold
import foodrats.core.designsystem.generated.resources.plus_jakarta_sans_regular
import foodrats.core.designsystem.generated.resources.plus_jakarta_sans_semibold
import org.jetbrains.compose.resources.Font

/**
 * Provides the active FoodRats font family to call sites that build [TextStyle]s outside the
 * Material ramp (see [FrTextStyles]). Defaults to system sans so Compose previews and unit tests
 * that render without the bundled resources still resolve a real family.
 *
 * The real Plus Jakarta Sans family is built by [rememberFrFontFamily] and provided from
 * [FoodRatsTheme]; the Material [Typography] is built from the same family by
 * [rememberFoodRatsTypography].
 */
val LocalFrFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }

/**
 * Plus Jakarta Sans — the FoodRats display/body family — built from bundled `composeResources/font`.
 * Must be called inside composition (Compose Resources' [Font] is `@Composable`).
 */
@Composable
fun rememberFrFontFamily(): FontFamily = FontFamily(
    Font(Res.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(Res.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(Res.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun frText(
    size: Int,
    line: Int,
    weight: FontWeight,
    family: FontFamily,
    letterSpacingSp: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = letterSpacingSp.sp,
)

/**
 * The Material 3 type ramp at FoodRats sizes, bound to [family]. Built in composition because the
 * family comes from `@Composable`-loaded font resources. Weights 600 (titles/labels) and 700
 * (display/headline) per the design system.
 */
@Composable
fun rememberFoodRatsTypography(family: FontFamily): Typography = Typography(
    displayLarge   = frText(57, 64, FontWeight.Bold,     family, letterSpacingSp = -0.25),
    displayMedium  = frText(45, 52, FontWeight.Bold,     family),
    displaySmall   = frText(36, 44, FontWeight.Bold,     family),
    headlineLarge  = frText(32, 40, FontWeight.Bold,     family),
    headlineMedium = frText(28, 36, FontWeight.Bold,     family),
    headlineSmall  = frText(24, 32, FontWeight.SemiBold, family),
    titleLarge     = frText(22, 28, FontWeight.SemiBold, family),
    titleMedium    = frText(16, 24, FontWeight.SemiBold, family, letterSpacingSp = 0.15),
    titleSmall     = frText(14, 20, FontWeight.SemiBold, family, letterSpacingSp = 0.10),
    bodyLarge      = frText(16, 24, FontWeight.Normal,   family, letterSpacingSp = 0.50),
    bodyMedium     = frText(14, 20, FontWeight.Normal,   family, letterSpacingSp = 0.25),
    bodySmall      = frText(12, 16, FontWeight.Normal,   family, letterSpacingSp = 0.40),
    labelLarge     = frText(14, 20, FontWeight.SemiBold, family, letterSpacingSp = 0.10),
    labelMedium    = frText(12, 16, FontWeight.SemiBold, family, letterSpacingSp = 0.50),
    labelSmall     = frText(11, 16, FontWeight.SemiBold, family, letterSpacingSp = 0.50),
)

/**
 * Extra styles outside the Material ramp. Use these for content where the M3 styles fall short —
 * e.g. tabular numerals for scores/streaks/leaderboards that must not shift width when the value
 * changes. Resolved as `@Composable` getters so they pick up the bundled family via
 * [LocalFrFontFamily] without every call site threading a font in.
 */
object FrTextStyles {
    /** 24sp bold with tabular numerals — for score badges, streak counters, ranks. */
    val statNumber: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalFrFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = "tnum",
        )

    /** 48sp extrabold tabular numerals — for the hero score on meal detail / podium. */
    val statNumberLarge: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalFrFontFamily.current,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 48.sp,
            lineHeight = 48.sp,
            letterSpacing = (-1).sp,
            fontFeatureSettings = "tnum",
        )

    /** 14sp semibold tabular numerals — for inline metric counts. */
    val statNumberSmall: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalFrFontFamily.current,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = "tnum",
        )
}
