# Handoff → `w0-consent-ui-presentation`

The domain/data-port consent layer is COMPLETE and tested. Build only the UI screen, the routing
gate, the settings toggle, and i18n copy. Do NOT add new domain/port surface — everything you need
exists. Inject `ConsentPort` via Koin (`single<ConsentPort>` already bound in
`shared/.../app/di/CoreDataModule.kt`).

## Imports

```kotlin
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.analytics.ConsentDecision
import es.schsebastian.foodrats.core.domain.analytics.needsDecision        // gate: show screen?
import es.schsebastian.foodrats.core.domain.analytics.isAnalyticsGranted   // (analytics gate uses this; you rarely need it)
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsConfig      // CURRENT_CONSENT_VERSION
```

`ConsentPort` (in `:core:domain`):
```kotlin
interface ConsentPort {
    val decision: Flow<ConsentDecision>   // local-first; emits before any network
    suspend fun grant()                   // -> ConsentDecision.Granted(CURRENT_CONSENT_VERSION, now)
    suspend fun deny()                    // -> ConsentDecision.Denied(CURRENT_CONSENT_VERSION, now)
    suspend fun revoke()                  // == deny(); settings opt-out / account deletion
}
```

`ConsentDecision` (sealed): `Unknown` | `Granted(version: Int, at: Instant)` | `Denied(version, at)`.

## (a) Read whether a decision is NEEDED — the routing gate

Observe the port and use the predicate; do NOT re-derive version staleness yourself:

```kotlin
consentPort.decision
    .map { it.needsDecision }            // true => route to the consent screen
    .distinctUntilChanged()
```

`needsDecision` is true for `Unknown` AND for any stored decision below
`AnalyticsConfig.CURRENT_CONSENT_VERSION` (schema bump ⇒ re-consent). It is **false** for a
current-version `Denied` — an explicit decline is settled; do NOT re-prompt a user who said no.
(`isAnalyticsGranted` is a different question — "is tracking on?" — and is already consumed inside
`ConsentGatedAnalytics`; you do not need to re-check consent at any call site.)

Gate placement per §0.2: insert it in `RootNavViewModel`'s stage machine **after `Ready`**, before
landing on Main. Reuse the existing deferred-effect pattern (`EventsEffect` /
`repeatOnLifecycle(RESUMED)`) — do NOT drop the navigate effect (that was the prior nav-audit bug).

## (b) Grant — on the screen's "Allow" / settings re-grant

```kotlin
consentPort.grant()   // suspend; writes Granted(version=CURRENT_CONSENT_VERSION). The
                      // ConsentGatedAnalytics decorator auto-flips applyConsent(true) by OBSERVING
                      // decision — you do NOT call AnalyticsPort.applyConsent yourself.
```

Then (your scope) stamp `Account.dataConsentVersion = CURRENT_CONSENT_VERSION` /
`dataConsentGrantedAt = now` via `AccountWritePort` per §0.2 bullet 2. Emit the
`consent_granted(version)` analytics event AFTER `grant()` returns Ok (charter rule #9 — call site in
the VM, never a use case).

## (c) Deny — on the screen's "Decline"

```kotlin
consentPort.deny()    // suspend; writes Denied(version=CURRENT_CONSENT_VERSION). Gate then treats the
                      // user as settled (needsDecision == false) and proceeds to Main with tracking OFF.
```

Settings opt-out later: call `consentPort.revoke()` (alias of `deny()`); the decorator pushes
`resetData()` to the SDK automatically on the transition.

## Re-prompt on version bump

Nothing extra to wire: when `AnalyticsConfig.CURRENT_CONSENT_VERSION` is bumped, every stored
decision below it becomes `needsDecision == true`, so the gate re-shows the screen for free.

## Verify (your task)

`:core:data:testAndroidHostTest` (ConsentGatedAnalytics + ConsentRepository) + `:shared:testAndroidHostTest`
(your new gate logic — add a `RootNavViewModelTest` case for the consent stage).
