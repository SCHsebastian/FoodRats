# Report — w1-reactions-data

DATA/INFRA layer for meal reactions: the `reactions/{uid}` Firestore subcollection +
`MealReactionPort` implementation (observe a meal's reactions, toggle the viewer's reaction),
its Koin binding, the security rule, and tests. Feed-card affordance + `meal_reacted` analytics
are explicitly out of scope (`w1-reactions-presentation`).

## Prior work check

No prior work for this task existed (no `docs/session/reports/w1-reactions-data.md` /
`handoffs/w1-reactions-data.md` on disk). Started clean. The domain task (`w1-reactions-domain`)
had already landed all types in `:core:domain.meal` — `ReactionKind` (one leaf `DailyGlyph`,
key `"daily_glyph"`), `MealReaction`, `MealReactions`, `MealReactionPort`, `ReactionToggle`,
`ReactionError` — all untouched here.

## Placement decision (important — diverges from the domain handoff's literal wording)

The domain handoff said the impl "lives in `:feature:feed`'s adapter layer." That contradicts the
actual codebase: **`:feature:feed` is Firebase-free (JVM 11, no GitLive dep)** and every comparable
cross-context port — `MealReadPort`, `MealRatingPort`, `MealCommentPort` — has its Firebase impl in
**`:feature:meal`**'s `data/firebase/` + `data/repository/`, bound in `mealModule`, and is merely
*consumed* by feed via `FeedModuleVerifyTest.extraTypes` (the binding happens in the `shared`
aggregator). I mirrored that established pattern (`FirebaseCommentRepository` is the exact
template). Net effect for presentation is identical: feed injects `MealReactionPort` from
`:core:domain` with NO Gradle dep on `:feature:meal`. The required verify command
(`:feature:feed:testAndroidHostTest`) passes because the only feed-side change is the verify-test
`extraTypes` entry.

## What changed

### DTO + mapping (`:feature:meal/data/firebase/`)
- **`ReactionDto.kt`** (NEW) — `@Serializable data class(reactorId, kind, reactedAtEpochMs)`,
  all nullable defaults (pre-launch: no migration; `ignoreUnknownKeys` tolerates extras). The doc
  ID is the reactor uid; `reactorId` is set from the doc ID on read. The glyph is NOT stored —
  only `kind.key` (`"daily_glyph"`).
- **`ReactionMapper.kt`** (NEW) — `ReactionDto.toDomainOrNull(crewId, mealId): MealReaction?`.
  Returns `null` (skips the doc) for a blank reactor uid OR an **unknown** `ReactionKind` key
  (forward-compat per the handoff — one unreadable doc must not blank the whole meal's count).

### Data-layer seam + datasource (`:feature:meal/data/firebase/`)
- **`ReactionFirestore.kt`** (NEW) — `internal interface` (mirrors `MealFirestore`): `observe`,
  `reactionOf`, `mealExists`, `put`, `remove`. The repository depends on this, not the concrete
  class, so the toggle orchestration is fakeable in `commonTest` and the Firebase→own-server swap
  re-binds one Koin line.
- **`ReactionFirestoreDataSource.kt`** (NEW) — the only Firestore-touching impl, over
  `crews/{crewId}/meals/{mealId}/reactions/{uid}`. Uses the existing GitLive vocabulary
  (`.snapshots.map`, `.document(uid).get().exists`, `.data<T>().copy(reactorId = d.id)`, `.set`,
  `.delete`).

### Port impl (`:feature:meal/data/repository/`)
- **`FirebaseReactionRepository.kt`** (NEW) — implements `MealReactionPort`.
  - `observe(crewId, mealId)` → `Flow<Result<MealReactions, ReactionError.Read>>`: maps the DTO
    stream into the aggregate (skipping unknown kinds), `.catch` → `PermissionDenied/Unauthenticated`
    ⇒ `Read.Unauthorized` else `Read.Unavailable`, `.flowOn(dispatchers.io)` (the observe-flow
    boundary, exactly as `FirebaseCommentRepository.observe`).
  - `toggle(crewId, mealId, reactorId, kind)` → `Result<ReactionToggle, ReactionError.Toggle>`:
    **exactly one** `withContext(dispatchers.io)`. Checks `mealExists` (→ `MealNotFound`), reads the
    member's doc, then **idempotent-by-intent**: same kind present ⇒ `remove` + `Removed`; else
    `put` + `Added`. Fault map: `PermissionDenied/Unauthenticated` ⇒ `Unauthorized`,
    `NotFound` ⇒ `MealNotFound`, `Unavailable` ⇒ `Offline`, else `Unavailable`.

### Koin (`:feature:meal/di/MealModule.kt`)
- `singleOf(::ReactionFirestoreDataSource)`, `single<ReactionFirestore> { get<…DataSource>() }`,
  and `single<MealReactionPort> { FirebaseReactionRepository(firestore = get(), clock = get(),
  dispatchers = get()) }`. No `MealModuleVerifyTest` change needed (all deps — `Clock`,
  `DispatcherProvider` — already in the meal graph; the port + seam are bound *inside* the module).

### Feed verify test (`:feature:feed/.../di/FeedModuleVerifyTest.kt`)
- Added `MealReactionPort::class` to `extraTypes` (feed consumes it; bound by meal in `shared`,
  same treatment as the other meal ports already listed).

### Security rules (`firestore.rules`)
- New `match /reactions/{reactorUid}` block under `crews/{crewId}/meals/{mealId}`:
  - **read**: any crew member (`uid in crews/{crewId}.memberIds`).
  - **create, update**: `reactorUid == auth.uid` AND crew member AND
    `resource.data.reactorId == auth.uid` AND `kind` is a 1–40 char string AND `reactedAtEpochMs`
    within ±60s of `request.time`. (`update` is allowed because a different-kind set overwrites the
    same doc; with the parked single kind it's effectively just create+delete.)
  - **delete**: own doc only (`reactorUid == auth.uid`) — the toggle-off path.
  - Validated: `firebase-tools deploy --only firestore:rules --dry-run` →
    `cloud.firestore: rules file firestore.rules compiled successfully`.

### Tests (`:feature:meal/commonTest/`)
- **`ReactionMapperTest.kt`** (NEW, 5) — happy path; skip unknown kind; skip missing kind; skip
  blank reactor id; default missing timestamp to epoch 0.
- **`FirebaseReactionRepositoryTest.kt`** (NEW, 6) — uses a behavioral `FakeReactionFirestore`
  (in-memory `uid→dto` map + `mealPresent` flag + injectable throwable):
  toggle add→remove round-trip; observe aggregate count + viewer reaction; observe skips an
  unknown-kind doc; toggle on missing meal ⇒ `MealNotFound` (no write); permission-denied fault ⇒
  `Toggle.Unauthorized`; observe failure ⇒ `Read.Unavailable`.

## Verify

```
$ ./gradlew :feature:feed:testAndroidHostTest
> Task :feature:feed:testAndroidHostTest
BUILD SUCCESSFUL in 6s
91 actionable tasks: 9 executed, 82 up-to-date
```

Also (where the new code lives):
```
$ ./gradlew :feature:meal:testAndroidHostTest
> Task :feature:meal:testAndroidHostTest
BUILD SUCCESSFUL in 4s
```

Per-class result XML: `ReactionMapperTest` tests=5 failures=0 errors=0;
`FirebaseReactionRepositoryTest` tests=6 failures=0 errors=0;
`FeedModuleVerifyTest` tests=1 failures=0 errors=0 (with the new `MealReactionPort::class`).
`firestore.rules` dry-run compiled successfully.

## Decisions

- **Impl in `:feature:meal`, not `:feature:feed`** — see "Placement decision" above. Feed stays
  Firebase-free; matches the comment/rating/read-port precedent exactly. Functionally identical
  for presentation.
- **No new `ReactionError` leaf** — the domain handoff's six leaves
  (`Read.{Unauthorized,Unavailable}`, `Toggle.{Unauthorized,MealNotFound,Offline,Unavailable}`)
  covered every failure mode, so no StringKey/mapper/exhaustiveness-test addition was required.
- **`reactorId` taken from the port arg, not ambient auth** — the port carries it explicitly (per
  the domain contract); the doc id == `reactorId.value`. The rule still pins `reactorUid == auth.uid`
  server-side, so a client can't write someone else's reaction.
- **`update` allowed in the rule** (not just create/delete) — a re-react of a *different* kind
  overwrites the same `reactions/{uid}` doc via `.set`, which Firestore treats as an update.
  Harmless for the parked single-kind set; future-proofs a multi-kind set.
- **Testable seam** (`ReactionFirestore` interface) mirrors `MealFirestore`, enabling the
  commonTest port test without a Firebase emulator.

## Blockers / pending (user)

- **Deploy `firestore.rules`** before reactions work against prod (the `reactions/{uid}` writes are
  denied until then):
  `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`
  (Reads are unaffected for existing data; there is none yet.)

## Suggested next

`w1-reactions-presentation` — consume `MealReactionPort` in `FeedViewModel`/`FeedMealUi`, render
the react affordance + who-reacted row on `FrFeedMealCard` (glyph = `DailyEmote.forDay(meal.day)`),
and fire the `meal_reacted` analytics leaf after `toggle` returns `Ok(Added)`. See the handoff.
