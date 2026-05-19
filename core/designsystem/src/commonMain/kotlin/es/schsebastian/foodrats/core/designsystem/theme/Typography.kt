package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Default family for FoodRats text. Resolves to system sans today.
 *
 * When Plus Jakarta Sans (or chosen geometric sans) is bundled under
 * `composeResources/font/`, flip this to a [FontFamily] built from those resources.
 * No call-site changes are required because every [TextStyle] in [FoodRatsTypography]
 * already references this constant.
 */
internal val FrFontFamily: FontFamily = FontFamily.SansSerif

private fun frText(
    size: Int,
    line: Int,
    weight: FontWeight,
    letterSpacingSp: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = FrFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = letterSpacingSp.sp,
)

internal val FoodRatsTypography = Typography(
    displayLarge   = frText(57, 64, FontWeight.Bold,     letterSpacingSp = -0.25),
    displayMedium  = frText(45, 52, FontWeight.Bold),
    displaySmall   = frText(36, 44, FontWeight.Bold),
    headlineLarge  = frText(32, 40, FontWeight.Bold),
    headlineMedium = frText(28, 36, FontWeight.Bold),
    headlineSmall  = frText(24, 32, FontWeight.SemiBold),
    titleLarge     = frText(22, 28, FontWeight.SemiBold),
    titleMedium    = frText(16, 24, FontWeight.SemiBold, letterSpacingSp = 0.15),
    titleSmall     = frText(14, 20, FontWeight.SemiBold, letterSpacingSp = 0.10),
    bodyLarge      = frText(16, 24, FontWeight.Normal,   letterSpacingSp = 0.50),
    bodyMedium     = frText(14, 20, FontWeight.Normal,   letterSpacingSp = 0.25),
    bodySmall      = frText(12, 16, FontWeight.Normal,   letterSpacingSp = 0.40),
    labelLarge     = frText(14, 20, FontWeight.SemiBold, letterSpacingSp = 0.10),
    labelMedium    = frText(12, 16, FontWeight.SemiBold, letterSpacingSp = 0.50),
    labelSmall     = frText(11, 16, FontWeight.SemiBold, letterSpacingSp = 0.50),
)

/**
 * Extra styles outside the Material ramp. Use these for content where the M3 styles
 * fall short — e.g. tabular numerals for scores/streaks/leaderboards that must not
 * shift width when the value changes.
 */
object FrTextStyles {
    /** 24sp bold with tabular numerals — for score badges, streak counters, ranks. */
    val statNumber: TextStyle = TextStyle(
        fontFamily = FrFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    )

    /** 14sp semibold tabular numerals — for inline metric counts. */
    val statNumberSmall: TextStyle = TextStyle(
        fontFamily = FrFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    )
}
