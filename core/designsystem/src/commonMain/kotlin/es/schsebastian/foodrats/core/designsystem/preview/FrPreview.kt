package es.schsebastian.foodrats.core.designsystem.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme

// One `@Preview` per gallery entry. We render light + dark inside the same tile via
// [FrPreviewLightDark] because Compose Multiplatform's commonMain `@Preview` does not
// expose `uiMode`, so a duplicated annotation cannot drive `isSystemInDarkTheme()`.
@Preview(name = "FoodRats", showBackground = false, widthDp = 360)
annotation class FrPreview

@Composable
fun FrPreviewSurface(
    label: String? = null,
    darkTheme: Boolean = false,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    FoodRatsTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(padding)) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                content()
            }
        }
    }
}

@Composable
fun FrPreviewLightDark(content: @Composable () -> Unit) {
    Column {
        FrPreviewSurface(label = "Light", darkTheme = false) { content() }
        HorizontalDivider()
        FrPreviewSurface(label = "Dark", darkTheme = true) { content() }
    }
}
