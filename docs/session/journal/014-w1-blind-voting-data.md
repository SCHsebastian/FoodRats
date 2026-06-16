# 014 · w1-blind-voting-data

**Status:** done

**Summary (≤6 lines):**
- Persists `Crew.blindVoting` in Firestore, binds `CrewBlindVotingPort`, owner-only `FrSwitch` toggle in CrewSettings (default OFF) via new `SetBlindVotingUseCase`.
- Files: `feature/crew/.../data/firebase/{CrewDto,CrewMapper,CrewDataSource,CrewFirestoreDataSource}.kt`, `.../data/repository/FirebaseCrewRepository.kt`, `.../domain/repository/CrewRepository.kt`, `.../domain/usecase/SetBlindVotingUseCase.kt` (new), `.../di/CrewModule.kt`, `.../presentation/settings/{CrewSettingsContract,CrewSettingsViewModel,CrewSettingsScreen}.kt`, `.../i18n/CrewStringKey.kt`, `composeResources/values{,-es}/strings.xml`, `firestore.rules`; tests `CrewMapperTest`, `CrewSettingsViewModelTest`, fakes.
- Decisions: IO boundary stays in datasource (repo does owner check, one `withContext` per data method); reused `Authorization.NotOwner`/`Backend.*` (no new error leaf); switch reflects `state.crew.blindVoting`.
- Blockers: none. MANUAL: deploy `firestore:rules` before the toggle works in prod.

**Verify (quoted):**
```
> Task :feature:crew:testAndroidHostTest
BUILD SUCCESSFUL in 5s
(VM 13/13, mapper 6/6, 0 failures)
```

**Presentation handoff:** consume port + apply `BlindVotingPolicy` in `FeedMealUi.toFeedUi`, `viewerHasVoted = viewerRating != null`.

Report: `docs/session/reports/w1-blind-voting-data.md` · Handoff: `docs/session/handoffs/w1-blind-voting-data.md`
