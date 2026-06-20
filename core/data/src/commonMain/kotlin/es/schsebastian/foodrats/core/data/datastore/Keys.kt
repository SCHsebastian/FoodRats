package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object Keys {
    val SessionToken         = StoreKey(stringPreferencesKey("session_token"))
    val ActiveCrewId         = StoreKey(stringPreferencesKey("active_crew_id"))
    val NotificationsAllowed = StoreKey(booleanPreferencesKey("notifications_allowed"))

    /**
     * One-shot flag set after the user has completed the post-signin notification
     * permission screen (whether they tapped Allow, Skip, or Open Settings). Drives
     * the RootNavViewModel gate that routes new accounts through that screen exactly
     * once. Cleared by signOut so a different account on the same device sees the
     * prompt again.
     */
    val NotificationsPermissionPrompted = StoreKey(booleanPreferencesKey("notifications_permission_prompted"))
    val LocaleTag            = StoreKey(stringPreferencesKey("locale_tag"))
    val ThemeMode            = StoreKey(stringPreferencesKey("theme_mode"))

    /**
     * Comma-separated `HH:mm` local times for the daily meal reminders (max 3), e.g. `"14:00,19:00"`.
     * Absent = the default single 14:00 reminder. Owned by `MealReminderScheduleRepository`.
     */
    val MealReminderTimes    = StoreKey(stringPreferencesKey("meal_reminder_times"))
    val MealDraftJson        = StoreKey(stringPreferencesKey("meal_draft_json"))

    /**
     * True iff a meal-upload was enqueued and has not yet succeeded. Survives
     * process death so the coordinator can resume the upload on next launch
     * (in addition to WorkManager retries on Android). Cleared by the
     * coordinator on successful publish; left set on failure so retries can
     * pick it up.
     */
    val MealUploadPending    = StoreKey(booleanPreferencesKey("meal_upload_pending"))

    /**
     * The durable offline-first publish queue (roadmap §5.2): a JSON array of
     * queued drafts (each with its status, attempt count, and base64 plate bytes)
     * so a process death or airplane-mode session never loses a composed plate.
     * Distinct from [MealDraftJson] (the single in-flight composer draft) and
     * [MealUploadPending] (the legacy single-flag resume marker). Owned by
     * `:feature:meal`'s `DraftQueueLocalStore`.
     */
    val DraftQueueJson       = StoreKey(stringPreferencesKey("draft_queue_json"))
    val IngredientCatalogJson = StoreKey(stringPreferencesKey("ingredient_catalog_json"))

    /**
     * The durable write outbox (offline-first P2): a JSON array of queued
     * rate / comment / reaction / crew-admin mutations (each with its
     * lifecycle status + attempt count), so a process death or airplane-mode
     * session never loses a user mutation. Coexists with — and is distinct from
     * — [DraftQueueJson] (the meal-publish queue, which is untouched). Owned by
     * `:core:data`'s `OutboxLocalStore`.
     */
    val OutboxJson           = StoreKey(stringPreferencesKey("outbox_json"))

    /**
     * BRIDGE (offline-first P1): the last-seen crew list for the signed-in member,
     * persisted as a JSON array of `CrewDto` so the crew picker survives offline.
     * Disposable — replaced by the SQLDelight read model in offline-first P3. Owned
     * by `:feature:crew`'s `CrewListCache`.
     */
    val CrewListJson         = StoreKey(stringPreferencesKey("crew_list_json"))

    /**
     * The EULA / Community-Guidelines version the user accepted at the login-screen gate (UGC
     * compliance §6). Absent = never accepted → the gate requires acceptance before sign-in. NOT
     * cleared on sign-out (a EULA is accepted by the human/device, not the account). Owned by
     * `EulaRepository`.
     */
    val EulaAcceptedVersion       = StoreKey(intPreferencesKey("eula_accepted_version"))

    // ── Analytics consent (GDPR/CCPA opt-in). Absence of [AnalyticsConsentState] = "Unknown"
    //    (no decision yet → analytics is a hard no-op). See ConsentRepository / ConsentGatedAnalytics.
    /** `"granted"` | `"denied"`; absent = no decision recorded yet. */
    val AnalyticsConsentState     = StoreKey(stringPreferencesKey("analytics_consent_state"))
    /** Consent-schema version in effect when the decision was made (re-consent on bump). */
    val AnalyticsConsentVersion   = StoreKey(intPreferencesKey("analytics_consent_version"))
    /** Epoch millis of the decision. */
    val AnalyticsConsentDecidedAt = StoreKey(longPreferencesKey("analytics_consent_decided_at"))
}
