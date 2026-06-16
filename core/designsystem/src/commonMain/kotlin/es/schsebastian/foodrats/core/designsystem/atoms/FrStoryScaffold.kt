package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Full-screen Instagram-Stories chrome: a [FrStoryProgressBar] header, a close affordance, and two
 * invisible tap zones (left third = previous, right two-thirds = next). A long-press anywhere pauses
 * (the caller stops the auto-advance clock in [onHoldStart] and resumes it in [onHoldEnd]).
 *
 * A pure design-system atom (spec §4.1, §4.4): it takes **primitives and lambdas only** — never a
 * domain type. Per-recap scene content is supplied via the [scene] slot, which feature code fills
 * with domain-aware composables. Keeping the chrome here (and the scene as a slot) is what lets Wave
 * 3 reuse a single scene composable both inside this player and as a rendered-to-bitmap share card.
 *
 * The [action] slot is an **overlay above the gesture tap-zones**: anything drawn there (e.g. a
 * "Share this recap" button) receives the click itself instead of advancing/rewinding the story. It
 * sits bottom-anchored and inset under the navigation bar / home indicator; pass `null` (the default)
 * for scenes with no in-scene action. This closes the gap the shareable-cards presentation task hit:
 * the full-size tap-zone `Row` used to consume every gesture, so a button placed inside [scene] was
 * un-tappable. Drawing the action AFTER (above) the tap zones lets its own pointer input win.
 *
 * @param segmentCount number of scenes.
 * @param currentIndex active scene index.
 * @param currentProgress 0f..1f fill of the active segment (the caller animates it).
 * @param onPrev tap on the left third.
 * @param onNext tap on the right region.
 * @param onClose the close (X) affordance.
 * @param onHoldStart press-and-hold began (pause the clock).
 * @param onHoldEnd the hold released (resume the clock).
 * @param scene the current scene's content, drawn full-bleed behind the chrome.
 * @param action an optional overlay action (e.g. a share button) drawn ABOVE the tap zones, so a
 *   click inside it does NOT advance the story; bottom-anchored and inset. `null` → no action.
 */
@Composable
fun FrStoryScaffold(
    segmentCount: Int,
    currentIndex: Int,
    currentProgress: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onHoldStart: () -> Unit = {},
    onHoldEnd: () -> Unit = {},
    background: Color = MaterialTheme.colorScheme.scrim,
    closeContentDescription: String? = null,
    progressContentDescription: String? = null,
    action: (@Composable () -> Unit)? = null,
    scene: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        // Scene fills the whole surface; chrome overlays it.
        Box(modifier = Modifier.fillMaxSize()) { scene() }

        // Tap / hold gesture zones. Two side-by-side regions so a tap on the left rewinds and a tap
        // on the right advances; a press-and-hold (onPress that outlives the touch-slop) pauses.
        Row(modifier = Modifier.fillMaxSize()) {
            StoryTapZone(weight = 1f, onTap = onPrev, onHoldStart = onHoldStart, onHoldEnd = onHoldEnd)
            StoryTapZone(weight = 2f, onTap = onNext, onHoldStart = onHoldStart, onHoldEnd = onHoldEnd)
        }

        // Overlay action (e.g. a share CTA). Drawn AFTER the tap-zone Row so its pointer input is
        // hit first — a click here is consumed by the action, never the underlying advance/rewind
        // zone. Bottom-anchored and inset under the system bars; only laid out around the action so
        // the rest of the surface stays a pass-through to the gesture zones.
        if (action != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = Spacing.md, vertical = Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                action()
            }
        }

        // Chrome: progress bar + close, inset under the status bar / notch.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            FrStoryProgressBar(
                segmentCount = segmentCount,
                currentIndex = currentIndex,
                currentProgress = currentProgress,
                contentDescription = progressContentDescription,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.storyProgressInsetTop),
            )
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(imageVector = FrIcons.Close, contentDescription = closeContentDescription ?: "Close")
            }
        }
    }
}

@Composable
private fun RowScope.StoryTapZone(
    weight: Float,
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures(
                    // onPress suspends until release/cancel; we pause for the whole press and only
                    // count it as a tap if the press completed normally (not cancelled by a drag).
                    onPress = {
                        onHoldStart()
                        val released = tryAwaitRelease()
                        onHoldEnd()
                        if (released) onTap()
                    },
                )
            },
    )
}
