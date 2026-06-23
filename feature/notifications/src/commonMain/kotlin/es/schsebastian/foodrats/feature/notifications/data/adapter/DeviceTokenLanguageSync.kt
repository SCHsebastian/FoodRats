package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RegisterDeviceTokenUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps the device-token doc's `languageTag` current so the server localizes this device's OS
 * notifications to the active in-app language.
 *
 * Re-registers (an idempotent upsert) whenever the signed-in account or the in-app locale changes:
 *  - **sign-in** — `SignInViewModel` already registers once on the sign-in event, but an already
 *    signed-in user reopening the app never re-signs-in; this observer covers that cold-start gap.
 *  - **language switch** — changing the language mid-session must update the stored tag without
 *    waiting for the next sign-in.
 *
 * Eager (`createdAtStart`) singleton, mirroring `MealReminderScheduler`. The use case re-reads the
 * token/session/locale itself; this observer only decides *when* to re-run it.
 */
internal class DeviceTokenLanguageSync(
    scope: CoroutineScope,
    session: SessionProvider,
    localePort: LocalePort,
    private val register: RegisterDeviceTokenUseCase,
) {
    init {
        combine(session.current, localePort.locale) { s, locale -> s?.accountId to locale }
            .distinctUntilChanged()
            // Only register while signed in; the use case would no-op (Unavailable) otherwise.
            .filter { (accountId, _) -> accountId != null }
            .onEach { register() }
            .launchIn(scope)
    }
}
