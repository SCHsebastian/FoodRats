package es.schsebastian.foodrats.feature.feed.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import org.koin.compose.koinInject

@Composable
actual fun FrLocationMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier,
) {
    val apiKey = koinInject<MapsApiKey>().value
    val context = LocalContext.current
    val center = "$latitude,$longitude"
    // Full-width banner (~2.5:1). Request a wide landscape tile (logical 600x240,
    // scale=2 for retina). Static Maps caps each dimension at 640 logical px.
    val url = "https://maps.googleapis.com/maps/api/staticmap" +
        "?center=$center" +
        "&zoom=15" +
        "&size=600x240" +
        "&scale=2" +
        "&markers=color:red%7C$center" +
        "&key=$apiKey"
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clickable {
            // Open the location in Google Maps (falls back to any maps app / browser).
            val mapsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$center")
            context.startActivity(
                Intent(Intent.ACTION_VIEW, mapsUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}
