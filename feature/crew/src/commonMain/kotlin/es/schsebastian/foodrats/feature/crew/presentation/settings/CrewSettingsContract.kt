package es.schsebastian.foodrats.feature.crew.presentation.settings

import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew

data class CrewSettingsState(
    val crew: Crew? = null,
    val isOwner: Boolean = false,
    val myAccountId: AccountId? = null,
    val editingCrewName: String = "",
    val isSavingCrewName: Boolean = false,
    val isLeaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isSavingBlindVoting: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    /**
     * Members whose owner-initiated removal is in flight. The row stays in the list (the live crew
     * flow drops it only once the write lands) but renders disabled + a spinner while present here.
     */
    val removingMemberIds: Set<AccountId> = emptySet(),
    val error: CrewError? = null,
    /**
     * Live identities resolved from `AccountReadPort.observeMany`. Keys are the current
     * crew's member ids; a `null` value means the account doc is missing or deleted.
     */
    val identities: Map<AccountId, Account?> = emptyMap(),
    /**
     * The tagline text the owner is currently editing. Seeded once from [Crew.tagline] when the
     * crew snapshot first arrives ([taglineSeeded]); an empty string is a legitimate edited value
     * (the owner cleared it), so it must NOT be re-seeded from the crew on later listener emissions.
     */
    val editingTagline: String = "",
    /** True once [editingTagline] has been seeded from the first crew snapshot — prevents clobber. */
    val taglineSeeded: Boolean = false,
    val isSavingTagline: Boolean = false,
    /**
     * The welcome message the owner is currently editing. Seeded once from [Crew.welcomeMessage]
     * when the crew snapshot first arrives ([welcomeMessageSeeded]). Same one-time-seed pattern as
     * [editingTagline].
     */
    val editingWelcomeMessage: String = "",
    /** True once [editingWelcomeMessage] has been seeded from the first crew snapshot. */
    val welcomeMessageSeeded: Boolean = false,
    val isSavingWelcomeMessage: Boolean = false,
    /**
     * The weekly challenge the owner is currently editing. Seeded once from [Crew.weeklyChallenge]
     * when the crew snapshot first arrives ([weeklyChallengeSeeded]). Same one-time-seed pattern as
     * [editingTagline].
     */
    val editingWeeklyChallenge: String = "",
    /** True once [editingWeeklyChallenge] has been seeded from the first crew snapshot. */
    val weeklyChallengeSeeded: Boolean = false,
    val isSavingWeeklyChallenge: Boolean = false,
    /** True while a score-style write is in flight (C8). */
    val isSavingScoreStyle: Boolean = false,
    /** True once the score-style picker sheet is showing. */
    val showScoreStylePicker: Boolean = false,
    // ── Banner (C9) ──────────────────────────────────────────────────────────────────────────────
    /** True while a banner upload or delete write is in flight. */
    val isSavingBanner: Boolean = false,
    /** True while the "Remove banner?" confirmation dialog is showing. */
    val showRemoveBannerConfirm: Boolean = false,
) : MviState

sealed interface CrewSettingsIntent : MviIntent {
    data class CrewNameChanged(val value: String) : CrewSettingsIntent
    data object SaveCrewName : CrewSettingsIntent
    data class ToggleBlindVoting(val enabled: Boolean) : CrewSettingsIntent

    /**
     * The user tapped Share invite link. The Composable still fires the system share sheet directly
     * (it owns the [ShareController] + the localized message); this intent exists so the analytics
     * fire lives in the ViewModel, never in the UI layer.
     */
    data object ShareLinkTapped : CrewSettingsIntent
    data object SwitchCrew : CrewSettingsIntent
    data object Leave : CrewSettingsIntent
    data object RequestDelete : CrewSettingsIntent
    data object ConfirmDelete : CrewSettingsIntent
    data object CancelDelete : CrewSettingsIntent
    data class RemoveMemberConfirmed(val accountId: AccountId) : CrewSettingsIntent
    data object DismissError : CrewSettingsIntent

    /** Owner is editing the tagline text field. */
    data class TaglineChanged(val value: String) : CrewSettingsIntent
    /** Owner tapped "Save" on the tagline field. */
    data object SaveTagline : CrewSettingsIntent

    /** Owner is editing the welcome message text field. */
    data class WelcomeMessageChanged(val value: String) : CrewSettingsIntent
    /** Owner tapped "Save" on the welcome message field. */
    data object SaveWelcomeMessage : CrewSettingsIntent

    /** Owner is editing the weekly challenge text field. */
    data class WeeklyChallengeChanged(val value: String) : CrewSettingsIntent
    /** Owner tapped "Save" on the weekly challenge field. */
    data object SaveWeeklyChallenge : CrewSettingsIntent

    // ── Score style (C8) ─────────────────────────────────────────────────────────────────────────

    /** Owner tapped the Score-style row; show the picker sheet. */
    data object OpenScoreStylePicker : CrewSettingsIntent
    /** Owner dismissed the picker without selecting. */
    data object DismissScoreStylePicker : CrewSettingsIntent
    /** Owner selected [style] from the picker (optimistic; rolls back on error). */
    data class SetScoreStyle(val style: CrewScoreStyle) : CrewSettingsIntent

    // ── Banner (C9) ──────────────────────────────────────────────────────────────────────────────

    /** Owner picked a new banner image — [bytes] are the JPEG bytes from the image picker. */
    data class BannerPicked(val bytes: ByteArray) : CrewSettingsIntent
    /** Owner tapped "Remove banner"; triggers confirmation dialog. */
    data object RemoveBanner : CrewSettingsIntent
    /** Owner confirmed banner removal in the dialog. */
    data object ConfirmRemoveBanner : CrewSettingsIntent
    /** Owner dismissed the remove-banner dialog without confirming. */
    data object CancelRemoveBanner : CrewSettingsIntent
}

sealed interface CrewSettingsEffect : MviEffect {
    data object NavigateToCrewPicker : CrewSettingsEffect
    data object Left : CrewSettingsEffect
    data object Deleted : CrewSettingsEffect

    /**
     * A member was removed successfully — the screen shows a confirmation snackbar. Carries the
     * removed member's live [displayName] when known (`null` ⇒ a deleted/unresolved account, for
     * which the screen substitutes the localized deleted-user fallback) — i18n stays in the UI layer.
     */
    data class MemberRemoved(val displayName: String?) : CrewSettingsEffect
}
