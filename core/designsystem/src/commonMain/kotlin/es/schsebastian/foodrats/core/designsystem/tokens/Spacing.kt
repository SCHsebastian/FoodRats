package es.schsebastian.foodrats.core.designsystem.tokens
import androidx.compose.ui.unit.dp
object Spacing {
    val xxs = 2.dp
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 16.dp
    val lg  = 24.dp
    val xl  = 32.dp
    val xxl = 64.dp

    // Extra top inset for the story chrome's progress bar, dropping it below the
    // status bar / notch so the segments clear the inset already applied by
    // WindowInsets.safeDrawing. Semantic — not a step on the scale above.
    val storyProgressInsetTop = 40.dp
}
