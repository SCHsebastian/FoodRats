# FoodRats Analytics Base Definition (AAA+ Playbook v1)

> Status: authoritative. Created 2026-06-14. The single source of truth for product
> analytics in FoodRats. Mirrors the existing `CrashReporter` capability-port pattern
> (`core/domain/telemetry/CrashReporter.kt` interface + `NoopCrashReporter`; native
> Android adapter + iOS Swift-bridge in `core/data/.../telemetry/`; bound per-platform,
> never in `coreDataModule`). Grounded in research across Segment, Amplitude, Mixpanel,
> RudderStack, PostHog, Google/Firebase docs, EDPB/Apple/Play policy, and Now-in-Android.

The Kotlin sealed `AnalyticsEvent` taxonomy in `:core:domain` **is** this tracking plan in
executable form; `analytics/TRACKING_PLAN.md` is the human-readable mirror generated from it.

---

## 1. Principles — the 8 non-negotiables

1. **Type-safe events only — no magic strings at call sites.** Events are a
   `sealed interface AnalyticsEvent` of `data class`/`data object` leaves carrying typed,
   value-class properties. Stringly-typed `logEvent("share", bundle)` is banned outside the
   adapter; typos / missing params are compile errors, not silent drops. Same
   "make-illegal-states-unrepresentable" discipline as the sealed `Result<T,E>` errors.
2. **Vendor-free domain.** `AnalyticsPort` + `AnalyticsEvent` live in `:core:domain`
   (commonMain; stdlib + kotlinx-datetime + coroutines only). Firebase appears *only* in
   `:core:data` adapters. Enforced for free by the existing `KonsistRulesTest` no-Firebase rule.
3. **Consent-gated — nothing fires before affirmative opt-in.** Under EDPB Guidelines 2/2023,
   persisting a device identifier and shipping events off-device requires **prior opt-in**;
   product analytics is not "strictly necessary". `track()` is a hard no-op until consent is
   `Granted`. The single global GDPR opt-in bar everywhere; a settings toggle satisfies
   CCPA/CPRA opt-out.
4. **No PII, ever** — in event names, properties, or user properties. No email, display name,
   free text (meal description / comments / crew names), precise location, or IDFA. Use the
   account UID as the pseudonymous id; booleanize/bucket free text (`has_description`, not the prose).
5. **Single source of truth = the Kotlin sealed taxonomy.** The committed `AnalyticsEvent`
   *is* the tracking plan; the Markdown mirror is regenerated from it, never hand-diverged. No
   external codegen tool (Avo/RudderTyper) — overkill at ~40 events.
6. **Debug isolation.** Debug builds bind `NoopAnalyticsTracker` (mirrors `NoopCrashReporter`);
   the SDK ships collection-disabled by default; a Koin-graph test locks the debug binding.
7. **Additive-only schema evolution.** New optional properties only. Renames are breaking (even
   case changes) → expand-contract. Pre-launch: spend the one-time free window naming things right.
8. **One choke point, fire-and-forget.** Every feature emits through `AnalyticsPort.track(event)`.
   Consent check + GA4 limit-enforcement live in exactly one place (the `ConsentGatedAnalytics`
   decorator + the adapter). `track()` is **non-suspending** — `logEvent` is fire-and-forget and
   batched, so it does NOT consume the "one `withContext(io)` per data method" budget.

## 2. Naming convention

**`snake_case`, all-lowercase, object-then-action, PAST tense, fixed strings** — forced by the
GA4/Firebase wire format (`[a-z][a-z0-9_]*`, ≤40 chars, case-sensitive, no spaces).

| Surface | Scheme | Example |
|---|---|---|
| Event name | `<object>_<past_verb>`, ≤40, letter-first | `meal_published`, `crew_joined`, `comment_posted`, `plate_classified` |
| Event property | snake_case; booleans `is_`/`has_`; counts `_count`; durations `_ms`; bytes `_bytes`; ids `_id` | `ingredient_count`, `has_description`, `classify_latency_ms`, `crew_id` |
| Property values | typed: numbers stay numbers, booleans stay booleans, enums = lowercase slugs | `meal_slot ∈ {breakfast,lunch,dinner,snack}`, `score=4` |
| User property | snake_case, name ≤24, value ≤36 | `active_crew_size`, `crews_count`, `notif_permission_state`, `data_consent_version` |
| Screen name | from the `Route` *type's* simple name → snake_case (NOT the raw route string) | `Route.MealDetail` → `meal_detail` |

**Closed verb allow-list** (frozen): `published, rated, posted, joined, left, created, deleted,
captured, classified, confirmed, viewed, opened, navigated, shared, prompted, granted, denied,
started, failed, dismissed`.

**Hard rules:** never interpolate values into names (use `meal_published` + `meal_slot="lunch"`,
not `meal_published_lunch`); one event per concept (rate, don't explode); never start with reserved
`firebase_`/`google_`/`ga_`; never redefine auto-collected names (`first_open`, `session_start`,
`screen_view`, `user_engagement`, `notification_*`).

**Reuse GA4 predefined names** where they fit: `login`, `sign_up`, `join_group`, `share`,
`select_content`, `post_score`, `level_up`. Custom otherwise.

## 3. ID & session model

- **Anonymous id** = Firebase app-instance id (auto, persisted, rotated on `resetAnalyticsData()`).
- **User id** = `Account.id` via `setUserId(accountId.value)` — never email/display name.
- **Identify timing:** stay anonymous until signed in **AND** consent `Granted`, then `setUserId`.
- **Reset:** on sign-out `setUserId(null)`; on consent revoke also `resetAnalyticsData()` + server
  User-Deletion.
- **Session:** GA4 default 30-min inactivity timeout; auto-collected — never log manually.

## 4. Privacy & consent

`ConsentDecision = Unknown | Granted(version, at) | Denied(version, at)` — a sealed interface in
`:core:domain`, persisted local-first in DataStore (gate works pre-network) and mirrorable to
Firestore. **It is not an analytics event.**

| State | `track()` | Adapter SDK calls |
|---|---|---|
| Unknown | NO-OP, no buffering | collection OFF; consent → DENIED |
| Granted | forward; `setUserId` once signed in | collection ON; consent → GRANTED |
| Denied | NO-OP | collection OFF; consent → DENIED; `resetAnalyticsData()`; (revoke ⇒ server deletion) |

The check lives in **one** `ConsentGatedAnalytics` decorator, never duplicated in ViewModels.
Bumping `CURRENT_CONSENT_VERSION` drops stale grants back to `Unknown` (re-consent).

**Defaults (backstop):** Android manifest `firebase_analytics_collection_enabled=false` + Consent
Mode `analytics_storage=denied` (+ ad signals denied permanently); iOS plist equivalents. **Stay off
"tracking"** → no ATT prompt, no IDFA, no Google Signals; ship `PrivacyInfo.xcprivacy`
(`NSPrivacyTracking=false`). Play Data Safety: App activity + Device IDs, purpose Analytics,
shared=no, optional, deletable.

**PII denylist (asserted in tests):** email · display name/handle · meal description text · comment
text · crew names · precise location · IDFA · raw classifier output beyond chosen slugs · photo
bytes/URL. **Safe:** account UID, `crew_id`, `meal_id`, enum slugs, counts, booleans, durations,
screen names.

**Retention:** GA4 → 2 months. **Erasure** runs through the port: `resetAnalyticsData()` + a Cloud
Function calling the User Deletion API by app-instance id.

## 5. Metrics tree

**North Star — Weekly Engaged Crew-Days:** count of `(crew, day)` pairs where ≥2 distinct members
each **published** a meal AND **reacted** (rated/commented) to another member's meal. Captures the
closed-group reciprocal loop; cannot be moved directly.

| Input | Definition | Feeding events |
|---|---|---|
| Activation rate | % new accounts activated within 72h | `login`, `crew_joined`/`crew_created`, `meal_published`, `crewmate_meal_viewed` |
| Publishing depth | meals per active member per week | `meal_published`(`crew_id`,`meal_slot`,`ingredient_count`) |
| Reaction rate | reactions ÷ crewmate meals viewed | `feed_day_viewed`, `meal_rated`(`post_score`), `comment_posted` |
| Streak-active rate | % active members with streak ≥3 | `streak_extended`, `streak_broken` |
| Crew fill | active crews; avg members/crew; invite→join | `crew_invite_shared`, `crew_joined`, `crew_left` |

**Guardrails:** notification opt-out rate · crew-leave + crew-death rate · reaction-spam ratio ·
`meal_publish_failed` rate · crash-free sessions.

**Activation funnel:** `login` → `crew_created|crew_joined` (member_count ≥ 2) →
`meal_composer_opened` → `meal_published` → `crewmate_meal_viewed | meal_rated | comment_posted`
(AHA). Activated = steps 2,4,5 within 72h. The solo-crew (`member_count=1`) segment is the predicted
#1 activation leak.

## 6. Event catalog shape

| Category | Events | Reuse vs custom |
|---|---|---|
| auth | `login`(`method`), `sign_up`(`method`), `sign_in_failed`(`error_leaf`) | predefined + custom |
| crew | `join_group`(`group_id`), `crew_created`(`crew_size`), `crew_left`, `crew_invite_shared` | predefined + custom |
| meal funnel | `meal_capture_started`(`source`), `plate_classified`(`detected_count`,`accepted_count`,`latency_ms`,`classifier_version`), `ingredients_confirmed`(`detected_count`,`confirmed_count`), `meal_composer_opened`, `meal_published`(`crew_id`,`meal_slot`,`ingredient_count`,`has_description`,`audience_crew_count`,`publish_source`), `meal_publish_failed`(`error_leaf`), `meal_deleted` | custom |
| feed | `select_content`(`content_type="meal"`,`item_id`), `post_score`(`score`), `comment_posted`(`meal_id`), `feed_day_viewed`(`meal_count`,`day_offset`) | predefined + custom |
| stats | `streak_viewed`, `leaderboard_viewed`, `streak_extended`, `streak_broken`, `level_up` | predefined + custom |
| notifications | `notification_permission_prompted`/`_granted`/`_denied`(`prompt_count`) | custom (delivery auto-collected) |
| lifecycle/screen | `screen_view` (auto, name from `Route` type) | auto-collect |
| consent | `consent_granted`(`version`), `consent_denied`, `consent_revoked` (emitted only after grant) | custom |

User properties (≤25, value ≤36): `active_crew_size`, `crews_count`, `app_locale`,
`notif_permission_state`, `data_consent_version`. Never PII. Plan target: 30–60 events.

## 7. Kotlin architecture

**Binding: per-platform native** (`com.google.firebase:firebase-analytics` androidMain + Swift
bridge iosMain), NOT GitLive. Rationale: (1) identical to the validated `CrashReporter` pattern,
satisfies the vendor-in-adapters rule + Konsist by construction; (2) GitLive couples *common* code
to `Firebase.analytics` — exactly what the owned-server swap removes — while still needing the iOS
native framework linked anyway.

`:core:domain/analytics/` (vendor-free): `AnalyticsPort`, `AnalyticsEvent` (sealed taxonomy),
`AnalyticsValue` (typed param), dimension enums (`MealSlotDimension`, `PublishSource`, `JoinMethod`,
`ClassifySource`), `ScreenName`, `UserProperty`, `ConsentDecision`/`ConsentPort`, `AnalyticsConfig`
(consent version + reserved prefixes + GA4 limits), `NoopAnalyticsTracker`,
`RecordingAnalyticsTracker` (test double, in commonMain like `FixedClock`).

`:core:data/analytics/`: `ConsentGatedAnalytics` decorator (the single choke point — caches latest
consent into a volatile flag, forwards only when granted); `FirebaseAnalyticsTracker` (androidMain,
the only Firebase/GA4-limits site, builds a `Bundle`); `IosAnalyticsTracker` (iosMain, Swift-bridge
lambdas) + `analyticsIosModule(...)`. `:core:data/preferences/ConsentRepository` implements
`ConsentPort` over `AppPreferences`. Swift `AnalyticsBridge.swift` mirrors `CrashlyticsBridge.swift`.

**Koin:** `single<ConsentPort>` in `coreDataModule` (common); `single<AnalyticsPort>` bound
per-platform (`analyticsAndroidModule()` / `analyticsIosModule(...)`) wrapping the platform tracker
in `ConsentGatedAnalytics`, Noop in debug.

**Screen-view auto-tracking:** an `addOnDestinationChangedListener` at the root NavHost in `shared/`
(no Firebase dep — calls the port). `Route` *type* → snake_case `ScreenName`.

**Result interplay:** track **after** a use case returns `Ok`, from the **ViewModel** (the layer that
also maps errors to StringKeys), never from a use case (keeps them side-effect-free). On `Err`,
optionally `track(...Failed(errorLeaf = error::class.simpleName))` — never the raw exception/PII.

ViewModels take `analytics: AnalyticsPort = NoopAnalyticsTracker` (default keeps existing tests
green); the Koin module passes the real one via `get()`; verify tests add `AnalyticsPort::class` to
`extraTypes`.

## 8. Governance & CI guardrails

- The sealed `AnalyticsEvent` taxonomy is the source of truth; `analytics/TRACKING_PLAN.md` is the
  generated mirror (columns: event_name, description, trigger, category, properties+type+required,
  status, code_ref). Status: `proposed → approved → live → deprecated`.
- Taxonomy change = a PR editing `AnalyticsEvent.kt`; evolve additively; rename via expand-contract.
- **Tests:** (1) `AnalyticsTaxonomyTest` asserts every event name ≤40, letter-first, snake_case, not
  reserved; (2) PII-denylist assertion; (3) Konsist no-Firebase (already module-wide); (4) Koin
  debug-binding-is-Noop test; (5) ViewModel analytics tests via `RecordingAnalyticsTracker`.
- **Pre-release QA gate (manual):** walk sign-in → crew → publish → feed → stats → notification with
  Firebase DebugView open on Android + iOS, asserting each event fires once with correct typed params.

## 9. GA4 / Firebase limits cheat-sheet

GA4 **silently drops** over-limit data — the adapter truncates/caps and `commonTest` asserts.

| Limit | Value |
|---|---|
| Event name length | 40 (`[a-zA-Z0-9_]`, letter-first, case-sensitive) |
| Parameters per event | 25 |
| Parameter name / string-value length | 40 / 100 |
| Distinct event names per app | 500 |
| User properties / name len / value len | 25 / 24 / 36 |
| Registered custom dimensions (event / user) | 50 / 25 |
| Conversions (key events) | 30 |
| Reserved prefixes | `firebase_`, `google_`, `ga_` |
| BigQuery export | enable daily (free at this scale; preserves raw history across the server swap) |
