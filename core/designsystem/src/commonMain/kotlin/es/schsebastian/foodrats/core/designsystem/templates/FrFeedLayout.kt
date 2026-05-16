package es.schsebastian.foodrats.core.designsystem.templates

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FrFeedLayout(
    dayHeader: @Composable () -> Unit,
    list: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        dayHeader()
        list()
    }
}
