package es.schsebastian.foodrats.app.recap

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Android actual: toggles status-bar icon appearance via the AndroidX compat wrapper
 * [WindowInsetsControllerCompat] rather than the raw platform `WindowInsetsController`. The
 * compat wrapper handles OEM quirks (some Samsung One UI builds ignore the raw
 * `setSystemBarsAppearance(...)` call when the OS is in light mode), so forcing white icons on
 * the dark recap actually takes effect. Captures the current `isAppearanceLightStatusBars` flag
 * before overriding so it can be restored on dispose, ensuring the caller's theme is not
 * permanently affected.
 */
@Composable
actual fun StatusBarIconsAppearance(lightIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(lightIcons) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, view)
            // Snapshot the current state so we can restore it on dispose.
            val wasLight = controller.isAppearanceLightStatusBars
            // `isAppearanceLightStatusBars = false` => light (white) icons, correct for the dark
            // recap background; `= true` => dark icons for a light background.
            controller.isAppearanceLightStatusBars = !lightIcons

            onDispose {
                // Re-resolve the controller on dispose; the window may have been recreated.
                val restoreWindow = (view.context as? Activity)?.window
                if (restoreWindow != null) {
                    WindowInsetsControllerCompat(restoreWindow, view)
                        .isAppearanceLightStatusBars = wasLight
                }
            }
        } else {
            onDispose { }
        }
    }
}
