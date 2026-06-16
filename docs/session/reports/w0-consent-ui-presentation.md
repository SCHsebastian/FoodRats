# Report — `w0-consent-ui-presentation`

## Status
DONE. The first-run analytics-consent screen, the root-nav routing gate, the consent ViewModel, the
Koin wiring, and en/es i18n copy are all implemented and verified. `:shared:testAndroidHostTest` is
green (31/31, 0 failures) and `:androidApp:assembleDebug` is green.

## What was built

### 1. Consent screen + ViewModel (home = `:shared`, layering justification below)
- `shared/.../app/consent/ConsentContract.kt` — `ConsentState(isSubmitting)`, `ConsentIntent.{Grant,Deny}`,
  `ConsentEffect.Decided`. Single source of truth; no parallel flow.
- `shared/.../app/consent/ConsentViewModel.kt` — MVI VM over `ConsentPort` + `AnalyticsPort`
  (`= NoopAnalyticsTracker` default so existing tests stay green). `Grant` → `consent.grant()` then
  `analytics.track(AnalyticsEvent.ConsentGranted(CURRENT_CONSENT_VERSION))` AFTER the write lands
  (charter rule #9). `Deny` → `consent.deny()`, records NO event (tracking is off when denied —
  emitting would be a consent violation). Guards against double-submit via `isSubmitting`. No
  `withContext`, no navigation in the VM.
- `shared/.../app/consent/ConsentScreen.kt` — uses only `Fr*` atoms (`FrLogo`, `FrText`, `FrButton`
  Primary/Ghost, `FrScreenScaffold`); explains what is/isn't collected + a privacy note framing
  (no ads, no cross-app tracking, no selling, changeable in Settings) per analytics spec §8; offers
  "Allow analytics" (Grant) and "No thanks" (Deny). All text via `resolve(SharedStringKey.*)`.

**Layering home — why `:shared`, not a new feature module or `:feature:auth`:** the consent screen is
a root-nav *onboarding gate* (sibling to Splash, which is already inline in `NavGraph`, and to
NotificationPermission). It depends only on `:core:domain` (`ConsentPort`), `:core:designsystem`, and
`:core:i18n` — no Firebase, no other feature. `:shared` already owns the root nav stage machine
(`RootNavViewModel`), `Route`, `NavGraph`, and a `SharedStringKey` enum with its own
`composeResources`, so it is the lowest-overhead, layering-correct home. A standalone
`:feature:consent` module would add a module for one screen with zero feature-specific domain/data;
`:feature:auth` would be wrong (consent is not auth, and adding it there creates a cross-cutting
dependency in the wrong module).

### 2. Routing gate (no regression to login-flash / deep-link-resume)
- `RootNavContract.kt` — added `RootStage.NeedsConsent` (between `NeedsCrew` and `Ready`).
- `RootNavViewModel.kt` — now injects `consent: ConsentPort`. The 3-input `combine` became a 4-input
  combine; the 4th input is `consent.decision.map { it.needsDecision }.distinctUntilChanged()`. Stage
  order: SignIn → NotificationPermission → Crew → **Consent** → Ready. `applyStage` emits
  `NavigateTopLevel(Route.Consent)` for `NeedsConsent`. Everything still flows through the SAME
  `navLock`-serialized stage path + `EventsEffect` deferral; the deep-link stash/resume and
  resolving-session (no placeholder null → no SignIn flash) logic are untouched.
- **Settled-decision semantics come for free:** `needsDecision` is true for `Unknown` and for any
  stored decision below `AnalyticsConfig.CURRENT_CONSENT_VERSION`, and FALSE for a current-version
  `Denied`. So an explicit decline is settled and does NOT re-prompt; bumping the consent version
  re-arms the gate with no extra code (regression-locked by a test).
- `Route.kt` — added `Route.Consent : Protected` + its `requiresSession()` arm (exhaustive `when`,
  no `else`, so the compiler forced classification).
- `NavGraph.kt` — `composable<Route.Consent> { ConsentScreen(onDecided = {}) }`. `onDecided` is a
  no-op: writing the decision drives `RootNavViewModel` to emit `NavigateTopLevel(Main)` (same pattern
  as NotificationPermission/CrewPicker). `RouteAccessTest` updated to include `Route.Consent`.

### 3. Koin wiring
- `AppModule.kt` — added `viewModel { ConsentViewModel(consent = get(), analytics = get()) }`
  (EXPLICIT, not `viewModelOf`, because `analytics` has a Noop default that `viewModelOf` would
  short-circuit — same convention as the feature `*Module` files). `RootNavViewModel`'s new
  `ConsentPort` param resolves automatically via the existing `single<ConsentPort>` in
  `coreDataModule` (`viewModelOf(::RootNavViewModel)` unchanged). `rootNavModule` lives in `appModules`
  already; no new module to register.

### 4. i18n (en + es)
- `shared/.../composeResources/values/strings.xml` + `values-es/strings.xml` — `consent_title`,
  `consent_body`, `consent_privacy_note`, `consent_allow`, `consent_deny`.
- `SharedStringKey.kt` — `ConsentTitle/ConsentBody/ConsentPrivacyNote/ConsentAllow/ConsentDeny`.

### 5. Tests
- `RootNavViewModelTest.kt` (now 7): added `shows_consent_when_decision_needed_then_proceeds_to_main_after_a_decision`
  (Unknown → routes to Consent, holds, then Main after `deny()` settles) and
  `settled_current_version_denial_does_not_re_show_consent` (current-version Denied → straight to Main,
  no re-prompt). Existing 5 tests updated to pass a fake `ConsentPort` defaulting to a current-version
  grant. All green.
- `ConsentViewModelTest.kt` (new, 3): grant writes decision + records `consent_granted`; deny writes
  decision + records NO event; `isSubmitting` clears after a decision.

## Verify
Command: `./gradlew :shared:testAndroidHostTest`
Last 3 lines:
```
> Task :shared:testAndroidHostTest

BUILD SUCCESSFUL in 5s
```
Per-suite (from JUnit XML): `ConsentViewModelTest` 3/3, `RootNavViewModelTest` 7/7,
`RouteAccessTest` 3/3; whole shared suite = 31 tests, 0 failures, 0 errors.
Also `./gradlew :androidApp:assembleDebug` → `BUILD SUCCESSFUL` (full graph + new DI/Route/NavGraph
wiring compiles).

## Decisions
- **Gate placement:** consent is the LAST onboarding gate (after crew, immediately before `Ready`/Main),
  honoring §0.2 "after sign-in, before Main."
- **Deny records no analytics event:** there is no `ConsentDenied` event in the taxonomy and the
  handoff forbids adding domain/port surface; a denial cannot be tracked anyway (tracking is off), so
  this is correct, not a gap.
- **`Account.dataConsentVersion` stamp NOT wired (deliberate).** §0.2 bullet 2 asks to mirror the
  consent version onto the account doc via `AccountWritePort`, but (a) `AccountWritePort` has no such
  method and the handoff explicitly says "Do NOT add new domain/port surface — everything you need
  exists," and (b) the decision IS already durably persisted by `ConsentPort` (DataStore), which is
  what the gate and the analytics decorator read. The account-doc mirror is a secondary copy; adding
  it requires extending `AccountWritePort` + a Firestore writer in `:feature:auth`. Deferred — see
  Suggested next.

## Blockers
None.

## Manual user steps (track, not codeable here — §0.2 bullet 5 / analytics spec §8)
- Play **Data Safety** form: App activity + Device IDs, purpose Analytics, shared = no, optional,
  deletable.
- iOS `PrivacyInfo.xcprivacy` with `NSPrivacyTracking=false`.
- Manifest/plist collection-disabled defaults (`firebase_analytics_collection_enabled=false` +
  Consent Mode `analytics_storage=denied`; iOS plist equivalents) as the backstop.
- Until a user actually grants on this screen, analytics stays a no-op (correct — the gate now exists,
  so it is no longer a *permanent* no-op).

## Suggested next
- **Settings toggle (§0.2 bullet 3):** add a revoke/re-grant toggle on the Profile/Settings surface
  (`:feature:auth` `ProfileScreen`) calling `ConsentPort.revoke()` / `grant()`. Out of this task's
  scope (different module; §0.2 lists it as a separate bullet) — pick up as a follow-up.
- **`Account.dataConsentVersion` mirror:** if the product wants the consent version on the account doc
  (e.g. for server-side audit), extend `AccountWritePort` with a stamp method + Firestore writer and
  call it from `ConsentViewModel.handle(Grant)` after `grant()`.
