# Report — w1-blind-voting-presentation

**Status:** DONE, verified green. Terminal task of blind voting.

## What

The FEED side of blind voting: when the active crew has `blindVoting` ON, a meal's author
identity (name + avatar) is hidden in the feed list until the viewer has cast their own score on
that meal — then revealed. Author always sees their own meal un-masked; voters' identities in the
votes list are unaffected. The masking decision reuses the pure domain
`BlindVotingPolicy.shouldMaskAuthor`; the port `CrewBlindVotingPort` carries the flag from the
crew read model into feed.

No prior/interrupted work existed for this task — implemented fresh.

## How (design decisions)

- **Flag plumbing (MVI, single source of truth).** `CrewBlindVotingPort` injected into
  `FeedViewModel` (it's a `:core:domain` port — NO `:feature:crew` Gradle dep; same pattern as
  `CrewOwnerPort`). A `blindVotingFlow` is derived from `activeCrew.current`
  (`distinctUntilChanged().flatMapLatest { observeBlindVoting(it) }`, `null` crew → `false`) and
  **combined** with the existing feed result flow so meals re-map whenever the feed OR the flag
  changes. The flag is also pushed into `FeedState.blindVoting`. NO parallel `MutableStateFlow`,
  NO `withContext` in the VM (per CHARTER rule 8 / 4).
- **Masking in the presentation layer.** `toFeedUi(viewerId, today, ingredientNames, blindVoting)`
  computes `authorMasked = BlindVotingPolicy.shouldMaskAuthor(blindVoting, isAuthor =
  meal.author.accountId == viewerId, viewerHasVoted = viewer != null || !windowOpen)`. The pure
  policy takes only its three inputs; **reveal-after-window-close** is gated at the call site by
  treating a closed rating window (`!windowOpen`, i.e. `daysSince(meal.day) !in 0..1`) as "voted".
  The result is a single `authorMasked: Boolean` on `FeedMealUi` — the model carries the boolean,
  not a resolved string (i18n stays in the composable, mirroring how `slot` is a presentation enum).
- **Card rendering.** `FrFeedMealRow` (feature-owned domain-aware component) renders, when
  `ui.authorMasked`: the i18n placeholder label `FeedStringKey.BlindAuthor`
  ("Hidden until you rate" / "Oculto hasta que puntúes") instead of the real name, and a generic
  `FrAvatar` (blank initials + null imageUrl → neutral primaryContainer circle). **No new
  `:core:designsystem` atom** was needed — `FrAvatar` already supports a generic placeholder — so
  no catalog entry. (CHARTER rule 7: feature-owned `Fr*` are NOT cataloged; the catalog depends
  only on `:core:designsystem`. No other feature `Fr*` — FrMealCard/FrFeedMealCard/FrCrewMemberRow
  — is cataloged either. The "blind scenario" the handoff asked for is realized as a Robolectric
  Compose UI test instead, the right surface for a feature-owned component.)
- **Koin.** `feedModule`'s already-explicit `viewModel { FeedViewModel(...) }` gained one more
  `get()` for the port (explicit `viewModel{}` was already in use because of analytics).
  `CrewBlindVotingPort::class` added to `FeedModuleVerifyTest.extraTypes` (provided by `crewModule`,
  external to feed's graph — exactly like `CrewOwnerPort::class`). Verified bound:
  `feature/crew/.../di/CrewModule.kt:59 single<CrewBlindVotingPort> { ... }`.

## Files changed

- `feature/feed/src/commonMain/kotlin/es/schsebastian/foodrats/feature/feed/presentation/components/FeedMealUi.kt`
  — `authorMasked` field + `blindVoting` param + policy call (with window-close gating).
- `feature/feed/src/commonMain/kotlin/es/schsebastian/foodrats/feature/feed/presentation/components/FrFeedMealRow.kt`
  — masked author label + generic avatar.
- `feature/feed/src/commonMain/kotlin/es/schsebastian/foodrats/feature/feed/presentation/feed/FeedViewModel.kt`
  — inject `CrewBlindVotingPort`, derive `blindVotingFlow`, combine into the feed collect.
- `feature/feed/src/commonMain/kotlin/es/schsebastian/foodrats/feature/feed/presentation/feed/FeedContract.kt`
  — `FeedState.blindVoting`.
- `feature/feed/src/commonMain/kotlin/es/schsebastian/foodrats/feature/feed/di/FeedModule.kt`
  — 9th `get()` for the port.
- `feature/feed/src/commonMain/kotlin/es/schsebastian/foodrats/feature/feed/i18n/FeedStringKey.kt`
  — `BlindAuthor` entry.
- `feature/feed/src/commonMain/composeResources/values/strings.xml` + `values-es/strings.xml`
  — `feed_blind_author`.
- `feature/feed/src/androidHostTest/.../di/FeedModuleVerifyTest.kt`
  — `CrewBlindVotingPort::class` in `extraTypes`.
- `feature/feed/src/commonTest/.../presentation/components/FeedMealUiTest.kt`
  — 5 mapper tests (off / on-masked / after-vote / own-meal / window-closed).
- `feature/feed/src/commonTest/.../presentation/feed/FeedViewModelTest.kt`
  — `FakeCrewBlindVotingPort` + `buildVm` wiring + 3 VM state tests (off / on-masked / own-meal).
- `feature/feed/src/androidHostTest/.../presentation/components/FrFeedMealRowTest.kt`
  — masked-rendering UI test (placeholder shown, real name absent).

## Tests proving the matrix

Mapper (`FeedMealUiTest`): blindVoting OFF → not masked; ON + not voted + not author → masked;
ON + voted → revealed; ON + own meal → revealed; ON + window closed → revealed.
VM (`FeedViewModelTest`): OFF → `state.blindVoting=false` & meal not masked; ON → `state.blindVoting=true`
& meal masked; ON + viewer-is-author → not masked. UI (`FrFeedMealRowTest`): masked shows the
placeholder and hides the real name.

## Verify

```
./gradlew :feature:feed:testAndroidHostTest
```
Last 3 lines:
```
> Task :feature:feed:testAndroidHostTest

BUILD SUCCESSFUL in 8s
```
Per-class (from test-results XML): `FeedMealUiTest` tests=12 failures=0 errors=0;
`FeedViewModelTest` tests=10 failures=0 errors=0; `FrFeedMealRowTest` tests=4 failures=0 errors=0;
`FeedModuleVerifyTest` tests=1 failures=0 errors=0. Module total: tests=61 failures=0 errors=0.
(No `:core:designsystem` atom was added, so its host-test verify was not required.)

## Scope notes / non-blockers

- **Meal detail screen is NOT masked.** `MealDetailViewModel` also calls `toFeedUi` but uses the
  default `blindVoting = false`, so the detail screen always shows the author. This matches the
  task brief ("the FEED side") and the roadmap §1.2 ("`FeedMealUi` mapping"); detail is where you
  actually rate. If product later wants detail masked too, thread the same port into
  `MealDetailViewModel` — the port + policy + `toFeedUi` param already support it.

## Carry-forward (user must run — restated from w1-blind-voting-data)

The owner toggle write needs the new `firestore.rules` branch. Deploy before the toggle works
against prod (the READ path `observeBlindVoting` is already allowed for any authenticated member):
```
pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec
```

## Blockers

None.
