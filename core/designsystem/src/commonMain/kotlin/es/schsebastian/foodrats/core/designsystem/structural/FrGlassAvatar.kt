package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors

/** Accent ring tone for [FrGlassAvatar] — a brand-keyed glow that frames an identity without a chrome box. */
enum class FrAvatarRing { None, Olive, Ember, Rust, Moss }

/**
 * A circular identity glyph for the Structural variant: zero-chrome, just a clipped photo (or a
 * frosted initials disc) optionally framed by a 2dp accent ring floating 2dp off the avatar — the
 * CSS `box-shadow:0 0 0 2px bg, 0 0 0 4px color` gap-ring, faked KMP-safe with a bordered outer box.
 */
@Composable
fun FrGlassAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    ring: FrAvatarRing = FrAvatarRing.None,
    image: Painter? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val ringColor: Color? = when (ring) {
        FrAvatarRing.None -> null
        FrAvatarRing.Olive -> scheme.primary
        FrAvatarRing.Ember -> scheme.secondary
        FrAvatarRing.Rust -> scheme.tertiary
        FrAvatarRing.Moss -> LocalFrSemanticColors.current.success
    }

    val avatar: @Composable () -> Unit = {
        if (image != null) {
            Image(
                painter = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.clip(CircleShape).size(size),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                FrText(
                    text = initials.take(2).uppercase(),
                    color = scheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.35f).sp,
                    ),
                )
            }
        }
    }

    if (ringColor != null) {
        Box(
            modifier = modifier
                .size(size + 8.dp)
                .border(2.dp, ringColor, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            avatar()
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            avatar()
        }
    }
}

@FrPreview
@Composable
private fun FrGlassAvatarPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrGlassAvatar("AN", ring = FrAvatarRing.Olive)
                FrGlassAvatar("RG", size = 32.dp, ring = FrAvatarRing.Ember)
                FrGlassAvatar("JU", size = 56.dp)
            }
        }
    }
}
