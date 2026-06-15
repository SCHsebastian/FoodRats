# w3-deep-linked-invites-presentation — Deep-linked crew invites + QR + accept flow

**Status:** DONE · all verify targets green · terminal Wave 3 task.

Upgrades crew invites from a bare 6-char code to a shareable deep **link** + a **QR code**, with a
smooth in-app accept flow (tap link / scan QR → land on a join preview → join the crew).

No prior/interrupted work existed on disk for this task (the dirty tree was the earlier Wave 3
share-card tasks); this was implemented fresh.

## What was built

### 1. Invite deep-link route + parser (`:shared`)
- `DeepLinks.SEGMENT_INVITE = "invite"` + `DeepLinks.inviteUrl(code)` — the single source of truth
  for the canonical URL `https://foodrats.app/invite/{code}` (Universal/App-Links host + the
  `foodrats://app/invite/{code}` custom-scheme fallback). Mirrors the existing meal/crew/digest
  contract: discriminator is the **first path segment**, parser stays pure Kotlin.
- `parseDeepLink` gained an `invite` arm → `Route.InvitePreview(code)`.
- New typed route `Route.InvitePreview(val code: String) : Route.Protected` + `requiresSession()`
  arm. **Classified `Protected` deliberately** (see Decisions) so a pre-auth invite tap is stashed
  in `RootNavState.pendingDeepLink` and replayed after sign-in by the EXISTING intercept-then-resume
  in `RootNavViewModel` — the invite survives the sign-in gate with zero new nav code.
- `NavGraph` wires `composable<Route.InvitePreview>` → `AcceptInviteScreen`, and passes
  `inviteUrlFor = { DeepLinks.inviteUrl(it) }` down to `CrewSettingsScreen` (keeps the URL builder
  in `:shared`, so `:feature:crew` takes no dependency on `:shared`).
- **No manifest change needed** — the existing intent-filters are host/scheme-based and path-agnostic.

### 2. QR generation (`:core:designsystem`) — pure-Kotlin, zero dependency
- `atoms/qr/QrCode.kt` + `QrEncoder.kt`: a self-contained ISO/IEC 18004 byte-mode QR encoder
  (versions 1–40, Reed-Solomon ECC over GF(256), all-8-mask penalty selection, format/version info).
  Returns a boolean `matrix`.
- `atoms/FrQrCode.kt`: renders the matrix on a Compose **Canvas** (no Android-only View → renders
  identically on Android + iOS) with a spec-mandated quiet zone. Catalog entry `atom.qrcode` added
  to `AtomStories.kt`.
- **Justification for hand-rolling vs a lib:** the only KMP-commonMain QR options are unmaintained or
  Android-only; a single small use (one invite link) doesn't warrant a third-party dep, and a Canvas
  renderer keeps it truly multiplatform. ~330 LOC, fully unit-tested.

### 3. Accept flow (`:feature:crew`)
- `ResolveCrewByCodeUseCase` — read-only preview resolve by code (validates code shape first, then
  `CrewRepository.findByCode`). New `findByCode` on `CrewRepository` → `FirebaseCrewRepository` →
  `CrewDataSource.fetchByCode` (Firestore: `crewCodes/{code}` → crew id → `crews/{crewId}`; firestore
  rules already permit any authed user to read both).
- `AcceptInvite{Contract,ViewModel,Screen}` — MVI single source of truth, explicit `viewModel { (code) -> … analytics = get() }`
  in `crewModule`, no `withContext` in the VM. Resolves the preview on init (crew name + member count
  via the read port), Join runs the EXISTING `JoinCrewByCodeUseCase`, switches active crew, emits
  `Joined`. All failure modes (`Invite.CodeUnknown`, `Membership.Full`, `Membership.AlreadyMember`,
  `Validation.CodeMalformed`) are already in the typed `CrewError` tree + its `toStringKey` mapper —
  **no new error leaf required**, so the existing `CrewErrorToStringKeyTest` still locks exhaustiveness.

### 4. Share + QR in CrewSettings
- The crew hero card now shows the tap-to-copy code chip + **"Share invite link"** (system share
  sheet via the existing `ShareController.shareText`, with an i18n message template carrying crew name
  + URL) + **"Show QR code"** (a dialog rendering `FrQrCode` of the invite URL). Replaces the old
  bare-code Share button.

### 5. Analytics (consent-gated, no PII)
- Reuses the existing `AnalyticsEvent.CrewInviteShared` (`share`, `content_type=crew_invite`) — already
  present; the share button is text/URL so no change needed there.
- Added `JoinMethod.INVITE_LINK` dimension; `AcceptInviteViewModel` fires
  `CrewJoined(crewId, INVITE_LINK)` AFTER the join `Result` is `Ok` (crew id only, no PII). The
  picker's code-join keeps `INVITE_CODE`.

### 6. i18n
- 11 new keys (en + es), including the share-message template and QR caption. `CrewStringKey` enum
  extended.

## Files changed

**shared/**
- `src/commonMain/.../app/navigation/DeepLink.kt` — invite segment + `inviteUrl()` builder + parser arm
- `src/commonMain/.../app/navigation/Route.kt` — `Route.InvitePreview` + `requiresSession()` arm
- `src/commonMain/.../app/navigation/NavGraph.kt` — `InvitePreview` composable + `inviteUrlFor` wiring
- `src/commonTest/.../app/navigation/DeepLinkParserTest.kt` — 4 new invite cases (incl. URL round-trip)

**core/domain/**
- `src/commonMain/.../analytics/AnalyticsDimensions.kt` — `JoinMethod.INVITE_LINK`

**core/designsystem/**
- `src/commonMain/.../atoms/qr/QrCode.kt`, `qr/QrEncoder.kt` — pure-Kotlin QR encoder (new)
- `src/commonMain/.../atoms/FrQrCode.kt` — Canvas QR atom (new)
- `src/androidHostTest/.../atoms/qr/QrCodeTest.kt` — 8 structural encoder tests (new)

**catalogApp/**
- `src/main/.../stories/AtomStories.kt` — `atom.qrcode` catalog entry + story

**feature/crew/**
- `domain/repository/CrewRepository.kt` — `findByCode`
- `domain/usecase/ResolveCrewByCodeUseCase.kt` — new
- `data/firebase/CrewDataSource.kt`, `CrewFirestoreDataSource.kt` — `fetchByCode`
- `data/repository/FirebaseCrewRepository.kt` — `findByCode` impl
- `presentation/invite/AcceptInvite{Contract,ViewModel,Screen}.kt` — new
- `presentation/settings/CrewSettingsScreen.kt` — share-link + QR dialog, `inviteUrlFor` param
- `i18n/CrewStringKey.kt` — 11 new keys
- `composeResources/values{,-es}/strings.xml` — 11 new strings each
- `di/CrewModule.kt` — `ResolveCrewByCodeUseCase` + `AcceptInviteViewModel` bindings
- `androidHostTest/.../di/CrewModuleVerifyTest.kt` — `String::class` extraType
- `commonTest/.../domain/test/FakeCrewRepository.kt`, `data/firebase/FakeCrewDataSource.kt` — `findByCode`/`fetchByCode`
- `commonTest/.../presentation/invite/AcceptInviteViewModelTest.kt` (6), `domain/usecase/ResolveCrewByCodeUseCaseTest.kt` (3) — new

**docs/session/human.md** — new section D3 (assetlinks/AASA hosting, web OG unfurl, "no new dep" note).

## Verify (all green, quoted)

- `./gradlew :shared:testAndroidHostTest :feature:crew:testAndroidHostTest` →
  `BUILD SUCCESSFUL in 8s` (DeepLinkParserTest 16/16, AcceptInviteViewModelTest 6/6,
  ResolveCrewByCodeUseCaseTest 3/3; existing crew suite incl. CrewModuleVerifyTest + CrewErrorToStringKeyTest green)
- `./gradlew :core:domain:testAndroidHostTest :core:designsystem:testAndroidHostTest` →
  `BUILD SUCCESSFUL in 17s` (AnalyticsTaxonomyTest green with new dimension; QrCodeTest 8/8)
- `./gradlew :androidApp:assembleDebug :catalogApp:assembleDebug` → `BUILD SUCCESSFUL in 5s`
- `./gradlew :shared:compileKotlinIosSimulatorArm64` → `BUILD SUCCESSFUL in 8s` (only pre-existing
  deprecation warnings; QR Canvas atom + shared route compile for iOS)

Per-suite XML counts confirmed: AcceptInvite 6/0/0, ResolveCrewByCode 3/0/0, DeepLinkParser 16/0/0,
QrCode 8/0/0.

## Decisions (spec §3.2 silent → chose + documented)

1. **New `…/invite/{code}` route carrying the CODE, not the crewId.** The join path resolves a crew
   by code (`crewCodes/{code}` → crew), so a code-bearing link is self-sufficient (the brief's
   preferred shape). The existing `…/crew/{crewId}` → `CrewSettings` stays members-only.
2. **`Route.InvitePreview` is `Protected`.** Reading the crew preview and joining both require an
   authenticated Firestore session. Protected means a pre-auth tap stashes + resumes after sign-in via
   the existing intercept-then-resume — the cleanest survival of the sign-in gate, and the preview can
   read the crew because the user is authed by the time it renders. (A Public preview would need an
   unauthenticated read path that firestore.rules doesn't grant.)
3. **Hand-rolled pure-Kotlin QR encoder, no dependency.** No maintained commonMain QR lib exists; one
   small use doesn't justify a dep; Canvas rendering keeps it multiplatform.
4. **Rich preview = the in-app preview screen** (crew name + member count + Join), per the brief. The
   web/OG unfurl (server hosting) is flagged as a USER follow-up — not faked.
5. **Reused `CrewInviteShared` + added `JoinMethod.INVITE_LINK`** rather than new event leaves.

## Blockers

None. (The assetlinks/AASA hosting + web OG unfurl are documented USER steps, not blockers — the
custom-scheme fallback already makes the link functional.)

## Suggested next

Wave 3 is complete. Per CHARTER, when all roadmap tasks are done/blocked, run the post-build multi-agent
REVIEW PACK (2 iterations). Before any release: host the invite association files / web unfurl (human.md
D3) and walk the on-device invite smoke (share link from CrewSettings → open on a second device → land on
preview → Join; scan the QR → same).
