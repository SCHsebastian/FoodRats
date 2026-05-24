package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSURL
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UIApplication

// Apple Maps (MapKit) — free, no API key. Non-interactive mini-map centered on the
// meal's coordinates with a pin. A transparent Compose overlay on top captures taps
// (the UIKitView would otherwise swallow them) and opens the spot in Apple Maps.
// Verified at compile/link on the Mac (the Android host-test loop does not compile iosMain).
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FrLocationMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier,
) {
    Box(modifier = modifier) {
        UIKitView(
            factory = {
                val mapView = MKMapView()
                val coordinate = CLLocationCoordinate2DMake(latitude, longitude)
                mapView.setRegion(
                    MKCoordinateRegionMakeWithDistance(coordinate, 1000.0, 1000.0),
                    animated = false,
                )
                val annotation = MKPointAnnotation()
                annotation.setCoordinate(coordinate)
                mapView.addAnnotation(annotation)
                mapView.setUserInteractionEnabled(false)
                mapView
            },
            modifier = Modifier.fillMaxSize(),
            update = {},
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    val url = NSURL.URLWithString(
                        "https://maps.apple.com/?ll=$latitude,$longitude&q=$latitude,$longitude",
                    )
                    if (url != null) {
                        UIApplication.sharedApplication.openURL(
                            url,
                            options = emptyMap<Any?, Any>(),
                            completionHandler = null,
                        )
                    }
                },
        )
    }
}
