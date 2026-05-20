# Server-request minimization — design spec

**Status**: ready for plan
**Date**: 2026-05-20
**Author**: Sebastián (with Claude Code)
**Supersedes**: §3.4 and §6 of `2026-05-19-peer-rating-redesign.md` — the `ratings/` subcollection is removed; ratings are denormalized onto the parent meal document. The rest of the peer-rating design (immutable one-shot votes, transparency, 1–5 stars, voting window) stands.

## 1. Goal

Cut Firestore listener count and one-shot reads as aggressively as possible without changing observable behavior. Today the app opens up to ~480 concurrent rating-subcollection listeners on Stats (1 per meal in the 30-day window), runs two parallel meal queries when Feed and Stats are both alive, and re-issues identical reads on every navigation. The target is **one shared listener per crew per data class**, no subcollection fanout, and no duplicated reads across screens that look at overlapping data.

## 2. Decisions taken during brainstorm

| # | Decision | Choice |
|---|---|---|
| 1 | Where rating aggregates live | **Denormalized onto the parent `meals/{mealId}` document** as `ratings: Map<accountId, Int>`, `ratingSum: Int`, `voterCount: Int`. The `ratings/` subcollection is removed. |
| 2 | Rating-write atomicity | **`runTransaction`** — single Firestore transaction reads the meal doc, validates the voter has not yet voted (immutable one-shot), and writes the updated map + sum + count atomically. |
| 3 | Feed vs Stats overlap | **One shared 30-day flow per crew.** Feed filters to a single day client-side; Stats consumes the full window. MealDetail filters the same flow to a single meal. |
| 4 | Sharing strategy | **`shareIn(repoScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)`** for hot shared flows. 5 s debounce lets quick tab swaps reuse the listener. |
| 5 | Scope ownership | **`FirebaseMealRepository` owns a `repoScope = CoroutineScope(SupervisorJob() + dispatchers.default)`.** Cleared via a no-op `close()` that we don't call in MVP (the repo is a Koin `single` for the app lifetime). |
| 6 | `takenSlots()` GETs | **One query** filtered by `authorId == uid` and `dayKey == today`, returning the set of taken slots. |
| 7 | Session sharing | **`session.current` becomes a `StateFlow`** via `stateIn(repoScope, WhileSubscribed(5_000), null)` — multiple `first()` callers share one upstream Firebase Auth listener. |
| 8 | Crew detail sharing | **`CrewFirestoreDataSource.observeCrew(crewId)` is cached per crewId** with `shareIn(WhileSubscribed(5_000), replay = 1)`. Back-nav within 5 s reuses the listener. |
| 9 | Migration of pre-existing ratings | **Wipe the dev crew's meals** as part of rollout (closed MVP — no production data). New schema; old `ratings/` subcollection docs become orphans that no client reads. |
| 10 | Security rules | **Rewritten** so `ratings.{uid}` can only be set/updated by the authenticated user matching that uid, the `voterCount` and `ratingSum` must equal the recomputed values from the new map, and the voter cannot mutate other voters' entries. |
| 11 | Domain port shape | **`MealReadPort.observeFeed(crewId, day)` and `observeRange(crewId, from, to)` keep their public signatures**, but both are now backed by the single shared 30-day flow inside the repository. Use-cases and ViewModels do not change. |
| 12 | When the 30-day window slides | **Pinned on subscribe.** The window is `[today - 29d, today]` at the time the shared flow starts; if midnight passes mid-session, the flow does not re-pin. Feed/Stats recompute `today` from `Clock` independently on each emission. Acceptable for MVP — closed group; users will reopen. |

## 3. Data model changes

### 3.1 `MealDto` (in `feature/meal/data/firebase/MealDto.kt`)

Add three fields. Existing fields unchanged.

```kotlin
data class MealDto(
    val id: String,
    val authorId: String,
    val crewId: String,
    val dayKey: String,
    val slot: String,
    val photoUrl: String,
    val dish: String,
    val tags: List<String>,
    val publishedAtEpochMs: Long,
    // NEW — denormalized rating aggregate
    val ratings: Map<String, Int> = emptyMap(),
    val ratingSum: Int = 0,
    val voterCount: Int = 0,
)
```

Defaults of `emptyMap()` / `0` keep the mapper resilient to pre-migration documents (treated as "no votes yet").

### 3.2 `MealRating` value object

Stays as-is in `:core:domain` (still useful for typed UI events). What changes is the **read path**: instead of an `ObserveRatingsUseCase` over the subcollection, the meal-with-ratings projection is computed in the repository from the denormalized fields.

### 3.3 `MealWithRatings` projection (in `:core:domain`)

Unchanged from the peer-rating spec. The repository now constructs it from `MealDto.ratings + ratingSum + voterCount` without any subcollection read.

### 3.4 Removed

- `feature/meal/data/firebase/MealRatingsFirestoreDataSource.kt` — deleted.
- `MealRatingDto` — deleted.
- The `crews/{crewId}/meals/{mealId}/ratings/` subcollection — no longer written.

## 4. Architecture changes

### 4.1 `FirebaseMealRepository` — shared 30-day flow per crew

```kotlin
class FirebaseMealRepository(
    private val ds: MealFirestoreDataSource,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : MealReadPort, MealWritePort {

    private val repoScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    // Keyed by crewId. One entry per active crew. Built lazily on first subscriber.
    private val streamsLock = Mutex()
    private val streams = mutableMapOf<CrewId, SharedFlow<List<MealWithRatings>>>()

    private suspend fun stream(crewId: CrewId): SharedFlow<List<MealWithRatings>> =
        streamsLock.withLock {
            streams.getOrPut(crewId) {
                ds.observeForRange(crewId.value, fromDayKey, toDayKey)
                    .map { dtos -> dtos.map { it.toMealWithRatings() } }
                    .shareIn(
                        scope = repoScope,
                        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                        replay = 1,
                    )
            }
        }

    override fun observeFeed(crewId: CrewId, day: MealDay): Flow<List<MealWithRatings>> =
        flow { emitAll(stream(crewId)) }
            .map { all -> all.filter { it.meal.day == day } }
            .distinctUntilChanged()

    override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<List<MealWithRatings>> =
        flow { emitAll(stream(crewId)) }
            .map { all -> all.filter { it.meal.day in from..to } }
            .distinctUntilChanged()
}
```

Notes:
- `fromDayKey` / `toDayKey` are computed once per `stream()` call from `clock.now()`. Decision #12 — pinned at subscribe time.
- `dispatchers.default` is correct here: the shared scope coordinates flow sharing, not I/O. The Firestore listener itself already hops to its own thread; the `withContext(dispatchers.io)` boundary inside `MealFirestoreDataSource` stays.
- `flow { emitAll(...) }` wrapping makes each consumer subscribe to the SharedFlow afresh so its independent filter pipeline runs in the consumer's collect scope.

### 4.2 `MealFirestoreDataSource` — `takenSlots` collapse

```kotlin
suspend fun takenSlots(crewId: String, authorId: String, dayKey: String): Set<MealSlot> =
    withContext(dispatchers.io) {
        firestore.collection("crews").document(crewId).collection("meals")
            .where("authorId", authorId)
            .where("dayKey", dayKey)
            .get()
            .documents
            .mapNotNull { runCatching { MealSlot.valueOf(it.get("slot")) }.getOrNull() }
            .toSet()
    }
```

One round-trip vs three.

### 4.3 `FirebaseAuthRepository` — `session.current` as StateFlow

```kotlin
override val current: StateFlow<Session?> =
    combine(authDataSource.sessions(), prefs.observe(SESSION_KEY)) { fbSession, stored ->
        toSession(fbSession, stored)
    }.stateIn(
        scope = repoScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )
```

The `MealReadPort` change above already covers the meal-side `repoScope`. `FirebaseAuthRepository` gains its own analogous scope. `Session?`'s `null` initial value is fine — callers already handle `null` as "unauthenticated".

**Caller change required**: callers that did `session.current.first { it != null }` still work (StateFlow is a Flow). Callers that do `session.current.first()` may now get the initial `null` immediately — audit each call site:
- `FeedViewModel.rateMeal` and `MealDetailViewModel.rateMeal`: use `.first { it != null }`
- `ComposePlateViewModel.loadTakenSlots`: same
- `RegisterDeviceTokenUseCase`: same

### 4.4 `CrewFirestoreDataSource` — per-crew shared flow cache

```kotlin
private val crewStreamsLock = Mutex()
private val crewStreams = mutableMapOf<String, SharedFlow<CrewDto?>>()

fun observeCrew(crewId: String): Flow<CrewDto?> = flow {
    val shared = crewStreamsLock.withLock {
        crewStreams.getOrPut(crewId) {
            firestore.collection("crews").document(crewId).snapshots
                .map { snap -> snap.takeIf { it.exists }?.data<CrewDto>() }
                .shareIn(repoScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
        }
    }
    emitAll(shared)
}
```

`CrewSettingsViewModel` and `CrewPickerViewModel` (which already projects via `observeMyCrews`, separate path) are not changed in their public API. Back-nav into `CrewSettings` within 5 s reuses the listener.

### 4.5 `RateMealUseCase` — denormalized atomic write

```kotlin
class RateMealUseCase(
    private val ds: MealFirestoreDataSource,
    private val activeCrew: ActiveCrewProvider,
    private val session: SessionProvider,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(mealId: MealId, score: Score): Result<Unit, MealError.Rate> =
        withContext(dispatchers.io) {
            val crewId = activeCrew.current.first() ?: return@withContext Result.failure(NoActiveCrew)
            val uid = session.current.first { it != null }!!.accountId.value
            ds.rateMeal(crewId.value, mealId.value, uid, score.value, clock.now())
        }
}
```

The transaction lives in `MealFirestoreDataSource.rateMeal()`:

```kotlin
suspend fun rateMeal(crewId: String, mealId: String, voterUid: String, score: Int, now: Instant) =
    withContext(dispatchers.io) {
        firestore.runTransaction { txn ->
            val ref = firestore.collection("crews").document(crewId).collection("meals").document(mealId)
            val snap = txn.get(ref)
            if (!snap.exists) return@runTransaction Result.failure(MealError.Rate.MealNotFound)
            val data = snap.data<MealDto>()
            if (voterUid == data.authorId) return@runTransaction Result.failure(SelfRatingForbidden)
            if (data.ratings.containsKey(voterUid)) return@runTransaction Result.failure(AlreadyRated)
            // Voting window: until end of (mealDay + 1d). Client validates; rule enforces.
            val mealDay = MealDay.fromKey(data.dayKey)
            if (!withinVotingWindow(now, mealDay)) return@runTransaction Result.failure(VotingClosed)
            val newRatings = data.ratings + (voterUid to score)
            txn.update(ref, mapOf(
                "ratings" to newRatings,
                "ratingSum" to newRatings.values.sum(),
                "voterCount" to newRatings.size,
            ))
            Result.success(Unit)
        }
    }
```

## 5. Security rules

`firestore.rules` — meal document updates from a non-author voter are constrained to ratings-aggregate fields only, and the diff must touch exactly the voter's own key.

```
match /crews/{crewId}/meals/{mealId} {
  allow read: if isCrewMember(crewId);

  // Author publishes — covered by existing create rule (unchanged).

  // Voter updates: only the three rating fields, only the voter's own ratings entry,
  // sum and count must be consistent with the new map.
  allow update: if isCrewMember(crewId)
    && request.auth.uid != resource.data.authorId
    && request.resource.data.diff(resource.data).affectedKeys()
         .hasOnly(['ratings', 'ratingSum', 'voterCount'])
    && request.resource.data.ratings.diff(resource.data.ratings).affectedKeys()
         .hasOnly([request.auth.uid])
    && request.resource.data.ratings[request.auth.uid] is int
    && request.resource.data.ratings[request.auth.uid] >= 1
    && request.resource.data.ratings[request.auth.uid] <= 5
    && request.resource.data.voterCount == request.resource.data.ratings.size()
    // Voting window: meal day + 48 h (lax bound for timezones), checked against publishedAtEpochMs
    && request.time.toMillis() <= resource.data.publishedAtEpochMs + duration.value(48, 'h').toMillis();
}
```

The `ratings/` subcollection's existing rules become dead but stay in the file behind a deprecation comment; we can sweep them in a follow-up.

## 6. Module impact

| Module | What changes |
|---|---|
| `:core:domain` | No type changes; `MealReadPort` signatures unchanged. |
| `:feature:meal` | `MealDto` gains 3 fields; `MealRatingsFirestoreDataSource` + `MealRatingDto` deleted; `RateMealUseCase` rewritten; `MealFirestoreDataSource.rateMeal` is new; `takenSlots` collapsed. |
| `:feature:feed` | `FirebaseMealRepository` gains repoScope + shared 30-day flow + per-crew cache. `FeedViewModel`/`MealDetailViewModel` adapt to `first { it != null }` for session reads. |
| `:feature:stats` | No code change — `observeRange` API unchanged; it now reuses the same shared flow. |
| `:feature:crew` | `CrewFirestoreDataSource.observeCrew` gains per-crewId shared cache. |
| `:feature:auth` | `FirebaseAuthRepository.current` becomes `StateFlow<Session?>`; callers use `.first { it != null }`. |
| `firestore.rules` | Rewritten section for meal updates; old ratings-subcollection rule deprecated. |
| Tests | New `*StreamSharingTest`s (Turbine) prove a second subscriber doesn't trigger a second upstream collect within the 5 s window. Konsist unchanged. |

## 7. Testing

- **`FirebaseMealRepositoryStreamSharingTest`** (commonTest): wire a fake `MealFirestoreDataSource` that increments a `subscriptionCount` on every `observeForRange` collect. Assert that two simultaneous `observeFeed` consumers + one `observeRange` consumer = `subscriptionCount == 1` for the crew. Tear down both, wait 6 s of virtual time, re-subscribe = `subscriptionCount == 2`.
- **`RateMealUseCaseTest`** (commonTest): transaction success, transaction conflict path (already-voted), self-rating rejected, outside voting window rejected. Use a fake transaction-capable Firestore.
- **`MealFirestoreDataSourceTakenSlotsTest`** (commonTest with fake): single `.get()` returns the expected `Set<MealSlot>`.
- **`FirebaseAuthRepositorySessionSharingTest`** (commonTest): multiple `current.first { it != null }` calls trigger one upstream Firebase Auth listener.
- **Existing `*ErrorToStringKeyTest`s**: extend the `MealError.Rate` sealed tree if new leaves (`AlreadyRated`, `MealNotFound`, `VotingClosed` if not present) are introduced; lock exhaustiveness.

## 8. Out of scope (explicit)

- Generic StoreKit-style query cache layer — not now.
- Switching meal reads to `Source.CACHE` with periodic refresh — Firestore's built-in offline persistence is enough.
- Background pre-fetch on app start.
- Backfilling old ratings — closed MVP, wipe the dev crew.
- Window-sliding logic when the user crosses midnight mid-session — acceptable artifact for MVP.

## 9. Rollout

1. Land code + tests on a feature branch.
2. Wipe the dev crew's meals in Firestore (single admin script or manual via console).
3. Deploy security rules: `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`.
4. Install on Android, smoke-test: publish → rate from another account → confirm Feed updates without a separate ratings listener, confirm Stats shows the same data.
5. Watch Firestore console "Active connections" panel — expected drop from O(meals) to O(1) per active client.
