# Report — `w0-consent-settings-toggle`

## Status
DONE. An analytics-consent revoke/re-grant toggle is wired into the Profile/Settings screen
(roadmap §0.2 bullet 3, GDPR Art. 7(3) withdrawal). `:feature:auth:testAndroidHostTest` is green.

## Prior-work check
No consent-toggle work had started. The working tree already carried the `w0-consent-ui-*`
(first-run screen + gate, in `:shared`) and `w0-account-deletion-*` (modified `ProfileViewModel`
+ new `ProfileViewModelTest`) changes from sibling tasks — I built on top of those, finishing my
slice rather than restarting.

## What was built

### 1. ViewModel — `ProfileViewModel.kt` (`:feature:auth`, MVI single source of truth)
- Injects `consent: ConsentPort` (positioned before the defaulted `analytics` param so the explicit
  Koin binding still controls graph resolution).
- New state field `ProfileState.analyticsConsentGranted: Boolean`, driven SOLELY by an init collector
  observing `consent.decision.map { it.isAnalyticsGranted }.distinctUntilChanged()` — the row reflects
  the observed decision (single source of truth; the handler never writes it directly).
- New intent `ProfileIntent.AnalyticsConsentToggled(enabled)` → `doSetAnalyticsConsent`:
  - **on** → `consent.grant()`, THEN `analytics.track(AnalyticsEvent.ConsentGranted(CURRENT_CONSENT_VERSION))`
    (charter rule #9 — call site in the VM, after the write lands; mirrors the first-run screen).
  - **off** → `consent.revoke()` (the port's documented settings-opt-out path; the
    `ConsentGatedAnalytics` decorator pushes `resetData()` on the granted→denied transition). No event
    fired — tracking is now off, so emitting one would be a consent violation.
  - No `withContext`, no `applyConsent` call (the decorator owns the SDK flip), no parallel flow.

### 2. Screen — `ProfileScreen.kt`
- New `FrSettingsRow` in the Preferences section (after Notifications, before the Danger Zone), with an
  `FrSwitch` trailing (per project memory: prefer `FrSwitch`, never inline raw Material3). Icon
  `FrIcons.Stats` (the bar-chart glyph; reused — no new icon alias needed). Subtitle toggles between
  on/off strings. `checked = state.analyticsConsentGranted`; `onCheckedChange` fires the intent.

### 3. i18n (en + es)
- `AuthStringKey`: `ProfileAnalyticsRow`, `ProfileAnalyticsSubtitleOn`, `ProfileAnalyticsSubtitleOff`.
- `values/strings.xml` + `values-es/strings.xml`: matching `auth_profile_analytics_row` /
  `_subtitle_on` / `_subtitle_off`. Copy frames it as anonymous usage data, "no personal content is
  ever collected", changeable at any time (privacy framing per analytics spec §8).

### 4. Koin — `AuthModule.kt` + `AuthModuleVerifyTest.kt`
- Extended the existing EXPLICIT `viewModel { ProfileViewModel(...) }` binding with `consent = get()`
  (resolves the `single<ConsentPort>` bound in `coreDataModule`).
- Added `ConsentPort::class` to `AuthModuleVerifyTest.extraTypes` (consumed cross-module, not bound by
  auth) + updated the KDoc.

### 5. Tests — `ProfileViewModelTest.kt`
- New `FakeConsentPort` (mutable `MutableStateFlow<ConsentDecision>` double; `grant`/`deny`/`revoke`
  push a current-version decision and bump call counters). `buildViewModel` gained a defaulted
  `consent` param so the two pre-existing delete-account tests are untouched.
- `consent_toggle_on_grants_records_event_and_row_reflects_decision`: Unknown start → row off →
  toggle on → `grantCount==1`, `revokeCount==0`, row reads granted, `ConsentGranted(version)` fired once.
- `consent_toggle_off_revokes_records_no_event_and_row_reflects_decision`: current-version Granted start
  → row on → toggle off → `revokeCount==1`, `grantCount==0`, row reads off, NO analytics event.
- Both use `expectMostRecentItem()` (the MVI coalescing pattern).

## Verify
Command: `./gradlew :feature:auth:testAndroidHostTest`
Last 3 lines:
```
> Task :feature:auth:testAndroidHostTest

BUILD SUCCESSFUL in 4s
```
Per-suite (JUnit XML): `ProfileViewModelTest` 4 tests / 0 failures (2 new consent tests + 2 existing
delete-account tests still green); `AuthModuleVerifyTest` 1 / 0 (graph complete with `ConsentPort`).

## Decisions
- **Off path uses `revoke()`, not `deny()`.** The port models `revoke()` as the settings/account-deletion
  opt-out (KDoc + handoff); `deny()` is the first-run decline. `revoke()` is the correct "withdraw after
  settled" verb and the decorator resets SDK data on it. (`revoke()` == `deny()` in the repo, but the
  intent is clearer.)
- **Switch state is purely observed, never optimistically written** (unlike the Notifications toggle,
  which is optimistic-with-rollback). Consent writes are local-first DataStore and cannot meaningfully
  fail in a way the user must act on; reading from `decision` keeps the row truthful to what's persisted
  and what the analytics gate sees. No error field for this row.
- **`FrIcons.Stats` reused** for the row icon (bar-chart, already vendored as `BarChartVector`) rather
  than adding a new `FrIcons` alias for one call site.
- **No `Account.dataConsentVersion` mirror** (roadmap §0.2 bullet 2, not bullet 3) — out of this task's
  scope; the consent decision is already durably persisted by `ConsentPort` (the gate + decorator read it).

## Blockers
None.

## Suggested next
- Manual (not codeable here, §0.2 bullet 5): Play Data Safety form, iOS `PrivacyInfo.xcprivacy`
  (`NSPrivacyTracking=false`), manifest/plist collection-disabled defaults.
- If product wants a server-side audit trail of consent, wire the `Account.dataConsentVersion` /
  `dataConsentGrantedAt` mirror via `AccountWritePort` (needs a new write method) from both the
  first-run grant and this toggle.
- On-device smoke: flip the toggle, confirm Firebase DebugView stops/starts events accordingly.
