package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.schsebastian.foodrats.core.designsystem.atoms.FrFlameIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrFontFamily
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius

/**
 * Zero-chrome streak token: a tight forge-orange pill that floats over the media
 * floor with no border and no divider. The flame pulses and the day count is set
 * in extreme-weight tabular figures so the badge never shifts width as a streak grows.
 */
@Composable
fun FrFlameBadge(
    days: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val semantics = LocalFrSemanticColors.current
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(semantics.streakHot)
            .padding(start = 7.dp, end = 9.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FrFlameIcon(
            size = 14.dp,
            tint = semantics.onStreakHot,
        )
        FrText(
            text = days.toString(),
            color = semantics.onStreakHot,
            style = TextStyle(
                fontFamily = LocalFrFontFamily.current,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontFeatureSettings = "tnum",
            ),
        )
    }
}

@FrPreview
@Composable
private fun FrFlameBadgePreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FrFlameBadge(days = 7)
                FrFlameBadge(days = 21)
            }
        }
    }
}
