package es.schsebastian.foodrats.core.designsystem.structural

/**
 * iOS: Compose-on-Skia renders `Modifier.blur` on every supported version, so the media floor blur
 * is always real here.
 */
internal actual fun structuralBlurSupported(): Boolean = true
