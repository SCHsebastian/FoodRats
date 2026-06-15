# Handoff — `w2-badges-data`

For `w2-badges-presentation` (and cuisine-passport / ingredient-bingo, which reuse the same persistence).
Data/infra layer is DONE and green. Spec §6/§16 followed.

## The port the ViewModel observes (`:core:domain`)

`es.schsebastian.foodrats.core.domain.achievement.AchievementProgressPort`:

```kotlin
interface AchievementProgressPort {
    // raw String achievement id → unlock epoch-ms. Map String→AchievementId against the catalog;
    // drop unknown ids (future-app forward-compat).
    fun observeUnlocks(accountId: AccountId): Flow<Result<Map<String, Long>, AchievementProgressError>>

    // idempotent batch; empty map = no-op returning Ok. Pass ONLY the ids you consider newly unlocked.
    suspend fun recordUnlocks(accountId: AccountId, newlyUnlocked: Map<String, Long>): Result<Unit, AchievementProgressError>
}

sealed interface AchievementProgressError { data object Unauthorized; data object Unavailable }
```

- Bound in `achievementsModule` as `single<AchievementProgressPort> { FirebaseAchievementRepository(...) }`.
- `observeUnlocks` is a cold Flow (no `withContext`); `recordUnlocks` carries the single `withContext(io)`.
- Map `AchievementProgressError` → `AchievementError.Read.*` in your use case (mirror stats'
  `MealReadError.toStatsError()`), as the domain handoff specified.

## Unlock-timestamp shape

- Firestore: `accounts/{uid}/achievements/{achievementId}` = `{ "unlockedAtEpochMs": <Long> }`. One
  doc per UNLOCKED achievement; **absence = locked**. Doc id IS `AchievementId.value` (not a body field).
- In code: `observeUnlocks` gives you `Map<rawId, unlockedAtEpochMs>`.

## "Newly unlocked" detection — already implemented, USE IT

Pure helper `feature/achievements/.../domain/AchievementReconciler.kt` (bound in `achievementsModule`):

```kotlin
class AchievementReconciler {
    data class Reconciled(val statuses: List<AchievementStatus>, val newlyUnlocked: Map<String, Long>)
    fun reconcile(evaluated: List<AchievementStatus>, persisted: Map<String, Long>, now: Long): Reconciled
}
```

For each evaluated status:
- id in `persisted` → `unlockedAtEpochMs = persisted[id]` (renders **earned**), not collected.
- else `progress.isMet` → collected into `newlyUnlocked[id] = now`, left `unlockedAtEpochMs = null`
  for this frame (locked-with-full-progress, no flicker — flips to earned on the next snapshot).
- else → stays locked.

**ViewModel pipeline (spec §7):**
```
combine(ActiveCrewProvider.current, SessionProvider.current)
  → flatMapLatest { combine(MealReadPort.observeRange(crew, today-365, today),
                            progress.observeUnlocks(accountId)) }
  → debounce(400ms)
  → build AchievementSignals (feature-local AchievementSignalsBuilder)
  → AchievementEvaluator.evaluate(catalog, signals)
  → AchievementReconciler.reconcile(evaluated, persisted, now = clock.now().toEpochMilliseconds())
  → State.statuses = reconciled.statuses
  → if reconciled.newlyUnlocked.isNotEmpty(): recordUnlocks(accountId, it);
      on Ok → emit Unlocked effect + track AnalyticsEvent.AchievementUnlocked (AFTER Ok, never before)
      on Err → leave badges met-but-locked, fire no analytics (idempotent: next snapshot retries)
```
The `recordUnlocks` idempotency + the "earned flip on next snapshot" are proven in
`FirebaseAchievementRepositoryTest`; the partition logic in `AchievementReconcilerTest`.

## Koin: what's bound vs. what YOU add

Already bound in `achievementsModule`: `AchievementEvaluator`, `AchievementReconciler`,
`AchievementErrorMapper`, `AchievementUnlockStore`, `AchievementProgressPort`. The module is already
in `shared` `appModules` and `shared/build.gradle.kts` depends on `:feature:achievements`.

YOU add to `achievementsModule`: `factoryOf(::ObserveAchievementsUseCase)` + the explicit
`viewModel { AchievementsViewModel(observeAchievements = get(), progress = get(), reconciler = get(),
clock = get(), analytics = get()) }` (explicit, NOT `viewModelOf`, so the `AnalyticsPort` default
isn't short-circuited). Then extend `AchievementsModuleVerifyTest.extraTypes` with `MealReadPort`,
`ActiveCrewProvider`, `SessionProvider`, `Clock`, `AnalyticsPort`.

## Rules/deploy step the USER must run (manual, not codeable)

```
pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec
```
The `accounts/{uid}/achievements/{id}` rule (owner-only read+write) is in `firestore.rules` and
emulator-tested (`firestore-tests/tests/achievements.test.ts`, 5/5). **Until deployed,
`observeUnlocks`/`recordUnlocks` get PERMISSION_DENIED** → the feature shows the read error and
persists nothing. (Maps to `AchievementProgressError.Unauthorized` via the error mapper.)
