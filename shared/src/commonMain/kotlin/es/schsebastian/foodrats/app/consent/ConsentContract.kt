package es.schsebastian.foodrats.app.consent

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState

/**
 * @param isSubmitting true while a grant/deny decision is being persisted; disables the buttons so a
 *   double-tap can't write twice. The single source of truth — no parallel flow holds it.
 */
data class ConsentState(
    val isSubmitting: Boolean = false,
) : MviState

sealed interface ConsentIntent : MviIntent {
    /** User opted IN to analytics. */
    data object Grant : ConsentIntent

    /** User explicitly declined. A current-version decline is settled and will not re-prompt. */
    data object Deny : ConsentIntent
}

sealed interface ConsentEffect : MviEffect {
    /**
     * The decision was durably written. The screen has no destination of its own — `RootNavViewModel`
     * observes `ConsentPort.decision` and advances the stage machine off `NeedsConsent` once the write
     * lands, so the host can treat this as a "decided, you may close" signal (it does not navigate).
     */
    data object Decided : ConsentEffect
}
