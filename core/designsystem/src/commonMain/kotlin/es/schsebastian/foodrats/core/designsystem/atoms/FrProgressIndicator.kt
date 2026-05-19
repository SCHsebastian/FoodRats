package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

@Composable
fun FrProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 4.dp,
) {
    if (color == Color.Unspecified) {
        CircularProgressIndicator(modifier = modifier, strokeWidth = strokeWidth)
    } else {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
    }
}

@FrPreview
@Composable
private fun FrProgressIndicatorPreview() {
    FrPreviewLightDark {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            FrProgressIndicator(modifier = Modifier.size(40.dp))
            FrProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
