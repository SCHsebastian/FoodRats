package es.schsebastian.foodrats.core.designsystem.structural

import android.os.Build

/**
 * Android: `Modifier.blur` is RenderEffect-backed and only renders on API 31+. On API 30 (the app's
 * `minSdk`) it's a no-op, so [FrMediaFloor] must not lean on it for legibility.
 */
internal actual fun structuralBlurSupported(): Boolean = Build.VERSION.SDK_INT >= 31
