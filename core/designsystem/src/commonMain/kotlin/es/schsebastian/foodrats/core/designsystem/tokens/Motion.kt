package es.schsebastian.foodrats.core.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Motion tokens. All durations are in milliseconds.
 *
 * - `quick`  — micro feedback (press, ripple, icon toggle)
 * - `short`  — UI element transitions (chip select, error banner enter/exit)
 * - `medium` — container transitions (card entry, slide between days)
 * - `long`   — celebratory or onboarding flourishes
 */
object Motion {
    const val quick = 120
    const val short = 220
    const val medium = 320
    const val long = 480

    /** Default standard easing used for most enter/exit transitions. */
    val Standard: Easing = FastOutSlowInEasing

    /** Decelerated entry — content lands on screen. */
    val Decelerated: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Accelerated exit — content leaves the screen. */
    val Accelerated: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Playful overshoot, used sparingly (badge pulse, scale-in confirmation). */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1.2f)
}
