package es.schsebastian.foodrats.feature.feed.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

private const val MAP_ZOOM = 15

/**
 * Non-interactive location preview. Primary render is the Google Static Maps PNG (uses the
 * configured [MapsApiKey]); if that request fails — empty key, or the key isn't authorized for
 * the Maps Static API (HTTP 403) — it falls back to a key-free OpenStreetMap tile map so a real
 * map is always shown. Tapping either opens the location in the device's maps app.
 */
@Composable
actual fun FrLocationMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier,
) {
    val apiKey = koinInject<MapsApiKey>().value
    val context = LocalContext.current
    val center = "$latitude,$longitude"
    val openInMaps: () -> Unit = {
        val mapsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$center")
        context.startActivity(
            Intent(Intent.ACTION_VIEW, mapsUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    if (apiKey.isBlank()) {
        OpenStreetMap(latitude, longitude, modifier.clickable(onClick = openInMaps))
        return
    }

    // Full-width banner (~2.5:1). Request a wide landscape tile (logical 600x240, scale=2 for
    // retina). Static Maps caps each dimension at 640 logical px.
    val url = "https://maps.googleapis.com/maps/api/staticmap" +
        "?center=$center" +
        "&zoom=$MAP_ZOOM" +
        "&size=600x240" +
        "&scale=2" +
        "&markers=color:red%7C$center" +
        "&key=$apiKey"
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clickable(onClick = openInMaps),
        error = { OpenStreetMap(latitude, longitude, Modifier) },
    )
}

/**
 * Key-free static map built from OpenStreetMap raster tiles. Renders a 3×3 tile grid around the
 * point and translates it so the exact coordinate sits at the viewport centre, with a marker drawn
 * on top. No API key, billing, or SDK — just `tile.openstreetmap.org` PNGs loaded via Coil.
 */
@Composable
private fun OpenStreetMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier,
) {
    val n = (1 shl MAP_ZOOM).toDouble()
    val xExact = (longitude + 180.0) / 360.0 * n
    val latRad = latitude * PI / 180.0
    val yExact = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
    val xCenter = floor(xExact).toInt()
    val yCenter = floor(yExact).toInt()
    val fracX = (xExact - xCenter).toFloat() // 0..1 within the centre tile
    val fracY = (yExact - yCenter).toFloat()

    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val tileDp = 256.dp
        val tilePx = with(density) { tileDp.toPx() }
        val viewportWpx = with(density) { maxWidth.toPx() }
        val viewportHpx = with(density) { maxHeight.toPx() }
        // The point lands inside the centre tile, which is column/row index 1 of the 3×3 grid.
        val pointXpx = (1f + fracX) * tilePx
        val pointYpx = (1f + fracY) * tilePx
        val gridOffsetX = (viewportWpx / 2f - pointXpx).roundToInt()
        val gridOffsetY = (viewportHpx / 2f - pointYpx).roundToInt()

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .offset { IntOffset(gridOffsetX, gridOffsetY) }
                .size(tileDp * 3),
        ) {
            for (dy in 0..2) {
                for (dx in 0..2) {
                    AsyncImage(
                        model = "https://tile.openstreetmap.org/$MAP_ZOOM/${xCenter - 1 + dx}/${yCenter - 1 + dy}.png",
                        contentDescription = null,
                        modifier = Modifier
                            .size(tileDp)
                            .offset(x = tileDp * dx, y = tileDp * dy),
                    )
                }
            }
        }

        // Marker: a filled dot with a contrasting ring at the exact location (viewport centre).
        val markerColor = LocalFrSemanticColors.current.danger
        val ringColor = MaterialTheme.colorScheme.surface
        Canvas(modifier = Modifier.size(18.dp).align(Alignment.Center)) {
            val r = size.minDimension / 2f
            drawCircle(color = ringColor, radius = r)
            drawCircle(color = markerColor, radius = r * 0.66f)
        }
    }
}
