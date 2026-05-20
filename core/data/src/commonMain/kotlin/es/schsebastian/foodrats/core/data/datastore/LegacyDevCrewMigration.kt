package es.schsebastian.foodrats.core.data.datastore

import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.flow.first

/**
 * One-shot migration for users upgrading from the dev-crew-hardcoded build.
 *
 * The pre-fix sign-in flow stamped every account with `activeCrewId = "test-crew-1"`
 * (see the removed `FirebaseAuthRepository.finishSignIn` branch). Users who never
 * fully signed out across the upgrade still carry that pref, and the Firestore
 * meal-create rule denies them with PERMISSION_DENIED → user-facing
 * "No tienes acceso a este grupo." Running this on every app start clears the
 * stale value so RootNavViewModel emits NeedsCrew → CrewPicker on the next tick
 * and the user can pick or create a real crew.
 *
 * Safe to call on every cold start: no-op once the pref is null or any non-legacy
 * value. The mirror migration in `FirebaseAuthRepository.finishSignIn` stays for
 * users who pre-update tokens later sign back in via a different account.
 */
suspend fun AppPreferences.clearLegacyDevCrewIfPresent() {
    val current = observe(Keys.ActiveCrewId).first()
    if (current == LEGACY_DEV_CREW_ID) {
        FrLog.d("LegacyMigration") { "wiping legacy activeCrewId='$LEGACY_DEV_CREW_ID'" }
        clear(Keys.ActiveCrewId)
    }
}

private const val LEGACY_DEV_CREW_ID = "test-crew-1"
