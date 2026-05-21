# Split Crew Settings into Your Profile + Crew Settings — design spec

**Status**: ready for plan
**Date**: 2026-05-21
**Author**: Sebastián (with Claude Code)
**Builds on**: [`2026-05-20-comments-identity-camera-only-design.md`](2026-05-20-comments-identity-camera-only-design.md) §2 ("Out-of-crew Settings screen" deferred).

## 1. Goals

1. **Promote Your Profile to an account-scoped surface.** Identity edits (display name, avatar) and sign-out leave the per-crew `CrewSettingsScreen` and live on a new `ProfileScreen` reachable from any tab in `MainScaffold`.
2. **Shrink Crew Settings to crew-scoped concerns only.** Crew name, invite code, members list, switch crew, leave crew, danger zone. The members list grows a per-row owner-only "Remove" affordance (stubbed — real implementation is tech debt).
3. **Move identity write code to where it belongs.** `:feature:auth` owns the canonical `accounts/{uid}` doc lifecycle, so it should own writes to it and to its associated `avatars/{uid}.jpg` Storage object. The denormalized `crews/{crewId}.members.{uid}` cache stays in `:feature:crew`, exposed via a new write port in `:core:domain`. No new cross-feature dependencies.

## 2. Out of scope (deferred)

- **Multi-crew rename fan-out.** Profile rename writes `accounts/{uid}` + the *active* crew's denormalized member entry only. Other crews where the user is a member keep their denormalized cache stale until a future "live reads everywhere" migration. Aligned with the dev-crew hardcoding still in place on `main`.
- **Members list live-reads via `AccountReadPort`.** The members list inside `CrewSettings` continues to read the denormalized `crews/{crewId}.members` map. Migrating it to live reads (same pattern as comments per the 2026-05-20 spec) is a separate refactor.
- **Real `Remove member` implementation.** Owner-only per-row Remove button ships as a stub that surfaces "coming soon." The use case, data-layer write, security rule, and removed-user notification are debt.
- **Future account-level controls.** Profile reserves no placeholders for language preference, notification settings, change email, or delete account. YAGNI — they land when needed.
- **`AccountReadPort` adoption inside `CrewSettings` members list.** Out of scope; tracked as the migration path under §11.

## 3. Information architecture

### 3.1 Entry points from `MainScaffold`

The top app bar gains a **leading** `FrAvatar` (size = `Sizes.avatarSm`) showing the current user's canonical avatar with initials fallback. It taps to `Route.Profile`. The existing **trailing** gear icon still goes to `Route.CrewSettings(activeCrewId.value)`.

Both surfaces are siblings of `Route.Main` — push onto the back stack, pop returns to whichever tab the user was on.

The leading avatar reads `AccountReadPort.observe(session.accountId)` so a rename/avatar upload from Profile reflects in the bar instantly (same live-source pattern as comments).

Pre-Main scaffold (`Splash`/`SignIn`/`NotificationPermission`/`CrewPicker`): no top app bar, no entry points. By the time the user enters `Route.Main`, `session.activeCrewId` is non-null (dev-crew hardcoding + CrewPicker enforce this) — so the gear's `CrewSettings(activeCrewId.value)` never sees null.

### 3.2 Content split

**Profile (account-scoped, no crew context required to view; active crew used at write-time):**
- Large `FrAvatar` (size ≈ 96dp). Tap → gallery picker.
- Display name `FrTextField` + Save button.
- Email (read-only, `Account.email`).
- Sign out button.

**Crew Settings (crew-scoped, requires `activeCrewId`):**
- Crew name + Save (owner only).
- Invite code (view, copy, share — owner only).
- Members list, with per-row owner-only Remove button (stub).
- Switch crew button.
- Leave crew button.
- Danger zone: Delete crew (owner only).

The "Your profile" section inside `CrewSettingsScreen` (lines 201–238) is deleted entirely.

## 4. Domain & data layer

### 4.1 New ports in `:core:domain`

```kotlin
// :core:domain/account/AccountWritePort.kt
interface AccountWritePort {
    suspend fun updateDisplayName(
        accountId: AccountId,
        name: String,
    ): Result<Unit, AccountWriteError>

    /** Uploads bytes to Storage at avatars/{uid}.jpg, writes the URL to
     *  accounts/{uid}.avatarUrl, and returns the URL so callers can fan it out
     *  to denormalized caches. */
    suspend fun uploadAndSetAvatar(
        accountId: AccountId,
        bytes: ByteArray,
    ): Result<String, AccountWriteError>
}

sealed interface AccountWriteError {
    sealed interface Validation : AccountWriteError {
        data object DisplayNameBlank : Validation
        data object DisplayNameTooLong : Validation
        data object EmptyBytes : Validation
    }
    sealed interface Backend : AccountWriteError {
        data object Unavailable : Backend
    }
}
```

```kotlin
// :core:domain/crew/CrewMemberCacheWritePort.kt
/** Writes the denormalized member fields in crews/{crewId}.members.{uid}.
 *  Canonical identity lives in accounts/{uid}; this port writes the read-optimized cache. */
interface CrewMemberCacheWritePort {
    suspend fun setDisplayName(
        crewId: CrewId,
        accountId: AccountId,
        name: String,
    ): Result<Unit, CrewMemberCacheWriteError>

    suspend fun setAvatarUrl(
        crewId: CrewId,
        accountId: AccountId,
        url: String,
    ): Result<Unit, CrewMemberCacheWriteError>
}

sealed interface CrewMemberCacheWriteError {
    sealed interface Backend : CrewMemberCacheWriteError {
        data object Unavailable : Backend
    }
}
```

**Konsist check.** Both ports use only `kotlin.stdlib`, `kotlinx-coroutines-core`, and existing `:core:domain` value objects (`AccountId`, `CrewId`). The existing rule in `KonsistRulesTest` stays green.

### 4.2 Adapters

| Port | Implementation | Module | Notes |
|---|---|---|---|
| `AccountWritePort` | `FirestoreAccountWriter` | `:feature:auth/data/firebase/` (new) | Wraps Firestore writes to `accounts/{uid}` and Storage upload via the moved `AvatarStorageDataSource`. |
| `CrewMemberCacheWritePort` | `FirestoreCrewMemberCacheWriter` | `:feature:crew/data/firebase/` (new) | Thin wrapper over the existing `CrewFirestoreDataSource` partial-update methods. |

**Files moved (not deleted) from `:feature:crew` to `:feature:auth`:**
- `feature/crew/data/firebase/AvatarStorageDataSource.kt` → `feature/auth/data/firebase/AvatarStorageDataSource.kt`
- `feature/crew/data/firebase/StorageData.kt` (commonMain expect) → `feature/auth/data/firebase/StorageData.kt`
- `feature/crew/data/firebase/StorageData.ios.kt` (iosMain actual) → `feature/auth/data/firebase/StorageData.ios.kt`
- Koin binding `singleOf(::AvatarStorageDataSource)` moves from `crewModule` to `authModule`.

**Files removed from `CrewFirestoreDataSource`:**
- `updateAccountAvatarUrl(...)` — moved to `FirestoreAccountWriter`.
- `renameMember(...)` and its inner `writer.renameAndPropagate(...)` path — replaced by `setMemberDisplayName(crewId, accountId, name)` (rename-only, no `accounts/{uid}` write). The new method backs `CrewMemberCacheWritePort.setDisplayName`.

### 4.3 New use cases in `:feature:auth/domain/usecase/profile/`

```kotlin
class UpdateMyDisplayNameUseCase(
    private val accountWrite: AccountWritePort,
    private val memberCacheWrite: CrewMemberCacheWritePort,
    private val session: SessionProvider,
    private val crashReporter: CrashReporter,
) {
    suspend operator fun invoke(name: String): Result<Unit, ProfileError> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(ProfileError.Validation.DisplayNameBlank)
        if (trimmed.length > MAX) return Result.failure(ProfileError.Validation.DisplayNameTooLong)

        val s = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }

        // Canonical write first; the cache write is best-effort.
        accountWrite.updateDisplayName(s.accountId, trimmed)
            .onFailure { return Result.failure(it.toProfileError()) }

        s.activeCrewId?.let { crewId ->
            memberCacheWrite.setDisplayName(crewId, s.accountId, trimmed)
                .onFailure { err ->
                    crashReporter.log(
                        message = "member cache drift",
                        throwable = null,
                        metadata = mapOf(
                            "op" to "setDisplayName",
                            "crewId" to crewId.value,
                            "accountId" to s.accountId.value,
                        ),
                    )
                }
        }
        return Result.success(Unit)
    }

    companion object { const val MAX = 40 }
}
```

```kotlin
class UpdateMyAvatarUseCase(
    private val accountWrite: AccountWritePort,
    private val memberCacheWrite: CrewMemberCacheWritePort,
    private val session: SessionProvider,
    private val crashReporter: CrashReporter,
) {
    suspend operator fun invoke(bytes: ByteArray): Result<String, ProfileError> {
        if (bytes.isEmpty()) return Result.failure(ProfileError.Validation.EmptyBytes)
        val s = when (val r = session.requireCurrent()) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.failure(ProfileError.Session.SignedOut)
        }

        val url = when (val u = accountWrite.uploadAndSetAvatar(s.accountId, bytes)) {
            is Result.Ok -> u.value
            is Result.Err -> return Result.failure(u.error.toProfileError())
        }

        s.activeCrewId?.let { crewId ->
            memberCacheWrite.setAvatarUrl(crewId, s.accountId, url)
                .onFailure {
                    crashReporter.log(
                        message = "member cache drift",
                        throwable = null,
                        metadata = mapOf(
                            "op" to "setAvatarUrl",
                            "crewId" to crewId.value,
                            "accountId" to s.accountId.value,
                        ),
                    )
                }
        }
        return Result.success(url)
    }
}
```

**Atomicity.** The previous `CrewMemberWriter.renameAndPropagate` did both writes in one Firestore batch. Splitting across two ports loses that atomicity. Acceptable because:

- The denormalized cache is by definition allowed to drift; canonical identity reads via `AccountReadPort` are correct everywhere it matters (comments, the new top-bar avatar).
- Member list still reads from the cache — stale rows are confined to that one surface and self-correct on the next successful rename.
- `CrashReporter.log("member cache drift", ...)` gives us telemetry to know if real-world drift is non-zero. If it is, the future "live reads everywhere" migration (§11) is the structural fix.

### 4.4 `ProfileError` in `:feature:auth/domain/error/`

```kotlin
sealed interface ProfileError {
    sealed interface Validation : ProfileError {
        data object DisplayNameBlank : Validation
        data object DisplayNameTooLong : Validation
        data object EmptyBytes : Validation
    }
    sealed interface Backend : ProfileError {
        data object Unavailable : Backend
    }
    sealed interface Session : ProfileError {
        data object SignedOut : Session
    }
}

internal fun AccountWriteError.toProfileError(): ProfileError = when (this) {
    AccountWriteError.Validation.DisplayNameBlank -> ProfileError.Validation.DisplayNameBlank
    AccountWriteError.Validation.DisplayNameTooLong -> ProfileError.Validation.DisplayNameTooLong
    AccountWriteError.Validation.EmptyBytes -> ProfileError.Validation.EmptyBytes
    AccountWriteError.Backend.Unavailable -> ProfileError.Backend.Unavailable
}
```

A `ProfileErrorToStringKey` mapper (exhaustive `when`) lives in `:feature:auth/presentation/profile/`, with a matching `ProfileErrorToStringKeyTest` in `commonTest` to lock exhaustiveness.

### 4.5 Deletions

- `:feature:crew/domain/usecase/RenameMemberUseCase.kt` + Koin binding + `RenameMemberUseCaseTest.kt`.
- `:feature:crew/domain/usecase/UpdateMyAvatarUseCase.kt` + Koin binding + `UpdateMyAvatarUseCaseTest.kt`.
- `:feature:crew/data/firebase/CrewMemberWriter.renameAndPropagate(...)` — replaced by a narrower `setMemberDisplayName(...)` (cache only).

### 4.6 Firestore security rules

No rule changes required. All paths already permit owner writes:
- `accounts/{uid}` (owner write — existing).
- `crews/{crewId}.members.{uid}` partial updates (existing rename path).
- Storage `avatars/{uid}.jpg` (existing avatar upload path).

## 5. Profile screen

```
┌─────────────────────────────────────────┐
│ ←  Your Profile                          │
├─────────────────────────────────────────┤
│              ┌────────┐                  │
│              │  🙂   │   ← FrAvatar Sizes.avatarXl (96dp)
│              └────────┘                  │     tap → ImagePickerKMP.launchGallery
│         "Tap to change avatar"           │
│                                          │
│  ─── Identity ──────────────────         │
│  Display name                            │
│  ┌────────────────────────────┐  [Save]  │
│  │ Sebastián                  │          │
│  └────────────────────────────┘          │
│                                          │
│  Signed in as                            │
│  schsebastiancardonahenao@gmail.com      │
│                                          │
│  ─── Account ────────────────────        │
│  [ Sign out ]                            │
└─────────────────────────────────────────┘
```

### 5.1 `ProfileViewModel`

Lives in `:feature:auth/presentation/profile/`. MVI base.

```kotlin
data class ProfileState(
    val account: Account? = null,                 // live from AccountReadPort
    val editingDisplayName: String = "",
    val isSavingDisplayName: Boolean = false,
    val saveDisplayNameError: StringKey? = null,
    val isUploadingAvatar: Boolean = false,
    val uploadAvatarError: StringKey? = null,
    val isSigningOut: Boolean = false,
    val signOutError: StringKey? = null,
)

sealed interface ProfileIntent {
    data class DisplayNameChanged(val value: String) : ProfileIntent
    data object SaveDisplayName : ProfileIntent
    data class AvatarPicked(val bytes: ByteArray) : ProfileIntent
    data object SignOut : ProfileIntent
}
```

**Single source of truth.** Following the `FeedViewModel` reference pattern (commit `fbf5e40`), `editingDisplayName` is seeded from the first `account.displayName` emission and then mutated only via the reducer. No parallel `MutableStateFlow`.

Dependencies (constructor-injected via Koin):
- `AccountReadPort` (live identity).
- `UpdateMyDisplayNameUseCase`.
- `UpdateMyAvatarUseCase`.
- `SignOutUseCase` (existing in `:feature:auth`).
- `SessionProvider` (for the `accountId`).
- `ProfileErrorToStringKey` mapper.

### 5.2 Composable

```kotlin
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    vm: ProfileViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val picker = rememberImagePickerKMP()

    LaunchedEffect(picker.result) {
        when (val r = picker.result) {
            is ImagePickerResult.Success -> {
                r.first?.asSource()?.readByteArray()?.let { bytes ->
                    vm.onIntent(ProfileIntent.AvatarPicked(bytes))
                }
                picker.reset()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { FrText(resolve(AuthStringKey.ProfileTitle)) },
                navigationIcon = {
                    IconButton(onBack) { Icon(FrIcons.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        LazyColumn(...) {
            item {
                FrAvatarPicker(
                    initials = state.account?.displayName?.initials() ?: "?",
                    avatarUrl = state.account?.avatarUrl,
                    onPickClick = { picker.launchGallery(allowMultiple = false, mimeTypes = listOf(MimeType.IMAGE_JPEG, MimeType.IMAGE_PNG)) },
                    busy = state.isUploadingAvatar,
                    changeLabel = resolve(AuthStringKey.ProfileChangeAvatarCta),
                    uploadingLabel = resolve(AuthStringKey.ProfileAvatarUploading),
                )
            }
            // Identity section, sign out section, etc.
        }
    }
}
```

### 5.3 Components

`MyAvatarPicker` molecule moves from `:feature:crew/presentation/components/` to `:core:designsystem/molecules/FrAvatarPicker.kt`. Already takes only primitives (`initials`, `avatarUrl`, `onPickClick`, `busy`, labels). Both Profile and (transitionally — CrewSettings doesn't use it after the split) consumers reach for it from the design system.

**Catalog entry:** `molecule.avatarpicker` — scenes for empty (initials), with avatar, busy.

## 6. Crew Settings after the split

```
┌─────────────────────────────────────────┐
│ ←  Crew settings                         │
├─────────────────────────────────────────┤
│  ─── Crew ──────────────────────         │
│  Crew name                               │
│  ┌─────────────────────┐  [Save]         │  (owner only)
│  │ test-crew-1         │                 │
│  └─────────────────────┘                 │
│                                          │
│  Invite code                             │
│  ABCD-1234   [📋] [↗]                    │  (owner only)
│                                          │
│  ─── Members ───────────────────         │
│  🙂  Sebastián  (you)                    │
│  🙂  Maria                       [✕]     │  (owner only)
│  🙂  Juan                        [✕]     │
│                                          │
│  ─── Actions ───────────────────         │
│  [ Switch crew ]                         │
│  [ Leave crew  ]                         │
│                                          │
│  ─── Danger zone ───────────────         │  (owner only)
│  [ Delete crew ]                         │
└─────────────────────────────────────────┘
```

### 6.1 `CrewSettingsScreen` deletions

- Entire "Your profile" section block (current lines 201–238).
- Sign-out button + `signOutError` banner in the Actions section.

### 6.2 `CrewSettingsViewModel` deletions

| State field | Reason |
|---|---|
| `myAvatarUrl: String?` | Read live in `ProfileViewModel` from `AccountReadPort`. |
| `editingMyDisplayName: String` | Moved to `ProfileState`. |
| `isSavingMyDisplayName`, `myDisplayNameError` | Moved. |
| `isUpdatingAvatar`, `updateAvatarError` | Moved. |
| `isSigningOut`, `signOutError` | Moved. |

Intents removed: `MyDisplayNameChanged`, `SaveMyDisplayName`, `MyAvatarPicked`, `SignOut`.

Injections removed: `RenameMemberUseCase`, `UpdateMyAvatarUseCase`, `SignOutUseCase`.

### 6.3 Remove member stub

UI: per-row `IconButton(FrIcons.Close)` on each member row, gated by `state.isOwner && row.accountId != session.accountId`. Tap → `AlertDialog` confirmation (title: "Remove %1$s?", body: "%1$s will lose access to the crew."). Confirm → `CrewSettingsIntent.RemoveMemberConfirmed(accountId)`.

Domain: new stub use case and error leaf.

```kotlin
// :feature:crew/domain/usecase/RemoveMemberUseCase.kt
class RemoveMemberUseCase {
    // TODO(scope = "feature:crew/moderation"): real implementation deferred.
    // See docs/specs/2026-05-21-your-profile-split-design.md §6.3 + tech debt §12.
    suspend operator fun invoke(memberId: AccountId): Result<Unit, CrewError> =
        Result.failure(CrewError.NotImplemented.RemoveMember)
}
```

`CrewError` gains:

```kotlin
sealed interface CrewError {
    // ...existing branches stay
    sealed interface NotImplemented : CrewError {
        data object RemoveMember : NotImplemented
    }
}
```

`CrewErrorToStringKey` maps `CrewError.NotImplemented.RemoveMember` → `CrewStringKey.RemoveMemberNotYetAvailable`. `CrewErrorToStringKeyTest`'s exhaustive `when` covers the new branch automatically.

Koin: `factoryOf(::RemoveMemberUseCase)` added to `crewModule`. Injected into `CrewSettingsViewModel`. Wired to `RemoveMemberConfirmed(...)` — on `Result.Err`, surface via `errorMapper.toStringKey(...)` into a transient toast state field.

## 7. Navigation

`shared/src/commonMain/.../navigation/Route.kt`:

```kotlin
sealed interface Route {
    // ...existing
    data object Profile : Route                       // NEW
    data class CrewSettings(val crewId: String) : Route
    // ...rest unchanged
}
```

`NavGraph.kt`:

```kotlin
composable<Route.Profile> {
    ProfileScreen(onBack = { rootController.popBackStack() })
}
```

`MainScaffold` top app bar:

```kotlin
TopAppBar(
    navigationIcon = {
        IconButton(onClick = { rootController.navigate(Route.Profile) }) {
            val avatar by topBarAvatarVm.state.collectAsStateWithLifecycle()
            FrAvatar(
                imageUrl = avatar.avatarUrl,
                initials = avatar.initials,
                size = Sizes.avatarSm,
                contentDescription = resolve(SharedStringKey.NavProfileCta),
            )
        }
    },
    actions = {
        IconButton(onClick = { rootController.navigate(Route.CrewSettings(activeCrewId.value)) }) {
            Icon(FrIcons.Settings, contentDescription = resolve(SharedStringKey.NavSettingsCta))
        }
    },
)
```

`TopBarAvatarViewModel` lives in `:feature:auth/presentation/topbar/` (auth owns canonical identity reads). Tiny — subscribes to `AccountReadPort.observe(session.accountId)` and exposes `(avatarUrl, initials)`. `:shared/MainScaffold` already imports from feature modules (it composes `FeedScreen`, etc. into the tab graph) so the Koin-injected VM follows the same direction.

## 8. i18n

### 8.1 New `AuthStringKey` entries

If `AuthStringKey` doesn't exist, create it following the per-feature pattern. Add the rows to `feature/auth/src/commonMain/composeResources/values/strings.xml` and `values-es/strings.xml`.

| Key | en | es |
|---|---|---|
| `ProfileTitle` | Your Profile | Tu perfil |
| `ProfileIdentitySection` | Identity | Identidad |
| `ProfileDisplayNameLabel` | Display name | Nombre |
| `ProfileSignedInAsLabel` | Signed in as | Sesión iniciada como |
| `ProfileSave` | Save | Guardar |
| `ProfileChangeAvatarCta` | Tap to change avatar | Toca para cambiar avatar |
| `ProfileAvatarUploading` | Uploading… | Subiendo… |
| `ProfileAccountSection` | Account | Cuenta |
| `ProfileSignOutCta` | Sign out | Cerrar sesión |
| `ProfileSignOutFailed` | Couldn't sign out. Try again. | No se pudo cerrar sesión. Inténtalo de nuevo. |
| `ProfileDisplayNameBlank` | Display name can't be empty. | El nombre no puede estar vacío. |
| `ProfileDisplayNameTooLong` | Display name is too long (max 40). | El nombre es demasiado largo (máx. 40). |
| `ProfileBackendUnavailable` | Network unavailable. Try again. | Sin conexión. Inténtalo de nuevo. |

### 8.2 New `CrewStringKey` entries

| Key | en | es |
|---|---|---|
| `SettingsRemoveMemberCta` | Remove | Quitar |
| `SettingsRemoveMemberConfirmTitle` | Remove %1$s? | ¿Quitar a %1$s? |
| `SettingsRemoveMemberConfirmBody` | %1$s will lose access to the crew. | %1$s perderá acceso al grupo. |
| `RemoveMemberNotYetAvailable` | Removing members isn't available yet — coming soon. | Quitar miembros aún no está disponible — pronto. |

### 8.3 Deleted `CrewStringKey` entries

Remove from sealed interface + strings.xml rows in `feature/crew/src/commonMain/composeResources/values{,-es}/strings.xml`:
- `SettingsMyProfileSection`
- `SettingsMyDisplayNameLabel`
- `SettingsChangeAvatarCta`
- `SettingsAvatarUploading`
- `SettingsSignOutCta`
- `SettingsSignOutFailed`

`SettingsSave` is **kept** — still used by the crew-name save button.

### 8.4 New `SharedStringKey` entry

| Key | en | es |
|---|---|---|
| `NavProfileCta` | Your Profile | Tu perfil |

Used as the `contentDescription` for the leading avatar icon button.

## 9. Tests

### 9.1 New tests

`:feature:auth` `commonTest`:
- `UpdateMyDisplayNameUseCaseTest`
  - validation rejects blank / too-long.
  - session signed out → `ProfileError.Session.SignedOut`.
  - canonical write success + active crew → both ports invoked, returns Ok.
  - canonical write success + no active crew → only `accountWrite` invoked, returns Ok.
  - canonical write failure → `ProfileError.Backend.Unavailable`, cache port NOT invoked.
  - cache write failure → returns Ok (best-effort), `CrashReporter.log` called with `op = "setDisplayName"`.
- `UpdateMyAvatarUseCaseTest`
  - empty bytes → `ProfileError.Validation.EmptyBytes`.
  - upload success → URL returned, cache port invoked.
  - upload failure → error mapped to `ProfileError`, cache port NOT invoked.
  - cache write failure → returns Ok with URL, `CrashReporter.log` with `op = "setAvatarUrl"`.
- `ProfileViewModelTest`
  - first emission seeds `editingDisplayName` from `account.displayName`.
  - DisplayNameChanged → state updates, save not yet invoked.
  - SaveDisplayName busy → success → reset busy, no error.
  - SaveDisplayName error → error mapped to `StringKey` in state.
  - AvatarPicked busy → success → busy cleared, account flow re-emits new avatarUrl.
  - SignOut busy → success → navigation effect emitted.
- `ProfileErrorToStringKeyTest` — exhaustive `when` over `ProfileError`.

`:feature:auth` `androidHostTest`:
- `FirestoreAccountWriterTest` (fake Firestore + fake Storage) — `updateDisplayName` writes `accounts/{uid}.displayName`; `uploadAndSetAvatar` uploads then writes URL; returns URL on success.

`:feature:crew` `androidHostTest`:
- `FirestoreCrewMemberCacheWriterTest` (fake Firestore) — `setDisplayName` writes `crews/{crewId}.members.{uid}.displayName`; `setAvatarUrl` writes `crews/{crewId}.members.{uid}.avatarUrl`.

### 9.2 Updated tests

- `CrewSettingsViewModelTest` — strip the 8 my-profile cases (rename, avatar, sign-out). Add "owner confirms remove member → toast resolves to `RemoveMemberNotYetAvailable`".
- `CrewErrorToStringKeyTest` — add coverage for `CrewError.NotImplemented.RemoveMember`.

### 9.3 Deleted tests

- `:feature:crew/commonTest/.../usecase/RenameMemberUseCaseTest.kt`
- `:feature:crew/commonTest/.../usecase/UpdateMyAvatarUseCaseTest.kt`

### 9.4 Konsist

Existing rule (`:core:domain` allows only stdlib + datetime + coroutines) still passes — the new ports use only those plus existing domain types (`AccountId`, `CrewId`).

## 10. Module / Gradle changes

No new modules. No new dependencies. Module-level changes:

- `:feature:auth` — gains `presentation/profile/`, `domain/usecase/profile/`, `domain/error/ProfileError.kt`, `data/firebase/FirestoreAccountWriter.kt`. The `AvatarStorageDataSource` + `StorageData` expect/actual move in from `:feature:crew`. (Auth is already on `JVM_17` and already pulls the Firebase BOM — no Gradle config change required.)
- `:feature:crew` — gains `data/firebase/FirestoreCrewMemberCacheWriter.kt` + `domain/usecase/RemoveMemberUseCase.kt`. Loses `AvatarStorageDataSource`, `StorageData{.kt,.ios.kt}`, two use cases + tests.
- `:core:domain` — gains `account/AccountWritePort.kt` + `crew/CrewMemberCacheWritePort.kt` + their error types.
- `:core:designsystem` — gains `molecules/FrAvatarPicker.kt` (moved from crew) + a catalog entry.
- `:shared` — gains `Route.Profile`, `TopBarAvatarViewModel`, and the avatar/gear wiring in `MainScaffold`'s `TopAppBar`.

## 11. Migration / followups

- **Members list live reads.** The denormalized `crews/{crewId}.members.{uid}` cache stays. A follow-up spec can migrate `CrewSettingsScreen`'s members list to resolve identity via `AccountReadPort` (same pattern as comments per the 2026-05-20 spec). At that point, the denormalized `displayName`/`avatarUrl` fields can be dropped from `MemberDto` and the cache write becomes structurally unnecessary.
- **Multi-crew fan-out.** When the dev-crew hardcoding (`test-crew-1`) is removed and real multi-crew lands, decide between (a) per-member-list live reads (above) or (b) a fan-out write across all the user's crews. Option (a) is the simpler endpoint.
- **Real Remove member.** Replace the stub with a `CrewRepository.removeMember(crewId, accountId)` write, a Firestore security rule (`owner only, can't remove self`), a confirmation polish, and a notification to the removed user. New `CrewError.RemoveMember.*` branches for the real failure modes.

## 12. Tech debt added by this spec

Two bullets to add to CLAUDE.md "Active tech debt":

1. **Remove member stub.** `RemoveMemberUseCase` in `:feature:crew` returns `CrewError.NotImplemented.RemoveMember` and surfaces "coming soon" in the UI. Real implementation needs: data-layer write (`CrewRepository.removeMember`), Firestore security rule, confirmation flow polish, and a member-removed notification.
2. **Member-cache drift.** Profile rename/avatar uses a two-port non-atomic dual-write. If the cache write fails after the canonical write succeeds, `crews/{activeCrewId}.members.{uid}` drifts from `accounts/{uid}` until the next successful write. Telemetry via `CrashReporter.log("member cache drift", ...)`. Future migration path: members list reads via `AccountReadPort` everywhere — would let us drop the denormalized field entirely.

## 13. References

- [Comments / identity / camera-only design (2026-05-20)](2026-05-20-comments-identity-camera-only-design.md) — established `AccountReadPort` and the "live identity reads" pattern; §2 deferred this surface.
- [FoodRats DDD/KMP design (2026-05-16)](2026-05-16-foodrats-ddd-kmp-design.md) — ports-and-adapters rule (§2.3, §3.1) backing §4 of this spec.
- [Healthy design system v3 (2026-05-19)](2026-05-19-healthy-design-system-design.md) — `FrAvatar`, `Sizes`, semantic colors used in §5.
