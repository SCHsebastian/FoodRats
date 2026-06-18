package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.sqrt

/** When true, Fr* surfaces are upholstered in minotaur fur (the hidden "Minotaur mode" easter egg). */
val LocalMinotaurMode = staticCompositionLocalOf { false }

/**
 * Upholsters the surface in minotaur fur when [enabled]: the whole component fill becomes a dense,
 * brushed brown pelt (a generated fur texture, clipped to [shape]) with a pulsing-free neon-green
 * rim glow hugging the edge. Caller content is drawn on top, so set a light `contentColor` for it.
 *
 * The fur texture is built once per size via [drawWithCache] (thousands of tapered hairs is too much
 * to redraw every frame), so the steady-state cost is a single `drawImage`. Off by default — the
 * disabled path early-returns and costs nothing.
 */
@Composable
fun Modifier.fur(enabled: Boolean, shape: Shape): Modifier {
    if (!enabled) return this
    val semantic = LocalFrSemanticColors.current
    val coat = semantic.fur
    val root = lerp(coat, Color.Black, 0.62f)
    val tan = lerp(coat, Color.White, 0.42f)
    val glow = semantic.furGlow
    return this.drawWithCache {
        val w = size.width.toInt().coerceAtLeast(1)
        val h = size.height.toInt().coerceAtLeast(1)
        val texture = buildFurTexture(w, h, root, coat, tan)
        onDrawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            drawFurGlow(glow, clip)
            clipPath(clip) {
                drawImage(texture)
                this@onDrawWithContent.drawContent()
            }
            // Crisp neon rim on top of the fur, tracing the exact shape.
            drawPath(clip, glow.copy(alpha = 0.85f), style = Stroke(width = 3f))
        }
    }
}

/** Soft green bloom hugging the shape edge: the same outline stroked wide→narrow, faint→bright. */
private fun DrawScope.drawFurGlow(glow: Color, path: Path) {
    val widths = floatArrayOf(36f, 26f, 18f, 11f, 6f)
    val alphas = floatArrayOf(0.05f, 0.09f, 0.15f, 0.26f, 0.5f)
    for (i in widths.indices) {
        drawPath(path, glow.copy(alpha = alphas[i]), style = Stroke(width = widths[i]))
    }
}

/**
 * Renders a dense brushed-fur field into an [ImageBitmap]: a dark base, then a grid of short tapered
 * hairs all leaning the same way (with per-hair jitter) and shaded dark root → tan tip, so it reads
 * as a real pelt rather than spiky grass. Built once per surface size and cached by the caller.
 */
private fun buildFurTexture(w: Int, h: Int, root: Color, coat: Color, tan: Color): ImageBitmap {
    val bitmap = ImageBitmap(w, h)
    val canvas = Canvas(bitmap)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        drawRect(root)
        val step = 4.5f
        var seed = 1
        var y = -3f
        while (y < h + 6f) {
            var x = -3f
            while (x < w + 6f) {
                furHair(Offset(x, y), seed, root, coat, tan)
                x += step
                seed++
            }
            y += step
            seed += 7
        }
    }
    return bitmap
}

/** One short tapered, gently curved hair leaning up-and-slightly-right, shaded by its seed. */
private fun DrawScope.furHair(origin: Offset, seed: Int, root: Color, coat: Color, tan: Color) {
    val r1 = rnd(seed)
    val r2 = rnd(seed * 7 + 1)
    val r3 = rnd(seed * 13 + 5)
    val len = 9f + r1 * 15f
    // Global lean (up + slightly right) with per-hair jitter, normalised to a unit direction.
    val dx = 0.34f + (r2 - 0.5f) * 0.55f
    val dy = -1f + (r3 - 0.5f) * 0.35f
    val inv = 1f / sqrt(dx * dx + dy * dy)
    val ox = dx * inv
    val oy = dy * inv
    val px = -oy
    val py = ox
    val baseHalf = 1.2f + r1 * 1.2f
    val tipX = origin.x + ox * len
    val tipY = origin.y + oy * len
    val midX = origin.x + ox * len * 0.5f + px * (r2 - 0.5f) * 2.5f
    val midY = origin.y + oy * len * 0.5f + py * (r2 - 0.5f) * 2.5f
    val hair = Path().apply {
        moveTo(origin.x + px * baseHalf, origin.y + py * baseHalf)
        quadraticTo(midX + px * baseHalf * 0.5f, midY + py * baseHalf * 0.5f, tipX, tipY)
        quadraticTo(midX - px * baseHalf * 0.5f, midY - py * baseHalf * 0.5f, origin.x - px * baseHalf, origin.y - py * baseHalf)
        close()
    }
    val shade = lerp(root, tan, r1 * 0.55f + r3 * 0.25f)
    drawPath(hair, shade)
}

/** Deterministic [0,1) hash of [seed] — keeps the coat stable frame-to-frame (no random in draw). */
private fun rnd(seed: Int): Float {
    var x = seed * 374761393 + 668265263
    x = x xor (x ushr 13)
    x *= 1274126177
    x = x xor (x ushr 16)
    return ((x and 0x7fffffff) % 100000) / 100000f
}
