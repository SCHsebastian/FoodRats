package es.schsebastian.foodrats.core.domain.session

/**
 * Erases this device's account-scoped local caches on sign-out (security #3).
 *
 * Two reasons this must happen at sign-out:
 *  1. **Confidentiality** — the SQLDelight cache (`foodrats.db`) holds the previous user's feed
 *     meals, descriptions, GPS, rater ids/scores, and full crew member lists, and several DataStore
 *     keys hold drafts / the publish queue / the chosen audience. Without a wipe, a different account
 *     signing in on the same device — or anyone with adb / file access — reads that data.
 *  2. **Correctness** — the durable write outbox holds the previous user's *queued* mutations; if it
 *     survives, those replay under the NEXT account's identity.
 *
 * Device/human-scoped preferences (theme, locale, accepted-EULA version, analytics-consent decision)
 * are intentionally NOT erased — they belong to the device, not the account.
 *
 * Implemented in the data layer (it spans the SQLDelight cache + DataStore); invoked from the single
 * sign-out funnel. Best-effort — a wipe failure must never block the sign-out itself.
 */
fun interface LocalDataEraser {
    suspend fun eraseLocalAccountData()
}
