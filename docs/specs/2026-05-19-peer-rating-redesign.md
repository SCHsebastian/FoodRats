# Peer-rating redesign — design spec

**Status**: ready for plan
**Date**: 2026-05-19
**Author**: Sebastián (with Claude Code)
**Supersedes**: §5.7 and §6 of `2026-05-16-foodrats-ddd-kmp-design.md` insofar as they describe the `Meal.score` author-assigned field. The rest of the v1 design stands.

## 1. Goal

Move the score that lives on a `Meal` from "author self-assigned at publish time" to "1–5 rating that **other** crew members give to the meal". The score becomes a peer-rating aggregate; the author no longer scores their own plate.

## 2. Decisions taken during brainstorm

| # | Decision | Choice |
|---|---|---|
| 1 | Mutability of a rater's vote | **Immutable, one-shot.** Mirrors meal immutability; no edit/delete path. |
| 2 | Transparency | **Transparent.** The feed shows the per-rater breakdown (`Maria: 4 ★`, …) plus the average and count. Crew is small and members know each other — anonymity adds no value. |
| 3 | Where the average is computed | **Client-side.** Each client observes the `ratings/` subcollection of every meal in view and computes `average` / `count` on the fly. No denormalization, meal stays immutable. |
| 4 | "Not yet voted" UX | **No special state.** Show whatever the current average is (or "Sin votos aún" when there are no ratings yet). No quorum threshold, no `fully rated` badge. |
| 5 | Migration of old `Meal.score` data | **Ignore.** `MealDto.score` is removed from the schema; old Firestore docs keep the field as orphan and the new mapper just drops it. |
| 6 | Port shape | **Extend `MealReadPort` to return `MealWithRatings`** for both `observeFeed` and `observeRange`, instead of adding a parallel method. |
| 7 | Stats impact | **Leaderboard now ranks by average of ratings received.** Meals with zero ratings are dropped from the average (do not count as 0). |
| 8 | Vote-widget UX | **1–5 stars** (quick-tap row). `Score.MAX` drops from 10 to 5. |
| 9 | Voting window | **Until the day after the meal day, local time.** Client checks strictly against `MealDay`; Firestore rule uses a lax 48 h cap on `publishedAtEpochMs` to cover any local timezone. |

## 3. Domain model changes (`:core:domain`)

### 3.1 `Score`
```kotlin
@JvmInline
value class Score private constructor(val value: Int) {
    companion object {
        const val MIN = 1
        const val MAX = 5            // was 10
        fun of(value: Int): Result<Score, MealValueObjectError> =
            if (value in MIN..MAX) Result.success(Score(value))
            else Result.failure(MealValueObjectError.ScoreOutOfRange)
    }
}
```
`MealValueObjectError.ScoreOutOfRange` stays as-is.

### 3.2 `Meal`
Remove the `score: Score` field. The data class becomes:
```kotlin
data class Meal(
    val id: MealId,
    val author: MealAuthor,
    val crewId: CrewId,
    val day: MealDay,
    val slot: MealSlot,
    val photoUrl: String,
    val dish: DishName,
    val tags: List<FoodTag>,
    val publishedAt: Instant,
)
```

### 3.3 New `MealRating` (peer rating)
```kotlin
data class MealRating(
    val raterId: AccountId,
    val raterDisplayName: String,
    val raterAvatarUrl: String?,
    val score: Score,
    val ratedAt: Instant,
)
```
Note: `raterDisplayName` / `raterAvatarUrl` are denormalized — same pattern the codebase already uses for `MealAuthor`. Lets the feed render `Maria: 4 ★` without an `accounts/` lookup, and keeps the rating self-contained for the future server-owned backend.

### 3.4 New `MealWithRatings` aggregate
```kotlin
data class MealWithRatings(
    val meal: Meal,
    val ratings: List<MealRating>,
) {
    val ratingCount: Int get() = ratings.size
    val averageScore: Double? get() =
        if (ratings.isEmpty()) null
        else ratings.map { it.score.value }.average()
    fun ratingBy(accountId: AccountId): MealRating? =
        ratings.firstOrNull { it.raterId == accountId }
}
```

### 3.5 `MealReadPort` — changed signature
```kotlin
interface MealReadPort {
    fun observeFeed(crewId: CrewId, day: MealDay):
        Flow<Result<List<MealWithRatings>, MealReadError>>
    fun observeRange(crewId: CrewId, from: MealDay, to: MealDay):
        Flow<Result<List<MealWithRatings>, MealReadError>>
}
enum class MealReadError { Unauthorized, CrewNotFound, Unavailable }   // unchanged
```
Both `observeFeed` and `observeRange` now emit `MealWithRatings`. Consumers that don't care about ratings can ignore the list at zero cost.

### 3.6 New `MealRatingPort` — write side (lives in `:core:domain`)
```kotlin
interface MealRatingPort {
    suspend fun rate(crewId: CrewId, mealId: MealId, score: Score):
        Result<Unit, RateError>
}

sealed interface RateError {
    data object Unauthorized       : RateError
    data object CannotRateOwnMeal  : RateError
    data object AlreadyRated       : RateError
    data object RatingWindowClosed : RateError
    data object RateUnavailable    : RateError
}
```
**Why `MealRatingPort` lives in `:core:domain`, not `:feature:meal`:** `:feature:feed` is the *consumer* (writes the rating from the feed card) and `:feature:meal` is the *producer* (owns the Firestore subcollection). Features cannot depend on each other, so the contract has to live in domain. This matches `MealReadPort` and `ActiveCrewProvider`.

**Why `RateError` is independent of `MealError`:** `MealError` lives in `:feature:meal`. If `RateError` were nested under it, `:core:domain` would import a feature type. Keeping `RateError` next to its port preserves the dependency direction.

## 4. Data layer (`:feature:meal`)

### 4.1 Firestore document shape

Subcollection path (new):
```
crews/{crewId}/meals/{mealId}/ratings/{raterUid}
```

Doc body:
```json
{
  "score": 4,                       // 1..5
  "ratedAtEpochMs": 1747681200000,
  "raterName": "Maria",
  "raterAvatarUrl": "https://…"     // nullable
}
```
- Doc ID `= raterUid` ⇒ one rating per rater per meal (the `!exists` rule enforces this on create).
- Immutable after create (no update/delete path).

### 4.2 New types
- `MealRatingDto(score, ratedAtEpochMs, raterName, raterAvatarUrl)` — `@Serializable`, all nullable defaults to match existing DTO style.
- `MealRatingMapper`:
  - `MealRatingDto.toDomain(raterId: AccountId): Result<MealRating, MealValueObjectError>` — `raterId` is sourced from the document ID, not the body.
  - `MealRating.toDto(): MealRatingDto`.
- `MealRatingsFirestoreDataSource`:
  - `fun observe(crewId, mealId): Flow<List<MealRatingDto>>` — wraps the subcollection's `snapshots`.
  - `suspend fun write(crewId, mealId, raterUid, dto: MealRatingDto)` — `set()` against `crews/{crew}/meals/{meal}/ratings/{raterUid}`.

### 4.3 `FirebaseMealRepository` changes
The class additionally implements `MealReadPort` and `MealRatingPort`.

`observeFeed` rewrites the read pipeline. Instead of mapping `MealDto → Meal`, it does:
```
firestore.observeForDay(crewId, day)              // Flow<List<MealDto>>
  .flatMapLatest { dtos ->
      if (dtos.isEmpty()) flowOf(emptyList())
      else combine(dtos.map { dto ->
          ratings.observe(crewId, dto.id!!)
              .map { rDtos -> dto to rDtos.toDomainList(dto.id) }
      }) { it.toList() }                          // List<Pair<MealDto, List<MealRating>>>
  }
  .map { pairs ->
      Result.success(pairs.mapNotNull { (dto, ratings) ->
          (dto.toDomain() as? Result.Ok)?.value?.let { MealWithRatings(it, ratings) }
      })
  }
  .catch { emit(Result.failure(errorMapper.mapRead(it))) }
  .flowOn(dispatchers.io)
```
`observeRange` reuses the same machinery (and the existing TODO that it currently delegates to `observeFeed(from)` is preserved — stats currently passes from == to).

`rate(crewId, mealId, score)`:
1. Read current account from `FirebaseAuth`. Missing ⇒ `RateError.Unauthorized`.
2. Read the meal doc (one-shot `get`). If `authorId == raterId` ⇒ `RateError.CannotRateOwnMeal`.
3. Compute `today = clock.now().asMealDay(TimeZone.currentSystemDefault())`. If `today.daysSince(meal.day) !in 0..1` ⇒ `RateError.RatingWindowClosed`. (`MealDay` exposes `dayKey`; add a `daysSince` helper.)
4. Build `MealRatingDto`, call `ratings.write(crewId, mealId, raterUid, dto)`.
5. Map Firestore errors via `MealErrorMapper.mapRate(throwable)`:
   - permission-denied ⇒ inspect message: if existing doc, `AlreadyRated`; else `Unauthorized` or `RatingWindowClosed` (best-effort — the rule denies in three places, distinguishing is heuristic). Default to `RateUnavailable` for unknowns.

### 4.4 `MealDto.score` removed
Drop the `score` property from the data class. Old Firestore documents keep the field; kotlinx-serialization's default behavior is to ignore unknown JSON keys, so deserialization continues to succeed.

Drop the `score = draft.score?.value` line from `publish()` in `FirebaseMealRepository`.

## 5. Firestore security rules

Add inside `match /crews/{crewId} { match /meals/{mealId} { … } }`:

```
match /ratings/{raterUid} {
  allow read: if request.auth != null
              && request.auth.uid in get(/databases/$(database)/documents/crews/$(crewId)).data.memberIds;

  allow create: if request.auth != null
                && raterUid == request.auth.uid
                && request.auth.uid in get(/databases/$(database)/documents/crews/$(crewId)).data.memberIds
                && request.auth.uid != get(/databases/$(database)/documents/crews/$(crewId)/meals/$(mealId)).data.authorId
                && !exists(/databases/$(database)/documents/crews/$(crewId)/meals/$(mealId)/ratings/$(raterUid))
                && request.resource.data.score is int
                && request.resource.data.score >= 1
                && request.resource.data.score <= 5
                && request.resource.data.ratedAtEpochMs is int
                && request.time.toMillis() - get(/databases/$(database)/documents/crews/$(crewId)/meals/$(mealId)).data.publishedAtEpochMs < 48 * 60 * 60 * 1000;
  // No update / delete: ratings are immutable.
}
```

Notes:
- The lax 48-hour cap is server-side; the client enforces the stricter "until end of day after publish, local time" check before calling.
- `!exists()` already prevents double-rating, so `AlreadyRated` maps to a `permission-denied` Firestore error.
- Existing meal/crew/code rules stay unchanged.

Deploy: `firebase deploy --only firestore:rules --project foodrats-de4ec` (requires interactive `firebase login`, no CI hookup).

## 6. UI changes

### 6.1 `:feature:meal` — publish flow
- `MealDraft.score: Score?` field — **delete**.
- `ComposePlateState.score` and `ComposePlateIntent.OnScoreChanged` — **delete**.
- `ComposePlateViewModel.persistAndAdvance` no longer reads or writes `score`.
- `ComposePlateScreen`: remove the `FrScorePicker` widget call.
- `PublishMealUseCase`: remove the `score == null ⇒ MealError.Validation.OutOfRange` branch.
- `MealStringKey.ScorePickerTitle` (or equivalent) and matching resources — **delete**.

The publish path's only validations after this change are: photo present, dish present, slot selected, day is today.

### 6.2 `:feature:feed` — feed card
`FeedMealUi` becomes:
```kotlin
data class FeedMealUi(
    val mealId: String,                 // for RateMeal intent
    val authorName: String,
    val authorAvatarUrl: String?,
    val authorId: String,               // for "I am the author, don't show widget"
    val photoUrl: String,
    val dishName: String,
    val tags: List<String>,
    val publishedAtEpochMs: Long,
    val dayEmote: String,
    val averageScore: Double?,          // null = no votes yet
    val ratingCount: Int,
    val votes: List<RaterVoteUi>,       // breakdown (transparent)
    val viewerRating: Int?,             // null if viewer hasn't voted
    val canRate: Boolean,               // false if viewer == author, window closed, or already voted
)
data class RaterVoteUi(
    val raterName: String,
    val raterAvatarUrl: String?,
    val score: Int,
)
```
Mapper signature changes to:
```kotlin
fun MealWithRatings.toFeedUi(viewerId: AccountId, today: MealDay): FeedMealUi
```
- `canRate = viewerId != meal.author.accountId && viewerRating == null && today.daysSince(meal.day) in 0..1`.

`FrFeedMealCard` layout:
- header row: avatar + author name on the left, average chip (e.g. `4.2 ★ · 3`) on the right; falls back to `"Sin votos aún"` text when `averageScore == null`.
- meal photo as today.
- dish + tags as today.
- **new** "Votes" section: vertical list of `RaterVoteUi` rendered as `FrAvatarWithName(initials, raterName) … FrText("★ ${score}")`. Hidden when `votes.isEmpty()`.
- **new** rate widget: if `canRate`, render a `FrStarRatingPicker` (1–5 horizontal buttons) that emits `FeedIntent.RateMeal(mealId, score)` on tap. If `viewerRating != null`, show `"Tu voto: ${viewerRating} ★"` text instead. Otherwise hide both.

`FrStarRatingPicker` is a new molecule in `:core:designsystem/molecules/`. `FrScorePicker` and `FrScoreBadge` (if used only by the deleted publish path / old feed) get reviewed and either repurposed or deleted.

### 6.3 `FeedContract` + ViewModel
```kotlin
sealed interface FeedIntent : MviIntent {
    data object PrevDay : FeedIntent
    data object NextDay : FeedIntent
    data object CaptureClicked : FeedIntent
    data object DismissError : FeedIntent
    data class  RateMeal(val mealId: String, val score: Int) : FeedIntent   // new
}

data class FeedState(
    val day: FeedDay? = null,
    val meals: List<FeedMealUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: FeedError? = null,
    val canGoPrev: Boolean = false,
    val canGoNext: Boolean = false,
    val pendingRateMealId: String? = null,       // for spinner / disabling buttons
    val rateError: RateError? = null,            // surfaced via snackbar; cleared by DismissError
) : MviState
```
`FeedViewModel` takes new constructor params: `MealRatingPort`, `SessionProvider` (to resolve `viewerId`). On `RateMeal`:
1. Validate `score in 1..5` (defensive — Score.of will reject).
2. Build `Score`, call `MealRatingPort.rate(activeCrewId, mealId, score)`.
3. On `Result.Err`, emit `state.copy(rateError = err)`. Mapping to user-visible strings happens in `FeedErrorToStringKey` (see §7).
4. On `Result.Ok`, do nothing — the `observeFeed` flow will surface the new rating.

### 6.4 `:feature:stats` — leaderboard
`computeLeaderboard` takes `List<MealWithRatings>` (was `List<Meal>`). Algorithm:
```kotlin
fun computeLeaderboard(meals: List<MealWithRatings>): Leaderboard {
    val byAuthor = meals.groupBy { it.meal.author.accountId }
    val entries = byAuthor.mapNotNull { (id, list) ->
        val averages = list.mapNotNull { it.averageScore }    // skip meals with 0 ratings
        if (averages.isEmpty()) null
        else {
            val sample = list.first().meal.author
            MemberAverage(
                accountId = id,
                displayName = sample.displayName,
                avatarUrl = sample.avatarUrl,
                averageScore = averages.average(),
                postCount = list.size,
            )
        }
    }.sortedWith(
        compareByDescending<MemberAverage> { it.averageScore }
            .thenByDescending { it.postCount }
            .thenBy { it.displayName },
    )
    return Leaderboard(entries)
}
```
Streaks (`PersonalStreak`, `CrewStreak`) don't read score — unchanged.

## 7. i18n

New `FeedStringKey` entries (English + Spanish):

| Key | English | Spanish |
|---|---|---|
| `NoVotesYet` | No votes yet | Sin votos aún |
| `AverageHeading` | Average | Promedio |
| `RateThisMeal` | Rate this meal | Puntúa este plato |
| `YourVote` | Your vote: %1$d ★ | Tu voto: %1$d ★ |
| `VotesHeading` | Votes | Votos |
| `RateErrorCannotRateOwnMeal` | You can't rate your own meal | No puedes puntuar tu propio plato |
| `RateErrorAlreadyRated` | You've already voted | Ya votaste |
| `RateErrorWindowClosed` | Voting is closed (more than a day has passed) | Ya pasó la ventana de voto |
| `RateErrorUnauthorized` | Sign in to vote | Inicia sesión para votar |
| `RateErrorUnavailable` | Couldn't submit your vote | No se pudo enviar tu voto |

`AverageHeading` and `VotesHeading` may be inlined as static labels rather than args — final names settled in implementation.

Args (e.g. `YourVote`'s `%1$d`) follow the existing pattern of `getString(resourceId, arg)` calls inside the Compose `resolve()` path. If the resolver does not currently support args, a small extension `resolveWithArgs(StringKey, vararg Any)` is added in `:core:i18n`. (Implementation plan will confirm what already exists.)

`FeedErrorToStringKey` is extended (or paired with a new `RateErrorToStringKey`) so the exhaustive `when` over `RateError` maps each case to a `FeedStringKey`. A matching `RateErrorToStringKeyTest` in `commonTest` locks exhaustiveness.

## 8. Tests (TDD order)

Order matches the implementation plan's commit checkpoints.

1. **Domain VO** (`core/domain`): `ScoreTest` — `Score.of(0)`, `of(5)`, `of(6)` cover boundaries with the new MAX=5.
2. **Aggregate helpers** (`core/domain`): `MealWithRatingsTest` — `averageScore` is `null` when empty, mean otherwise; `ratingBy` returns the matching rater.
3. **Mapper** (`feature/meal`): `MealRatingMapperTest` — DTO ↔ domain with a known `raterId`.
4. **Read pipeline** (`feature/meal`): existing `MealMapperTest` updated to drop `score`. Existing `FakeMealRepository` updated to expose `observeFeed` / `observeRange` returning `MealWithRatings`.
5. **Write port behaviour** (`feature/meal`): `FakeMealRatingPort` in `commonTest`; new test class covers `rate()` happy path, `CannotRateOwnMeal`, `RatingWindowClosed`, `AlreadyRated` (using a fake that simulates `!exists`).
6. **Publish use case** (`feature/meal`): `PublishMealUseCaseTest` drops score-related assertions; verifies score is no longer required for a valid publish.
7. **Feed** (`feature/feed`):
   - `FeedMealUiTest` — covers `canRate` true/false matrix (author = viewer / already voted / window closed / OK), `averageScore` null path, `votes` ordering.
   - `FeedViewModelTest` — `RateMeal` happy path, `RateMeal` self-rate maps to `RateErrorCannotRateOwnMeal`, `RateMeal` while pending preserves `pendingRateMealId`.
   - `RateErrorToStringKeyTest` — exhaustive over `RateError`.
8. **Stats** (`feature/stats`): `LeaderboardTest` — meals with zero ratings are excluded from `averageScore` but counted in `postCount`. Sorting unchanged.
9. **Konsist** (`core/domain`): existing `KonsistRulesTest` re-runs unchanged; no new imports introduced in domain.

Per CLAUDE.md, run with `./gradlew :feature:meal:testAndroidHostTest :feature:feed:testAndroidHostTest :feature:stats:testAndroidHostTest :core:domain:testAndroidHostTest` after each commit.

## 9. Migration & deploy steps

1. Implement everything per plan; tests green.
2. Build verification:
   - `./gradlew :core:domain:compileAndroidMain :feature:meal:compileAndroidMain :feature:feed:compileAndroidMain :feature:stats:compileAndroidMain`
   - `./gradlew :feature:meal:compileKotlinIosSimulatorArm64 :feature:feed:compileKotlinIosSimulatorArm64`
3. Deploy Firestore rules: `firebase deploy --only firestore:rules --project foodrats-de4ec` (manual `firebase login` once, then redeploy whenever rules change).
4. Manual smoke test on the two physical devices (Samsung R7AX10SF67D + iPhone AD755318) using the install commands in CLAUDE.md. Smoke cases:
   - Publish a meal on device A → it appears on device B feed with "Sin votos aún".
   - Rate the meal from device B → device A sees the average update; the rate widget on device B is replaced by "Tu voto: 4 ★".
   - Attempt to rate own meal from device A → error toast `No puedes puntuar tu propio plato`.
   - Open yesterday's meal in the feed → rate widget present. Open a meal from 2+ days ago (if reachable) → widget hidden.

## 10. Out of scope / explicitly deferred

- Editing or deleting a rating (decision 1).
- Push notification when a meal gets a new rating (could be a follow-up: "Maria rated your meal 4 ★").
- Server-side aggregate (`avgScore` / `ratingCount` denormalized on the meal doc) — only worth doing once we leave Firestore.
- The two acknowledged cross-feature dependency-direction violations (`feature:auth → feature:notifications`, `feature:meal → feature:notifications`) remain; not in scope here.
- Self-rate validation lives in `MealRatingPort` impl + Firestore rule; we do **not** also add a domain-layer check inside `MealWithRatings` or a use case wrapper — the port is the seam.

## 11. Open questions absorbed (not asked back)

- **i18n resolver signature for args (`%1$d`)**: the implementation plan verifies whether `resolve(StringKey)` already supports args or whether a sibling `resolveWithArgs` is needed. Spec assumes the latter; cheap to revisit.
- **`MealRating` denormalized name/avatar drift**: if the rater renames themselves later, old ratings keep the old name. Same trade-off `MealAuthor` makes today. Acceptable for MVP.
- **Score MAX change ripples**: any consumer that hard-codes `1..10` (e.g. unit tests with sample `Score.of(8)`) is updated to use values in `1..5`. Caught at compile time and by failing tests.
