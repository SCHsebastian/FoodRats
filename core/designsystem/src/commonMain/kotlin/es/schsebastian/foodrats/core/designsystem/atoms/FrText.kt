package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles

@Composable
fun FrText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@FrPreview
@Composable
private fun FrTextPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FrText("Display large", style = MaterialTheme.typography.displaySmall)
            FrText("Headline medium", style = MaterialTheme.typography.headlineMedium)
            FrText("Title large", style = MaterialTheme.typography.titleLarge)
            FrText("Title medium", style = MaterialTheme.typography.titleMedium)
            FrText("Body large — the quick brown fox jumps over the lazy rat.", style = MaterialTheme.typography.bodyLarge)
            FrText("Body medium — supporting text.", style = MaterialTheme.typography.bodyMedium)
            FrText("LABEL LARGE", style = MaterialTheme.typography.labelLarge)
            FrText("12345", style = FrTextStyles.statNumber)
        }
    }
}
