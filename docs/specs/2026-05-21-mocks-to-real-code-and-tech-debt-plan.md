# Mocks → Real Code + Tech-Debt Cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert every intentional stub / placeholder / mocked code path in FoodRats into real production code, and pay down all open tech-debt items documented in CLAUDE.md (cross-feature dep violations, member-cache drift, dead code, missing test coverage, vendored design-system icons, dev-crew legacy migration, iOS Share bridge).

**Architecture:** Phases are independently mergeable PRs. Order honors dependency direction — ports come before adapters; live-reads migration (Phase D) must land before the dead-code deletion it enables. Risky/large phases (B, C, D) get their own PRs; cosmetic/safe phases (A, E, G, H) are smaller follow-ups.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, GitLive Firebase KMP (Firestore + Storage + Auth + Messaging), Coil 3, ImagePickerKMP, kotlinx-coroutines, kotlin-test, Turbine, kotlinx-datetime, Konsist.

**Spec / inputs:**
- Survey output: see Mock findings + Tech debt in conversation (2026-05-21).
- `docs/specs/2026-05-21-your-profile-split-design.md` (drives Phase D's "drop denormalized cache" decision).
- `CLAUDE.md` "Active tech debt" section (lines 138–151) — single source of truth for what's left.

**Ship order (one PR per phase):**
1. **Phase A** — Bucket A safe fixes (empty crew display name + ProfileError EmptyBytes mapper). Small. First.
2. **Phase B** — Cross-feature ports for notifications. Architectural; isolated.
3. **Phase D** — Member-cache live-reads migration + dead-code cleanup. Largest. Touches multiple features.
4. **Phase C** — Real RemoveMember feature. Depends on D (members shape no longer carries `displayName` cache).
5. **Phase F** — iOS Share via `UIActivityViewController`.
6. **Phase G** — Broaden `CrewSettingsViewModelTest`.
7. **Phase H** — Vendor SVGs for FrIcons substitutes.
8. **Phase E** — Dev-crew legacy migration removal (date-gated; ship only after 2026-06-15 or your release-rollout date).

---

## File map

**Create:**
- `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/notifications/TokenRegistrationPort.kt` *(Phase B)*
- `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/notifications/StreakNotificationPort.kt` *(Phase B)*
- `feature/notifications/src/commonMain/kotlin/es/schsebastian/foodrats/feature/notifications/data/adapter/TokenRegistrationAdapter.kt` *(Phase B)*
- `feature/notifications/src/commonMain/kotlin/es/schsebastian/foodrats/feature/notifications/data/adapter/StreakNotificationAdapter.kt` *(Phase B)*
- `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/crew/CrewMemberWritePort.kt` *(Phase C)*
- `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/data/firebase/FirestoreCrewMemberWriter.kt` already exists — extend or add `removeMember` *(Phase C)*
- `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/account/AccountReadPort.kt` — extend with `observeMany(ids)` *(Phase D)*
- `iosApp/iosApp/ShareControllerBridge.swift` *(Phase F)*
- Test files mirroring each adapter / use case.

**Modify:**
- `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/presentation/picker/CrewPickerViewModel.kt:49,63` *(Phase A)*
- `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/presentation/profile/ProfileErrorToStringKey.kt` *(Phase A)*
- `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/i18n/AuthStringKey.kt` *(Phase A)*
- `feature/auth/src/commonMain/composeResources/values/strings.xml` + `values-es/strings.xml` *(Phase A)*
- `feature/auth/build.gradle.kts` — drop `implementation(projects.feature.notifications)` *(Phase B)*
- `feature/meal/build.gradle.kts` — drop `implementation(projects.feature.notifications)` *(Phase B)*
- `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/presentation/signin/SignInViewModel.kt` — swap `RegisterDeviceTokenUseCase` → `TokenRegistrationPort` *(Phase B)*
- `feature/meal/src/commonMain/kotlin/es/schsebastian/foodrats/feature/meal/presentation/publish/PublishMealViewModel.kt` — swap `ScheduleStreakNudgeUseCase` + `NotificationStringKey` → `StreakNotificationPort` *(Phase B)*
- `feature/auth/.../di/AuthModule.kt`, `feature/meal/.../di/MealModule.kt`, `feature/notifications/.../di/NotificationsModule.kt` — bind/unbind accordingly *(Phase B)*
- `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/domain/model/Member.kt` — drop `displayName` + `avatarUrl` *(Phase D)*
- `feature/crew/.../data/firebase/CrewMapper.kt`, `MemberDto.kt`, `CrewFirestoreDataSource.kt` — read-only legacy fields *(Phase D)*
- `feature/crew/.../presentation/components/FrCrewMemberRow.kt` — accept `AccountId` instead of `displayName/avatarUrl`, resolve via `AccountReadPort` *(Phase D)*
- `feature/auth/.../UpdateMyDisplayNameUseCase.kt` + `UpdateMyAvatarUseCase.kt` — drop cache-write step + telemetry *(Phase D)*
- `feature/crew/.../domain/usecase/RemoveMemberUseCase.kt` — real impl *(Phase C)*
- `feature/crew/.../domain/error/CrewError.kt` — remove `NotImplemented.RemoveMember`; add `Validation.CannotRemoveSelf` *(Phase C)*
- `feature/crew/.../presentation/CrewErrorToStringKey.kt` + tests *(Phase C)*
- `feature/auth/.../FirebaseAuthRepository.kt` — remove `LEGACY_DEV_CREW_ID` + `clearLegacyDevCrewIfPresent()` *(Phase E)*
- `firestore.rules` — owner-only removeMember rule *(Phase C)*
- `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/atoms/FrIcons.kt` *(Phase H)*
- `core/designsystem/src/commonMain/composeResources/drawable/` — vendor SVG → XML drawable *(Phase H)*
- `CLAUDE.md` — debt section updates after each phase

**Delete (Phase D's cleanup):**
- `feature/crew/.../data/firebase/CrewMemberCacheWriter*` (already in CrewMemberCacheWritePort impl)
- `core/domain/.../crew/CrewMemberCacheWritePort.kt`
- `feature/crew/.../data/firebase/CrewMemberWriter.kt` (the legacy one — `FirestoreCrewMemberWriter` stays as the typed port impl)
- `CrewFirestoreDataSource.renameMember`, `updateAccountAvatarUrl`, `setMemberDisplayName`, `updateMemberAvatarUrl`

---

## Phase A — Bucket A safe fixes (separate small PR after #3 merges)

### Task A1: Fix empty crew display name in `CrewPickerViewModel`

**Files:**
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/presentation/picker/CrewPickerViewModel.kt`
- Modify: `feature/crew/build.gradle.kts` — ensure `:core:domain` `AccountReadPort` is already on the dep graph (it is — verify)

- [ ] **Step 1: Verify `AccountReadPort.observe(accountId)` exists and returns `Flow<Account?>`**

Run: `grep -n "interface AccountReadPort\|fun observe" /Users/sebastiancardonahenao/AndroidStudioProjects/FoodRats/core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/account/AccountReadPort.kt`
Expected: an `observe(accountId: AccountId): Flow<Account?>` signature.

- [ ] **Step 2: Inject `AccountReadPort` into `CrewPickerViewModel`**

```kotlin
class CrewPickerViewModel(
    private val session: SessionProvider,
    private val observeMyCrews: ObserveMyCrewsUseCase,
    private val createCrew: CreateCrewUseCase,
    private val joinCrew: JoinCrewByCodeUseCase,
    private val switchActive: SwitchActiveCrewUseCase,
    private val accountRead: AccountReadPort,
) : MviViewModel<CrewPickerState, CrewPickerIntent, CrewPickerEffect>(CrewPickerState()) { ... }
```

- [ ] **Step 3: Resolve the current display name before calling create/join**

Replace the two `/* TODO displayName from profile */ ""` sites with a resolved value. Add a private helper:

```kotlin
private suspend fun myDisplayName(accountId: AccountId): String =
    accountRead.observe(accountId).first()?.displayName.orEmpty()
```

Then in `doCreate()` and `doJoin()`:

```kotlin
when (val r = createCrew(state.createInput, account, myDisplayName(account))) { ... }
when (val r = joinCrew(state.joinInput, account, myDisplayName(account))) { ... }
```

- [ ] **Step 4: Update `CrewModule.kt` to pass the port**

In `crewModule`:

```kotlin
viewModelOf(::CrewPickerViewModel)  // ← Koin resolves the new AccountReadPort param automatically
```

- [ ] **Step 5: Update `CrewPickerViewModelTest` to inject a fake `AccountReadPort`**

Add a minimal fake (mirror existing testdoubles) and pass `account.displayName = "Me"` so the existing tests continue to assert against `"Me"` (or assert empty if the fake returns null).

- [ ] **Step 6: Build + run test**

Run: `./gradlew :feature:crew:testAndroidHostTest --tests "*CrewPickerViewModelTest*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/presentation/picker/CrewPickerViewModel.kt
git add feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/di/CrewModule.kt
git add feature/crew/src/commonTest/kotlin/es/schsebastian/foodrats/feature/crew/presentation/picker/CrewPickerViewModelTest.kt
git commit -m "$(cat <<'EOF'
fix(feature:crew): resolve member displayName from AccountReadPort on create/join

Was passing the empty string with a TODO; first-time crew creators / joiners
now appear with their canonical account displayName.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task A2: `ProfileError.Validation.EmptyBytes` gets its own string key

**Files:**
- Modify: `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/i18n/AuthStringKey.kt`
- Modify: `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/presentation/profile/ProfileErrorToStringKey.kt`
- Modify: `feature/auth/src/commonMain/composeResources/values/strings.xml` + `values-es/strings.xml`
- Modify: `feature/auth/src/commonTest/.../ProfileErrorToStringKeyTest.kt`

- [ ] **Step 1: Add the string entries**

`values/strings.xml`:
```xml
<string name="auth_profile_avatar_empty_bytes">Couldn\'t read the selected image. Try another one.</string>
```
`values-es/strings.xml`:
```xml
<string name="auth_profile_avatar_empty_bytes">No se pudo leer la imagen seleccionada. Prueba con otra.</string>
```

- [ ] **Step 2: Add `AuthStringKey.ProfileAvatarEmptyBytes`**

```kotlin
data object ProfileAvatarEmptyBytes : AuthStringKey { override val resourceId get() = Res.string.auth_profile_avatar_empty_bytes }
```
(import the resource matching the file pattern.)

- [ ] **Step 3: Update the mapper**

```kotlin
ProfileError.Validation.EmptyBytes -> AuthStringKey.ProfileAvatarEmptyBytes
```

- [ ] **Step 4: Update the test**

```kotlin
@Test fun empty_bytes_maps_to_dedicated_key() =
    assertEquals(AuthStringKey.ProfileAvatarEmptyBytes, ProfileError.Validation.EmptyBytes.toStringKey())
```

- [ ] **Step 5: Build + test**

Run: `./gradlew :feature:auth:testAndroidHostTest --tests "*ProfileErrorToStringKey*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/i18n/AuthStringKey.kt
git add feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/presentation/profile/ProfileErrorToStringKey.kt
git add feature/auth/src/commonMain/composeResources/values/strings.xml feature/auth/src/commonMain/composeResources/values-es/strings.xml
git add feature/auth/src/commonTest/kotlin/es/schsebastian/foodrats/feature/auth/presentation/profile/ProfileErrorToStringKeyTest.kt
git commit -m "$(cat <<'EOF'
fix(feature:auth): ProfileError.EmptyBytes gets dedicated string key

Was falling back to the generic BackendUnavailable copy.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase B — Cross-feature ports for notifications

Currently, `:feature:auth` and `:feature:meal` both import from `:feature:notifications`. We declare two ports in `:core:domain`; the consumers depend on the port; `:feature:notifications` implements them and binds the implementation in its Koin module.

### Task B1: `TokenRegistrationPort` in `:core:domain`

**Files:**
- Create: `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/notifications/TokenRegistrationPort.kt`

- [ ] **Step 1: Create the port**

```kotlin
package es.schsebastian.foodrats.core.domain.notifications

import es.schsebastian.foodrats.core.domain.result.Result

interface TokenRegistrationPort {
    /** Idempotent: safe to call after every successful sign-in. */
    suspend fun registerCurrentDeviceToken(): Result<Unit, TokenRegistrationError>
}

sealed interface TokenRegistrationError {
    data object NoToken : TokenRegistrationError
    data object NotSignedIn : TokenRegistrationError
    data object Unavailable : TokenRegistrationError
}
```

- [ ] **Step 2: Build + Konsist (port lives in domain — Konsist rule must remain green)**

Run: `./gradlew :core:domain:compileKotlinMetadata :core:domain:testAndroidHostTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/notifications/TokenRegistrationPort.kt
git commit -m "$(cat <<'EOF'
feat(core:domain): add TokenRegistrationPort

Breaks the :feature:auth → :feature:notifications direct dep.
Adapter implementation lands in :feature:notifications.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task B2: `StreakNotificationPort` in `:core:domain`

**Files:**
- Create: `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/notifications/StreakNotificationPort.kt`

- [ ] **Step 1: Create the port**

```kotlin
package es.schsebastian.foodrats.core.domain.notifications

import es.schsebastian.foodrats.core.domain.result.Result

interface StreakNotificationPort {
    /** Schedules a local "your streak is at risk" nudge for the next delivery window. */
    suspend fun scheduleStreakNudge(): Result<Unit, StreakNotificationError>
}

sealed interface StreakNotificationError {
    data object Unavailable : StreakNotificationError
}
```

NOTE: title/body are resolved inside the adapter (which has access to `:core:i18n`), NOT here — keeps domain free of presentation strings. The adapter owns the resource lookup that was previously inline in `PublishMealViewModel`.

- [ ] **Step 2: Build**

Run: `./gradlew :core:domain:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/notifications/StreakNotificationPort.kt
git commit -m "$(cat <<'EOF'
feat(core:domain): add StreakNotificationPort

Breaks the :feature:meal → :feature:notifications direct dep.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task B3: Adapter implementations in `:feature:notifications`

**Files:**
- Create: `feature/notifications/src/commonMain/kotlin/es/schsebastian/foodrats/feature/notifications/data/adapter/TokenRegistrationAdapter.kt`
- Create: `feature/notifications/src/commonMain/kotlin/es/schsebastian/foodrats/feature/notifications/data/adapter/StreakNotificationAdapter.kt`
- Test: matching tests under `commonTest/.../data/adapter/`

- [ ] **Step 1: Implement `TokenRegistrationAdapter`**

```kotlin
package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationError
import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RegisterDeviceTokenUseCase

class TokenRegistrationAdapter(
    private val useCase: RegisterDeviceTokenUseCase,
) : TokenRegistrationPort {
    override suspend fun registerCurrentDeviceToken(): Result<Unit, TokenRegistrationError> =
        when (val r = useCase()) {
            is Result.Ok  -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toPortError())
        }

    private fun NotificationError.Token.toPortError(): TokenRegistrationError = when (this) {
        NotificationError.Token.Unavailable -> TokenRegistrationError.Unavailable
        // map other branches as needed — verify the real error tree shape
    }
}
```

- [ ] **Step 2: Implement `StreakNotificationAdapter`**

This adapter owns the i18n resource lookup that used to live in `PublishMealViewModel`:

```kotlin
package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationError
import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleStreakNudgeUseCase
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import org.jetbrains.compose.resources.getString

@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
class StreakNotificationAdapter(
    private val useCase: ScheduleStreakNudgeUseCase,
) : StreakNotificationPort {
    override suspend fun scheduleStreakNudge(): Result<Unit, StreakNotificationError> {
        return try {
            val title = getString(NotificationStringKey.StreakTitle.resourceId)
            val body  = getString(NotificationStringKey.StreakBody.resourceId)
            when (val r = useCase(title, body)) {
                is Result.Ok  -> Result.success(Unit)
                is Result.Err -> Result.failure(StreakNotificationError.Unavailable)
            }
        } catch (t: Throwable) {
            // Compose Resources unavailable (e.g., tests). Fail silently — same as previous behavior.
            Result.failure(StreakNotificationError.Unavailable)
        }
    }
}
```

- [ ] **Step 3: Write adapter tests**

For each adapter, a test that the OK path returns `Result.Ok` and the Err path maps correctly. Use existing `FakeFcmTokenProvider` + `FakeDeviceTokenRepository` patterns from `RegisterDeviceTokenUseCaseTest`.

- [ ] **Step 4: Bind in `notificationsModule`**

In `feature/notifications/.../di/NotificationsModule.kt`:

```kotlin
single<TokenRegistrationPort> { TokenRegistrationAdapter(get()) }
single<StreakNotificationPort> { StreakNotificationAdapter(get()) }
```

- [ ] **Step 5: Build + test**

Run: `./gradlew :feature:notifications:testAndroidHostTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/notifications/
git commit -m "$(cat <<'EOF'
feat(feature:notifications): adapters for TokenRegistrationPort + StreakNotificationPort

The adapters wrap the existing in-feature use cases and own the i18n lookup
that previously lived in PublishMealViewModel.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task B4: Switch `SignInViewModel` to the port

**Files:**
- Modify: `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/presentation/signin/SignInViewModel.kt`
- Modify: `feature/auth/build.gradle.kts` — drop `implementation(projects.feature.notifications)` from `commonMain.dependencies`
- Modify: `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/di/AuthModule.kt`
- Modify: `feature/auth/src/commonTest/kotlin/es/schsebastian/foodrats/feature/auth/presentation/signin/SignInViewModelTest.kt`

- [ ] **Step 1: Swap the import + constructor parameter**

```kotlin
// remove:
// import es.schsebastian.foodrats.feature.notifications.domain.usecase.RegisterDeviceTokenUseCase

import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationPort

class SignInViewModel(
    private val auth: AuthRepository,
    private val tokenRegistration: TokenRegistrationPort,  // was RegisterDeviceTokenUseCase
) : ...
```

Replace the call site `registerDeviceToken()` → `tokenRegistration.registerCurrentDeviceToken()`.

- [ ] **Step 2: Drop the Gradle dep**

In `feature/auth/build.gradle.kts`, find `implementation(projects.feature.notifications)` in `commonMain.dependencies` and delete the line.

- [ ] **Step 3: Update `authModule`**

The VM constructor signature changed — Koin's `viewModelOf` reflection should resolve `TokenRegistrationPort` automatically (it's a `single` in `notificationsModule`).

- [ ] **Step 4: Update `SignInViewModelTest`**

Replace `noopRegisterDeviceToken()` helper with a fake port:

```kotlin
private object NoopTokenRegistrationPort : TokenRegistrationPort {
    override suspend fun registerCurrentDeviceToken() = Result.success(Unit)
}
```

Wire it into every VM construction site.

- [ ] **Step 5: Build + test**

Run: `./gradlew :feature:auth:testAndroidHostTest`
Expected: PASS. Compilation should fail loudly if any reference to `RegisterDeviceTokenUseCase` is left over.

- [ ] **Step 6: Commit**

```bash
git add feature/auth/
git commit -m "$(cat <<'EOF'
refactor(feature:auth): consume TokenRegistrationPort instead of importing :feature:notifications

Removes the cross-feature dep violation. SignIn no longer knows about FCM
internals — just asks the port to register the current device.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task B5: Switch `PublishMealViewModel` to the port

**Files:**
- Modify: `feature/meal/src/commonMain/kotlin/es/schsebastian/foodrats/feature/meal/presentation/publish/PublishMealViewModel.kt`
- Modify: `feature/meal/build.gradle.kts` — drop `implementation(projects.feature.notifications)`
- Modify: `feature/meal/src/commonMain/kotlin/es/schsebastian/foodrats/feature/meal/di/MealModule.kt`
- Modify: `feature/meal/src/commonTest/kotlin/es/schsebastian/foodrats/feature/meal/presentation/publish/PublishMealViewModelTest.kt`

- [ ] **Step 1: Swap the constructor + body**

```kotlin
// remove:
// import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleStreakNudgeUseCase
// import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
// import org.jetbrains.compose.resources.getString

import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationPort

class PublishMealViewModel(
    private val observeDraft: ObserveMealDraftUseCase,
    private val publishMeal: PublishMealUseCase,
    private val streakNotifications: StreakNotificationPort,   // replaces ScheduleStreakNudgeUseCase
    private val clock: Clock,
    private val zone: TimeZone,
) : ... {
    // in handle(Publish):
    if (r is Result.Ok) {
        viewModelScope.launch { streakNotifications.scheduleStreakNudge() }
    }
}
```

Delete the inline `getString(...)` + try/catch — moved into the adapter.

Also delete `@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)` line if no other Compose Resources call remains in the file.

- [ ] **Step 2: Drop the Gradle dep + update DI + tests**

Same shape as Task B4. Test fake:

```kotlin
private object NoopStreakNotificationPort : StreakNotificationPort {
    override suspend fun scheduleStreakNudge() = Result.success(Unit)
}
```

- [ ] **Step 3: Build + test**

Run: `./gradlew :feature:meal:testAndroidHostTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add feature/meal/
git commit -m "$(cat <<'EOF'
refactor(feature:meal): consume StreakNotificationPort instead of importing :feature:notifications

Removes the second cross-feature dep violation. The i18n lookup for the
WorkManager title/body moves into the StreakNotificationAdapter, keeping it
out of presentation.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task B6: Update `CLAUDE.md` — strike the two cross-feature violations

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Edit lines 142 in the "Active tech debt" section**

Remove the bullet:
> **Cross-feature dep violation:** see Architecture rules above.

And the corresponding paragraph in the Architecture rules section that lists the two violations (paragraph near §3.1 of the bullets).

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(claude): cross-feature dep violations resolved via notifications ports

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase D — Member-cache live-reads migration + dead-code cleanup

This phase eliminates the denormalized member cache entirely. After this phase: `crews/{id}.members.{uid}` keeps only `{accountId, joinedAt}` (no displayName / no avatarUrl). All display name + avatar lookups go through `AccountReadPort.observe(accountId)`.

This unblocks dropping `CrewMemberCacheWritePort`, `FirestoreCrewMemberCacheWriter`, and the dead methods in `CrewFirestoreDataSource` + `CrewMemberWriter`.

### Task D1: Extend `AccountReadPort` with `observeMany`

**Files:**
- Modify: `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/account/AccountReadPort.kt`

- [ ] **Step 1: Add the method**

```kotlin
interface AccountReadPort {
    fun observe(accountId: AccountId): Flow<Account?>
    /** Resolves many ids at once into a map; missing accounts map to null. */
    fun observeMany(accountIds: Set<AccountId>): Flow<Map<AccountId, Account?>>
}
```

NOTE: implementing `observeMany` efficiently in Firestore means N parallel single-doc listeners then `combine`d — there's no batched read for "many docs by id". The naive implementation (combine N flows from `observe`) is fine for crew sizes ≤ 8.

- [ ] **Step 2: Default impl using `observe(id)` (so existing adapters compile)**

Add a default that combines per-id flows:

```kotlin
// in a new file: core/domain/.../account/AccountReadPortDefaults.kt
fun AccountReadPort.observeManyByDefault(ids: Set<AccountId>): Flow<Map<AccountId, Account?>> =
    if (ids.isEmpty()) flowOf(emptyMap())
    else combine(ids.map { id -> observe(id).map { id to it } }) { pairs -> pairs.toMap() }
```

Or just provide `observeMany` in the interface with `observe(id)` over each — implementation flexibility.

- [ ] **Step 3: Update the existing `FirestoreAccountReadDataSource` to implement `observeMany`**

Wire it as the default-impl call.

- [ ] **Step 4: Build + test**

Run: `./gradlew :core:domain:compileKotlinMetadata :feature:auth:testAndroidHostTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/domain/ feature/auth/
git commit -m "$(cat <<'EOF'
feat(core:domain): AccountReadPort.observeMany for crew members live-reads

Phase D of the member-cache live-reads migration. Implementation combines
per-id observers; fine for crew sizes ≤ 8.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task D2: Drop `displayName` + `avatarUrl` from the `Member` model

**Files:**
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/domain/model/Member.kt`
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/data/firebase/MemberDto.kt` — keep the field (don't break writers) but flag it as legacy
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/data/firebase/CrewMapper.kt` — drop the field passthrough into the domain `Member`

- [ ] **Step 1: Update `Member.kt`**

```kotlin
package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlin.time.Instant

data class Member(
    val accountId: AccountId,
    val joinedAt: Instant,
)
```

Drop `displayName: String` + `avatarUrl: String?`.

- [ ] **Step 2: Update `CrewMapper.kt`**

Where the DTO → domain conversion sets `displayName = dto.displayName ?: ""`, just drop those fields.

- [ ] **Step 3: Keep DTO field for backward-compat reads**

`MemberDto.kt`:
```kotlin
@Serializable
data class MemberDto(
    val accountId: String = "",
    val joinedAt: Long = 0L,
    // Legacy denormalized fields — kept as nullable read-only so older docs still deserialize.
    // Writers no longer populate these. Drop the fields when a Firestore migration backfills.
    val displayName: String? = null,
    val avatarUrl: String? = null,
)
```

- [ ] **Step 4: Build (this will cause compile errors in everything that reads `member.displayName`/`member.avatarUrl`)**

Run: `./gradlew :feature:crew:compileKotlinMetadata 2>&1 | head -30`
Expected: errors pointing to `FrCrewMemberRow.kt`, `CrewSettingsScreen.kt`, etc. — fix in next tasks.

- [ ] **Step 5: Commit (broken-build commit is fine; the next task fixes)**

Skip commit until D3 is done — D2 + D3 commit together.

### Task D3: Resolve member identity via `AccountReadPort` in presentation

**Files:**
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/presentation/components/FrCrewMemberRow.kt`
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/presentation/settings/CrewSettingsScreen.kt`
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/presentation/settings/CrewSettingsViewModel.kt` — compute a `Map<AccountId, Account?>` from `AccountReadPort.observeMany(crew.members.map { it.accountId }.toSet())` and add it to `CrewSettingsState`

- [ ] **Step 1: `CrewSettingsState` gains a resolved-identity map**

```kotlin
data class CrewSettingsState(
    ...
    val identities: Map<AccountId, Account?> = emptyMap(),
) : MviState
```

- [ ] **Step 2: VM combines crew flow + identity flow**

```kotlin
init {
    viewModelScope.launch {
        observeCrew(crewId).collect { r ->
            if (r is Result.Ok) {
                val crew = r.value
                update { it.copy(crew = crew, ...) }
                val ids = crew.members.map { it.accountId }.toSet()
                // launch the identity observer when membership changes
                launch {
                    accountRead.observeMany(ids).collect { map ->
                        update { it.copy(identities = map) }
                    }
                }
            } else { ... }
        }
    }
}
```

NOTE: cancel + relaunch the inner job when the member set changes to avoid leaked observers. Use `distinctUntilChanged()` on the id set.

- [ ] **Step 3: Update `FrCrewMemberRow` signature**

```kotlin
@Composable
fun FrCrewMemberRow(
    account: Account?,           // null = "Loading…" or "Deleted user"
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
)
```

Use the comment-row "Deleted user" pattern as reference for null.

- [ ] **Step 4: Update `CrewSettingsScreen` call site**

```kotlin
items(crew.members, key = { it.accountId.value }) { m ->
    val account = state.identities[m.accountId]
    val canRemove = state.isOwner && m.accountId != state.myAccountId
    FrCrewMemberRow(
        account = account,
        trailing = if (canRemove) { { /* Remove icon */ } } else null,
    )
}
```

- [ ] **Step 5: Build + run all crew + auth tests**

Run: `./gradlew :feature:crew:testAndroidHostTest :feature:auth:testAndroidHostTest`
Expected: PASS (after test fakes updated).

- [ ] **Step 6: Commit (combines D2 + D3)**

```bash
git add feature/crew/ core/domain/
git commit -m "$(cat <<'EOF'
refactor(feature:crew): resolve member identity via AccountReadPort

Drops the denormalized displayName/avatarUrl fields from the Member domain
model and the CrewMapper. CrewSettings now observes per-member Account flows
and renders "Deleted user" for missing accounts (same pattern as comments).

DTO keeps the legacy fields as nullable read-only so older crew docs still
deserialize cleanly. A future Firestore backfill can clear them.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task D4: Strip the cache-write path from `Update*UseCase`

**Files:**
- Modify: `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/domain/usecase/profile/UpdateMyDisplayNameUseCase.kt`
- Modify: `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/domain/usecase/profile/UpdateMyAvatarUseCase.kt`
- Modify: matching tests
- Modify: `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/di/AuthModule.kt` — drop the cache port + crash reporter constructor args

- [ ] **Step 1: Simplify `UpdateMyDisplayNameUseCase`**

```kotlin
class UpdateMyDisplayNameUseCase(
    private val accountWrite: AccountWritePort,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(name: String): Result<Unit, ProfileError> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(ProfileError.Validation.DisplayNameBlank)
        if (trimmed.length > MAX) return Result.failure(ProfileError.Validation.DisplayNameTooLong)
        val s = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }
        return when (val r = accountWrite.updateDisplayName(s.accountId, trimmed)) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toProfileError())
        }
    }
    companion object { const val MAX = 40 }
}
```

(Drop `CrewMemberCacheWritePort` + `CrashReporter` constructor args + the cache-write block + the telemetry call.)

- [ ] **Step 2: Same for `UpdateMyAvatarUseCase` — drop the cache step**

- [ ] **Step 3: Delete the now-irrelevant tests**

The tests `cache_write_failure_swallowed_and_logged` and `success_writes_account_then_cache` go away. Keep validation + signed-out + backend tests.

- [ ] **Step 4: Update Koin module — drop the dropped deps**

In `authModule`:
```kotlin
factoryOf(::UpdateMyDisplayNameUseCase)   // ← Koin auto-resolves the simpler constructor
factoryOf(::UpdateMyAvatarUseCase)
```

- [ ] **Step 5: Build + test**

Run: `./gradlew :feature:auth:testAndroidHostTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/auth/
git commit -m "$(cat <<'EOF'
refactor(feature:auth): drop cache-write step from profile update use cases

Member list now reads identity live via AccountReadPort, so the
denormalized cache field is gone and the dual-write hazard disappears
along with it.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task D5: Delete `CrewMemberCacheWritePort` + adapter + DataSource methods + `CrewMemberWriter`

**Files:**
- Delete: `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/crew/CrewMemberCacheWritePort.kt`
- Delete: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/data/firebase/FirestoreCrewMemberCacheWriter.kt`
- Delete: `feature/crew/src/androidHostTest/kotlin/es/schsebastian/foodrats/feature/crew/data/firebase/FirestoreCrewMemberCacheWriterTest.kt`
- Delete: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/data/firebase/CrewMemberWriter.kt`
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/di/CrewModule.kt` — drop the bindings
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/data/firebase/CrewFirestoreDataSource.kt` — drop `renameMember`, `updateAccountAvatarUrl`, `setMemberDisplayName`, `updateMemberAvatarUrl`

- [ ] **Step 1: Run a final consumer scan**

Run: `grep -rn "CrewMemberCacheWritePort\|FirestoreCrewMemberCacheWriter\|CrewMemberWriter\|renameMember\|updateAccountAvatarUrl\|setMemberDisplayName\|updateMemberAvatarUrl" /Users/sebastiancardonahenao/AndroidStudioProjects/FoodRats/ --include="*.kt"`
Expected: hits only inside the files we're about to delete + their Koin binding lines.

- [ ] **Step 2: Delete files + Koin bindings + DataSource methods**

```bash
rm core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/crew/CrewMemberCacheWritePort.kt
rm feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/data/firebase/FirestoreCrewMemberCacheWriter.kt
rm feature/crew/src/androidHostTest/kotlin/es/schsebastian/foodrats/feature/crew/data/firebase/FirestoreCrewMemberCacheWriterTest.kt
rm feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/data/firebase/CrewMemberWriter.kt
```

In `CrewModule.kt`, remove:
- `single<CrewMemberWriter> { FirestoreCrewMemberWriter(firestore = get()) }` (the legacy CrewMemberWriter interface)
- `single<CrewMemberCacheWritePort> { FirestoreCrewMemberCacheWriter(dataSource = get()) }`
- their imports

In `CrewFirestoreDataSource.kt`, delete the four methods listed above.

- [ ] **Step 3: Build + test full crew + domain modules**

Run: `./gradlew :core:domain:testAndroidHostTest :feature:crew:testAndroidHostTest :feature:auth:testAndroidHostTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/domain/ feature/crew/
git commit -m "$(cat <<'EOF'
chore(feature:crew): delete CrewMemberCacheWritePort + dead writer methods

The denormalized member cache is gone (Phase D). Identity now flows from
accounts/{uid} via AccountReadPort. Drops:
- core/domain CrewMemberCacheWritePort + Error
- FirestoreCrewMemberCacheWriter + test
- CrewMemberWriter interface + FirestoreCrewMemberWriter impl
- CrewFirestoreDataSource.{renameMember,updateAccountAvatarUrl,setMemberDisplayName,updateMemberAvatarUrl}

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task D6: Update CLAUDE.md — strike member-cache + dead-code entries

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Remove the two bullets**

> - **Member-cache drift (Profile split):** ...
> - **Dead code in `CrewFirestoreDataSource`:** ...

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(claude): member-cache drift + dead-code debt resolved (Phase D)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase C — Real RemoveMember feature (post Phase D)

Depends on Phase D because `Member` no longer carries `displayName` (so the UI must already resolve identity via `AccountReadPort`).

### Task C1: Add `removeMember` to `CrewRepository` interface

**Files:**
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/domain/repository/CrewRepository.kt`
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/data/repository/FirebaseCrewRepository.kt`
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/data/firebase/CrewFirestoreDataSource.kt`

- [ ] **Step 1: Add the interface method**

```kotlin
interface CrewRepository {
    ...
    /** Owner-only. Removes the member doc from crews/{crewId}.members.{memberId}. */
    suspend fun removeMember(
        crewId: CrewId,
        requestedBy: AccountId,
        memberId: AccountId,
    ): Result<Unit, CrewError>
}
```

- [ ] **Step 2: Implement in `FirebaseCrewRepository`**

```kotlin
override suspend fun removeMember(
    crewId: CrewId,
    requestedBy: AccountId,
    memberId: AccountId,
): Result<Unit, CrewError> {
    if (requestedBy == memberId) return Result.failure(CrewError.Validation.CannotRemoveSelf)
    // owner check enforced server-side by Firestore rule; client check is a friendly fast-fail
    return dataSource.removeMember(crewId, memberId)
}
```

- [ ] **Step 3: Implement in `CrewFirestoreDataSource`**

```kotlin
suspend fun removeMember(crewId: CrewId, memberId: AccountId): Result<Unit, CrewError> =
    withContext(dispatchers.io) {
        runCatching {
            crewsCol.document(crewId.value)
                .update("members.${memberId.value}" to com.google.firebase.firestore.FieldValue.delete())
            Result.success(Unit)
        }.getOrElse { Result.failure(errorMapper.map(it)) }
    }
```

NOTE: the FieldValue.delete() path on KMP through GitLive's bindings — verify the API. If GitLive doesn't expose `FieldValue.delete()`, fall back to reading the crew doc, removing the entry from the members map, and writing back (with a transaction).

- [ ] **Step 4: Add the new error leaves**

In `CrewError.kt`:
```kotlin
sealed interface Validation : CrewError {
    ...
    data object CannotRemoveSelf : Validation
}
```

Remove `NotImplemented.RemoveMember` and the whole `NotImplemented` group (assuming nothing else uses it):
```kotlin
// delete:
// sealed interface NotImplemented : CrewError {
//     data object RemoveMember : NotImplemented
// }
```

- [ ] **Step 5: Update `CrewErrorToStringKey.kt` + i18n + test**

Add:
```kotlin
CrewError.Validation.CannotRemoveSelf -> CrewStringKey.ErrorCannotRemoveSelf
```

Remove the `NotImplemented.RemoveMember -> RemoveMemberNotYetAvailable` branch.

Add strings:
- `<string name="crew_error_cannot_remove_self">You can't remove yourself. Leave the crew instead.</string>`
- Spanish equivalent.

Add `CrewStringKey.ErrorCannotRemoveSelf` entry + import.

Remove `CrewStringKey.RemoveMemberNotYetAvailable` + its strings.

- [ ] **Step 6: Update `CrewErrorToStringKeyTest`**

Remove the `maps_not_implemented_remove_member` test. Add `maps_validation_cannot_remove_self`.

- [ ] **Step 7: Build**

Run: `./gradlew :feature:crew:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add feature/crew/
git commit -m "$(cat <<'EOF'
feat(feature:crew): CrewRepository.removeMember + CannotRemoveSelf error

Drops the NotImplemented.RemoveMember stub error in favor of real validation
errors. Firestore data source uses FieldValue.delete() (or a transactional
read/write fallback if GitLive doesn't expose it).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task C2: Real `RemoveMemberUseCase`

**Files:**
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/domain/usecase/RemoveMemberUseCase.kt`
- Modify: `feature/crew/src/commonTest/kotlin/es/schsebastian/foodrats/feature/crew/domain/usecase/RemoveMemberUseCaseTest.kt`
- Modify: `feature/crew/src/commonMain/kotlin/es/schsebastian/foodrats/feature/crew/di/CrewModule.kt`

- [ ] **Step 1: Replace the stub with the real implementation**

```kotlin
package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

class RemoveMemberUseCase(
    private val repo: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId, memberId: AccountId): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok  -> s.value.accountId
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        return repo.removeMember(crewId, accountId, memberId)
    }
}
```

Note: the use case signature now takes `(crewId, memberId)`. Update all callers (the VM in particular).

- [ ] **Step 2: Update the test**

```kotlin
class RemoveMemberUseCaseTest {
    private val owner = aid("uid-owner")
    private val member = aid("uid-other")
    private val crewId = cid("c-1")

    @Test fun rejects_removing_self() = runTest {
        val repo = FakeCrewRepository(/* with a crew */)
        val session = FakeSessionProvider(Session(owner, crewId))
        val uc = RemoveMemberUseCase(repo, session)
        val r = uc.invoke(crewId, owner)   // try to remove self
        assertEquals(Result.failure(CrewError.Validation.CannotRemoveSelf), r)
    }

    @Test fun delegates_to_repo_for_other_member() = runTest {
        val repo = FakeCrewRepository(/* with a crew */).also { it.nextRemoveMember = Result.success(Unit) }
        val session = FakeSessionProvider(Session(owner, crewId))
        val uc = RemoveMemberUseCase(repo, session)
        val r = uc.invoke(crewId, member)
        assertEquals(Result.success(Unit), r)
        assertEquals(Triple(crewId, owner, member), repo.lastRemoveMember)
    }
}
```

Add the corresponding fields + override to `FakeCrewRepository`.

- [ ] **Step 3: Update Koin binding (constructor signature changed)**

`factoryOf(::RemoveMemberUseCase)` — Koin resolves the two new params automatically; no change needed to the binding line.

- [ ] **Step 4: Update `CrewSettingsViewModel.doRemoveMember` to pass `crewId`**

```kotlin
private suspend fun doRemoveMember(intent: CrewSettingsIntent.RemoveMemberConfirmed) {
    when (val r = removeMember(crewId, intent.accountId)) {
        is Result.Ok  -> Unit
        is Result.Err -> update { it.copy(error = r.error) }
    }
}
```

- [ ] **Step 5: Build + test**

Run: `./gradlew :feature:crew:testAndroidHostTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/crew/
git commit -m "$(cat <<'EOF'
feat(feature:crew): real RemoveMemberUseCase

Removes the stub. Delegates to CrewRepository.removeMember with the
caller's accountId from session; rejects self-removal client-side.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task C3: Firestore security rule — owner-only removeMember

**Files:**
- Modify: `firestore.rules`

- [ ] **Step 1: Add the rule**

Find the existing `match /crews/{crewId} { ... }` block. Allow partial-update writes that remove a member from the members map **only** when:
- the requester is `crew.ownerId`
- the removed accountId is NOT the requester

The exact `allow update` predicate depends on existing rule shape — read the file and add a focused condition. Example sketch:

```
allow update: if request.auth.uid == resource.data.ownerId
              && request.resource.data.diff(resource.data).affectedKeys().hasOnly(["members"])
              // disallow removing self via this path; owner uses leave/delete instead
              && !(request.resource.data.diff(resource.data).removedKeys().hasAny([request.auth.uid]));
```

NOTE: rule expression depends on whether `members` is a map keyed by accountId or a list. Verify shape with `gh api repos/SCHsebastian/FoodRats/contents/firestore.rules` or by reading the file.

- [ ] **Step 2: Deploy the rules**

Run: `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`
Expected: deploy success.

- [ ] **Step 3: Manual smoke**

On a connected device, sign in as owner, remove a member, verify the action succeeds. Sign in as a non-owner, attempt the same Firestore write directly (via debug tool), verify PERMISSION_DENIED.

- [ ] **Step 4: Commit**

```bash
git add firestore.rules
git commit -m "$(cat <<'EOF'
chore(rules): owner-only removeMember on crews/{crewId}

Permits the partial members-map update only when:
- the caller is the crew owner, AND
- the removed key is not the caller themselves.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task C4: Update CLAUDE.md — strike the RemoveMember stub debt

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Replace the bullet**

The current "Remove-member stub" bullet → just delete it. Real implementation has shipped.

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(claude): real RemoveMember shipped (Phase C)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase F — iOS Share via `UIActivityViewController`

Replaces the `ShareControllerIos` stub at `core/data/src/iosMain/kotlin/.../share/ShareControllerIos.kt`.

### Task F1: Define lambda-injected `ShareControllerIos`

**Files:**
- Modify: `core/data/src/iosMain/kotlin/es/schsebastian/foodrats/core/data/share/ShareControllerIos.kt`

- [ ] **Step 1: Make `shareText` take a lambda**

Mirror the `GoogleSignInBridge` lambda-injection pattern.

```kotlin
package es.schsebastian.foodrats.core.data.share

class ShareControllerIos(
    private val present: (String) -> Unit,
) : ShareController {
    override fun shareText(text: String) = present(text)
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :core:data:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/data/
git commit -m "$(cat <<'EOF'
refactor(core:data): ShareControllerIos accepts present lambda

Swift wires UIActivityViewController on the iOS side (matches GoogleSignIn
bridge pattern). Was a no-op console stub.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task F2: `ShareControllerBridge.swift` + wire through `MainViewController`

**Files:**
- Create: `iosApp/iosApp/ShareControllerBridge.swift`
- Modify: `iosApp/iosApp/ContentView.swift` — pass the share lambda into `MainViewController`
- Modify: `shared/src/iosMain/kotlin/es/schsebastian/foodrats/MainViewController.kt` — accept the lambda + install a Koin module providing `ShareController` via the lambda

- [ ] **Step 1: Swift bridge**

```swift
import UIKit

@objc class ShareControllerBridge: NSObject {
    @objc static func present(text: String) {
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow })?
            .rootViewController else { return }

        let topmost = ShareControllerBridge.topmost(from: root)
        let vc = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        if let pop = vc.popoverPresentationController {
            pop.sourceView = topmost.view
            pop.sourceRect = CGRect(x: topmost.view.bounds.midX, y: topmost.view.bounds.midY, width: 0, height: 0)
            pop.permittedArrowDirections = []
        }
        topmost.present(vc, animated: true)
    }

    private static func topmost(from root: UIViewController) -> UIViewController {
        if let presented = root.presentedViewController { return topmost(from: presented) }
        if let nav = root as? UINavigationController, let top = nav.topViewController { return topmost(from: top) }
        if let tab = root as? UITabBarController, let sel = tab.selectedViewController { return topmost(from: sel) }
        return root
    }
}
```

- [ ] **Step 2: Pass lambda into `MainViewController` from Swift**

In `ContentView.swift`:

```swift
MainViewControllerKt.MainViewController(
    ...,
    presentShareSheet: { text in
        ShareControllerBridge.present(text: text)
    }
)
```

In `MainViewController.kt`:

```kotlin
fun MainViewController(
    ...,
    presentShareSheet: (String) -> Unit,
): UIViewController {
    val shareModule = module {
        single<ShareController> { ShareControllerIos(present = presentShareSheet) }
    }
    startKoin { modules(..., shareModule) }
    return ComposeUIViewController { App() }
}
```

- [ ] **Step 3: Manual smoke on iOS sim** (after Xcode rebuild)

Sign in, open Crew Settings, tap Share — UIActivityViewController should appear with the invite code as the share content.

- [ ] **Step 4: Commit**

```bash
git add iosApp/ shared/
git commit -m "$(cat <<'EOF'
feat(ios): wire ShareController to UIActivityViewController via Swift bridge

Replaces the no-op stub. Mirrors the GoogleSignInBridge lambda-injection
pattern; topmost-presenter resolution covers nav/tab containers.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase G — Broaden `CrewSettingsViewModelTest`

**Files:**
- Modify: `feature/crew/src/commonTest/kotlin/es/schsebastian/foodrats/feature/crew/presentation/settings/CrewSettingsViewModelTest.kt`

- [ ] **Step 1: Add tests covering**

For each, follow the existing minimal-test pattern (sync `runTest` + `vm.state.value`):
- `save_crew_name_success_keeps_state_clean`
- `save_crew_name_fails_with_authorization_not_owner_when_non_owner`
- `save_crew_name_blank_is_rejected_locally` (delegated to use case validation)
- `leave_crew_emits_left_effect_on_success`
- `leave_crew_error_surfaces_on_state`
- `confirm_delete_emits_deleted_effect_on_success_owner_only`
- `cancel_delete_clears_dialog_flag`
- `switch_crew_emits_navigate_effect`

Each case: ~10 lines. Reuse the existing `buildVm(actingAs)` helper, parameterized with new `nextRename`/`nextLeave`/`nextDelete` stubs on `FakeCrewRepository`.

- [ ] **Step 2: Build + run**

Run: `./gradlew :feature:crew:testAndroidHostTest --tests "*CrewSettingsViewModelTest*"`
Expected: PASS (all new cases).

- [ ] **Step 3: Update CLAUDE.md — strike the "minimal test" bullet**

- [ ] **Step 4: Commit**

```bash
git add feature/crew/ CLAUDE.md
git commit -m "$(cat <<'EOF'
test(feature:crew): broaden CrewSettingsViewModelTest

Adds coverage for crew-name save, leave, delete, switch-crew, and the
non-owner authorization path.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase H — Vendor SVGs for FrIcons substitutes

**Files:**
- Modify: `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/atoms/FrIcons.kt`

Currently substituted (placeholder → wanted):
- `AddPhoto = Icons.Filled.Add` → wants `AddAPhoto`
- `GalleryImport = Icons.Filled.List` → wants `Image`
- `CameraOff = Icons.Filled.Warning` → wants `NoPhotography`
- `Stats = Icons.Filled.Star` → wants `BarChart`

`PhotoCameraVector` is already correctly vendored using the `materialIcon` DSL — use it as the template.

- [ ] **Step 1: Pull the four SVG path data sets**

Source: Google Material Symbols at https://fonts.google.com/icons (Outlined/Filled variants — pick the variant that matches the existing icon set).

For each icon, copy the SVG's `<path d="…">` and translate the `M/L/H/V/C/S/Z` commands into the `materialIcon` DSL builder calls (`moveTo`, `lineTo`, `horizontalLineToRelative`, `curveTo`, etc.). The existing `PhotoCameraVector` (lines 37–68) is the worked example.

- [ ] **Step 2: Define each vector**

```kotlin
private val AddAPhotoVector: ImageVector = materialIcon(name = "Filled.AddAPhoto") { /* path */ }
private val ImageVector_: ImageVector    = materialIcon(name = "Filled.Image")     { /* path */ }
private val NoPhotographyVector: ImageVector = materialIcon(name = "Filled.NoPhotography") { /* path */ }
private val BarChartVector: ImageVector  = materialIcon(name = "Filled.BarChart") { /* path */ }
```

(Use distinct variable names — `ImageVector` is a class.)

- [ ] **Step 3: Swap `FrIcons` to point to the vendored vectors**

```kotlin
object FrIcons {
    ...
    val AddPhoto: ImageVector      = AddAPhotoVector
    val GalleryImport: ImageVector = ImageVector_
    val CameraOff: ImageVector     = NoPhotographyVector
    val Stats: ImageVector         = BarChartVector
    ...
}
```

Update the preview to drop the `*` suffix on the relabeled icons.

- [ ] **Step 4: Run designsystem tests + catalog build**

Run: `./gradlew :core:designsystem:testAndroidHostTest :catalogApp:assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 5: Update CLAUDE.md — strike the material-icons-core placeholder bullet**

- [ ] **Step 6: Commit**

```bash
git add core/designsystem/ CLAUDE.md
git commit -m "$(cat <<'EOF'
feat(designsystem): vendor real glyphs for AddAPhoto / Image / NoPhotography / BarChart

Replaces the material-icons-core placeholders that shipped because
material-icons-extended has no KMP iOS artifact. Path data copied from
Google Material Symbols.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase E — Dev-crew legacy migration removal (date-gated)

Ship only AFTER 2026-06-15 OR after the public release rollout completes — whichever is later. Before that date, the migration code protects users mid-upgrade and removing it would orphan their stale prefs.

### Task E1: Remove `LEGACY_DEV_CREW_ID` + helper

**Files:**
- Modify: `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/data/repository/FirebaseAuthRepository.kt`
- Modify: `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/data/migration/LegacyDevCrewMigration.kt` (or wherever `clearLegacyDevCrewIfPresent()` lives)
- Modify: matching test

- [ ] **Step 1: Confirm we're past the trigger date**

Manual check — verify with the team or your release notes.

- [ ] **Step 2: Delete `LEGACY_DEV_CREW_ID` const + the migration call + the helper file**

- [ ] **Step 3: Build + run**

Run: `./gradlew :feature:auth:testAndroidHostTest`
Expected: PASS.

- [ ] **Step 4: Update CLAUDE.md — strike the dev-crew bullet**

- [ ] **Step 5: Commit**

```bash
git add feature/auth/ CLAUDE.md
git commit -m "$(cat <<'EOF'
chore(feature:auth): remove LEGACY_DEV_CREW_ID one-shot migration

Past rollout horizon (2026-06-15). Pre-update prefs were already cleared
on next sign-in for any user who upgraded; this code is dead.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase I — Documentation-only: iOS Crashlytics + CoreLocation manual steps

No code changes — these are Xcode steps documented in `CLAUDE.md` "iOS status" + "Active tech debt". Verify the doc is current after each iOS rebuild lands; if the steps have changed, update accordingly. No commit if no change.

---

## Self-review (run before handing off)

**1. Spec / requirements coverage:**
- ✅ #1 (cross-feature dep violations) — Phase B
- ✅ #2 (real RemoveMember) — Phase C
- ✅ #3 (member-cache live-reads migration) — Phase D
- ✅ #4 (empty crew display name) — Phase A1
- ✅ #5 (dead code in CrewFirestoreDataSource) — folded into Phase D (Tasks D5/D6)
- ✅ #6 (broaden CrewSettingsViewModelTest) — Phase G
- ✅ #7 (vendor SVGs) — Phase H
- ✅ Bucket-A safe fixes — Phase A (A1, A2)
- ✅ Bucket-B intentional stubs — Phase C (real RemoveMember), Phase E (dev-crew), Phase F (iOS Share)

**2. Placeholder scan:** No "TBD"; every step has actual code or an exact command. The two NOTE blocks (GitLive `FieldValue.delete()` shape + Firestore rule predicate) call out *verifications* the implementer must do — not unfinished work.

**3. Type consistency:**
- `TokenRegistrationPort.registerCurrentDeviceToken()` — same signature in B1, B3 adapter, B4 VM, B4 test fake.
- `StreakNotificationPort.scheduleStreakNudge()` — same signature in B2, B3, B5, B5 test fake.
- `AccountReadPort.observeMany(ids: Set<AccountId>)` — same in D1 + D3.
- `RemoveMemberUseCase.invoke(crewId, memberId)` — same in C2 use case body, C2 test, and the VM update in C2 step 4.
- `CrewError.Validation.CannotRemoveSelf` — added in C1, mapped in C1, removed `NotImplemented.RemoveMember` in C1.

**4. Cross-task drift:** Phase D drops `Member.displayName`/`avatarUrl` — Phase C's UI work (added in earlier task to `CrewSettingsScreen`) already uses the post-D shape via `state.identities[m.accountId]`. Phase C can land after Phase D without re-touching the screen.

**5. Risk-ordered:** A → B → D → C → F → G → H → E. The Profile-split spec's §4.3 explicitly says member live-reads is the future fix — Phase D directly executes that future plan.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-21-mocks-to-real-code-and-tech-debt.md`. Two execution options:

1. **Subagent-Driven** (recommended) — fresh subagent per task, two-stage review between tasks, fast iteration.
2. **Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
