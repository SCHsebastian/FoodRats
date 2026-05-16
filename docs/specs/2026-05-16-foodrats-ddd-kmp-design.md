# FoodRats — Base DDD + Clean Architecture for KMP

**Date:** 2026-05-16
**Status:** Draft, pending user review
**Scope:** Initial architectural scaffold for the FoodRats KMP project. Establishes Domain-Driven Design with bounded contexts, Clean Architecture module boundaries, roll-your-own MVI, Atomic Design for composables, error-as-enum-per-feature, i18n via compose-multiplatform-resources, Firebase MVP backend, Peekaboo camera, AndroidX DataStore Preferences, FCM + local notifications, Koin DI, Jetpack Nav Compose Multiplatform.

This spec **does not** implement the architecture — it defines the structure and the patterns. A separate implementation plan (writing-plans) will sequence the actual code.

---

## 1. Overview

FoodRats is a Kotlin Multiplatform Mobile app (Android + iOS via Compose Multiplatform) for closed-group daily meal sharing — 3-8 friends/family/colleagues post one photo per day with an overlay (date, food tag, 1-10 rating, dish name) and see group statistics (streaks, most-eaten dish, variety). The tone is social and lightly competitive, explicitly anti-calorie-tracking.

**MVP backend:** Firebase (Auth, Firestore, Storage, Messaging) via GitLive KMP bindings. Designed so the data layer can be swapped to a server we own later, without touching the domain.

**MVP delivery shape:** All 6 feature modules scaffolded with canonical package layout. **Auth** wired minimum-functional end-to-end (Google Sign-In → Firebase Auth → Session). **Meal** wired as the rich-domain exemplar (value objects, error enum, Peekaboo camera, draft persistence, i18n keys, MVI). The other 4 features get module skeletons + base classes + a one-paragraph README per feature.

---

## 2. Domain — bounded contexts and ubiquitous language

### 2.1 Bounded contexts

| Context | Responsibility | Key concepts |
|---|---|---|
| **Identity** | Sign in/up/out, current user profile | `Account`, `Session`, `Handle`, `DisplayName` |
| **Crew** | The closed group; invites, membership | `Crew`, `CrewCode`, `Member`, `Invite`, `CrewSize` (invariant: 3-8) |
| **Meal** | Daily post; capture, draft, publish | `Meal`, `Plate`, `Dish`, `FoodTag`, `Score`, `MealDay` |
| **Feed** | A crew's day-window of meals to view | `FeedEntry`, `FeedDay`, `FeedQuery` |
| **Stats** | Group analytics, streaks, comparisons | `Streak`, `Roundup`, `DishTally`, `VarietyScore`, `Leaderboard` |
| **Notifications** | Push + local reminders | `Reminder`, `StreakNudge`, `DeviceToken`, `DeliveryWindow` |

### 2.2 Ubiquitous-language rules

Enforced in code review and lint where possible. No synonyms.

- **Meal** = the daily domain entity. Never "Post", "Entry", "Bite" in code.
- **Plate** = the photo + overlay artifact attached to a Meal. Distinct from `Photo` (raw bytes from camera). Camera produces `Photo`; user composes `Plate`; `Plate` is part of `Meal`.
- **Score** = the 1-10 rating value object. Never "rating", "stars".
- **Crew** = the closed group. Never "group", "team", "circle".
- **Member** = a user *within* a specific Crew (distinct from `Account` in Identity context). One `Account` can be `Member` of multiple `Crew`s.
- **MealDay** = a calendar date in the user's local timezone, normalized. The invariant "one meal per day per member" lives on this value object.

### 2.3 Cross-context coupling rules

- Identity owns `Account`. Other contexts reference accounts via `AccountId` (value object), never the full `Account`.
- Meal references `MealAuthor = (AccountId, DisplayName, AvatarUrl?)` — a denormalized projection, not the full Identity entity.
- Stats reads `Meal`s through a thin read-only port (see §3.1); never reaches into Meal internals.
- Feed reads `Meal`s through the same read-only port. Feed never depends on `:feature:meal`.
- Notifications references `AccountId` only; never depends on Identity feature module directly.

---

## 3. Gradle module layout

Module dependencies enforce Clean Architecture at compile time. If you can't draw the dependency arrow in Gradle, you can't draw it in code.

```
androidApp/                     Android entry point. MainActivity, FoodRatsApplication, FirebaseMessagingService.
iosApp/                         iOS entry point. iOSApp.swift, AppDelegate, UNUserNotificationCenter wiring.
shared/                         Thin umbrella module. Only the Compose root, NavGraph, Koin composition, MainViewController.

core/
  domain/                       NO external deps except kotlin.stdlib + kotlinx-datetime + kotlinx-coroutines-core
                                (for Flow in observed-stream interfaces).
                                Custom Result<T,E>, Clock, DispatcherProvider, SessionProvider, CrashReporter.
                                Shared cross-feature domain types: AccountId, CrewId, Meal, MealId, Score, DishName,
                                FoodTag, MealDay, MealAuthor, MealReadPort.
  data/                         Depends on: core:domain.
                                Firebase init helpers, DataStore factory, error-mapping helpers, AppPreferences.
  designsystem/                 Depends on: core:i18n (for resolve() in previews only).
                                Theme, tokens, atoms, molecules, templates, FrIcons, FrPreview multi-preview.
                                NEVER imports domain types. Pure presentation primitives.
  presentation/                 Depends on: core:domain, core:i18n.
                                MviContract / MviViewModel base, collectAsStateMvi helper, ErrorToStringMapper
                                interface, KMP lifecycle-aware Compose helpers.
  i18n/                         No external deps. Sealed StringKey + CommonStringKey enum + resolve() composable.

feature/
  auth/                         Depends on: core:domain, core:data, core:designsystem, core:presentation, core:i18n
                                + platform Google SignIn libs (androidMain/iosMain).
  crew/                         Depends on: core:domain, core:data, core:designsystem, core:presentation, core:i18n.
  meal/                         Depends on: core:domain, core:data, core:designsystem, core:presentation, core:i18n,
                                peekaboo-ui, peekaboo-image-picker.
  feed/                         Depends on: core:domain, core:data, core:designsystem, core:presentation, core:i18n.
                                Reads Meals via MealReadPort from core:domain — NO direct dep on feature:meal.
  stats/                        Depends on: core:domain, core:data, core:designsystem, core:presentation, core:i18n.
                                Reads Meals via MealReadPort from core:domain — NO direct dep on feature:meal.
  notifications/                Depends on: core:domain, core:data, core:designsystem, core:presentation, core:i18n
                                + FCM. Heaviest expect/actual surface.
```

**Hard rules (Gradle-enforced):**

1. `:core:domain` declares NO external dependencies except `kotlin.stdlib`, `kotlinx-datetime` (for `Clock`/`Instant`/`LocalDate`), and `kotlinx-coroutines-core` (for `Flow` in observed-stream interfaces). One-shot suspend functions return `Result<T,E>`; streams return `Flow<Result<T, E>>` so errors are addressed to the same subscriber as data (see §8.7).
2. **Features cannot depend on other features.** Period. Cross-context reads go through ports in `:core:domain` (§3.1).
3. `shared/` contains no business logic.
4. `androidApp/` and `iosApp/` are entry points only.

**Single Gradle module per feature**, organized internally by package. Defer splitting features into per-layer Gradle modules until a feature's build time genuinely hurts.

### 3.1 Cross-context read pattern: shared domain types + read ports

Reference KMP projects (NowInAndroid, Mifos KMP) keep cross-feature contracts in `:core:domain` (NIA: `core:model` + `:feature:<x>:api` modules). FoodRats applies the simpler `:core:domain` variant.

**Rule:** any domain type that crosses contexts lives in `:core:domain`. Contexts that own write paths keep their write-only types and behavior in their own feature module.

Concretely:

```
:core:domain
  model/
    AccountId.kt, CrewId.kt                   ← always cross-context
    Meal.kt, MealId.kt                        ← read-shared (Feed and Stats consume Meals)
    Score.kt, DishName.kt, FoodTag.kt
    MealDay.kt, MealAuthor.kt                 ← value objects of the shared Meal
  port/
    MealReadPort.kt                           ← read-only interface

:feature:meal:domain
  model/
    MealDraft.kt, Plate.kt                    ← Meal-write-only types
  repository/
    MealRepository.kt                         ← extends MealReadPort, adds write surface
  error/
    MealError.kt
  usecase/
    StartMealDraftUseCase.kt
    UpdateMealDraftUseCase.kt
    PublishMealUseCase.kt
    ...
```

```kotlin
// :core:domain/port/MealReadPort.kt
interface MealReadPort {
    fun observeFeed(crewId: CrewId, day: MealDay): Flow<Result<List<Meal>, MealReadError>>
    fun observeRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<Result<List<Meal>, MealReadError>>
}

enum class MealReadError { Unauthorized, CrewNotFound, Unavailable }

// :feature:meal:domain/repository/MealRepository.kt
interface MealRepository : MealReadPort {
    suspend fun publish(draft: MealDraft): Result<Meal, MealError>
    suspend fun delete(id: MealId): Result<Unit, MealError>
    suspend fun saveDraft(draft: MealDraft): Result<Unit, MealError>
    fun observeDraft(): Flow<MealDraft?>
}

// :feature:meal:di — Koin binds MealReadPort to the same instance bound to MealRepository
val mealModule = module {
    single<MealRepository> { FirebaseMealRepository(...) }
    single<MealReadPort> { get<MealRepository>() }
}
```

Feed and Stats depend on `:core:domain` only; they inject `MealReadPort`. No Gradle dependency edge from `:feature:feed` or `:feature:stats` to `:feature:meal`. Identity ↔ everything else still goes through `SessionProvider` in `:core:domain`.

**Konsist enforcement** (`com.lemonappdev:konsist` test in `:core:domain:test`):
- `:core:domain` Kotlin files must not import `kotlinx.coroutines` beyond `kotlinx.coroutines.flow.Flow`.
- `:core:domain` Kotlin files must not import anything from `androidx.*`, `com.google.*`, `dev.gitlive.*`.

The "no feature-to-feature deps" rule is now enforced by Gradle alone (no Konsist needed) — `:feature:feed/build.gradle.kts` simply doesn't declare `:feature:meal`.

---

## 4. Presentation layer — `core:designsystem` + `core:presentation`

Reference projects (NowInAndroid, Mifos KMP) split this into two modules: a strictly-domain-agnostic design system, and a presentation utilities module. FoodRats follows the same split, adding Atomic Design vocabulary (atoms/molecules/templates) inside `:core:designsystem`.

### 4.1 `:core:designsystem`

Pure presentation primitives. Knows NOTHING about domain types — every parameter is a primitive, a String, or a presentation-layer enum.

```
core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/
  theme/
    FoodRatsTheme.kt
    Colors.kt                   light + dark ColorScheme tokens
    Typography.kt
    Shapes.kt
  tokens/                       design-token constants (NOT composables)
    Spacing.kt                  Spacing.xs=4dp, sm=8dp, md=16dp, lg=24dp, xl=32dp, xxl=64dp
    Radius.kt
    Elevation.kt
    Sizes.kt
  atoms/
    FrButton.kt                 variant: Primary / Secondary / Ghost
    FrIconButton.kt
    FrText.kt                   enforces typography token
    FrTextField.kt
    FrIcon.kt
    FrAvatar.kt
    FrChip.kt
    FrDivider.kt
    FrSpacer.kt
    FrProgressIndicator.kt
    FrShutterButton.kt
    FrIcons.kt                  curated icon set (CameraOff, GalleryImport, ...)
  molecules/
    FrLabeledTextField.kt
    FrAvatarWithName.kt
    FrTagChipRow.kt
    FrScoreBadge.kt             takes Int 1..10, not Score
    FrScorePicker.kt            takes (value: Int, onChange: (Int) -> Unit)
    FrEmptyState.kt             icon + headline + subtext + content slot
    FrErrorBanner.kt            takes a String (already i18n-resolved)
  templates/                    page-shaped layouts with content slots, still domain-agnostic
    FrScreenScaffold.kt
    FrCaptureLayout.kt          viewfinder slot + controls slot
    FrFeedLayout.kt             sticky day header slot + lazy list slot
    FrFormLayout.kt
  preview/
    FrPreview.kt                multi-preview: light/dark/RTL/large-font
```

### 4.2 `:core:presentation`

Cross-cutting presentation utilities that may reference domain types. Hosts the MVI base.

```
core/presentation/src/commonMain/kotlin/es/schsebastian/foodrats/core/presentation/
  mvi/
    MviContract.kt              MviState, MviIntent, MviEffect marker interfaces
    MviViewModel.kt             base class (~40 LOC)
    collectAsStateMvi.kt        Compose helper, KMP-friendly
  error/
    ErrorToStringMapper.kt      interface only — implementations live in features
  lifecycle/
    collectAsStateWithLifecycleKmp.kt    KMP-safe wrapper
```

### 4.3 Where domain-aware composables live

Anything that takes or knows a domain type lives in the **owning feature's `presentation/components/` package**, NOT in `:core:designsystem`. This matches NowInAndroid's convention: `NewsResourceCard` lives in `feature/news/`, not `core/designsystem/`.

Examples:
- `FrMealCard` → `:feature:meal:presentation/components/FrMealCard.kt` (takes `MealUi`)
- `FrFeedDayHeader` → `:feature:feed:presentation/components/FrFeedDayHeader.kt`
- `FrCrewMemberRow` → `:feature:crew:presentation/components/FrCrewMemberRow.kt`

Feature-local components still follow the `Fr*` prefix and the "takes UI-shaped DTOs not domain entities" rule. They just aren't part of the shared design system.

### 4.4 Rules

- **All design-system composables prefixed `Fr*`.** Distinguishes from Material3 at a glance. Convention used by NowInAndroid (`Nia*`), Confetti (`Confetti*`), Mifos (`Kpt*`).
- **Variants via enum, not naming explosion.** `FrButton(variant = FrButtonVariant.Primary)`.
- **`:core:designsystem` atoms and molecules NEVER take domain types.** Compile-time check: `:core:designsystem` Kotlin must not import from `core.domain.model.*` or any feature package.
- **Feature-local components** (e.g. `FrMealCard`) take UI-shaped DTOs (`MealUi`), constructed by the feature's `UiMapper`. This is the "UI model" / "presentation model" pattern.
- **Pages live in feature modules.** A "page" = a Template filled by a ViewModel.
- **Every `:core:designsystem` atom and molecule has `@Preview` annotated with `FrPreview`** (light/dark/RTL/large-font).

### 4.5 MVI base (in `:core:presentation`)

```kotlin
interface MviState
interface MviIntent
interface MviEffect

abstract class MviViewModel<S : MviState, I : MviIntent, E : MviEffect>(
    initial: S,
) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()
    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    fun onIntent(intent: I) {
        viewModelScope.launch { handle(intent) }
    }

    protected abstract suspend fun handle(intent: I)

    protected fun update(reducer: (S) -> S) { _state.update(reducer) }
    protected suspend fun emit(effect: E) { _effects.send(effect) }
}
```

`handle(intent)` is impure (may suspend, may call use cases). Trade-off vs. pure `reduce(state, intent): S` accepted to avoid pushing every side effect into Effects. **Acknowledged divergence** from MVIKotlin's Executor+Reducer split and Orbit's `intent { reduce { } }` separation — we collapse them into one suspending function. Cost: no time-travel debugging, no deterministic replay. Benefit: ~80 LOC of infrastructure instead of a framework dependency. Tests assert against state-flow emissions via Turbine.

---

## 5. Domain conventions

### 5.1 Canonical per-feature package layout

Every feature follows this skeleton. Meal is the exemplar.

```
feature/<name>/src/commonMain/kotlin/.../feature/<name>/
  domain/
    model/                      entities + value objects
    repository/                 INTERFACE only
    usecase/                    one class per verb
    error/                      <Name>Error.kt (sealed interface + nested enums)
  data/
    firebase/                   DTOs, data sources, error mapper, mappers
    local/                      DataStore-backed local stores
    repository/                 <Name>RepositoryImpl
  presentation/
    <screen>/                   <Screen>Screen.kt, <Screen>ViewModel.kt, <Screen>Contract.kt, <Screen>UiMapper.kt
  di/                           <Name>Module.kt — Koin module
  i18n/                         (only inside features that have user-visible text)
    <Name>StringKey.kt
    composeResources/values/strings.xml
    composeResources/values-es/strings.xml
```

### 5.2 Value objects

Constructors private. Factory `of()` returns `Result<T, FeatureError>`. Invariants enforced in `init { }` or factory body. `@JvmInline value class` for single-field wrappers.

```kotlin
@JvmInline
value class Score private constructor(val value: Int) {
    companion object {
        const val MIN = 1
        const val MAX = 10
        fun of(value: Int): Result<Score, MealError.Validation> =
            if (value in MIN..MAX) Result.success(Score(value))
            else Result.failure(MealError.Validation.OutOfRange)
    }
}

@JvmInline
value class DishName private constructor(val value: String) {
    companion object {
        const val MAX_LEN = 60
        fun of(raw: String): Result<DishName, MealError.Validation> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty()             -> Result.failure(MealError.Validation.Blank)
                trimmed.length > MAX_LEN      -> Result.failure(MealError.Validation.TooLong)
                else                          -> Result.success(DishName(trimmed))
            }
        }
    }
}

data class MealDay(val date: LocalDate, val zone: TimeZone) {
    fun toKey(): String = date.toString()  // ISO yyyy-MM-dd, used as Firestore field
    companion object {
        fun today(clock: Clock, zone: TimeZone): MealDay =
            MealDay(clock.now().toLocalDateTime(zone).date, zone)
    }
}
```

All time logic uses `kotlinx-datetime` + an injected `Clock`. No `System.currentTimeMillis()`.

### 5.3 Entities

Immutable `data class`es. No business methods on entities for MVP — keep logic in use cases. Promote to entity methods only when the same logic appears on 3+ use cases.

```kotlin
data class Meal(
    val id: MealId,
    val author: MealAuthor,
    val crewId: CrewId,
    val day: MealDay,
    val plate: Plate,
    val score: Score,
    val dish: DishName,
    val tags: List<FoodTag>,
    val publishedAt: Instant,
)
```

### 5.4 Repository interfaces

Live in domain. Return `Result<T, FeatureError>` for fallible suspend operations. Observed streams return `Flow<Result<T, E>>` so errors are addressed to the same subscriber as data (see §8.7 for the rationale). Streams emit domain types, never DTOs.

```kotlin
// :feature:meal:domain (extends MealReadPort declared in :core:domain)
interface MealRepository : MealReadPort {
    suspend fun publish(draft: MealDraft): Result<Meal, MealError>
    suspend fun delete(id: MealId): Result<Unit, MealError>
    suspend fun saveDraft(draft: MealDraft): Result<Unit, MealError>
    fun observeDraft(): Flow<MealDraft?>      // local-only, can't fail → no Result wrapper
}

// :core:domain
interface MealReadPort {
    fun observeFeed(crewId: CrewId, day: MealDay): Flow<Result<List<Meal>, MealReadError>>
    fun observeRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<Result<List<Meal>, MealReadError>>
}
```

**Rule of thumb:** if the source can fail mid-stream (remote subscription, anything network-touching), wrap in `Result`. If the source is local-only and a failure is exceptional (DataStore read), don't.

### 5.5 Use cases

One class per verb. `operator fun invoke(...)`. Pure orchestration — no `withContext`, no exception catching.

```kotlin
class PublishMealUseCase(
    private val repository: MealRepository,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    suspend operator fun invoke(draft: MealDraft): Result<Meal, MealError> {
        val today = MealDay.today(clock, zone)
        if (draft.day != today) return Result.failure(MealError.Publish.NotToday)
        return repository.publish(draft)
    }
}
```

When a ViewModel needs three operations, it injects three use cases.

### 5.6 The `Result<T, E>` type

Custom type in `:core:domain`. NOT Kotlin's stdlib `Result<T>` (which carries `Throwable`).

```kotlin
sealed interface Result<out T, out E> {
    data class Ok<T>(val value: T) : Result<T, Nothing>
    data class Err<E>(val error: E) : Result<Nothing, E>
    companion object {
        fun <T> success(value: T): Result<T, Nothing> = Ok(value)
        fun <E> failure(error: E): Result<Nothing, E> = Err(error)
    }
}

inline fun <T, E, R> Result<T, E>.fold(onOk: (T) -> R, onErr: (E) -> R): R
inline fun <T, E, R> Result<T, E>.map(f: (T) -> R): Result<R, E>
inline fun <T, E, E2> Result<T, E>.mapError(f: (E) -> E2): Result<T, E2>
inline fun <T, E> Result<T, E>.getOrElse(default: (E) -> T): T
fun <T, E> Result<T, E>.getOrNull(): T?
```

### 5.7 Errors as nested sealed interfaces per feature

One error type per bounded concern, modeled as a `sealed interface` with **nested sealed interfaces** containing `data object` leaves. Not nested `enum`s — `data object` leaves preserve the ability to add per-case payloads later (e.g. attach context to `PhotoUploadFailed`) without a breaking refactor.

```kotlin
sealed interface MealError {
    sealed interface Validation : MealError {
        data object Blank : Validation
        data object TooLong : Validation
        data object OutOfRange : Validation
        data object NoPhoto : Validation
    }
    sealed interface Publish : MealError {
        data object AlreadyPostedToday : Publish
        data object NotToday : Publish
        data object PublishUnavailable : Publish
        data object PhotoUploadFailed : Publish
    }
    sealed interface Read : MealError {
        data object Unauthorized : Read
        data object CrewNotFound : Read
        data object NotFound : Read
    }
}
```

Adding `data class PhotoUploadFailed(val cause: UploadCause) : Publish` later is a single-line change. Same exhaustiveness as enums in `when` expressions.

**No `Unknown` cases — aspirational.** Unrecognized exceptions at the data boundary map to the safest user-actionable case (e.g., `PublishUnavailable` = "try again"); the original exception is logged via `CrashReporter`. Be honest about reality: when third-party SDKs throw novel exceptions, a pragmatic `Unexpected` case may need to be added in maintenance. The principle remains: don't introduce `Unknown` casually; do introduce it when the alternative is misleading users.

**Errors never carry strings.** Translation happens at the UI boundary via the error → StringKey mapper.

---

## 6. Dispatchers

The rule: **a `suspend` function must be safe to call from the main thread.** Whoever does actual blocking work is responsible for `withContext`. That means **dispatcher switching lives in the data layer only.** Use cases stay pure.

```kotlin
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
}
```

`Dispatchers.IO` has been available in `commonMain` on all Kotlin/Native targets since `kotlinx-coroutines-core 1.7.2` (mid-2023). No `expect/actual` is needed. The wrapper interface remains for DI swappability and test injection — but the iOS-specific fallback to `Dispatchers.Default` is obsolete and removed.

**Boundary:** exactly one `withContext(dispatchers.io) { ... }` per public data-layer method. Zero `withContext` anywhere above.

```
[Main]   ViewModel.onIntent → viewModelScope.launch (Main.immediate)
[Main]   useCase.invoke()                 — pure orchestration
[IO]     repository.publish() → withContext(io) { firestore.set(...) }
[Main]   result returns, _state.update { }
```

Tests inject a `TestDispatcherProvider` using `UnconfinedTestDispatcher` for all three slots.

---

## 7. i18n strategy

All user-facing text — including every error message — flows through compose-multiplatform-resources. No hardcoded strings outside `:core:i18n` and feature-specific resource folders.

### 7.1 Layout

Compose Multiplatform's resource processor generates a per-module `Res` object from each module's `composeResources/` directory. Each feature gets its own typed access surface; `core:i18n` only contains cross-feature commons + the `resolve()` accessor.

```
core/i18n/src/commonMain/composeResources/
  values/strings.xml              English (source of truth) — common_retry, common_cancel, app_name, ...
  values-es/strings.xml           Spanish (launch locale)
  # values-ar/strings.xml         Arabic (deferred; useful as RTL test target)

core/i18n/src/commonMain/kotlin/.../core/i18n/
  StringKey.kt                    common sealed interface — every feature's <Feature>StringKey implements it
  CommonStringKey.kt              enum implementing StringKey, only cross-feature commons
  resolve.kt                      @Composable fun resolve(key: StringKey, vararg args: Any): String
  Locale.kt                       current Locale, change-locale helper

feature/meal/src/commonMain/composeResources/values/strings.xml
feature/meal/src/commonMain/composeResources/values-es/strings.xml
feature/meal/src/commonMain/kotlin/.../feature/meal/i18n/MealStringKey.kt
```

### 7.2 Pattern

`StringKey` is a sealed interface in `:core:i18n`. Each feature defines its own enum implementing it, holding a reference to that feature's generated `StringResource`.

```kotlin
// :core:i18n
sealed interface StringKey {
    val resourceId: StringResource
}

enum class CommonStringKey(override val resourceId: StringResource) : StringKey {
    AppName(CoreI18nRes.string.app_name),
    Retry(CoreI18nRes.string.common_retry),
    Cancel(CoreI18nRes.string.common_cancel),
}
```

```kotlin
// :feature:meal — note: each module generates its own `Res` object;
// when a file imports more than one, alias with `import ... as MealRes`.
import foodrats.feature.meal.generated.resources.Res as MealRes
import foodrats.feature.meal.generated.resources.*

enum class MealStringKey(override val resourceId: StringResource) : StringKey {
    CaptureTitle(MealRes.string.meal_capture_title),
    CaptureNoPermissionHeadline(MealRes.string.meal_capture_no_perm_headline),
    CaptureNoPermissionSubtext(MealRes.string.meal_capture_no_perm_subtext),
    CommonOpenSettings(MealRes.string.meal_common_open_settings),
    MealErrorAlreadyPosted(MealRes.string.meal_error_already_posted),
    MealErrorNotToday(MealRes.string.meal_error_not_today),
    MealErrorPublishUnavailable(MealRes.string.meal_error_publish_unavailable),
    MealErrorPhotoUploadFailed(MealRes.string.meal_error_photo_upload_failed),
    MealErrorValidationBlank(MealRes.string.meal_error_validation_blank),
    MealErrorValidationTooLong(MealRes.string.meal_error_validation_too_long),
    MealErrorValidationOutOfRange(MealRes.string.meal_error_validation_out_of_range),
    MealErrorValidationNoPhoto(MealRes.string.meal_error_validation_no_photo),
    // exhaustive over MealError + all UI strings
}
```

`resolve(key: StringKey, vararg args: Any): String` in `:core:i18n` takes any `StringKey` and resolves the underlying `StringResource` via Compose Multiplatform's `stringResource(...)`.

### 7.3 Error → string mapping

Per-feature mapper, exhaustive on the sealed error type:

```kotlin
// feature/meal/presentation/.../MealErrorToStringKey.kt
fun MealError.toStringKey(): MealStringKey = when (this) {
    MealError.Publish.AlreadyPostedToday  -> MealStringKey.MealErrorAlreadyPosted
    MealError.Publish.NotToday            -> MealStringKey.MealErrorNotToday
    MealError.Publish.PublishUnavailable  -> MealStringKey.MealErrorPublishUnavailable
    MealError.Publish.PhotoUploadFailed   -> MealStringKey.MealErrorPhotoUploadFailed
    MealError.Validation.Blank            -> MealStringKey.MealErrorValidationBlank
    MealError.Validation.TooLong          -> MealStringKey.MealErrorValidationTooLong
    MealError.Validation.OutOfRange       -> MealStringKey.MealErrorValidationOutOfRange
    MealError.Validation.NoPhoto          -> MealStringKey.MealErrorValidationNoPhoto
    MealError.Read.Unauthorized           -> MealStringKey.MealErrorReadUnauthorized
    MealError.Read.CrewNotFound           -> MealStringKey.MealErrorReadCrewNotFound
    MealError.Read.NotFound               -> MealStringKey.MealErrorReadNotFound
}
```

`when` is exhaustive — adding a new `MealError` case forces a compile error in the mapper, forcing the translation.

In screens:

```kotlin
state.error?.let { err -> FrErrorBanner(text = resolve(err.toStringKey())) }
```

`FrErrorBanner` takes a `String`. Translation happens at the page boundary — the only place that knows the user's locale.

---

## 8. Data layer

### 8.1 Per-feature data structure

```
feature/<name>/data/
  firebase/
    <Name>Dto.kt                  @Serializable, mirrors Firestore doc shape; all fields nullable
    <Name>FirestoreDataSource.kt  raw Firestore reads/writes, throws freely
    <Name>StorageDataSource.kt    (optional) file uploads to Firebase Storage
    <Name>ErrorMapper.kt          Throwable → FeatureError
    <Name>Mapper.kt               Dto ↔ Domain (returns Result, total function)
  local/
    <Name>LocalStore.kt           DataStore-backed
  repository/
    <Name>RepositoryImpl.kt       orchestrates the above, implements domain interface
```

### 8.2 Six rules

1. **DTOs are pure transport.** No invariants, no behavior. Nullable everywhere.
2. **Mappers are total functions.** `MealDto.toDomain(): Result<Meal, MealError.Read>` — never throws. Missing required fields → `MealError.Read.NotFound` or `Validation`.
3. **Repository impls are the ONLY layer that `try/catch`es.** Once per public method, via `runCatching { ... }.fold(...)`. For streaming, use `.catch { t -> emit(Result.failure(errorMapper.map(t))) }`.
4. **Repository impls are the ONLY layer that calls `withContext(dispatchers.io)`.** Once per public method.
5. **One Firestore document shape per feature.** Even if "User" appears in three contexts, each context owns its own DTO. Bounded-context discipline.
6. **Observed streams that can fail return `Flow<Result<T, E>>`**, not `Flow<T>` + sidecar error channel. Rationale in §5.4. Local-only streams (DataStore) keep plain `Flow<T>`.

### 8.3 Canonical repository skeleton

```kotlin
internal class FirebaseMealRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val drafts: MealDraftLocalStore,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: MealErrorMapper,
    private val clock: Clock,
) : MealRepository {

    override suspend fun publish(draft: MealDraft): Result<Meal, MealError> =
        withContext(dispatchers.io) {
            runCatching {
                val photoUrl = storage.uploadPlate(draft.crewId, draft.plate)
                val dto = draft.toDto(photoUrl, publishedAt = clock.now())
                firestore
                    .collection("crews").document(draft.crewId.value)
                    .collection("meals").document(dto.id)
                    .set(dto)
                dto.toDomain().getOrElse { return@runCatching Result.failure(it) }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(errorMapper.mapPublish(it)) },
            )
        }

    override fun observeFeed(crewId: CrewId, day: MealDay): Flow<Result<List<Meal>, MealReadError>> =
        firestore
            .collection("crews").document(crewId.value).collection("meals")
            .where { "dayKey" equalTo day.toKey() }
            .snapshots
            .map<QuerySnapshot, Result<List<Meal>, MealReadError>> { snap ->
                Result.success(snap.documents.mapNotNull { it.data<MealDto>().toDomain().getOrNull() })
            }
            .catch { t -> emit(Result.failure(errorMapper.mapRead(t))) }
            .flowOn(dispatchers.io)
}
```

The `.catch { }` operator converts thrown exceptions to a `Result.failure` emission, so the subscriber sees errors in-band and never has to handle a `Throwable`.

### 8.4 Firebase wiring

**Library:** GitLive Firebase KMP bindings (`dev.gitlive:firebase-*`).

```
core/data/.../firebase/
  FirebaseInitializer.kt          expect — calls FirebaseApp.initializeApp
  FirebaseClock.kt                wraps FieldValue.serverTimestamp()
  FirebaseErrorMapping.kt         shared helpers for Auth/Firestore exception codes
  CrashReporter.kt                interface — Crashlytics impl in androidApp/iosApp
```

Platform setup is one-time:
- **androidApp**: add `com.google.gms.google-services` plugin, drop `google-services.json` in `androidApp/`, call `FirebaseInitializer.init()` in `FoodRatsApplication.onCreate`.
- **iosApp**: add `GoogleService-Info.plist` to Xcode target, call `FirebaseApp.configure()` in `iOSApp.swift` before constructing the Compose root.

### 8.5 Firestore document layout

```
/accounts/{accountId}                              Identity context — AccountDto
/crews/{crewId}                                    Crew context — CrewDto
/crews/{crewId}/members/{accountId}                Crew membership subcollection — MemberDto
/crews/{crewId}/meals/{mealId}                     Meal context — MealDto
/crews/{crewId}/stats/daily/{dayKey}               Stats — DailyStatsDto (server-aggregated later)
/devices/{accountId}/tokens/{deviceId}             Notifications — DeviceTokenDto
```

`mealId` generated client-side via `firestore.collection(...).document().id`. `publishedAt` is server-set via `FieldValue.serverTimestamp()` — clients can't backdate posts (matters for streaks).

### 8.6 DataStore

`androidx.datastore:datastore-preferences:1.2.1` (Preferences only — Proto not KMP-ready).

```
core/data/.../datastore/
  AppDataStore.kt                 expect fun providePreferencesDataStore(): DataStore<Preferences>
  AppDataStore.android.kt         actual — uses context.filesDir
  AppDataStore.ios.kt             actual — uses NSDocumentDirectory via Okio
  StoreKey.kt                     typed wrapper over Preferences.Key<T>
  AppPreferences.kt               observe(key) / set(key) / clear(key)
```

```kotlin
class StoreKey<T : Any>(internal val prefs: Preferences.Key<T>)

object Keys {
    val SessionToken         = StoreKey(stringPreferencesKey("session_token"))
    val ActiveCrewId         = StoreKey(stringPreferencesKey("active_crew_id"))
    val NotificationsAllowed = StoreKey(booleanPreferencesKey("notifications_allowed"))
    val Locale               = StoreKey(stringPreferencesKey("locale"))
}
```

Feature-specific local stores wrap `AppPreferences` with typed accessors and JSON-serialize complex objects via `kotlinx.serialization`.

### 8.7 Auth state — the special case

Identity owns the single source of truth for "who's signed in". Other features observe via `SessionProvider` in `core:domain`:

```kotlin
interface SessionProvider {
    val current: Flow<Session?>
    suspend fun requireCurrent(): Result<Session, AuthError.Session>
}

data class Session(val accountId: AccountId, val activeCrewId: CrewId?)
```

The Identity feature's `FirebaseAuthRepository` implements `SessionProvider`. Other repositories inject `SessionProvider`, never `AuthRepository`.

---

## 9. Exemplar A — Auth (Google Sign-In)

Google Sign-In is the only auth method for MVP. No email/password, no Apple Sign-In (deferred — App Store will eventually require it).

### 9.1 Layout

```
feature/auth/src/commonMain/kotlin/.../feature/auth/
  domain/
    model/Account.kt              AccountId, Handle, DisplayName, AvatarUrl?
    model/Session.kt              (defined in core:domain)
    repository/AuthRepository.kt  interface — implements SessionProvider
    usecase/SignInWithGoogleUseCase.kt
    usecase/SignOutUseCase.kt
    usecase/ObserveSessionUseCase.kt
    error/AuthError.kt
  data/
    google/
      GoogleAuthClient.kt         expect class — signIn() and signOut()
      GoogleIdToken.kt            @JvmInline value class
    firebase/
      FirebaseAuthDataSource.kt   exchanges GoogleIdToken for FirebaseUser
      AccountDto.kt
      AccountMapper.kt
      AuthErrorMapper.kt
    repository/FirebaseAuthRepository.kt
  presentation/signin/
    SignInScreen.kt               single "Continue with Google" CTA + loading + error
    SignInViewModel.kt
    SignInContract.kt
  di/AuthModule.kt
  i18n/AuthStringKey.kt + composeResources

feature/auth/src/androidMain/kotlin/.../data/google/
  GoogleAuthClient.android.kt     actual — Credential Manager + GetGoogleIdOption

feature/auth/src/iosMain/kotlin/.../data/google/
  GoogleAuthClient.ios.kt         actual — GoogleSignIn iOS SDK via Swift wrapper
```

### 9.2 `GoogleAuthClient` contract

```kotlin
expect class GoogleAuthClient {
    suspend fun signIn(): Result<GoogleIdToken, AuthError.GoogleSignIn>
    suspend fun signOut()
}

@JvmInline value class GoogleIdToken(val raw: String)

sealed interface AuthError {
    enum class GoogleSignIn : AuthError {
        UserCancelled,
        NoGoogleAccountsOnDevice,
        PlayServicesUnavailable,            // Android-only; iOS impl never emits
        NetworkUnavailable,
        UnknownClientFailure,
    }
    enum class Session : AuthError {
        NotSignedIn,
        TokenExpired,
        AccountDisabled,
        FirebaseUnavailable,
    }
}
```

### 9.3 Android actual

Uses Credential Manager (`androidx.credentials:credentials` + `androidx.credentials:credentials-play-services-auth` + `com.google.android.libraries.identity.googleid:googleid`). `MainActivity` provides a `Context` provider into Koin so the actual can present the credential picker.

### 9.4 iOS actual

Uses GoogleSignIn-iOS SDK added via Swift Package Manager into `iosApp.xcodeproj`. A small Swift wrapper exposes `signIn(presenting:) async throws -> String` (the id token) back to Kotlin. Simpler than raw cinterop for this SDK. The wrapper is invoked from the Kotlin actual via `suspendCoroutine`.

### 9.5 Firebase Auth exchange

```kotlin
internal class FirebaseAuthDataSource(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun signInWithGoogle(token: GoogleIdToken): FirebaseUser =
        withContext(dispatchers.io) {
            val cred = GoogleAuthProvider.credential(token.raw, accessToken = null)
            auth.signInWithCredential(cred).user!!
        }

    suspend fun ensureAccountDoc(user: FirebaseUser): AccountDto =
        withContext(dispatchers.io) {
            val ref = firestore.collection("accounts").document(user.uid)
            val existing = ref.get()
            if (existing.exists) existing.data<AccountDto>()
            else AccountDto.fromFirebaseUser(user).also { ref.set(it) }
        }

    val sessions: Flow<Session?> =
        auth.authStateChanged.map { user -> user?.toSession() }.flowOn(dispatchers.io)
}
```

### 9.6 Repository orchestrates

```kotlin
internal class FirebaseAuthRepository(
    private val googleClient: GoogleAuthClient,
    private val firebaseAuth: FirebaseAuthDataSource,
    private val errorMapper: AuthErrorMapper,
    private val prefs: AppPreferences,
) : AuthRepository, SessionProvider {
    override val current: Flow<Session?> = firebaseAuth.sessions

    override suspend fun signInWithGoogle(): Result<Session, AuthError> {
        val token = googleClient.signIn().fold(onOk = { it }, onErr = { return Result.failure(it) })
        return runCatching {
            val user = firebaseAuth.signInWithGoogle(token)
            val account = firebaseAuth.ensureAccountDoc(user)
            account.toSession()
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(errorMapper.mapFirebase(it)) },
        )
    }

    override suspend fun signOut(): Result<Unit, AuthError> = runCatching {
        firebaseAuth.signOut()
        googleClient.signOut()
        prefs.clear(Keys.SessionToken)
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(errorMapper.mapFirebase(it)) },
    )
}
```

### 9.7 `SignInScreen`

Minimal: single `FrButton(variant = Primary, label = resolve(AuthStringKey.SignInWithGoogle))`. Observes `state.isLoading` and `state.error`. On success, emits `Effect.NavigateToCrewPicker`. No domain richness — polish (offline detection, re-auth, multi-account) deferred.

---

## 10. Exemplar B — Meal (with Peekaboo)

Three screens: **Capture** (camera) → **Compose** (overlay editor) → **Publish** (review + submit). Demonstrates value objects, errors-as-enum, draft persistence, MVI, i18n, and Peekaboo.

### 10.1 Layout

See §5.1; concrete files:

Cross-feature shared types live in `:core:domain`; feature-private types and behavior live in `:feature:meal`.

```
core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/
  model/Meal.kt                    entity — read-shared across Feed and Stats
  model/MealId.kt                  @JvmInline
  model/Score.kt                   @JvmInline value class
  model/DishName.kt
  model/FoodTag.kt                 enum of curated tags + Custom(String)
  model/MealDay.kt
  model/MealAuthor.kt              AccountId + DisplayName + AvatarUrl?
  port/MealReadPort.kt             read-only interface consumed by Feed + Stats

feature/meal/src/commonMain/kotlin/es/schsebastian/foodrats/feature/meal/
  domain/
    model/MealDraft.kt             pre-publish, persisted to DataStore
    model/Plate.kt                 photo bytes + overlay metadata
    repository/MealRepository.kt   extends MealReadPort; adds write surface
    usecase/StartMealDraftUseCase.kt
    usecase/UpdateMealDraftUseCase.kt
    usecase/PublishMealUseCase.kt
    usecase/DiscardMealDraftUseCase.kt
    usecase/ObserveMealDraftUseCase.kt
    error/MealError.kt
  data/
    firebase/MealDto.kt
    firebase/MealFirestoreDataSource.kt
    firebase/PlateStorageDataSource.kt    uploads to /crews/{cid}/meals/{mid}.jpg
    firebase/MealErrorMapper.kt
    firebase/MealMapper.kt
    local/MealDraftLocalStore.kt          DataStore-backed
    repository/FirebaseMealRepository.kt  implements MealRepository (and thus MealReadPort)
  presentation/
    capture/CaptureMealScreen.kt
    capture/CaptureMealViewModel.kt
    capture/CaptureMealContract.kt
    compose/ComposePlateScreen.kt
    compose/ComposePlateViewModel.kt
    compose/ComposePlateContract.kt
    publish/PublishMealScreen.kt
    publish/PublishMealViewModel.kt
    publish/PublishMealContract.kt
    components/FrMealCard.kt              feature-local UI organism, takes MealUi
  i18n/
    MealStringKey.kt
    composeResources/values/strings.xml
    composeResources/values-es/strings.xml
  di/MealModule.kt
```

### 10.2 Peekaboo wiring

**Library:** Peekaboo 0.5.2 (`io.github.onseok:peekaboo-ui:0.5.2`, `io.github.onseok:peekaboo-image-picker:0.5.2`). Both work directly in commonMain — no expect/actual needed.

iOS Info.plist:
```xml
<key>NSCameraUsageDescription</key><string>FoodRats uses the camera to capture your daily meal.</string>
<key>NSPhotoLibraryUsageDescription</key><string>FoodRats can import meal photos from your library.</string>
```

Android Manifest:
```xml
<uses-feature android:name="android.hardware.camera" android:required="false" />
```
(`required="false"` so emulators and no-camera devices can still install.)

### 10.3 `CaptureMealScreen`

```kotlin
@Composable
fun CaptureMealScreen(vm: CaptureMealViewModel = koinViewModel(), onCaptured: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                CaptureMealEffect.NavigateToCompose -> onCaptured()
                /* ... */
            }
        }
    }
    FrScreenScaffold(topBar = { FrTopBar(title = resolve(MealStringKey.CaptureTitle)) }) {
        FrCaptureLayout(
            viewfinder = {
                PeekabooCamera(
                    modifier = Modifier.fillMaxSize(),
                    cameraMode = CameraMode.Back,
                    captureIcon = { onClick -> FrShutterButton(enabled = !state.isCapturing, onClick = onClick) },
                    onCapture = { bytes -> bytes?.let { vm.onIntent(CaptureMealIntent.PhotoTaken(it)) } },
                    permissionDeniedContent = {
                        FrEmptyState(
                            icon = FrIcons.CameraOff,
                            headline = resolve(MealStringKey.CaptureNoPermissionHeadline),
                            subtext = resolve(MealStringKey.CaptureNoPermissionSubtext),
                        ) {
                            FrButton(
                                variant = FrButtonVariant.Primary,
                                label = resolve(MealStringKey.CommonOpenSettings),
                                onClick = { vm.onIntent(CaptureMealIntent.OpenSettings) },
                            )
                        }
                    },
                )
            },
            controls = {
                FrIconButton(icon = FrIcons.GalleryImport, onClick = { vm.onIntent(CaptureMealIntent.PickFromGallery) })
            },
        )
    }
    state.error?.let { err -> FrErrorBanner(text = resolve(err.toStringKey())) }
}
```

### 10.4 `CaptureMealViewModel`

```kotlin
class CaptureMealViewModel(
    private val startDraft: StartMealDraftUseCase,
    private val updateDraft: UpdateMealDraftUseCase,
    private val sessionProvider: SessionProvider,
) : MviViewModel<CaptureMealState, CaptureMealIntent, CaptureMealEffect>(CaptureMealState()) {

    override suspend fun handle(intent: CaptureMealIntent) {
        when (intent) {
            CaptureMealIntent.Start -> {
                val session = sessionProvider.requireCurrent().getOrElse {
                    update { it.copy(error = MealError.Read.Unauthorized) }
                    return
                }
                startDraft(session.activeCrewId!!, session.accountId).fold(
                    onOk  = { update { it.copy(error = null) } },
                    onErr = { e -> update { it.copy(error = e) } },
                )
            }
            is CaptureMealIntent.PhotoTaken -> {
                update { it.copy(isCapturing = true) }
                updateDraft(UpdateMealDraftCommand.SetPhoto(intent.bytes)).fold(
                    onOk  = { update { it.copy(isCapturing = false) }; emit(CaptureMealEffect.NavigateToCompose) },
                    onErr = { e -> update { it.copy(isCapturing = false, error = e) } },
                )
            }
            CaptureMealIntent.PickFromGallery -> emit(CaptureMealEffect.OpenGalleryPicker)
            CaptureMealIntent.OpenSettings    -> emit(CaptureMealEffect.OpenAppSettings)
        }
    }
}
```

### 10.5 Draft persistence

Every `UpdateMealDraftUseCase` call persists to DataStore via `MealDraftLocalStore`. `ObserveMealDraftUseCase` exposes `Flow<MealDraft?>` so the Compose screen always picks up wherever the user left off. Force-close mid-compose does not lose the user's photo + overlay.

---

## 11. Notifications

Heaviest expect/actual feature. Domain stays pure; OS surface lives in platform source sets.

### 11.1 Layout

```
feature/notifications/src/commonMain/kotlin/.../feature/notifications/
  domain/
    model/Reminder.kt                  id, kind, deliverAt, payload
    model/ReminderKind.kt              enum: StreakAtRisk, CrewMemberPosted, WeeklyRoundupReady
    model/DeviceToken.kt
    model/DeliveryWindow.kt
    repository/DeviceTokenRepository.kt
    repository/NotificationPermissionGateway.kt    expect interface
    repository/LocalReminderScheduler.kt           expect interface
    usecase/RegisterDeviceTokenUseCase.kt
    usecase/RequestNotificationPermissionUseCase.kt
    usecase/ScheduleStreakNudgeUseCase.kt
    usecase/HandleIncomingPushUseCase.kt           called from platform receivers
    error/NotificationError.kt
  data/
    firebase/DeviceTokenFirestoreDataSource.kt
    firebase/FcmTokenProvider.kt                   expect
    repository/DeviceTokenRepositoryImpl.kt
  presentation/permission/
    NotificationPermissionScreen.kt                in-app rationale before requesting OS-level permission
    NotificationPermissionViewModel.kt
  di/NotificationsModule.kt

feature/notifications/src/androidMain/kotlin/.../platform/
  FoodRatsFirebaseMessagingService.kt              receives push → HandleIncomingPushUseCase
  AndroidNotificationPermissionGateway.kt          ActivityResultContracts.RequestPermission
  AndroidLocalReminderScheduler.kt                 AlarmManager + WorkManager
  AndroidFcmTokenProvider.kt
  NotificationChannels.kt                          POSTING + NUDGE channels

feature/notifications/src/iosMain/kotlin/.../platform/
  IosNotificationPermissionGateway.kt              UNUserNotificationCenter.requestAuthorization
  IosLocalReminderScheduler.kt                     UNUserNotificationCenter scheduled local notifs
  IosFcmTokenProvider.kt                           FirebaseMessaging.messaging.token()
  PushDelegate.kt                                  UNUserNotificationCenterDelegate bridge
```

### 11.2 Permission gateway

```kotlin
expect interface NotificationPermissionGateway {
    suspend fun current(): NotificationPermission
    suspend fun request(): NotificationPermission
    fun openSystemSettings()
}

enum class NotificationPermission { Granted, Denied, DeniedForever, NotYetRequested }
```

Flow: app start → if `NotYetRequested`, show `NotificationPermissionScreen` (in-app rationale: "We'll only nudge you when your streak is about to break"). CTA → `gateway.request()`. `DeniedForever` → fallback screen with "Open Settings".

### 11.3 FCM token

Registered via `RegisterDeviceTokenUseCase` called from `AppBootstrapper` after sign-in completes. Token rotations re-invoke the same use case via FCM's token-refresh callback (handled in the platform messaging service).

### 11.4 Streak nudges = local notifications

Scheduled client-side after each Meal publish: `ScheduleStreakNudgeUseCase` schedules a local notification for the next day at the user's `DeliveryWindow` start. Subsequent posts cancel and reschedule. No server-side cron for MVP.

### 11.5 Incoming push handling

Push converted to `Reminder` at the OS boundary, handed to dispatcher-pure `HandleIncomingPushUseCase` which decides system-notification vs in-app banner (foreground vs backgrounded) via a `NotificationBus` observed by `:shared`.

---

## 12. Dependency Injection — Koin

One module per feature + one per core layer.

```
shared/.../app/di/AppModule.kt
core/data/.../di/CoreDataModule.kt
core/designsystem/.../di/DesignSystemModule.kt    (theme provider; trivial)
core/presentation/.../di/PresentationModule.kt    (binds ErrorToStringMapper variants)
core/i18n/.../di/CoreI18nModule.kt
feature/auth/.../di/AuthModule.kt
feature/meal/.../di/MealModule.kt
feature/crew/.../di/CrewModule.kt
feature/feed/.../di/FeedModule.kt
feature/stats/.../di/StatsModule.kt
feature/notifications/.../di/NotificationsModule.kt
```

Pattern:

```kotlin
val mealModule = module {
    singleOf(::MealFirestoreDataSource)
    singleOf(::PlateStorageDataSource)
    singleOf(::MealDraftLocalStore)
    singleOf(::MealErrorMapper)
    single<MealRepository> { FirebaseMealRepository(get(), get(), get(), get(), get(), get()) }

    factoryOf(::StartMealDraftUseCase)
    factoryOf(::UpdateMealDraftUseCase)
    factoryOf(::PublishMealUseCase)
    factoryOf(::DiscardMealDraftUseCase)
    factoryOf(::ObserveMealDraftUseCase)

    viewModelOf(::CaptureMealViewModel)
    viewModelOf(::ComposePlateViewModel)
    viewModelOf(::PublishMealViewModel)
}
```

`AppModule.appModules` aggregates all feature + core modules. Android `Application.onCreate` calls `startKoin { modules(appModules) }`. iOS `iOSApp.swift` calls `KoinKt.doInitKoin()`. Platform-specific args (Android `Context`, iOS root `UIViewController`) passed via `parametersOf` at the entry point.

---

## 13. Navigation — Jetpack Nav Compose Multiplatform

Type-safe routes via `kotlinx.serialization`.

```kotlin
sealed interface Route {
    @Serializable data object SignIn : Route
    @Serializable data object CrewPicker : Route
    @Serializable data object Feed : Route
    @Serializable data object CaptureMeal : Route
    @Serializable data object ComposePlate : Route
    @Serializable data object PublishMeal : Route
    @Serializable data class CrewSettings(val crewId: String) : Route
    @Serializable data object Stats : Route
    @Serializable data object NotificationPermission : Route
}

@Composable
fun NavGraph(startDestination: Route) {
    val navController = rememberNavController()
    NavHost(navController, startDestination) {
        composable<Route.SignIn>      { SignInScreen(onSignedIn = { navController.navigate(Route.CrewPicker) }) }
        composable<Route.CaptureMeal> { CaptureMealScreen(onCaptured = { navController.navigate(Route.ComposePlate) }) }
        composable<Route.ComposePlate> { ComposePlateScreen(onComposed = { navController.navigate(Route.PublishMeal) }) }
        composable<Route.PublishMeal> { PublishMealScreen(onPublished = { navController.popBackStack(Route.Feed, inclusive = false) }) }
        composable<Route.Feed> { FeedScreen(onCaptureClick = { navController.navigate(Route.CaptureMeal) }) }
        composable<Route.Stats> { StatsScreen() }
        composable<Route.CrewPicker> { CrewPickerScreen(onPicked = { navController.navigate(Route.Feed) }) }
        composable<Route.CrewSettings> { entry -> CrewSettingsScreen(crewId = entry.toRoute<Route.CrewSettings>().crewId) }
        composable<Route.NotificationPermission> { NotificationPermissionScreen(onContinue = { navController.popBackStack() }) }
    }
}
```

**ViewModels never hold a `NavController`.** They emit `Effect.Navigate(Route)`; the screen's `LaunchedEffect` translates that to `navController.navigate(...)`. ViewModels stay platform-test-friendly.

---

## 14. Testing strategy

| Layer | Test type | Tooling | What to assert |
|---|---|---|---|
| Domain (entities, value objects, use cases) | Unit (commonTest) | `kotlin-test` + `kotest-assertions-core` | Invariants, factory `of()` results, use case orchestration. No coroutine machinery. |
| Repository impls | Unit (commonTest) | `kotlin-test` + `turbine` + fake DataSources + `TestDispatcherProvider` | Error mapping (Throwable → FeatureError), DTO ↔ domain round-trip, dispatcher behavior. |
| ViewModels | Unit (commonTest) | `kotlin-test` + `turbine` + `kotlinx-coroutines-test` | Intent → State emissions, Effect emissions. Use cases mocked. |
| Composables | Snapshot/UI (androidTest, iOS later) | Roborazzi (screenshots), Compose UI test (interaction) | Atom/molecule render under each variant; one happy-path screen test per feature. |
| End-to-end | Manual for MVP | Firebase emulator suite locally | Sign-in → publish meal → see in Feed. |

`commonTest` is the default. Only platform-specific tests (Android Context, iOS UIKit) live under `androidUnitTest` / `iosTest`. `:core:domain` targets ~90% coverage; feature presentation layers target happy-path + one error path per ViewModel. No coverage chase on UI code.

---

## 15. Version catalog additions

To be added to `gradle/libs.versions.toml`. Specific patch versions resolved during writing-plans phase.

```toml
[versions]
koin = "4.0.x"
kotlinxCoroutines = "1.10.x"
kotlinxDatetime = "0.6.x"
kotlinxSerialization = "1.7.x"
ktor = "3.x.x"                            # for future server swap, included as scaffold
datastore = "1.2.1"                       # confirmed
peekaboo = "0.5.2"                        # confirmed
gitliveFirebase = "2.x.x"
googleId = "1.1.x"
credentials = "1.3.x"
turbine = "1.x.x"
roborazzi = "1.x.x"
kotest = "5.x.x"
navCompose = "2.8.x"
okio = "3.x.x"                            # for iOS DataStore path

[libraries]
koin-core               = { module = "io.insert-koin:koin-core",                 version.ref = "koin" }
koin-compose            = { module = "io.insert-koin:koin-compose",              version.ref = "koin" }
koin-compose-viewmodel  = { module = "io.insert-koin:koin-compose-viewmodel",    version.ref = "koin" }
kotlinx-coroutines-core    = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core",   version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test    = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test",   version.ref = "kotlinxCoroutines" }
kotlinx-datetime           = { module = "org.jetbrains.kotlinx:kotlinx-datetime",          version.ref = "kotlinxDatetime" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
androidx-datastore-preferences          = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
peekaboo-ui                             = { module = "io.github.onseok:peekaboo-ui",             version.ref = "peekaboo" }
peekaboo-image-picker                   = { module = "io.github.onseok:peekaboo-image-picker",   version.ref = "peekaboo" }
firebase-auth                           = { module = "dev.gitlive:firebase-auth",                version.ref = "gitliveFirebase" }
firebase-firestore                      = { module = "dev.gitlive:firebase-firestore",           version.ref = "gitliveFirebase" }
firebase-storage                        = { module = "dev.gitlive:firebase-storage",             version.ref = "gitliveFirebase" }
firebase-messaging                      = { module = "dev.gitlive:firebase-messaging",           version.ref = "gitliveFirebase" }
firebase-common                         = { module = "dev.gitlive:firebase-common",              version.ref = "gitliveFirebase" }
androidx-credentials                    = { module = "androidx.credentials:credentials",                   version.ref = "credentials" }
androidx-credentials-play-services-auth = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "credentials" }
google-id                               = { module = "com.google.android.libraries.identity.googleid:googleid", version.ref = "googleId" }
nav-compose                             = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navCompose" }
turbine                                 = { module = "app.cash.turbine:turbine",                 version.ref = "turbine" }
kotest-assertions-core                  = { module = "io.kotest:kotest-assertions-core",         version.ref = "kotest" }
okio                                    = { module = "com.squareup.okio:okio",                   version.ref = "okio" }

[plugins]
kotlinx-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
google-services       = { id = "com.google.gms.google-services",            version = "4.x.x" }
```

---

## 16. Rename + namespace cleanup

Decision: align all packages to `es.schsebastian.foodrats`.

One-time refactor while codebase is tiny:
- `androidApp` namespace: `es.schsebastian.biteclub` → `es.schsebastian.foodrats`
- `androidApp` applicationId: `es.schsebastian.biteclub` → `es.schsebastian.foodrats`
- `shared` namespace: `es.schsebastian.biteclub.shared` → `es.schsebastian.foodrats.shared`
- Kotlin packages: `es.schsebastian.biteclub.*` → `es.schsebastian.foodrats.*`
- iOS framework `baseName`: `Shared` → `FoodRatsShared` (avoid generic name in DerivedData)
- Delete `Greeting.kt`, `GreetingUtil.kt`, `App.kt` boilerplate (kept until first feature compiles)

---

## 17. Out of scope (deferred)

- **Apple Sign-In** — required by App Store eventually; add when nearing iOS release.
- **Email/password auth** — not in MVP.
- **Server-side stats aggregation** — Stats derived client-side for MVP; move to Cloud Functions when crews grow.
- **SQLDelight** — DataStore Preferences is sufficient for MVP-scale data. Add SQLDelight when Stats needs offline-readable structured data.
- **Ktor** — listed in version catalog as scaffold for the future server swap. Not used by any feature in MVP.
- **Backend (owned server)** — Firebase-only for MVP. Domain layer designed so swap is a data-layer replacement, not a rewrite.
- **Arabic / RTL** — `values-ar/` deferred; useful future RTL test target.
- **Proto DataStore** — not KMP-ready as of writing.
- **Notifications: server-side scheduled nudges** — local-only for MVP.
- **Per-layer Gradle modules within features** — single module per feature; split if a feature's build time hurts.
- **Multi-crew membership UI polish** — domain supports it; UI for switching active crew may be deferred.

---

## 18. Open questions for the implementation plan

These don't block the design but should be resolved early in writing-plans:

1. **Exact Koin major version** — Koin 4.0 introduces `koin-bom` and updated `koin-compose-viewmodel`; confirm latest stable.
2. **Nav Compose Multiplatform exact version** — the `org.jetbrains.androidx.navigation:navigation-compose` artifact; pin once published versions are confirmed against Compose 1.11.
3. **`FoodRatsShared` framework `baseName`** — verify no collision with system frameworks.
4. **Firestore security rules** — out of scope for this spec but must accompany first publish to a live project.
5. **App icons + splash** — outside architecture; deferred to a design sprint.
6. **Crashlytics enablement** — interface in `core:data` defined; platform impls in `androidApp` / `iosApp` deferred to writing-plans.
7. **Streaming error handling** — RESOLVED in cross-check revision. Settled on `Flow<Result<List<T>, FeatureError>>` everywhere a stream can fail; local-only streams keep `Flow<T>`. See §5.4 and §8.2 rule 6.

---

## 19. Acceptance criteria for the scaffold

The scaffold is "done" when:

- All 6 feature modules compile (even if presentation/data are stubs).
- `:core:domain` compiles with only `kotlin.stdlib` + `kotlinx-datetime` + `kotlinx-coroutines-core` (verified by a Konsist test).
- `androidApp` builds and launches to a `SignInScreen` (real Google Sign-In via Credential Manager).
- `iosApp` builds and launches to a `SignInScreen` (real Google Sign-In via GoogleSignIn-iOS).
- A signed-in user can complete: SignIn → CaptureMeal (Peekaboo) → ComposePlate → PublishMeal → meal appears in Firestore.
- `./gradlew :core:domain:allTests` passes (a non-trivial value-object + use-case test suite is included).
- `./gradlew :feature:meal:allTests` passes with at least: value-object factory tests, MealMapper round-trip, FirebaseMealRepository error-mapping tests with fake datasources, CaptureMealViewModel state-emission tests.
- All user-visible strings render in both English and Spanish.
- Notification permission rationale screen renders on first launch; declining doesn't break the app.

This list anchors the next phase (writing-plans).

---

## 20. AAA reference-project cross-check log

After the initial draft, three parallel research agents cross-checked the design against production-grade KMP reference projects: NowInAndroid (Android-only, gold standard for module architecture), Confetti, Mifos KMP (`openMF/kmp-project-template`), Touchlab KaMP Kit, Kotlin/`kmm-production-sample`, MVIKotlin and Orbit samples, joreilly/PeopleInSpace. This section records what was verified, what changed, and what we intentionally kept divergent.

### 20.1 Adopted from the cross-check

| Change | Was | Now | Rationale |
|---|---|---|---|
| Errors | nested `enum`s inside `sealed interface` | nested **`sealed interface`** with **`data object`** leaves | Same exhaustiveness; preserves ability to attach per-case payloads later without breaking refactor. Aligns with idiomatic modern Kotlin. |
| Streaming errors | open question between `Flow<Result<>>` vs sidecar `SharedFlow` | `Flow<Result<List<T>, E>>` everywhere a stream can fail | Sidecar `SharedFlow` has notoriously awkward replay/cancellation semantics (Kotlin coroutines #2890). In-band errors preserve ordering and address the same subscriber. |
| iOS dispatcher | `expect fun ioDispatcher()` → `Dispatchers.Default` on iOS Native | `Dispatchers.IO` directly in `commonMain` | `Dispatchers.IO` has been available on all Native targets since kotlinx-coroutines 1.7.2 (mid-2023). The expect/actual fallback is obsolete. |
| Module: `:core:ui` | one module hosting Atomic Design + MVI + theme + helpers | split into `:core:designsystem` (atoms/molecules/templates, tokens, theme, FrPreview) + `:core:presentation` (MVI base, ErrorToStringMapper, lifecycle helpers) | NowInAndroid + Mifos both maintain this split; collapsing them mixes domain-agnostic primitives with domain-aware utilities. |
| Domain-aware composables | `FrMealCard`, `FrFeedDayHeader`, `FrCrewMemberRow` lived in `:core:ui` organisms | live in their owning feature's `presentation/components/` package | NowInAndroid puts `NewsResourceCard` in the feature module, not the design system. Anything that takes a domain type belongs in the feature. |
| Cross-feature dependency | Feed and Stats depended on `:feature:meal:domain` (with Konsist import restriction) | Feed and Stats depend on `:core:domain` only; the shared `Meal` entity + `MealReadPort` live in `:core:domain` | NIA's `feature:foryou:api` / `feature:foryou:impl` split solves this with a published API module. Hoisting to `:core:domain` is the simpler equivalent for our 6-feature scale; matches Mifos KMP's `core:model` philosophy. Eliminates a coupling time-bomb. |

### 20.2 Divergences we are keeping (with eyes open)

| Choice | Common practice | Why we diverge |
|---|---|---|
| Atomic Design vocabulary (atoms/molecules/templates) | Reference projects use **flat** `core/designsystem/component/` (NIA, Confetti, KaMP Kit, kmm-production-sample). Only `Tweener/czan` (a library, not an app) uses Atomic Design vocabulary. | User explicitly requested Atomic Design. It scales the vocabulary well; the cost is unfamiliarity for engineers used to NIA's flat layout. Mitigation: README in `:core:designsystem` explains the four tiers. |
| Multi-module-per-feature at 6 features | Pure-KMP samples (Confetti, KaMP Kit, kmm-production-sample, PeopleInSpace) stay fat in `:shared` with internal packages. Multi-module appears in NIA (Android-only, 6 features) and Mifos KMP (6 features). | At 6 features we're at the threshold where the cost flips. Splitting now is cheaper than retrofitting later. We accept that most KMP samples don't do this and we lean on the Android-flavored Mifos KMP template as our closest reference. |
| Thin `:shared` umbrella | Confetti, KaMP Kit, kmm-production-sample, PeopleInSpace all make `:shared` the **fat** module containing most code. Only Mifos KMP validates the thin-umbrella pattern. | Multi-module-per-feature requires a thin assembler at the top. The two choices reinforce each other. We accept this is a Mifos-only pattern outside of NIA-style Android-only projects. |
| Typed `<Feature>StringKey` enum implementing a sealed `StringKey` | Most projects call `stringResource(Res.string.x)` inline; the generated accessor is already type-safe. | Two real benefits: (1) unit-testable error→string mapping without Compose context (the `MealError.toStringKey()` function can be exercised in plain JUnit). (2) Encourages exhaustive `when` in error mappers. Cost is one extra indirection. |
| Impure `handle(intent)` in MVI | MVIKotlin/Orbit separate pure `reduce` from impure executor/intent block | Roll-your-own MVI is ~80 LOC vs an external framework dep; impure handle is the standard simplification in roll-your-own. Cost: no time-travel debugging. Accepted. |
| Konsist for boundary checks | NowInAndroid uses `dependencyGuard` (transitive locking) but not Konsist for boundary enforcement. The Gradle module boundary itself is the dominant enforcement. | Konsist is stricter, costs ~50 LOC of tests, and catches mistakes the compiler can't (e.g. importing a Firebase symbol into `:core:domain`). Net win. |

### 20.3 Honest realism

| Item | Original principle | Reality |
|---|---|---|
| "No `Unknown` error case ever" | Aspirational | Expect to add `Unexpected` cases pragmatically as third-party SDKs throw novel exceptions. Principle stands; absolute purity does not. |
| "Thin `:shared` umbrella will stay thin" | Aspirational | Reference projects show shared code tends to creep in. Quarterly review: any new code in `:shared` that contains business logic gets moved into a feature or core module. |

### 20.4 Sources cited by the cross-check

- NowInAndroid: <https://github.com/android/nowinandroid>
- Mifos KMP project template: <https://github.com/openMF/kmp-project-template>
- Confetti: <https://github.com/joreilly/Confetti>
- KaMP Kit: <https://github.com/touchlab/KaMPKit>
- kmm-production-sample: <https://github.com/Kotlin/kmm-production-sample>
- MVIKotlin: <https://github.com/arkivanov/MVIKotlin>
- Orbit MVI: <https://orbit-mvi.org/Core/>
- Tweener/czan (only KMP DS using Atomic Design vocab): <https://github.com/Tweener/czan>
- Kotlin coroutines `Dispatchers.IO` on Native: <https://github.com/Kotlin/kotlinx.coroutines/issues/3205>
- Kotlin coroutines `SharedFlow` semantics gotchas: <https://github.com/Kotlin/kotlinx.coroutines/issues/2890>
