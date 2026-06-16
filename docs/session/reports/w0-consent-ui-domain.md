# Report — `w0-consent-ui-domain`

Consent capability: DOMAIN/port + decision-model layer that the consent UI (`w0-consent-ui-presentation`)
will drive. Domain/data-port layer ONLY — no UI screen, no routing gate, no i18n copy.

## Verdict: mostly VERIFY + one minimal gap filled

The analytics base (2026-06-14) already shipped almost all of this. I verified the existing surface
against what roadmap §0.2 needs, added the one missing signal the routing gate needs, and locked the
decision/version model + the (previously untested) persistence with tests.

## What already existed (verified, unchanged)

- `core/domain/.../analytics/Consent.kt`
  - `sealed interface ConsentDecision` = `Unknown` | `Granted(version, at)` | `Denied(version, at)`.
  - `val ConsentDecision.isAnalyticsGranted` — true only for a **current-version** `Granted`. The
    predicate the *analytics* gate (`ConsentGatedAnalytics`) trusts.
  - `interface ConsentPort { val decision: Flow<ConsentDecision>; suspend grant(); suspend deny();
    suspend revoke() }`. Vendor-free (Konsist-enforced).
- `core/domain/.../analytics/AnalyticsConfig.kt` — `CURRENT_CONSENT_VERSION = 1`; bumping it drops a
  stale stored grant below current back to "needs re-consent" (modeled via the predicates).
- `core/data/.../preferences/ConsentRepository.kt` — `ConsentPort` over `AppPreferences` (DataStore).
  - Three DataStore keys (`analytics_consent_state` / `_version` / `_decided_at`) in `datastore/Keys.kt`.
  - Absence of state reads back as `Unknown`.
  - `grant()`/`deny()`/`revoke()` each stamp `CURRENT_CONSENT_VERSION` + `clock.now()`; `revoke()` ==
    `deny()` (settles on `Denied`).
  - **One** `withContext(dispatchers.io)` per public write. Rule #4 satisfied.
- `core/data/.../analytics/ConsentGatedAnalytics.kt` — the single consent choke point (unchanged). It
  observes `decision`, maps via `isAnalyticsGranted`, caches a volatile flag, flips the SDK on grant
  transition, and `resetData()` on revoke. Tested by `ConsentGatedAnalyticsTest` (5/5).
- Koin: `single<ConsentPort> { ConsentRepository(...) }` already bound in
  `shared/.../app/di/CoreDataModule.kt`. No DI change needed.
- `core/data/build.gradle.kts` already has `withHostTest { isIncludeAndroidResources = true }`, so
  `:core:data:testAndroidHostTest` runs `commonTest` (despite the stale "No host-test task" note in
  `core/data/CLAUDE.md`).

## The gap I filled (minimal, non-invasive)

The routing gate (§0.2 bullet 4) needs a single trusted **"must I show the consent screen?"** signal.
`isAnalyticsGranted` is NOT that signal: a **current-version `Denied`** is "not granted" for tracking
yet is a *settled* decision the gate must NOT re-prompt. Without a dedicated predicate the gate would
have to re-derive the version-staleness rule inline — duplicating the logic that lives next to
`isAnalyticsGranted`, exactly the duplication the charter warns against.

Added to `core/domain/.../analytics/Consent.kt`:

```kotlin
val ConsentDecision.needsDecision: Boolean
    get() = when (this) {
        is ConsentDecision.Unknown -> true
        is ConsentDecision.Granted -> version < AnalyticsConfig.CURRENT_CONSENT_VERSION
        is ConsentDecision.Denied  -> version < AnalyticsConfig.CURRENT_CONSENT_VERSION
    }
```

Truth table (for the presentation task):

| decision                          | `isAnalyticsGranted` | `needsDecision` | gate behaviour                |
|-----------------------------------|----------------------|-----------------|-------------------------------|
| `Unknown`                         | false                | **true**        | show consent screen           |
| `Granted(current)`                | **true**             | false           | settled — proceed, track ON   |
| `Denied(current)`                 | false                | false           | settled — proceed, track OFF  |
| `Granted(<current)` (stale)       | false                | **true**        | re-prompt (schema bumped)     |
| `Denied(<current)` (stale)        | false                | **true**        | re-prompt (schema bumped)     |

`@JvmInline` N/A (extension prop, no value class added). No new file, no new type — just one extension
property beside the existing one, so the model surface stays small.

## Tests added

- `core/domain/.../analytics/ConsentDecisionTest.kt` (5) — locks `isAnalyticsGranted` + `needsDecision`
  across Unknown / current-grant / current-deny / stale-grant / stale-deny.
- `core/data/.../preferences/ConsentRepositoryTest.kt` (4) — round-trips the repo over an in-memory
  `FakeDataStore` + `FixedClock`: absent→Unknown, grant→`Granted(current, now)`, deny→`Denied(current)`,
  revoke-after-grant→`Denied`. This path was previously untested (only `ConsentGatedAnalyticsTest`
  existed, and it uses a `FakeConsent`, never the real repository).

## Not done (out of scope — `w0-consent-ui-presentation`)

Consent Composable screen; `RootNavViewModel` consent gate / `Route` Public/Protected wiring; i18n
en/es copy; `AccountWritePort` stamping of `Account.dataConsentVersion` / `dataConsentGrantedAt` on
grant (that's the presentation/account-write side); Play Data-Safety / `PrivacyInfo.xcprivacy` (manual).

## Verify

```
$ ./gradlew :core:domain:testAndroidHostTest :core:data:testAndroidHostTest
> Task :core:data:testAndroidHostTest
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 20s
40 actionable tasks: 10 executed, 30 up-to-date
```

New classes executed (JUnit XML):
- `ConsentDecisionTest` — tests="5" skipped="0" failures="0" errors="0"
- `ConsentRepositoryTest` — tests="4" skipped="0" failures="0" errors="0"

Konsist (`:core:domain:testAndroidHostTest`) green → domain stays vendor-free.
