# Live comment identity, image cache, and camera-only capture — design spec

**Status**: ready for plan
**Date**: 2026-05-20
**Author**: Sebastián (with Claude Code)
**Supersedes (partially)**: [`2026-05-20-crew-comments-design.md`](2026-05-20-crew-comments-design.md) §2 row 5 (author info denormalization)

## 1. Goals

Three related changes that share a single touch surface (`MealDetailScreen` + `CaptureMealScreen`):

1. **Comments show the author's current display name.** Default is the Google account name; if the user renames themselves in Crew Settings → "Your profile", every past and future comment reflects the new name immediately.
2. **Comments show the author's uploaded avatar** when one exists. Only avatars uploaded to our Firebase Storage count — never the Google `photoURL`. Without an upload, the row falls back to initials.
3. **Meal capture is camera-only and direct.** The gallery picker is removed. Tapping the capture FAB launches the camera immediately, bypassing the current intermediate viewfinder/gallery screen, and routes straight to compose-plate on success.

Image cache configuration changes ride along because (2) introduces a second image consumer (avatars) and the current `ImageLoader` has no persistent cache, so cold-start refetches every photo.

## 2. Out of scope (deferred)

- **Per-crew aliases.** One canonical `displayName` per account.
- **Camera-only avatar.** The avatar uploader in `CrewSettingsScreen` keeps its gallery picker. Taking a selfie to set an avatar is awkward UX.
- **Backfilling existing comment documents.** Old comments lose their stamped `authorName` / `authorAvatarUrl` when the DTO fields are removed; identity is resolved live from `accounts/{uid}`. No data migration job.
- **Out-of-crew Settings screen.** Identity edits stay inside Crew Settings → "Your profile".
- **Forensic `authorUsernameAtPostTime`.** Considered (the Twitter-X "rename to impersonate" mitigation) but YAGNI for a closed-group app. Flagged here so a future moderation feature can revisit.
- **`FrIcons.GalleryImport` removal from the design system.** The icon is no longer referenced by `CaptureMealScreen`, but other consumers may exist; pruning it is a design-system concern.

## 3. Revision to crew-comments §2 row 5

| # | Field | Old decision | New decision |
|---|---|---|---|
| 5 | Author info | **Denormalized at write time** (`authorName`, `authorAvatarUrl` snapshot in the comment doc); crew rename does not back-propagate. | **Resolved live at render.** Comment doc stores only `authorId`. UI joins against the canonical `accounts/{uid}` doc per unique author. Renames + new avatar uploads are immediately visible on past comments. |

**Rationale (research summary).** Discord, Slack, Instagram, Twitter/X all resolve identity live; only typed `@mention` text is frozen. The Firebase blog's denormalization guidance treats it as an optimization for *immutable, hot-path read* fields — author identity is neither. At our scale (tens of users per crew, ≤30 comments per meal), the read fan-out is negligible (≤8 unique authors per meal × 1 doc each), and the design eliminates the rename bug, simplifies a future GDPR erasure path (delete `accounts/{uid}` → every old comment renders "Deleted user"), and removes one entire snapshot field that can drift.

## 4. Data model

### 4.1 `accounts/{uid}` is the single canonical identity record

Two fields matter for comments:

| Field | Today | After |
|---|---|---|
| `displayName: String` | Initialized to literal `"Rat"` on first sign-in; never updated thereafter. | Initialized from `FirebaseAuth.currentUser.displayName` on first sign-in; updated by Crew Settings rename. |
| `avatarUrl: String?` | `null` until the user uploads via `updateMyAvatar`. **Never populated from Google.** | Unchanged. `null` ⇒ render initials. |

### 4.2 `CommentDto` drops snapshot fields

`feature/meal/data/firebase/CommentDto.kt`:

```kotlin
@Serializable
data class CommentDto(
    val authorId: String = "",
    val text: String = "",
    val createdAtEpochMs: Long = 0L,
)
```

Removed: `authorName`, `authorAvatarUrl`. Old documents parse cleanly — kotlinx-serialization ignores extra fields by default — they simply lose the unused snapshot values, which is fine because the read path resolves identity live.

### 4.3 `MealComment` collapses `CommentAuthor` to `AccountId`

`core/domain/meal/MealComment.kt`:

```kotlin
data class MealComment(
    val id: MealCommentId,
    val mealId: MealId,
    val crewId: CrewId,
    val authorId: AccountId,          // was: author: CommentAuthor
    val text: CommentText,
    val createdAt: Instant,
)
```

The `CommentAuthor` value object is removed entirely — it was just a triplet wrapper for `(accountId, displayName, avatarUrl)`, and only `accountId` survives the move.

## 5. New port: `AccountReadPort`

Lives in `:core:domain` next to `MealReadPort`. Reactive — exposing `Flow<Account?>` lets the open Meal Detail screen update instantly when an author renames themselves elsewhere.

```kotlin
// core/domain/account/AccountReadPort.kt
interface AccountReadPort {
    fun observe(id: AccountId): Flow<Account?> // null = doc missing or deleted
}
```

`Account` already exists in `feature:auth/domain/model/Account.kt` with fields `(id, handle, displayName, email, avatarUrl)` — it stays put; the port references it via an interface so the domain layer doesn't import from a feature.

**Wait — `:core:domain` cannot import from `:feature:auth`.** Concretely: move `Account` to `:core:domain/account/Account.kt` (it's already a vendor-free domain type) and have `:feature:auth` consume it. Cheap refactor; matches the cross-context-reads-via-domain-ports rule already enforced by `KonsistRulesTest`.

**Implementation** in `feature:auth/data/firebase/FirestoreAccountReadDataSource.kt`:
- Subscribes to `firestore.collection("accounts").document(uid).snapshots`.
- Maps `AccountDto` → `Account` (existing mapper).
- Emits `null` for missing docs (Firestore exposes this as `snapshot.exists == false`).
- Bound to `AccountReadPort` in `authModule` (Koin).

**Allowed-imports check.** The port itself uses only `kotlin.stdlib`, `kotlinx-coroutines-core`, and the existing `AccountId` value object. Konsist rule stays green.

## 6. Write paths

### 6.1 Sign-in (`FirebaseAuthDataSource.ensureAccountDoc`)

Today (line 36–42):
```kotlin
val dto = if (snap.exists) snap.data<AccountDto>()
    else AccountDto(id = uid, handle = uid.take(8), displayName = "Rat", createdAtEpochMs = 0L)
```

After:
```kotlin
val googleName = firebaseAuth.currentUser?.displayName.orEmpty()
val dto = when {
    !snap.exists -> AccountDto(
        id = uid,
        handle = uid.take(8),
        displayName = googleName,
        avatarUrl = null,
        createdAtEpochMs = clock.now().toEpochMilliseconds(),
    )
    // Self-heal: legacy "Rat" placeholder + any user whose displayName never got initialized.
    snap.data<AccountDto>().displayName.let { it.isBlank() || it == "Rat" } -> {
        val existing = snap.data<AccountDto>()
        existing.copy(displayName = googleName).also { firestore.set("accounts/$uid", it, merge = true) }
    }
    else -> snap.data<AccountDto>()
}
```

This is the established "self-healing migration runs on app start" pattern (commit `68622d5`). `avatarUrl` is **never** touched here — only `updateMyAvatar` writes it.

Edge case: a Google account literally named "Rat" would be self-healed by overwriting their `displayName` with the identical string — a no-op, accepted. We are not tracking a separate "has user edited?" flag for this rare collision.

### 6.2 Rename (`CrewFirestoreDataSource.renameMember`)

Today (line 195–203) only updates `crews/{crewId}.members.{uid}.displayName`. After: writes both, batched.

```kotlin
suspend fun renameMember(crewId: CrewId, accountId: AccountId, newDisplayName: String) {
    firestore.batch {
        update("accounts/${accountId.value}", "displayName" to newDisplayName)
        update("crews/${crewId.value}", "members.${accountId.value}.displayName" to newDisplayName)
    }.commit()
}
```

The crew member entry stays — it's a denormalized cache used by member lists (where the read pattern is "load the crew, get all members in one doc"). The canonical source is `accounts/{uid}`.

### 6.3 Comment write (`FirebaseCommentRepository.postComment`)

Today reads `FirebaseAuth.currentUser` to stamp `authorName` + `authorAvatarUrl`. After: stamps `authorId` only.

```kotlin
val dto = CommentDto(
    authorId = user.uid,
    text = text.value,
    createdAtEpochMs = clock.now().toEpochMilliseconds(),
)
```

### 6.4 Firestore security rules

No rule changes required:
- `accounts/{uid}` already allows owner writes (existing `updateMyAvatar` path).
- Comment rules already validate `authorId == request.auth.uid`; removing fields from the validated set is safe.

## 7. Read path

### 7.1 Resolver (`MealDetailViewModel`)

Two upstream flows are joined:

- `comments: Flow<List<MealComment>>` — existing.
- `authors: Flow<Map<AccountId, Account?>>` — derived as follows:
  - Compute `Set<AccountId>` from `comments` via `map { it.map(MealComment::authorId).toSet() }.distinctUntilChanged()`.
  - For each newly observed id, lazily start an `accountReadPort.observe(id)` flow and remember it (per-ViewModel `mutableMapOf<AccountId, Flow<Account?>>`).
  - `combine(idSet, perIdFlows) { ids, flows -> Map<AccountId, Account?> }` and `distinctUntilChanged()` to absorb churn.

The final state stream:

```kotlin
data class CommentRow(
    val id: MealCommentId,
    val displayName: String,    // "Deleted user" if authors[id] == null
    val avatarUrl: String?,     // null when uploaded avatar absent OR author deleted
    val text: String,
    val relative: RelativeTimestamp,
    val loading: Boolean,       // true between comment-emit and first author-resolve
    val isDeleted: Boolean,     // true when authors[id] == null
)
```

`combine(comments, authors) { cs, m -> cs.map { c -> toRow(c, m[c.authorId]) } }`. The first comments emission with a not-yet-resolved author renders `loading = true` for that row (per-row skeleton, not whole-list gate).

### 7.2 Listener dedup

Comments by the same author share a single `accounts/{uid}` listener. Implementation can use `flow { ... }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000))` per id, but the simpler pattern is one cold flow per id kept in a `mutableMapOf` — `combine` subscribes to all of them and unsubscribes when the ViewModel clears.

### 7.3 In-screen freshness

Because flows are reactive, an active Meal Detail listening to author X will re-render automatically when X renames themselves in Crew Settings (Crew Settings writes to `accounts/{uid}`, the snapshot listener fires). No manual refresh.

## 8. UI

### 8.1 `FrAvatar` atom extension

`core/designsystem/atoms/FrAvatar.kt`:

```kotlin
@Composable
fun FrAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.avatarMd,
    imageUrl: String? = null,                  // NEW
    contentDescription: String? = null,
)
```

Behaviour:
- `imageUrl == null || imageUrl.isBlank()` → existing initials-circle behaviour.
- Otherwise: `coil3.compose.AsyncImage` clipped to `CircleShape`, sized to `size`, using the existing singleton `ImageLoader` (so it benefits from the new disk cache from §9). `placeholder` and `error` slots both render the initials variant — so a slow network shows initials → photo, and a failed load stays on initials.
- Same `clearAndSetSemantics { }` rule. The label next to the avatar carries the meaning.

### 8.2 `FrCommentRow` molecule

`feature/feed/presentation/components/FrCommentRow.kt`:

- Pass `avatarUrl` through to `FrAvatar(imageUrl = avatarUrl, initials = ...)` (it currently accepts the prop but discards it).
- Add `loading: Boolean = false` — when `true`, render a shimmer/skeleton in place of the avatar and `…` in the name slot. Body text + timestamp still render.
- Add `isDeleted: Boolean = false` — when `true`, render neutral `?` initials and resolve `FeedStringKey.DeletedAuthor` in the name slot. Body text is preserved (it was the author's content; deleting the author shouldn't censor the comment).

### 8.3 i18n additions

`FeedStringKey`:
- `DeletedAuthor` → `"Deleted user"` / `"Usuario eliminado"`

No other user-visible strings change.

### 8.4 Catalog

`core/designsystem` catalog story for `atom.avatar` gains scenes:
- initials only (existing)
- image loaded
- image loading (placeholder shows initials)
- image error (fallback shows initials)

`molecule.commentrow` story gains scenes: loaded, loading, deleted-author.

## 9. Coil cache configuration

### 9.1 Relocate + rename

Move `installFeedImageLoader()` (currently in `feature/feed/data/image/ImageLoaderSetup.kt`) to `core/data/image/ImageLoaderSetup.kt` and rename to `installImageLoader()`. Two callers update their import:

- `FoodRatsApplication.onCreate()` (Android)
- `MainViewController()` (iOS)

Avatars and meal photos share the same loader/cache. Coil keys by URL, so no collision.

### 9.2 Builder additions

```kotlin
fun installImageLoader() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.20).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDirectory(context))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .build()
    }
}
```

- Memory cache: 20% of device max (Coil's recommended default; documenting it explicitly so future readers know it's intentional).
- Disk cache: 50 MB cap. Avatars are KB-sized; meal photos dominate. Revisit if real-world traffic shows churn.

### 9.3 Cache directory expect/actual

`core/data/image/`:

```kotlin
internal expect fun imageCacheDirectory(context: PlatformContext): okio.Path
```

- **Android**: `context.cacheDir.resolve("image_cache").toOkioPath()` — OS-managed cache dir, auto-purged under storage pressure.
- **iOS**: `NSFileManager.defaultManager.URLForDirectory(NSCachesDirectory, ...)/image_cache` — same OS semantics.

### 9.4 Dependencies

`gradle/libs.versions.toml`: add `coil3-disk-cache` if not already pulled by the umbrella `coil3` artifact (verify during implementation; in Coil 3 the disk-cache types ship in `coil3` core but okio is a direct dependency that may need pinning).

### 9.5 Call sites

`AsyncImage` consumers (`FrFeedMealCard`, the new `FrAvatar` image variant) need zero changes — caching is fully transparent.

## 10. Capture flow

### 10.1 Phantom orchestrator route (recommended)

`Route.CaptureMeal` stays in the nav graph, but `CaptureMealScreen.kt` shrinks to a UI-less orchestrator:

```kotlin
@Composable
fun CaptureMealScreen(
    onCaptured: () -> Unit,
    onCancelled: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: CaptureMealViewModel = koinViewModel(),
) {
    val picker = rememberImagePickerKMP()
    LaunchedEffect(Unit) { picker.launchCamera() }

    LaunchedEffect(picker.result) {
        when (val r = picker.result) {
            is ImagePickerResult.Success -> {
                val photo = r.first ?: return@LaunchedEffect
                val bytes = photo.asSource().readByteArray().resizeForUpload()
                vm.onIntent(CaptureMealIntent.PhotoTaken(bytes))
                picker.reset()
            }
            is ImagePickerResult.Dismissed -> { picker.reset(); onCancelled() }
            is ImagePickerResult.Error -> {
                println("[CaptureMealScreen] picker error: ${r.exception.message}")
                picker.reset()
                onCancelled()
            }
            is ImagePickerResult.Loading, is ImagePickerResult.Idle -> Unit
        }
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                CaptureMealEffect.NavigateToCompose -> onCaptured()
                CaptureMealEffect.OpenAppSettings -> onOpenSettings()
            }
        }
    }
}
```

`NavGraph.kt`:

```kotlin
composable<Route.CaptureMeal> {
    CaptureMealScreen(
        onCaptured = {
            controller.navigate(Route.ComposePlate) {
                popUpTo<Route.CaptureMeal> { inclusive = true }
            }
        },
        onCancelled = { controller.popBackStack() },
        onOpenSettings = { /* existing */ },
    )
}
```

User-perceived flow: tap capture FAB → camera → compose-plate, with no intermediate UI.

### 10.2 Deletions

- `CaptureMealIntent.PickFromGallery` and `CaptureMealEffect.OpenGalleryPicker` (the contract surface shrinks).
- `feature/meal/presentation/components/CaptureFrame.kt` (only used by the deleted UI).
- `core/designsystem/templates/FrCaptureLayout.kt` if this was its only consumer (verify during implementation; if other features use it, leave it).
- `DailyEmoteBadge`: was a UX flourish on the deleted viewfinder. **Sub-decision deferred to implementation** — relocate to ComposePlate as a header, or drop. The badge is not load-bearing; if there's no obvious home in ComposePlate, drop it.
- `FrIcons.GalleryImport` reference in `CaptureMealScreen` (the icon itself stays in the design system).
- `MealStringKey` entries for "Open gallery" / "Pick from gallery" / related a11y, plus matching `values/strings.xml` + `values-es/strings.xml` rows. `MealErrorMapper`'s exhaustiveness test must still pass — verify by running `:feature:meal:testAndroidHostTest` after the pruning.

### 10.3 Permissions / errors

`CaptureMealViewModel` keeps its permission-rationale + error state if those branches survive after the gallery removal. If after pruning the VM holds no meaningful state, inline its work into the screen and delete it.

### 10.4 Native dependencies (unchanged)

- `feature/meal/build.gradle.kts`: `material-icons-extended` exclude on `imagepickerkmp` stays (camera path still needs it transitively).
- iOS: `CoreLocation.framework` manual Xcode link step stays.

## 11. Migration / self-healing summary

| What | Strategy | Where |
|---|---|---|
| `accounts/{uid}.displayName == "Rat"` (legacy) | On next sign-in, overwrite with `firebaseAuth.currentUser.displayName`. | §6.1 |
| `accounts/{uid}` missing for a returning user | Already covered: `ensureAccountDoc` creates it. | §6.1 |
| Old `CommentDto` documents with `authorName` / `authorAvatarUrl` fields | Fields are silently ignored on read; identity resolved live from `accounts/{uid}`. No batch job. | §4.2 |
| Comments by an author whose `accounts/{uid}` is missing (e.g., test data, future deletion) | Render "Deleted user" + neutral initials. | §7.1, §8.2 |

## 12. Testing

- **`AccountReadPortTest` (commonTest)**: fake `FirestoreSnapshot` flow → verify `Flow<Account?>` emits `null` on missing, mapped `Account` on present, and re-emits on doc updates.
- **`MealDetailViewModelTest` (commonTest)**:
  - One author, comment list emits → row shows resolved identity.
  - Two comments same author → `combine` resolves with one shared listener (verify via fake count).
  - Comment for unknown author → row marked `isDeleted = true`, label resolves to `FeedStringKey.DeletedAuthor`.
  - Author rename mid-flight → row updates without comments re-emitting.
- **`FirebaseAuthDataSourceTest`**: existing test for ensureAccountDoc gains a "legacy `Rat` self-heals" case.
- **`FirebaseCrewRepositoryTest`**: `renameMember` writes to both `accounts/{uid}` and the crew member map (fake firestore — assert two writes in one batch).
- **`FrAvatarTest` (androidHostTest, Compose UI)**: with `imageUrl == null` renders initials; with `imageUrl != null` renders `AsyncImage`. (Coil's loaded-state can't be asserted in Robolectric without a fake fetcher — pin to "node count" assertions.)
- **`FrCommentRowTest` (androidHostTest)**: `loading` → skeleton; `isDeleted` → resolves `DeletedAuthor` string; both `false` → resolved identity rendered.
- **`MealErrorMapperRateTest` (existing)**: must still pass after pruning gallery-related error → string-key branches.
- **Capture flow**: no UI to test (orchestrator). The picker-result handling is exercised indirectly by `CaptureMealViewModelTest`'s `PhotoTaken` intent assertions.

## 13. Open questions / sub-decisions deferred to implementation

1. **`DailyEmoteBadge` placement** — relocate to ComposePlate or drop entirely (§10.2).
2. **`CaptureMealViewModel` survival** — keep slim VM for permission/error state, or inline into screen (§10.3).
3. **`Account` move from `feature:auth/domain/model` to `core:domain/account`** — confirm no awkward imports surface during implementation (§5).
4. **Coil disk-cache directory naming** — `image_cache` is a reasonable default; revisit if app-wide cache discoverability matters later (§9.3).

## 14. References

- [Crew comments design spec (2026-05-20)](2026-05-20-crew-comments-design.md) — original comment model; this spec revises §2 row 5.
- [FoodRats DDD/KMP design (2026-05-16)](2026-05-16-foodrats-ddd-kmp-design.md) — base architecture; ports-and-adapters rule used in §5.
- [Server request minimization (2026-05-20)](2026-05-20-server-request-minimization-design.md) — listener lifecycle context for §7.
- Discord 2023 username overhaul — split unique handle from free-form display name, render live (discord.com/blog/usernames).
- Firebase blog: "Denormalizing your data is normal" — caveat about denormalizing mutable fields.
- Coil 3 docs — disk cache is opt-in on multiplatform; resolve the exact builder symbol during implementation.
