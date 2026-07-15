package es.schsebastian.foodrats.app.locale

import androidx.compose.runtime.Composable

/**
 * Keeps the Android streak-nudge notification channel's user-visible name in sync with the
 * in-app language. Must be called INSIDE [ProvideAppLocale]'s content — the channel name is
 * resolved via compose-resources `getString`, which is only guaranteed to read the user's chosen
 * language after `LocalAppLocale.provides` has applied it in the same frame (resolving from
 * `Application.onCreate` raced the first composition and stuck the channel on the device locale).
 * Re-runs on every language change, so the channel renames live. No-op on iOS (no channel concept).
 */
@Composable
internal expect fun SyncNotificationChannelName()
