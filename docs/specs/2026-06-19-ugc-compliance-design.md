# UGC Compliance — Apple App Store Guideline 1.2 (Safety / User-Generated Content)

**Date:** 2026-06-19 · **Branch/worktree:** `feature/ugc-compliance` · **Status:** authoritative design (implementable)
**Supersedes/extends:** nothing — net-new bounded context. Grounded in `docs/session/2026-06-19-ugc-compliance/PLAN.md` (confirmed decisions) and the base design `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` (DDD/Clean conventions).

> When this spec and code disagree, this spec wins until explicitly revised. All decisions below are the **confirmed** ones from `PLAN.md` — do not re-litigate them during implementation.

---

## 0. Why & scope

Apple Guideline 1.2 requires every app with user-generated content (FoodRats has profiles, meals, plate photos, dish names, free-text descriptions, comments, reactions, leaderboards) to ship **all four** of:

1. **A method for filtering objectionable material** from being posted.
2. **A mechanism to report offensive content** and timely responses to concerns (we treat "timely" as a ≤ 24 h SLA).
3. **The ability to block abusive users.**
4. **Published contact information** so users can reach the developer, plus a clear **EULA**.

This spec covers the four requirements (`§3` filter, `§4` report, `§5` block, `§6` EULA) plus the cross-cutting pieces: a new `feature/moderation` module (`§7`), security-rules deltas (`§8`), and the `foodrats.app` purge (`§9`). The companion runbook is `docs/moderation/RUNBOOK.md`.

### 0.1 Non-negotiable conventions (carried from root `CLAUDE.md` + `~/.claude/rules`)

- Custom `Result<T, E>` in `:core:domain` (never `kotlin.Result`). Errors = `sealed interface` with nested sealed-interface groups + `data object` leaves (never enums). No `Unknown` leaves unless justified.
- Features never depend on other features. Cross-context reads go through ports declared in `:core:domain`.
- Exactly one `withContext(dispatchers.io){}` per public repository method. Zero in use cases / ViewModels.
- `:core:domain` imports only kotlin stdlib + `kotlinx-datetime` + `kotlinx-coroutines-core` (Konsist-enforced).
- Firebase-touching modules → `JvmTarget.JVM_17` + Firebase BOM in `androidMain`. Non-Firebase modules → JVM 11.
- Every `value class` in `commonMain` is `@JvmInline`.
- All user-visible text (incl. punctuation/glyphs) via `resolve(StringKey)`; each feature owns a `<Feature>StringKey` enum + en/es `composeResources` strings + an exhaustive `<Feature>Error.toStringKey()` mapper + a `*ErrorToStringKeyTest`.
- DS composables prefixed `Fr*`; atoms/molecules in `:core:designsystem` never import domain types. Meaning colors via `LocalFrSemanticColors.current`; never raw `Color(0x…)`.
- MVI single source of truth: state lives only in `MviViewModel` `State`; derive flows via `state.map{…}.distinctUntilChanged()`; read with `currentState`; mutate via `update { it.copy(…) }`.
- Typesafe project accessors: `projects.feature.moderation`, never `project(":feature:moderation")`.

### 0.2 Design decisions (confirmed in PLAN)

| Area | Decision |
|---|---|
| Content filter | **Text-only, on-device, en/es** wordlist. Hard-block on comments; **advisory** banner on meal description. No image moderation (zero-paid-infra). |
| Reports | **Auto-hide at a threshold of 3 distinct reporters + manual review.** A Cloud Function counts distinct reporters per target and auto-removes at threshold (meal → reuse `onMealDeleted` cascade; comment → Admin SDK delete) and logs for review. Satisfies the ≤ 24 h SLA without 24/7 staffing. |
| Block | Client-side exclusion of blocked accounts' content from feed/detail/stats. Block list is owner-private in Firestore. |
| EULA | Apple's **standard** EULA referenced in App Store Connect **and** full Community Guidelines + EULA text embedded in-app, surfaced **at the login screen** (en/es), acceptance recorded with a versioned flag, also reachable from Profile. |
| Domain | Real domain is **`foodrats-de4ec.web.app`** (already `DeepLinks.WEB_HOST`). `foodrats.app` is **not** ours — purge every reference. |

---

## 1. Bounded-context placement (module map)

```
:core:domain  (no new gradle deps — stays JVM 11, kotlin/datetime/coroutines only)
  moderation/
    TextModerationPort.kt          port + ModerationVerdict
    WordlistTextModeration.kt      PURE multilingual (en/es) impl — lives in commonMain
    Wordlists.kt                   the en/es term sets (internal)
    BlockedAccountsPort.kt         port + BlockError
    ReportPort.kt                  port + ReportTarget + ReportReason + ReportError
  (CommentError gains a Write.Objectionable leaf — see §3.2)

:core:data
  preferences/
    EulaRepository.kt              EulaPort impl over AppPreferences (DataStore)
  (Keys.kt gains EulaAcceptedVersion)
  (analytics/ AnalyticsEvent.kt gains the moderation leaves — see §10)

:core:domain  legal/
    Eula.kt                        EulaPort + CURRENT_EULA_VERSION + needsAcceptance helper

:feature:moderation   (NEW — JVM_17 + Firebase BOM)
  data/firebase/
    BlockDto.kt, BlockFirestoreDataSource.kt, FirebaseBlockRepository.kt
    ReportDto.kt, ReportFirestoreDataSource.kt, FirebaseReportRepository.kt
    BlockErrorMapper.kt, ReportErrorMapper.kt
  presentation/
    blocked/ BlockedUsersScreen.kt + BlockedUsersViewModel.kt + Contract
    report/  ReportSheetHost.kt (drives FrReportSheet) + ReportViewModel.kt + Contract
    ModerationStringKey.kt, ModerationErrorMapper.kt, ModerationErrorToStringKeyTest (commonTest)
  di/ ModerationModule.kt + ModerationModuleVerifyTest (androidHostTest)

:core:designsystem
  molecules/ FrReportSheet.kt      pure molecule (presentation enum + callbacks only)

:shared  app/legal/
    LegalDocsScreen.kt             scrollable embedded EULA + Community Guidelines (en/es)
    LegalDocViewModel.kt (only if state needed; otherwise stateless)
  app/navigation/Route.kt          + Route.Legal(doc) + Route.BlockedUsers
  app/navigation/NavGraph.kt       wire the two new composables
  app/di/AppModule.kt              register moderationModule

:feature:auth
  presentation/signin/SignInScreen.kt   + EULA/Guidelines links + acceptance gate
  presentation/profile/ProfileScreen.kt + "Blocked users" + "Legal" rows

firestore.rules                    + accounts/{uid}/blocks/{blockedUid} + reports/{reportId}
functions/src/triggers/onReportCreated.ts  + index.ts export + __tests__/onReportCreated.test.ts
```

`feature/moderation` implements the three `:core:domain` ports (`BlockedAccountsPort`, `ReportPort`; `TextModerationPort` has a pure impl already in domain). Feed/stats/meal consume only the `:core:domain` ports — they never gain a gradle dep on `:feature:moderation`. New module registered in `settings.gradle.kts` and `appModules`.

---

## 2. Requirement 1 — Filter objectionable content

### 2.1 `TextModerationPort` + `ModerationVerdict` (`:core:domain/moderation/TextModerationPort.kt`)

Pure, synchronous, no IO, no flows — moderation is a CPU classification over a string. Lives in domain so every layer can call it directly (the comment ViewModel, the compose ViewModel, and — later — a server reuse). It is **not** a repository, so it has no `withContext`.

```kotlin
package es.schsebastian.foodrats.core.domain.moderation

/**
 * Result of screening a free-text field for objectionable content. Sealed (domain-error shape) so
 * call sites exhaust it. `Clean` carries nothing; `Objectionable` carries the matched category so the
 * UI can show a specific reason and analytics can bucket it (NEVER the matched term — no PII/echo).
 */
sealed interface ModerationVerdict {
    data object Clean : ModerationVerdict
    data class Objectionable(val category: ModerationCategory) : ModerationVerdict
}

/** Coarse buckets the wordlist maps to. Used for the user-facing reason + analytics dimension. */
enum class ModerationCategory { HATE, SEXUAL, HARASSMENT, VIOLENCE, PROFANITY }

/**
 * Screens a single user-supplied text field. Synchronous + pure: no IO, no suspension. Implemented by
 * the on-device [WordlistTextModeration]; a future server-side classifier can implement the same port
 * without touching call sites.
 */
fun interface TextModerationPort {
    /** @param languageTag BCP-47 active language ("en", "es"); selects which wordlist(s) apply. */
    fun screen(text: String, languageTag: String): ModerationVerdict
}
```

> `ModerationCategory` is an `enum` (not a sealed-interface error tree) **by design**: it is a value/dimension, not an error. The error contract (`§3.2`) stays sealed. This mirrors the existing `MealSlot` / `ReactionKind` value enums.

### 2.2 `WordlistTextModeration` (pure impl, `:core:domain/moderation/WordlistTextModeration.kt`)

- Constructed with the term sets (defaults to the bundled en + es sets in `Wordlists.kt`, `internal`).
- Algorithm: normalize the input (lowercase, strip diacritics, collapse leetspeak digits → letters `1→i 3→e 0→o 4→a @→a $→s`, collapse repeated chars `fuuuck→fuck`), then **whole-word** match against the union of the requested-language set + a small always-on language-neutral set (slurs that are identical across en/es). Whole-word (token boundary) matching avoids the "Scunthorpe problem" (`assassin`, `class`, `cocktail` must pass).
- Returns the **first** matched term's `ModerationCategory`; ties resolve by category order `HATE > SEXUAL > HARASSMENT > VIOLENCE > PROFANITY` so the most severe wins.
- Deterministic and allocation-light (tokenization + set lookup). No regex catastrophic-backtracking.
- The wordlist is a curated, conservative list (favoring precision over recall) — this is a **first-pass deterrent**, not a guarantee; the report/block paths are the safety net. Document this trade-off inline.

`Wordlists.kt` keeps the actual terms `internal` so they don't leak into public API / generated docs:

```kotlin
internal object Wordlists {
    val EN: Map<String, ModerationCategory> = mapOf(/* curated */)
    val ES: Map<String, ModerationCategory> = mapOf(/* curated */)
    val NEUTRAL: Map<String, ModerationCategory> = mapOf(/* cross-language slurs */)
    fun forLanguage(tag: String): Map<String, ModerationCategory> = when (tag.lowercase().take(2)) {
        "es" -> ES + NEUTRAL
        else -> EN + NEUTRAL   // default English
    }
}
```

### 2.3 Tests (`:core:domain/commonTest/.../moderation/`)

`WordlistTextModerationTest` — table-driven:
- Clean: `"delicious lasagna"`, `"assassin's creed pasta"` (Scunthorpe), `"clase de cocina"` → `Clean`.
- Hit (en): a profanity term → `Objectionable(PROFANITY)`; a slur → `Objectionable(HATE)`.
- Hit (es): a Spanish profanity under `"es"` → `Objectionable(...)`; same Spanish word under `"en"` still hit only if it is in `NEUTRAL` (documents the language-scoping behavior).
- Evasion: leetspeak (`"f4ck"`), repeated chars (`"shiiiit"`), diacritics → still caught.
- Severity ordering: a string with both a profanity and a slur → returns `HATE`.

### 2.4 Hook point A — comments (HARD block)

`MealDetailViewModel.postComment()` (`feature/feed/.../detail/MealDetailViewModel.kt`). Inject `private val textModeration: TextModerationPort` and the active language tag (the app already exposes a `LocalePort`-derived language tag — feed it into the VM via Koin as a `String` provider or the existing locale port; see `§3.7`). The screen runs **before** the existing online/outbox branch (so an objectionable comment never reaches Firestore *or* the outbox):

```kotlin
// inside postComment(), after CommentText.of(...) succeeds and authorId is resolved,
// BEFORE the connectivity / outbox branch:
val verdict = textModeration.screen(text.value, languageTag)
if (verdict is ModerationVerdict.Objectionable) {
    return update { it.copy(isPostingComment = false, commentWriteError = CommentError.Write.Objectionable) }
}
```

This requires a new error leaf on the **shared** comment error in `:core:domain` (comments are a `:core:domain` concept used by feed):

### 2.5 Hook point B — meal description (ADVISORY banner)

`ComposePlateViewModel` (`feature/meal/.../compose/ComposePlateViewModel.kt`). The description filter is **advisory** — it never blocks publish (`canContinue`/`persistDraft` are untouched), it only surfaces a non-blocking warning banner, exactly like the AI-classifier advisory pattern this module already uses.

- Add `descriptionWarning: Boolean` (or `descriptionModeration: ModerationCategory?`) to `ComposePlateState`.
- On `ComposePlateIntent.DescriptionChanged`, after the existing `tooLong` computation, run `textModeration.screen(intent.value, languageTag)` and set the warning flag; render via an `FrErrorBanner`-style advisory using `LocalFrSemanticColors.current.warning` (not `danger` — advisory, not blocking).
- `persistDraft()` is unchanged: a flagged description still persists and publishes. (We deliberately do not hard-block descriptions: a closed crew of 3–8 trusted friends + the report path is sufficient, and false positives on prose are higher.)
- Inject `textModeration: TextModerationPort` + language tag into `ComposePlateViewModel` constructor (default-free param wired in `mealModule`).

> Both hook points read the active language tag so the right wordlist applies. The wordlist screen is synchronous and pure, so neither call introduces a `withContext` (correct — these are ViewModels).

---

## 3. Requirement 1 (cont.) — error + i18n shapes

### 3.1 `CommentError.Write.Objectionable` (new leaf, `:core:domain/meal/MealCommentPort.kt`)

```kotlin
sealed interface Write : CommentError {
    data object Unauthorized  : Write
    data object Blank         : Write
    data object TooLong       : Write
    data object Objectionable : Write   // NEW — blocked by the on-device text filter
    data object Unavailable   : Write
}
```

### 3.2 i18n + mapper updates (feed owns the comment UI)

- `FeedStringKey` gains `CommentsErrorObjectionable` → `feed_comments_error_objectionable` in `values/strings.xml` + `values-es/strings.xml`.
  - en: `"That comment may break the community guidelines. Please rephrase it."`
  - es: `"Ese comentario podría incumplir las normas de la comunidad. Reformúlalo."`
- `CommentError.toStringKey()` (in `feature/feed/.../presentation`) gains the arm:
  `CommentError.Write.Objectionable -> FeedStringKey.CommentsErrorObjectionable`.
  The existing `CommentErrorToStringKeyTest` (commonTest) auto-fails until the arm is added — that locks exhaustiveness.

### 3.3 Meal-description advisory strings (meal owns the composer UI)

- `MealStringKey` gains `DescriptionModerationWarning` → `meal_description_moderation_warning`.
  - en: `"Heads up — this description may break the community guidelines."`
  - es: `"Aviso: esta descripción podría incumplir las normas de la comunidad."`
- No new `MealError` leaf (advisory ≠ error; it must not enter the `MealError` tree or it would gate publish).

---

## 4. Requirement 2 — Report content/users

### 4.1 Domain port + value types (`:core:domain/moderation/ReportPort.kt`)

```kotlin
package es.schsebastian.foodrats.core.domain.moderation

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.result.Result

/** What is being reported. Sealed so the rules + function can resolve the doc deterministically. */
sealed interface ReportTarget {
    /** A meal copy in a specific crew (matches the per-crew meal doc path). */
    data class Meal(val crewId: CrewId, val mealId: MealId) : ReportTarget
    data class Comment(val crewId: CrewId, val mealId: MealId, val commentId: MealCommentId) : ReportTarget
    /** A user/profile (no crew scope). */
    data class Account(val accountId: AccountId) : ReportTarget
}

/** Why. Sealed (not enum) per the project error/value convention — leaves stay payload-extensible. */
sealed interface ReportReason {
    data object Spam        : ReportReason
    data object Harassment  : ReportReason
    data object Hate        : ReportReason
    data object Sexual      : ReportReason
    data object Violence    : ReportReason
    data object Other       : ReportReason
}

sealed interface ReportError {
    sealed interface Submit : ReportError {
        data object NotSignedIn   : Submit
        data object SelfReport    : Submit   // can't report your own content/account
        data object AlreadyReported : Submit // this reporter already reported this target
        data object Unavailable   : Submit   // network / backend
    }
}

/**
 * Submits a report. Write-only from the client's perspective — reports are server-only readable
 * (moderation queue), so there is no `observe`. Implemented in `:feature:moderation` over Firestore.
 */
interface ReportPort {
    suspend fun submit(
        reporter: AccountId,
        target: ReportTarget,
        reason: ReportReason,
    ): Result<Unit, ReportError.Submit>
}
```

> `ReportReason` is a sealed interface (consistent with the project's "no enums for closed sets that may carry payloads later" stance — `Other` may someday carry a free-text note; keeping it sealed leaves that door open and matches `ReportTarget`).

### 4.2 Firestore data model (`reports/{reportId}`)

One flat top-level collection `reports`, document id = deterministic `"${reporterUid}|${targetKey}"` so a reporter can report a given target **at most once** (idempotent; powers `AlreadyReported` and the distinct-reporter count). `targetKey` is a stable, `|`-delimited string — `|` cannot appear in crew/meal/account ids, day keys or slots (all `[A-Za-z0-9_-]`), so the join is collision-free where the original `_`/`-` scheme was ambiguous (meal ids embed `_`):
- Meal: `"meal|${crewId}|${mealId}"`
- Comment: `"comment|${crewId}|${mealId}|${commentId}"`
- Account: `"account|${accountId}"`

`ReportDto` fields (all client-written at create, then immutable):
```
reporterId   : string   == request.auth.uid
targetType   : "meal" | "comment" | "account"
crewId       : string?  (meal/comment only)
mealId       : string?  (meal/comment only)
commentId    : string?  (comment only)
accountId    : string?  (account only) — the reported account
targetKey    : string   the dedupe/count key above
reason       : "spam" | "harassment" | "hate" | "sexual" | "violence" | "other"
status       : "open"   (server may later flip to "actioned"/"dismissed" via Admin SDK)
createdAtEpochMs : int
```
The unused `*Id` fields ride as explicit `null` (GitLive `.set` encodes defaults); the create rule whitelists exactly these ten keys and **pins** each present id back into `targetKey`, so the trigger's delete-by-field provably targets the reported content. There is no `authorId` field: authorization for meal/comment targets is by **crew membership + target existence** (rules), and account self-report is blocked by `accountId != request.auth.uid`.

Rules: **create-only-by-reporter, server-only read** (see `§8.2`). The client never reads `reports` (it's a moderation queue); `ReportPort.report` is a transactional `.set()` on the deterministic id and surfaces a pre-existing doc as `AlreadyReported` (an existing report with the same id means the reporter already reported it).

### 4.3 Repository (`feature/moderation/data/firebase/FirebaseReportRepository.kt`)

- One public method `submit(...)` → exactly one `withContext(dispatchers.io){}`.
- Account self-report guard is enforced client-side (`reporter == accountId` → `Result.failure(SelfReport)`) **and** in rules (`accountId != request.auth.uid`, defense in depth).
- Maps GitLive Firestore exceptions to `ReportError.Submit` via `ReportErrorMapper` (vendor types stay in `data/firebase/`).

### 4.4 Report UI — `FrReportSheet` (pure molecule) + host

`FrReportSheet` lives in `:core:designsystem/molecules/FrReportSheet.kt` and takes only primitives + a presentation enum + callbacks (no domain types):

```kotlin
enum class FrReportReasonOption { SPAM, HARASSMENT, HATE, SEXUAL, VIOLENCE, OTHER }

@Composable
fun FrReportSheet(
    title: String,
    reasonLabels: Map<FrReportReasonOption, String>,   // resolved by the caller via resolve(...)
    submitLabel: String,
    cancelLabel: String,
    submitting: Boolean,
    onSubmit: (FrReportReasonOption) -> Unit,
    onDismiss: () -> Unit,
)
```

The owning feature maps `FrReportReasonOption ↔ ReportReason` (the feature is allowed to know domain types; the molecule is not). The host composable (`feature/moderation/presentation/report/ReportSheetHost.kt`) is invoked from:
- **Meal detail** — the `PhotoHero` pill row in `MealDetailScreen` gains a "Report" action (and a "Block author" action — see `§5.5`). On tap it opens `FrReportSheet` with `ReportTarget.Meal(activeCrew, mealId)` (and exposes "Report user" → `ReportTarget.Account(authorId)`).
- **Comment row** — `FrCommentRow` gains a "Report" overflow action → `ReportTarget.Comment(...)`.

The report action is **hidden on your own content** (we already know the author id in `MealDetailViewModel.joinRows` / `match.authorId`), so `SelfReport` is primarily a server guard.

A successful submit shows a confirmation snackbar ("Thanks — we'll review this within 24 hours.") and (optimistically, for the reporter only) hides the reported row locally for the session. Durable hiding is the server's job (`§4.6`).

### 4.5 i18n + error mapper (`feature/moderation`)

`ModerationStringKey` covers: sheet title, the six reason labels, submit/cancel, the success message, and every `ReportError.Submit` + `BlockError` (see `§5`) leaf. `ModerationErrorMapper` is an exhaustive `when` over `ReportError` **and** `BlockError`; `ModerationErrorToStringKeyTest` (commonTest) locks exhaustiveness. en + es strings in `feature/moderation/src/commonMain/composeResources/{values,values-es}/strings.xml`.

### 4.6 Server — `onReportCreated` Cloud Function (auto-hide at threshold)

`functions/src/triggers/onReportCreated.ts`, exported from `functions/src/index.ts`, region `europe-west3`, mirrors the shape of `onCommentCreated` / `onMealDeleted`.

Trigger: `onDocumentCreated("reports/{reportId}")`.

```
const THRESHOLD = 3;   // distinct reporters

on create(report):
  1. Count DISTINCT reporters for report.targetKey (only status=="open" docs):
       db.collection("reports").where("targetKey","==",report.targetKey)
         .where("status","==","open").get()
       distinct = new Set(docs.map(d => d.reporterId)).size
     (deterministic doc ids already guarantee one doc per reporter per target, so
      docs.length == distinct, but we de-dupe defensively.)
  2. If distinct < THRESHOLD: log {targetKey, distinct} for the manual queue and return.
  3. If distinct >= THRESHOLD: AUTO-HIDE:
       - targetType "meal":    db.doc(`crews/${crewId}/meals/${mealId}`).delete()
                               → this fires the EXISTING onMealDeleted cascade (subcollections +
                                 Storage plate + thumbnail). No new cleanup code.
       - targetType "comment": db.doc(`crews/${crewId}/meals/${mealId}/comments/${commentId}`).delete()
                               (Admin SDK delete; comments have no Storage/subcollections).
       - targetType "account": DO NOT auto-delete an account. Flag for manual review only
                               (log + write reports docs status, optionally set a server-only
                                `accounts/{uid}/private/moderation` flag). Account-level takedown
                                is always a human decision (it may be a false dogpile).
  4. Mark every report doc for targetKey status="actioned" (Admin SDK), and logger.info a
     structured audit line the runbook greps for.
```

Idempotency: deleting an already-deleted meal/comment is a no-op (`onMealDeleted` is idempotent; `delete()` on a missing comment doc is harmless). A re-fire (e.g. a 4th report after action) recomputes, sees the content already gone, and just re-logs.

The `THRESHOLD = 3` constant is exported so the test pins it. Test: `functions/__tests__/onReportCreated.test.ts` (vitest) with a seam over the Firestore reads/deletes (mirror the `MealBlobStore` seam pattern in `onMealDeleted.test.ts`): below threshold → no delete; at/above threshold for a meal → meal delete called; for a comment → comment delete called; for an account → no delete, flagged.

### 4.7 SLA & runbook

The ≤ 24 h "timely response" requirement is met by the combination of (a) automatic removal at 3 distinct reporters (typically within minutes inside a 3–8-person crew), and (b) a documented daily manual review of the `reports` queue with a ≤ 24 h SLA, plus manual takedown via the existing crew-owner/author delete RBAC. Full process: `docs/moderation/RUNBOOK.md` (`§11`).

---

## 5. Requirement 3 — Block users

### 5.1 Domain port (`:core:domain/moderation/BlockedAccountsPort.kt`)

```kotlin
package es.schsebastian.foodrats.core.domain.moderation

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

sealed interface BlockError {
    sealed interface Write : BlockError {
        data object NotSignedIn  : Write
        data object SelfBlock    : Write   // can't block yourself
        data object Unavailable  : Write
    }
}

/**
 * The signed-in user's block list. `observeBlocked` is the single source consumers (feed/detail/stats)
 * subscribe to in order to exclude blocked authors' content client-side. Implemented in
 * `:feature:moderation` over `accounts/{uid}/blocks/{blockedUid}` (owner-private).
 */
interface BlockedAccountsPort {
    /** Live set of account ids the current user has blocked. Emits `emptySet()` when signed-out. */
    fun observeBlocked(): Flow<Set<AccountId>>
    suspend fun block(blocked: AccountId): Result<Unit, BlockError.Write>
    suspend fun unblock(blocked: AccountId): Result<Unit, BlockError.Write>
}
```

`observeBlocked()` keys off the current session internally (the repository captures `SessionProvider`), so consumers don't pass an id — they just subscribe.

### 5.2 Firestore data model

`accounts/{uid}/blocks/{blockedUid}` — doc id IS the blocked account id; body `{ blockedAtEpochMs: int }`. Owner-private on read and write (`§8.1`), exactly mirroring the existing `accounts/{uid}/achievements` and `accounts/{uid}/devices` owner-only subcollections. No reverse index, no server fan-out — blocking is purely a client-side read filter for the MVP (a closed-crew app; a blocked user is simply made invisible to the blocker).

### 5.3 Repository (`feature/moderation/data/firebase/FirebaseBlockRepository.kt`)

- `observeBlocked()` → subscribes to the `blocks` subcollection of the current uid, maps doc ids → `Set<AccountId>`; `flatMapLatest` over `session.current` so a sign-out/switch re-scopes (no `withContext` — it's a `Flow` builder).
- `block(...)` / `unblock(...)` → each exactly one `withContext(dispatchers.io){}`; self-block guarded client-side (`blocked == currentUid → SelfBlock`).
- Errors mapped via `BlockErrorMapper`.

### 5.4 Client-side exclusion (the consumers)

Blocking is enforced as a **read filter** in three places, all fed by `BlockedAccountsPort.observeBlocked()`. The port is consumed from `:core:domain`, so no feature gains a dep on `:feature:moderation`.

1. **Feed** — `ObserveFeedUseCase` gains a `blocked: BlockedAccountsPort` dependency; `combine` the blocked set into the stream and filter `meals.filter { it.meal.author.accountId !in blockedSet }` before emitting. `FeedViewModel` wiring: the use case already returns the filtered list, so the VM is unchanged except for Koin (`feedModule`) passing the new port. (Alternative if we want to keep the use case signature stable: filter in `FeedViewModel` by combining `observeBlocked()` into its state stream — but putting it in the use case keeps the rule in the domain-orchestration layer and is preferred.)
2. **Meal detail comments** — `MealDetailViewModel.joinRows(...)` (and its comment stream) drop comments whose `authorId ∈ blockedSet`. Inject `BlockedAccountsPort`; `combine` `observeBlocked()` into the existing `rbacFlow`/comments combine so blocked commenters vanish reactively. The meal itself: if a user blocks a meal's author, the detail screen for that meal shows the same "not found" path the feed exclusion produces (the blocked author's meals are already filtered out of the feed list the detail screen reads).
3. **Stats** — `ObserveStatsUseCase.compose(...)` excludes blocked authors from leaderboards/rankings: filter `currentMeals`/`historicMeals` by `author.accountId !in blockedSet` before `computeWindow`/`computeHeroStats`. Inject `BlockedAccountsPort`; `combine` `observeBlocked()` into the `invoke(...)` flow. (The signed-in member's OWN meals are never blocked — you can't block yourself, `SelfBlock`.)

> Exclusion is **one-directional and local**: blocking hides the blocked user's content from the blocker. It does not notify or restrict the blocked user (consistent with how block works in most social apps and sufficient for Guideline 1.2). A future symmetric/server-enforced block can be added without changing the port.

### 5.5 Block UI

- **Action points:** the `MealDetailScreen` `PhotoHero` pill row ("Block author") and the `FrCommentRow` overflow ("Block user"), co-located with Report. Tapping shows an `FrConfirmDialog` (`destructive = false`, it's reversible) → `BlockedAccountsPort.block(authorId)`. Hidden on your own content.
- **`BlockedUsersScreen`** (`feature/moderation/presentation/blocked/`): lists blocked accounts (resolved to display name/avatar via `AccountReadPort`) with an "Unblock" button per row. MVI: `BlockedUsersViewModel` observes `observeBlocked()` + `AccountReadPort.observeMany(...)`; `Unblock` intent calls `unblock(...)`.
- **Profile entry:** `ProfileScreen` gains a "Blocked users" row (in a new privacy/safety `FrSettingsSection` or the existing General section) → `Route.BlockedUsers`.

### 5.6 Navigation

`Route.kt` gains `@Serializable data object BlockedUsers : Route.Protected` (+ arm in `requiresSession()` → `true`). `NavGraph.kt` adds `composable<Route.BlockedUsers> { BlockedUsersScreen(onBack = { controller.popBackStack() }) }`. Profile passes `onOpenBlockedUsers = { controller.navigate(Route.BlockedUsers) { launchSingleTop = true } }`.

---

## 6. Requirement 4 — EULA & Community Guidelines

### 6.1 Two layers

1. **App Store Connect:** reference Apple's **standard EULA** (the default LSA) in the app's "License Agreement" field. No custom hosted page required (this is the confirmed decision). The store-listing support URL/contact uses the published Firebase Hosting domain — see `§6.6`.
2. **Embedded in-app docs:** the **full** EULA + Community Guidelines text, en/es, scrollable, surfaced **at the login screen** (links on `SignIn`) and also reachable from Profile. Acceptance is recorded with a versioned flag.

### 6.2 `EulaPort` + version (`:core:domain/legal/Eula.kt`)

Mirrors the `ConsentPort` shape (already in `:core:domain/analytics/Consent.kt`), local-first over DataStore so the gate works before any network:

```kotlin
package es.schsebastian.foodrats.core.domain.legal

import kotlinx.coroutines.flow.Flow

/** Bump when the EULA / Community Guidelines text changes materially → forces re-acceptance. */
const val CURRENT_EULA_VERSION: Int = 1

interface EulaPort {
    /** The accepted EULA version, or `null` if never accepted (or below current → re-acceptance). */
    val acceptedVersion: Flow<Int?>
    /** Records acceptance at [CURRENT_EULA_VERSION] with the current time. */
    suspend fun accept()
}

/** True when the login screen must require (re-)acceptance before sign-in. */
fun Int?.needsEulaAcceptance(): Boolean = (this ?: 0) < CURRENT_EULA_VERSION
```

### 6.3 `EulaRepository` (`:core:data/preferences/EulaRepository.kt`)

Implements `EulaPort` over `AppPreferences`. `Keys.kt` gains:
```kotlin
val EulaAcceptedVersion = StoreKey(intPreferencesKey("eula_accepted_version"))
```
`acceptedVersion` = `prefs.observe(Keys.EulaAcceptedVersion)`. `accept()` = one `withContext(dispatchers.io){ prefs.set(EulaAcceptedVersion, CURRENT_EULA_VERSION) }`. Bound in `coreDataModule` (`shared/.../di/CoreDataModule.kt`): `single<EulaPort> { EulaRepository(prefs = get(), clock = get(), dispatchers = get()) }`. Test: `EulaRepositoryTest` (commonTest, mirrors `ConsentRepositoryTest`): absent → `needsEulaAcceptance()==true`; after `accept()` → version persists, `needsEulaAcceptance()==false`; below-version stored → still needs acceptance.

### 6.4 Embedded legal docs (`shared/app/legal/`)

- `LegalDocsScreen.kt` — a scrollable screen rendering one of two documents (EULA or Community Guidelines), selected by a `LegalDoc` enum param. The full text is i18n via `SharedStringKey` (en/es) — long-form strings live in `core/i18n/.../composeResources/{values,values-es}/strings.xml` (the app's shared string surface). The screen is plain scrollable typography (`FrText` + `verticalScroll`), back button via `FrScreenScaffold`, same pattern as `ConsentScreen`.
- `LegalDoc` enum: `{ EULA, COMMUNITY_GUIDELINES }` (presentation enum, lives in `shared`).
- The **Community Guidelines** text must state the prohibited content (the categories the filter screens for + harassment/illegal content), the report mechanism, the block capability, and the ≤ 24 h moderation commitment — this is what Apple reviewers look for, and it doubles as the user-facing description of `§2`/`§4`/`§5`.

### 6.5 Login-screen surfacing + acceptance gate

`SignInScreen` (`feature/auth/.../signin/SignInScreen.kt`):
- Add a footer line with two tappable links: "Terms (EULA)" and "Community Guidelines", each navigating to `Route.Legal(LegalDoc.X)`. (`SignInScreen` is in `:feature:auth`; navigation is exposed via a callback param `onOpenLegal: (LegalDoc) -> Unit` passed by `NavGraph`, keeping the feature free of `shared`'s `Route`. `LegalDoc` must therefore live somewhere both can see — put the `LegalDoc` enum in `:core:i18n` or a tiny `:core:domain/legal` value, OR pass two distinct callbacks `onOpenEula` / `onOpenGuidelines`. **Chosen:** two callbacks, no shared enum leak into `:feature:auth`.)
- **Acceptance gate:** sign-in is gated on EULA acceptance. `SignInViewModel` injects `EulaPort`; state carries `eulaAccepted: Boolean` derived from `acceptedVersion.needsEulaAcceptance()`. The screen shows a required **"I agree to the Terms & Community Guidelines"** `FrSwitch`/checkbox above the auth buttons (a11y-named). The sign-in/sign-up/Google/Apple actions are `enabled = eulaAccepted && !isLoading`. Toggling it on calls `EulaPort.accept()`. Rationale: putting the gate at login (rather than a separate root-nav stage) is the confirmed decision and matches "surfaced at the login screen"; it also means acceptance is recorded for every account before any content is created.

> We deliberately do **not** add a `Route.Eula` root-nav stage (unlike Consent). The acceptance lives on the login screen, and the embedded docs are reachable both from login and Profile. This keeps the post-auth gate machine (`RootNavViewModel`) unchanged.

### 6.6 Profile entry + support contact

- `ProfileScreen` gains a "Legal" `FrSettingsSection` (or rows in an "About" section): "Terms (EULA)", "Community Guidelines" → `Route.Legal(...)`. This satisfies "also reachable from Profile."
- **Published contact info** (the implicit 5th requirement): the Community Guidelines doc + the App Store Connect support URL both point to **`https://foodrats-de4ec.web.app`** (the live Firebase Hosting domain) with a support contact placeholder `hello@chsumiapps.com` (or a contact form on that domain). The runbook records this as the canonical published contact. This is a content/store-config item, not code.

### 6.7 Routing for legal docs

`Route.kt` gains `@Serializable data class Legal(val doc: String) : Route.Public` (Public — the docs must be readable pre-auth from the login links) + arm in `requiresSession()` → `false`. `NavGraph.kt`: `composable<Route.Legal> { entry -> LegalDocsScreen(doc = LegalDoc.valueOf(entry.toRoute<Route.Legal>().doc), onBack = { controller.popBackStack() }) }`. `SignInScreen` is wired with `onOpenEula`/`onOpenGuidelines` callbacks that `controller.navigate(Route.Legal(...))`.

---

## 7. New module `feature/moderation`

### 7.1 `settings.gradle.kts`

Add (alphabetical, with the other features):
```kotlin
include(":feature:moderation")
```

### 7.2 `feature/moderation/build.gradle.kts`

Clone `feature/crew/build.gradle.kts` (the closest Firebase-feature template): `kotlinMultiplatform` + `androidMultiplatformLibrary` + `composeMultiplatform` + `composeCompiler` + `kotlinxSerialization`; `iosArm64()` + `iosSimulatorArm64()`; `namespace = "es.schsebastian.foodrats.feature.moderation"`; **`jvmTarget = JvmTarget.JVM_17`** (Firebase); `withHostTest { isIncludeAndroidResources = true }`.

`commonMain` deps: `projects.core.domain`, `projects.core.data`, `projects.core.designsystem`, `projects.core.presentation`, `projects.core.i18n`, `libs.bundles.feature.ui`, `libs.bundles.firebase.gitlive`, `libs.bundles.kotlinx.common`, `libs.androidx.datastore.preferences` (if needed), `libs.coil.compose` + `libs.coil.network.ktor3` + `libs.ktor.client.core` (BlockedUsers avatars).
`androidMain`: `implementation(project.dependencies.platform(libs.firebase.bom))` + `libs.ktor.client.okhttp`.
`iosMain`: `libs.ktor.client.darwin`.
`commonTest`: `libs.bundles.feature.test`. `androidHostTest`: `libs.bundles.feature.hosttest`.

### 7.3 `ModerationModule.kt` (`feature/moderation/.../di/`)

```kotlin
val moderationModule = module {
    // Block
    single<BlockDataSource> { BlockFirestoreDataSource(get(), get()) }
    single<BlockedAccountsPort> { FirebaseBlockRepository(get(), get(), get(), session = get()) }
    // Report
    single<ReportDataSource> { ReportFirestoreDataSource(get(), get()) }
    singleOf(::ReportErrorMapper)
    singleOf(::BlockErrorMapper)
    single<ReportPort> { FirebaseReportRepository(get(), get(), get()) }
    // TextModeration — the PURE impl from :core:domain (no IO), single for cheap reuse.
    single<TextModerationPort> { WordlistTextModeration() }
    // ViewModels (explicit get() for ports; analytics passed explicitly per the project convention)
    viewModel { BlockedUsersViewModel(blocked = get(), accountRead = get(), analytics = get()) }
    viewModel { (target: ReportTargetArg) -> ReportViewModel(target = target, report = get(), session = get(), analytics = get()) }
}
```
Registered in `shared/.../app/di/AppModule.kt` `appModules` list (after `feedModule`/`statsModule`, before the catalog modules). The `TextModerationPort` single is consumed by feed (`MealDetailViewModel`) and meal (`ComposePlateViewModel`) via the shared graph — those `*Module` files pass `textModeration = get()` explicitly (no default param, so the binding is required).

### 7.4 `ModerationModuleVerifyTest` (`androidHostTest`)

`moderationModule.verify(extraTypes = listOf(...))` with the cross-module types moderation consumes but does not bind: `SessionProvider::class`, `AccountReadPort::class`, `AnalyticsPort::class`, `DispatcherProvider::class` (if injected directly), `Clock::class`, plus the Firestore handle type used by the data sources (mirror how `crewModule`/`feedModule` list theirs). A missing/mistyped binding fails here, not at launch.

### 7.5 Consumer-module verify tests update

`FeedModuleVerifyTest`, `StatsModuleVerifyTest`, and `MealModuleVerifyTest` `extraTypes` gain `BlockedAccountsPort::class` (feed + stats) and `TextModerationPort::class` (feed + meal) since those VMs/use cases now consume them through the shared graph.

---

## 8. Security rules deltas (`firestore.rules`)

### 8.1 Block list — owner-private subcollection

Add inside `match /accounts/{uid}` (next to `devices`/`achievements`):
```
// Block list (UGC compliance §5). Owner-private on read AND write — mirrors /devices and
// /achievements. The doc id IS the blocked account id; only the owner can see or modify whom
// they've blocked. Used purely as a client-side read filter (no server fan-out).
match /blocks/{blockedUid} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
```

### 8.2 Reports — create-only-by-reporter, server-only read

Add a top-level `match /reports/{reportId}` collection (next to `nudges`/catalog collections). The authoritative rule lives in `firestore.rules` (and is exercised by `firestore-tests/tests/reports.test.ts`). It is hardened beyond the first cut (security review F3–F6):

```
match /reports/{reportId} {
  allow read:          if false;   // server-only (Admin SDK)
  allow update, delete: if false;  // immutable; server actions use Admin SDK
  allow create: if request.auth != null
                && request.resource.data.reporterId == request.auth.uid
                && request.resource.data.keys().hasOnly([            // F4 field whitelist
                     'reporterId','targetType','crewId','mealId','commentId',
                     'accountId','targetKey','reason','status','createdAtEpochMs'])
                && request.resource.data.status == "open"
                && request.resource.data.reason in [...]
                && <createdAtEpochMs is int within ±60s>
                && reportId == request.auth.uid + "|" + request.resource.data.targetKey   // F3
                && (
                  // meal: fields reconstruct targetKey, reporter ∈ crew.memberIds, meal exists  (F5)
                  // comment: + commentId, comment exists                                        (F5)
                  // account: accountId != reporter (no self-report), account exists             (F6)
                );
}
```

> Reads are denied to clients (a moderation queue must not be world/crew-readable); the function reads via the Admin SDK (bypasses rules). The create rule **pins** each id field into `targetKey` (so the trigger's delete-by-field can't be aimed at an unrelated victim — F4), gates meal/comment reports on **crew membership + target existence** (so off-crew throwaway accounts can't dogpile-nuke content they can't see — F5), and blocks account self-reports via `accountId != request.auth.uid` (F6). The deterministic `|`-joined id makes a second report by the same reporter a denied create (mapped to `AlreadyReported`).

### 8.3 Collections in use after this change

| Collection / path | Purpose | Client read | Client write |
|---|---|---|---|
| `accounts/{uid}/blocks/{blockedUid}` | block list (`§5`) | owner only | owner only |
| `reports/{reportId}` | report queue (`§4`) | **never** | create-only-by-reporter, immutable |

`storage.rules` is **unchanged** — no new blobs (image moderation is out of scope; auto-hide reuses the existing `onMealDeleted` Storage reclaim).

---

## 9. `foodrats.app` purge

Real domain is `foodrats-de4ec.web.app` (already `DeepLinks.WEB_HOST`, the manifest App-Links host, and the iOS Universal-Links entitlement). Remaining literal `foodrats.app` references are **docs-only** and must be corrected:
- `docs/store-release/PUBLICATION.md` (lines ~60, ~94–95, ~111) — replace the "future vanity domain `foodrats.app`" guidance with `foodrats-de4ec.web.app` as the canonical published domain (account-deletion landing + support + `.well-known`).
- `deeplinks/README.md` (line ~12) — drop the "`foodrats.app` is a future vanity domain" note (we are not pursuing it).
- Root `CLAUDE.md` (the navigation-decision entry, line ~133) references hosting `.well-known` at `https://foodrats.app/.well-known/` — correct to `https://foodrats-de4ec.web.app/.well-known/`.

Code/manifest/entitlements already use the live domain — verify with `grep -rn 'foodrats\.app' --exclude-dir=.git --exclude-dir=build .` and confirm only intended `foodrats-de4ec.web.app` / package-name (`es.schsebastian.foodrats.app`) matches remain.

---

## 10. Analytics (optional but recommended)

Add moderation leaves to the sealed `AnalyticsEvent` taxonomy (`:core:domain/analytics/AnalyticsEvent.kt`), fired at the call site **after** the use case/port returns `Ok` (never inside a use case), per the project convention:
- `ContentReported(targetType: String, reason: String)` → snake_case `content_reported`.
- `UserBlocked` / `UserUnblocked` → `user_blocked` / `user_unblocked`.
- `CommentBlockedByFilter(category: String)` → `comment_blocked_by_filter` (fired when `CommentError.Write.Objectionable` is returned).
- `EulaAccepted(version: Int)` → `eula_accepted`.
No PII / no matched term / no reported text — only the coarse `ReportReason` / `ModerationCategory` string. Update `docs/analytics/TRACKING_PLAN.md`. The consent gate (`ConsentGatedAnalytics`) already no-ops these until opt-in.

---

## 11. Moderation runbook

See `docs/moderation/RUNBOOK.md` — review process, ≤ 24 h SLA, the `THRESHOLD = 3` auto-hide rule, manual takedown via crew-owner/author delete RBAC, the structured `onReportCreated` audit log to grep, and the published support contact (`https://foodrats-de4ec.web.app`, `hello@chsumiapps.com`).

---

## 12. Verification plan (per phase — run on the worktree)

This spec is docs-only. Implementation phases verify with:
- `:core:domain:testAndroidHostTest` — `WordlistTextModerationTest`, port shapes, Konsist (no Firebase/Android/Compose in domain).
- `:core:data:testAndroidHostTest` — `EulaRepositoryTest`.
- `:feature:moderation:testAndroidHostTest` — `ModerationErrorToStringKeyTest` + `ModerationModuleVerifyTest`.
- `:feature:feed:testAndroidHostTest` — updated `CommentErrorToStringKeyTest` (Objectionable arm) + `FeedModuleVerifyTest`.
- `:feature:meal:testAndroidHostTest` + `:feature:stats:testAndroidHostTest` — verify tests with new `extraTypes`.
- `:androidApp:assembleDebug` and `:shared:linkDebugFrameworkIosSimulatorArm64`.
- `functions`: `pnpm --dir functions build` (tsc) + `pnpm --dir functions test` (vitest, incl. `onReportCreated.test.ts`).

**Manual (not codeable here):** deploy `firestore.rules` + functions (`pnpm dlx firebase-tools deploy --only firestore:rules,functions --project foodrats-de4ec`); set the standard EULA + support URL in App Store Connect; the on-device smoke walk (block → confirm content hidden in feed/detail/stats; report ×3 → confirm auto-hide; comment with a flagged term → confirm hard-block; EULA gate at login).

---

## 13. Open items / deferred

- **Image moderation** is explicitly out of scope (zero-paid-infra). If Apple pushes back specifically on plate photos, the cheapest path is on-device NSFW classification reusing the MediaPipe pipeline — a separate spec.
- **Symmetric/server-enforced block** (the blocked user also can't see the blocker) is deferred; the MVP block is a one-directional client read filter, which is standard and sufficient.
- **Account-level auto-takedown** is intentionally human-gated (`onReportCreated` flags but never deletes accounts).
- **EULA root-nav stage** was considered and rejected in favor of the login-screen gate (confirmed decision).
