package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A small, non-interactive map preview centered on a meal's coordinates.
 * Per-platform: Android renders a Google Static Maps tile; iOS renders an Apple
 * MapKit (`MKMapView`) snapshot. Callers size it (the feed/detail use a 2:3 box).
 */
@Composable
expect fun FrLocationMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
)
