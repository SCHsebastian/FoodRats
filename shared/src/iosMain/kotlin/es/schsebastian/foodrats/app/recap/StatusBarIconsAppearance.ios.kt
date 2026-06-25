package es.schsebastian.foodrats.app.recap

import androidx.compose.runtime.Composable

/**
 * iOS no-op: Compose Multiplatform on iOS derives status-bar icon tint from the UIKit
 * `preferredStatusBarStyle`, which honours the active `colorScheme`. Since [WeeklyStoryScreen]
 * wraps its content in `FoodRatsTheme(darkTheme = true)`, the system automatically shows light
 * (white) status-bar icons — no explicit override is needed here.
 */
@Composable
actual fun StatusBarIconsAppearance(lightIcons: Boolean) = Unit
