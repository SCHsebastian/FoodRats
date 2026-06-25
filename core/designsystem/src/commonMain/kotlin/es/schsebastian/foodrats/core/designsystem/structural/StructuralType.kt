package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrFontFamily

/**
 * The Structural language's signature **extreme typographic contrast**: oversized [metricXl]…[metricSm]
 * (weight 800, tabular, tight tracking, 0.84 line-height) set directly against 10sp uppercase
 * [micro] metadata. Built as `@Composable` getters so they pick up the bundled Plus Jakarta Sans via
 * [LocalFrFontFamily] without every call site threading a family in (mirrors `FrTextStyles`).
 *
 * Color is **not** baked in — callers set it (`StructuralColors.foreground`, `MaterialTheme` roles,
 * or `LocalFrSemanticColors`). `micro`/`eyebrow` are uppercase by tracking convention: pass already
 * upper-cased text (the DS stays string-free, so no transform is applied here).
 */
object StructuralType {
    private val metricBase: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalFrFontFamily.current,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.045).em,
            lineHeight = 0.84.em,
            fontFeatureSettings = "tnum",
        )

    /** 96sp — the recap number / podium hero. */
    val metricXl: TextStyle @Composable get() = metricBase.copy(fontSize = 96.sp)

    /** 68sp — the streak count / primary stat. */
    val metricLg: TextStyle @Composable get() = metricBase.copy(fontSize = 68.sp)

    /** 44sp — headline metrics in a bento tile. */
    val metricMd: TextStyle @Composable get() = metricBase.copy(fontSize = 44.sp)

    /** 30sp — compact metrics. */
    val metricSm: TextStyle @Composable get() = metricBase.copy(fontSize = 30.sp)

    /** 34sp / 800 — screen title rendered as oversized type in the content plane (zero-chrome). */
    val titleXl: TextStyle @Composable get() = TextStyle(
        fontFamily = LocalFrFontFamily.current,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 1.02.em,
        letterSpacing = (-0.02).em,
    )

    /** 24sp / 700 — section title. */
    val titleLg: TextStyle @Composable get() = TextStyle(
        fontFamily = LocalFrFontFamily.current,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 1.1.em,
        letterSpacing = (-0.01).em,
    )

    /** 17sp / 700 — tile title. */
    val titleMd: TextStyle @Composable get() = TextStyle(
        fontFamily = LocalFrFontFamily.current,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 1.2.em,
    )

    /** 14sp / 400 — body copy. */
    val body: TextStyle @Composable get() = TextStyle(
        fontFamily = LocalFrFontFamily.current,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 1.5.em,
    )

    /** 10sp / 700 uppercase, wide tracking — the microscopic high-density metadata array. */
    val micro: TextStyle @Composable get() = TextStyle(
        fontFamily = LocalFrFontFamily.current,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 1.3.em,
        letterSpacing = 0.14.em,
    )

    /** 10sp / 700 monospace — tabular figures inside a [micro] array (codes, counts). */
    val microMono: TextStyle @Composable get() = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 1.3.em,
        letterSpacing = 0.06.em,
        fontFeatureSettings = "tnum",
    )

    /** 10sp / 800 ultra-wide tracking — the olive eyebrow over a section. */
    val eyebrow: TextStyle @Composable get() = TextStyle(
        fontFamily = LocalFrFontFamily.current,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp,
        lineHeight = 1.3.em,
        letterSpacing = 0.22.em,
    )
}
