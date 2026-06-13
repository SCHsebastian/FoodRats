package es.schsebastian.foodrats.app.notifications

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.notifications.domain.bus.NotificationBus
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder

/**
 * Foreground/in-app consumer of [NotificationBus.stream].
 *
 * When a push arrives while the app is in the foreground, the OS does NOT show a tray notification —
 * so without this consumer the localized [Reminder] would display nowhere (the historical
 * "foreground pushes show nothing" gap). This collects the bus and surfaces each incoming reminder
 * as a Material3 snackbar on [snackbarHostState].
 *
 * The reminder's `title`/`body` are already resolved through `NotificationStringKey` for the active
 * locale by `PushPayloadMapper.toReminder` (the bus only ever carries display-ready strings), so this
 * layer does no string assembly — it just renders [reminderToSnackbarMessage].
 *
 * Lifecycle discipline mirrors `EventsEffect`: collection runs under
 * [repeatOnLifecycle]`(RESUMED)`, so a push that lands while the host is paused is buffered by the
 * bus and delivered on resume rather than racing a torn-down composition. This holds no parallel
 * state — the bus is the single source of truth and the snackbar host is Compose-owned.
 *
 * Tap-to-navigate is intentionally NOT handled here: deep-linking belongs to the notification *tap*
 * path (the platform entry points already forward `data.link` to the `DeepLinkBus`), and
 * [Reminder]'s payload does not carry the day segment the deep-link parser requires for a meal route.
 */
@Composable
fun InAppPushBanner(
    bus: NotificationBus,
    snackbarHostState: SnackbarHostState,
) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(bus, snackbarHostState, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            bus.stream.collect { reminder ->
                val message = reminderToSnackbarMessage(reminder)
                if (message.isBlank()) return@collect
                FrLog.d(FrLog.Tags.Lifecycle) { "InAppPushBanner show kind=${reminder.kind} id=${reminder.id}" }
                snackbarHostState.showSnackbar(
                    message = message,
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
}

/**
 * Single snackbar line built from the already-localized [Reminder.title]/[Reminder.body].
 * Material3 snackbars render one message; we lead with the title and append the body when present.
 * Pure + internal so it can be unit-tested without Compose.
 */
internal fun reminderToSnackbarMessage(reminder: Reminder): String =
    listOf(reminder.title, reminder.body)
        .filter { it.isNotBlank() }
        .joinToString(separator = " — ")
