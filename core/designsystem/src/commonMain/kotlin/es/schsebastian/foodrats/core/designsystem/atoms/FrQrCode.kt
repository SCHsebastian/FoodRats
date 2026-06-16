package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.qr.QrCode
import es.schsebastian.foodrats.core.designsystem.atoms.qr.QrEcc

/**
 * Renders [content] as a scannable QR code on a Compose Canvas — pure Kotlin, no platform View, so it
 * draws identically on Android and iOS. The matrix is encoded once with the dependency-free [QrCode]
 * encoder (`remember`ed on [content]/[ecc]) and painted as solid square modules with a quiet-zone
 * border (required by the QR spec for reliable scanning).
 *
 * Used in the crew invite UI to turn the canonical invite URL into a "scan to join" code.
 */
@Composable
fun FrQrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    ecc: QrEcc = QrEcc.MEDIUM,
    foreground: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.surface,
    /** Quiet-zone width in modules (spec minimum is 4). */
    quietZone: Int = 4,
) {
    val qr = remember(content, ecc) { QrCode.encode(content, ecc) }
    Canvas(modifier = Modifier.size(size).then(modifier)) {
        val totalModules = qr.size + quietZone * 2
        val moduleSize = this.size.minDimension / totalModules
        drawRect(color = background, topLeft = Offset.Zero, size = this.size)
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.isDark(x, y)) {
                    drawRect(
                        color = foreground,
                        topLeft = Offset(
                            x = (x + quietZone) * moduleSize,
                            y = (y + quietZone) * moduleSize,
                        ),
                        size = Size(moduleSize, moduleSize),
                    )
                }
            }
        }
    }
}
