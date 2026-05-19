package es.schsebastian.foodrats.feature.meal.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

data class MealUi(
    val dish: String,
    val tags: List<String>,
    val photoBytes: ByteArray?,
)

@Composable
fun FrMealCard(ui: MealUi, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            ui.photoBytes?.let { bytes ->
                val img = remember(bytes) { decodeImageBitmap(bytes) }
                if (img != null) {
                    Image(
                        bitmap = img,
                        contentDescription = ui.dish,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(Spacing.sm))
                            .padding(bottom = Spacing.sm),
                    )
                }
            }
            FrText(text = ui.dish)
            FrText(text = ui.tags.joinToString(" • "))
        }
    }
}
