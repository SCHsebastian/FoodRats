package es.schsebastian.foodrats.app.recap

import androidx.compose.runtime.Composable

/**
 * While in composition, overrides the system status-bar icon tint and restores the previous
 * value on dispose.
 *
 * [lightIcons] = `true` → white (light) icons, suited for dark backgrounds (e.g. the
 * forced-dark recap player). [lightIcons] = `false` → dark icons, suited for light backgrounds.
 *
 * Android actual: `DisposableEffect` on the activity window's `InsetsController` (API 30+,
 * minSdk = 30) — no extra dependency needed.
 * iOS actual: no-op; the iOS system automatically tints status-bar icons based on the
 * `preferredStatusBarStyle` of the topmost view controller. The recap screen is wrapped in
 * `FoodRatsTheme(darkTheme = true)` which already sets the correct appearance on iOS via the
 * Compose-MP UIKit integration.
 */
@Composable
expect fun StatusBarIconsAppearance(lightIcons: Boolean)
