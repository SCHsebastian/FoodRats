package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simplified, recognisable country-flag [ImageVector]s for the closed 14-cuisine catalog — the
 * food-passport tiles. Built directly with [ImageVector.Builder] (not the single-tint `materialIcon`
 * DSL) because flags are deliberately **multi-colour and must ignore any tint**: each cell renders
 * the flag full-colour when collected and desaturated when locked (the caller applies a saturation-0
 * `ColorMatrix`), never recoloured by a brand tint.
 *
 * These are geometric gestalts (tricolours, discs, stars, a saltire), not pixel-accurate vexillology
 * — at a ~56dp tile the colour layout + central motif carries recognition. The hex values are flag
 * **asset data** (national colours), so they live here as private constants exactly like the vendored
 * path data in [FrIcons]; they are not theme "meaning" colours and must not be reused as such.
 *
 * Mirrors the [FrIcons] pattern: a plain `object` of `ImageVector`s with a single `@FrPreview`, so no
 * per-flag catalog entry is required. Resolve by cuisine `iconKey` (== slug) via [forCuisine].
 */
object FrFlags {
    val American: ImageVector = flag("American") {
        // 7 horizontal bands (red/white) + blue canton with a white star.
        var y = 0f
        repeat(7) { i ->
            rect(0f, y, 24f, BAND, if (i % 2 == 0) UsRed else White)
            y += BAND
        }
        rect(0f, 0f, 11f, BAND * 4, UsBlue)
        star(5.5f, BAND * 2f, 2.6f, White)
    }

    val Italian: ImageVector = flag("Italian") {
        bg(White)
        rect(0f, 0f, 8f, 24f, ItGreen)
        rect(16f, 0f, 8f, 24f, ItRed)
    }

    val French: ImageVector = flag("French") {
        bg(White)
        rect(0f, 0f, 8f, 24f, FrBlue)
        rect(16f, 0f, 8f, 24f, FrRed)
    }

    val Mexican: ImageVector = flag("Mexican") {
        bg(White)
        rect(0f, 0f, 8f, 24f, MxGreen)
        rect(16f, 0f, 8f, 24f, MxRed)
        disc(12f, 12f, 2.2f, MxEmblem)
    }

    val Spanish: ImageVector = flag("Spanish") {
        // Rojigualda: red / wide yellow / red.
        rect(0f, 0f, 24f, 6f, EsRed)
        rect(0f, 6f, 24f, 12f, EsYellow)
        rect(0f, 18f, 24f, 6f, EsRed)
        disc(8f, 12f, 1.6f, EsEmblem)
    }

    val Greek: ImageVector = flag("Greek") {
        bg(GrBlue)
        // White cross in the upper-left canton.
        rect(4f, 0f, 3f, 11f, White)
        rect(0f, 4f, 11f, 3f, White)
        // White/blue stripes across the lower field.
        rect(0f, 11f, 24f, 2.2f, White)
        rect(0f, 15.4f, 24f, 2.2f, White)
        rect(0f, 19.8f, 24f, 2.2f, White)
    }

    // Regional motif — there is no single "Middle Eastern" nation. A white crescent + star on green
    // is a widely-read pan-regional culinary gestalt, deliberately not any one country's flag.
    val MiddleEastern: ImageVector = flag("MiddleEastern") {
        bg(MeGreen)
        disc(10.5f, 12f, 6f, White)
        disc(12.5f, 12f, 5f, MeGreen)
        star(16.5f, 12f, 1.6f, White)
    }

    val Japanese: ImageVector = flag("Japanese") {
        bg(White)
        disc(12f, 12f, 6.5f, JpRed)
    }

    val Chinese: ImageVector = flag("Chinese") {
        bg(CnRed)
        star(7f, 7f, 3.2f, CnYellow)
        star(12.5f, 4.5f, 1.1f, CnYellow)
        star(14.5f, 7f, 1.1f, CnYellow)
        star(14f, 10f, 1.1f, CnYellow)
        star(11.5f, 11.5f, 1.1f, CnYellow)
    }

    val Korean: ImageVector = flag("Korean") {
        bg(White)
        // Taegeuk: red over blue split disc (simplified yin-yang).
        disc(12f, 12f, 4.6f, KrRed)
        disc(12f, 13.6f, 2.9f, KrBlue)
        disc(12f, 10.4f, 2.9f, KrRed)
    }

    val Thai: ImageVector = flag("Thai") {
        rect(0f, 0f, 24f, 4f, ThRed)
        rect(0f, 4f, 24f, 4f, White)
        rect(0f, 8f, 24f, 8f, ThBlue)
        rect(0f, 16f, 24f, 4f, White)
        rect(0f, 20f, 24f, 4f, ThRed)
    }

    val Vietnamese: ImageVector = flag("Vietnamese") {
        bg(VnRed)
        star(12f, 12f, 5f, VnYellow)
    }

    val Indian: ImageVector = flag("Indian") {
        rect(0f, 0f, 24f, 8f, InSaffron)
        rect(0f, 8f, 24f, 8f, White)
        rect(0f, 16f, 24f, 8f, InGreen)
        disc(12f, 12f, 2.3f, InChakra)
        disc(12f, 12f, 1.1f, White)
    }

    val British: ImageVector = flag("British") {
        bg(GbBlue)
        // White saltire (two diagonal bands corner-to-corner).
        quad(0f, 0f, 4.5f, 0f, 24f, 19.5f, 24f, 24f, White)
        quad(19.5f, 0f, 24f, 0f, 4.5f, 24f, 0f, 24f, White)
        // Red saltire (thinner, on top of the white).
        quad(0f, 0f, 2.6f, 0f, 24f, 21.4f, 24f, 24f, GbRed)
        quad(21.4f, 0f, 24f, 0f, 2.6f, 24f, 0f, 24f, GbRed)
        // White St George cross then the red cross over it.
        rect(9f, 0f, 6f, 24f, White)
        rect(0f, 9f, 24f, 6f, White)
        rect(10.2f, 0f, 3.6f, 24f, GbRed)
        rect(0f, 10.2f, 24f, 3.6f, GbRed)
    }

    /** Neutral fallback for an unknown/absent cuisine slug (the catalog is closed, so rare). */
    val Generic: ImageVector = flag("Generic") {
        bg(NeutralField)
        disc(12f, 12f, 6f, NeutralMark)
    }

    /**
     * Resolves a cuisine `iconKey` (== slug; see `Cuisine.iconKey`) to its flag. Unknown keys fall
     * back to [Generic]. Kept as a `String` lookup so this atom holds **no** domain type.
     */
    fun forCuisine(iconKey: String): ImageVector = when (iconKey) {
        "american" -> American
        "italian" -> Italian
        "french" -> French
        "mexican" -> Mexican
        "spanish" -> Spanish
        "greek" -> Greek
        "middle_eastern" -> MiddleEastern
        "japanese" -> Japanese
        "chinese" -> Chinese
        "korean" -> Korean
        "thai" -> Thai
        "vietnamese" -> Vietnamese
        "indian" -> Indian
        "british" -> British
        else -> Generic
    }
}

// ── Geometry helpers (24×24 viewport) ───────────────────────────────────────────────────────────

private const val BAND = 24f / 7f // US stripe height.

private inline fun flag(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = "Flag.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

private fun ImageVector.Builder.bg(color: Color): ImageVector.Builder = rect(0f, 0f, 24f, 24f, color)

private fun ImageVector.Builder.rect(x: Float, y: Float, w: Float, h: Float, color: Color): ImageVector.Builder =
    path(fill = SolidColor(color)) {
        moveTo(x, y)
        lineTo(x + w, y)
        lineTo(x + w, y + h)
        lineTo(x, y + h)
        close()
    }

private fun ImageVector.Builder.quad(
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, x3: Float, y3: Float,
    color: Color,
): ImageVector.Builder = path(fill = SolidColor(color)) {
    moveTo(x0, y0)
    lineTo(x1, y1)
    lineTo(x2, y2)
    lineTo(x3, y3)
    close()
}

private fun ImageVector.Builder.disc(cx: Float, cy: Float, r: Float, color: Color): ImageVector.Builder {
    val k = 0.5523f * r // cubic-bezier circle approximation.
    return path(fill = SolidColor(color)) {
        moveTo(cx + r, cy)
        curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        close()
    }
}

private fun ImageVector.Builder.star(cx: Float, cy: Float, r: Float, color: Color): ImageVector.Builder {
    val inner = r * 0.382f
    return path(fill = SolidColor(color)) {
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else inner
            val angle = (-90.0 + i * 36.0) * (3.141592653589793 / 180.0)
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

// ── Flag colour data (national colours; asset constants, not theme/meaning colours) ──────────────

private val White = Color(0xFFFFFFFF)
private val UsRed = Color(0xFFB22234)
private val UsBlue = Color(0xFF3C3B6E)
private val ItGreen = Color(0xFF009246)
private val ItRed = Color(0xFFCE2B37)
private val FrBlue = Color(0xFF0055A4)
private val FrRed = Color(0xFFEF4135)
private val MxGreen = Color(0xFF006847)
private val MxRed = Color(0xFFCE1126)
private val MxEmblem = Color(0xFF5A3A22)
private val EsRed = Color(0xFFAA151B)
private val EsYellow = Color(0xFFF1BF00)
private val EsEmblem = Color(0xFFAD1519)
private val GrBlue = Color(0xFF0D5EAF)
private val MeGreen = Color(0xFF1A7A3A)
private val JpRed = Color(0xFFBC002D)
private val CnRed = Color(0xFFDE2910)
private val CnYellow = Color(0xFFFFDE00)
private val KrRed = Color(0xFFC60C30)
private val KrBlue = Color(0xFF003478)
private val ThRed = Color(0xFFA51931)
private val ThBlue = Color(0xFF2D2A4A)
private val VnRed = Color(0xFFDA251D)
private val VnYellow = Color(0xFFFFFF00)
private val InSaffron = Color(0xFFFF9933)
private val InGreen = Color(0xFF138808)
private val InChakra = Color(0xFF000080)
private val GbBlue = Color(0xFF012169)
private val GbRed = Color(0xFFC8102E)
private val NeutralField = Color(0xFFB0BEC5)
private val NeutralMark = Color(0xFF607D8B)

@FrPreview
@Composable
private fun FrFlagsPreview() {
    val entries: List<Pair<String, ImageVector>> = listOf(
        "US" to FrFlags.American,
        "IT" to FrFlags.Italian,
        "FR" to FrFlags.French,
        "MX" to FrFlags.Mexican,
        "ES" to FrFlags.Spanish,
        "GR" to FrFlags.Greek,
        "ME" to FrFlags.MiddleEastern,
        "JP" to FrFlags.Japanese,
        "CN" to FrFlags.Chinese,
        "KR" to FrFlags.Korean,
        "TH" to FrFlags.Thai,
        "VN" to FrFlags.Vietnamese,
        "IN" to FrFlags.Indian,
        "GB" to FrFlags.British,
        "??" to FrFlags.Generic,
    )
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.chunked(5).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { (label, flag) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                imageVector = flag,
                                contentDescription = label,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
