package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrFontFamily
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Motion

/** One destination in an [FrDock]. [badge] paints a streak-hot dot over the icon (unseen activity). */
data class FrDockItem(
    val icon: ImageVector,
    val label: String,
    val badge: Boolean = false,
)

/**
 * The zero-chrome floating navigation: a frosted, border-less rounded bar (read by translucency +
 * depth, never a top-bar fill) with a raised center FAB. The bar renders itself; the **caller**
 * positions it (bottom inset, left/right margins) and gives the FAB room to rise above the top edge.
 *
 * Items split evenly around the FAB. The selected destination glows olive; the FAB carries the
 * ember→streak-hot brand gradient and can pulse when the user hasn't posted today.
 */
@Composable
fun FrDock(
    items: List<FrDockItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onFabClick: () -> Unit,
    fabIcon: ImageVector,
    fabContentDescription: String,
    modifier: Modifier = Modifier,
    fabPulsing: Boolean = false,
) {
    Box(modifier) {
        val dockShape = RoundedCornerShape(32.dp)
        val leftCount = items.size / 2
        val isLight = StructuralColors.isLight
        // Light mode: an opaque fill + shallow elevation. A drop shadow over a TRANSLUCENT fill renders
        // a hard double-edge on the light floor ("white square with a wrong fade", user report
        // 2026-06-23); dark keeps the translucent dock (its shadow vanishes into the dark floor).
        val dockFill = if (isLight) StructuralColors.dock.copy(alpha = 1f) else StructuralColors.dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter)
                .shadow(if (isLight) 8.dp else 18.dp, dockShape, clip = false)
                .clip(dockShape)
                .background(dockFill)
                .frTopLightEdge()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                if (index == leftCount) {
                    // reserve the central column for the raised FAB
                    Spacer(Modifier.weight(1.2f))
                }
                NavButton(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            if (leftCount == items.size) {
                Spacer(Modifier.weight(1.2f))
            }
        }

        // raised center FAB (+ optional pulse ring) overlaid on the bar's top edge
        Box(
            modifier = Modifier.align(Alignment.Center).offset(y = (-18).dp),
            contentAlignment = Alignment.Center,
        ) {
            if (fabPulsing) {
                // Finite attention pulse: a few cycles when the nudge appears, then settle to rest
                // (t = 1f → alpha 0 → invisible) so the render thread can idle. No infinite loop.
                val pulse = remember { Animatable(1f) }
                LaunchedEffect(fabPulsing) {
                    repeat(3) {
                        pulse.snapTo(0f)
                        pulse.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(Motion.pulse, easing = Motion.Standard),
                        )
                    }
                    pulse.snapTo(1f) // rest: 1f scale / 0 alpha ring
                }
                Box(
                    Modifier
                        .size(58.dp)
                        .graphicsLayer {
                            val t = pulse.value
                            scaleX = 1f + 0.2f * t
                            scaleY = 1f + 0.2f * t
                            alpha = 0.7f * (1f - t)
                        }
                        .clip(CircleShape)
                        .border(2.dp, LocalFrSemanticColors.current.streakHot, CircleShape),
                )
            }
            Fab(icon = fabIcon, contentDescription = fabContentDescription, onClick = onFabClick)
        }
    }
}

@Composable
private fun NavButton(
    item: FrDockItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
            if (item.badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(LocalFrSemanticColors.current.streakHot),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        FrText(
            text = item.label,
            color = color,
            style = TextStyle(
                fontFamily = LocalFrFontFamily.current,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Fab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(58.dp)
            .shadow(12.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(scheme.secondary, LocalFrSemanticColors.current.streakHot)))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = StructuralColors.foreground,
            modifier = Modifier.size(28.dp),
        )
    }
}

@FrPreview
@Composable
private fun FrDockPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(top = 40.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            FrDock(
                items = listOf(
                    FrDockItem(Icons.Filled.Home, "FEED"),
                    FrDockItem(Icons.Filled.Person, "CREW", badge = true),
                ),
                selectedIndex = 0,
                onSelect = {},
                onFabClick = {},
                fabIcon = Icons.Filled.Add,
                fabContentDescription = "Add a meal",
                fabPulsing = true,
            )
        }
    }
}
