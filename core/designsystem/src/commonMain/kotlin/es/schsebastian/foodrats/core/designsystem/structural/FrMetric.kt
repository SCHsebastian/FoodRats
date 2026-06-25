package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme

/** The four oversized metric scales, mapping to [StructuralType]'s `metricXl…metricSm` (96/68/44/30sp). */
enum class FrMetricSize { Xl, Lg, Md, Sm }

/**
 * The Structural language's **extreme-typographic-contrast hero number** — a single oversized, tabular,
 * tightly-tracked metric set directly into the content plane (zero chrome, no container required). An
 * optional [unit] rides as a ~0.30x suffix at 70% alpha, so "9.2" and "/10" read as one glyph cluster
 * with the figure dominating. Color is caller-set; the figure carries the meaning, not a card around it.
 */
@Composable
fun FrMetric(
    value: String,
    modifier: Modifier = Modifier,
    size: FrMetricSize = FrMetricSize.Md,
    unit: String? = null,
    color: Color = StructuralColors.foreground,
) {
    val style: TextStyle = when (size) {
        FrMetricSize.Xl -> StructuralType.metricXl
        FrMetricSize.Lg -> StructuralType.metricLg
        FrMetricSize.Md -> StructuralType.metricMd
        FrMetricSize.Sm -> StructuralType.metricSm
    }
    // CSS `.metric .u { font-size: 0.30em }` — per-size lookup keeps the suffix tabular-crisp.
    val unitSp = when (size) {
        FrMetricSize.Xl -> 29.sp
        FrMetricSize.Lg -> 20.sp
        FrMetricSize.Md -> 13.sp
        FrMetricSize.Sm -> 9.sp
    }

    val text: AnnotatedString = buildAnnotatedString {
        append(value)
        if (unit != null) {
            withStyle(
                SpanStyle(
                    fontSize = unitSp,
                    fontWeight = FontWeight.Bold,
                    color = color.copy(alpha = 0.7f),
                    letterSpacing = 0.sp,
                ),
            ) {
                append(unit)
            }
        }
    }

    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
    )
}

@FrPreview
@Composable
private fun FrMetricPreview() {
    FoodRatsTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(StructuralColors.stageFloor)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FrMetric("9.2", size = FrMetricSize.Lg, unit = "/10")
            FrMetric("128", size = FrMetricSize.Md)
        }
    }
}
