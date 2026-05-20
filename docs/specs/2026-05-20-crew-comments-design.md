# Crew comments on meals — design spec

**Status**: ready for plan
**Date**: 2026-05-20
**Author**: Sebastián (with Claude Code)

## 1. Goal

Allow members of a crew to write flat, chronological, immutable comments on every meal published in that crew. Comments are visible only inside `MealDetailScreen`; the Feed shows nothing about comments (no count, no preview). This keeps the Feed listener load identical to today's after the request-minimization refactor.

## 2. Decisions taken during brainstorm

| # | Decision | Choice |
|---|---|---|
| 1 | Visibility | **MealDetail only**, no count or preview on Feed cards. Cero overhead on Feed. |
| 2 | Mutability | **Immutable, nobody can delete.** Same write-once semantics as meals and ratings. |
| 3 | Structure | **Flat, chronological list** (ascending by `createdAtEpochMs`). No threads. |
| 4 | Storage | **Subcollection** `crews/{crewId}/meals/{mealId}/comments/{commentId}` with auto-generated `commentId`. |
| 5 | Author info | **Denormalized at write time** (`authorName`, `authorAvatarUrl` snapshot copied into the comment doc) — same as `MealDto.authorName`. Crew rename does not back-propagate to old comments. |
| 6 | Text bounds | **1..500 chars** trimmed. Empty after trim → reject. |
| 7 | Timestamps | **Relative** ("hace 5 min", "hace 2 h", "ayer"). Absolute date only when older than 7 days. |
| 8 | Listener pattern | One snapshot listener per meal, opened only while `MealDetailScreen` is alive. **No `shareIn(WhileSubscribed)` indirection** — same `ViewModelScope` lifetime, no cross-screen sharing needed. |
| 9 | Send UX | Optimistic? **No.** Show a subtle progress indicator next to the send button, disable input while in flight, surface failures via `FrErrorBanner` above the input. |

## 3. Domain model (`:core:domain`)

### 3.1 Value objects + entity

```kotlin
@JvmInline value class MealCommentId(val value: String)

@JvmInline
value class CommentText private constructor(val value: String) {
    companion object {
        const val MIN_LEN = 1
        const val MAX_LEN = 500
        fun of(raw: String): Result<CommentText, CommentValidationError> {
            val trimmed = raw.trim()
            return when {
                trimmed.length < MIN_LEN -> Result.failure(CommentValidationError.Blank)
                trimmed.length > MAX_LEN -> Result.failure(CommentValidationError.TooLong)
                else -> Result.success(CommentText(trimmed))
            }
        }
    }
}

data class CommentAuthor(
    val accountId: AccountId,
    val displayName: String,
    val avatarUrl: String?,
)

data class MealComment(
    val id: MealCommentId,
    val mealId: MealId,
    val crewId: CrewId,
    val author: CommentAuthor,
    val text: CommentText,
    val createdAt: Instant,
)
```

### 3.2 Errors (sealed interface, data object leaves)

```kotlin
sealed interface CommentValidationError {
    data object Blank : CommentValidationError
    data object TooLong : CommentValidationError
}

sealed interface CommentError {
    sealed interface Read : CommentError {
        data object Unauthorized : Read
        data object Unavailable  : Read
    }
    sealed interface Write : CommentError {
        data object Unauthorized : Write
        data object Blank        : Write
        data object TooLong      : Write
        data object Unavailable  : Write
    }
}
```

### 3.3 Port

```kotlin
interface MealCommentPort {
    fun observe(crewId: CrewId, mealId: MealId): Flow<Result<List<MealComment>, CommentError.Read>>
    suspend fun post(crewId: CrewId, mealId: MealId, text: CommentText): Result<Unit, CommentError.Write>
}
```

The port is consumed by `:feature:feed` (`MealDetailViewModel`); implemented by `:feature:meal`.

## 4. Data layer (`:feature:meal`)

### 4.1 DTO + datasource

```kotlin
@Serializable
data class CommentDto(
    val id: String? = null,             // populated by mapper from snapshot.id
    val authorId: String? = null,
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    val text: String? = null,
    val createdAtEpochMs: Long? = null,
)

class CommentFirestoreDataSource(private val firestore: FirebaseFirestore) {
    fun observe(crewId: CrewId, mealId: MealId): Flow<List<CommentDto>> =
        firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId.value)
            .collection("comments")
            .orderBy("createdAtEpochMs", Direction.ASCENDING)
            .snapshots
            .map { snap -> snap.documents.map { d -> d.data<CommentDto>().copy(id = d.id) } }

    suspend fun create(crewId: CrewId, mealId: MealId, dto: CommentDto) {
        firestore.collection("crews").document(crewId.value)
            .collection("meals").document(mealId.value)
            .collection("comments")
            .add(dto)
    }
}
```

### 4.2 Mapper

```kotlin
fun CommentDto.toDomain(crewId: CrewId, mealId: MealId): Result<MealComment, CommentError.Read> {
    val id = id ?: return Result.failure(CommentError.Read.Unavailable)
    val accountResult = AccountId.of(authorId.orEmpty())
    val accountId = (accountResult as? Result.Ok)?.value
        ?: return Result.failure(CommentError.Read.Unavailable)
    val textResult = CommentText.of(text.orEmpty())
    val txt = (textResult as? Result.Ok)?.value
        ?: return Result.failure(CommentError.Read.Unavailable)
    return Result.success(
        MealComment(
            id = MealCommentId(id),
            mealId = mealId,
            crewId = crewId,
            author = CommentAuthor(accountId, authorName.orEmpty(), authorAvatarUrl),
            text = txt,
            createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs ?: 0L),
        )
    )
}
```

### 4.3 Repository (implements port)

```kotlin
class FirebaseCommentRepository(
    private val ds: CommentFirestoreDataSource,
    private val auth: FirebaseAuth,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : MealCommentPort {

    override fun observe(crewId: CrewId, mealId: MealId): Flow<Result<List<MealComment>, CommentError.Read>> =
        ds.observe(crewId, mealId)
            .map<List<CommentDto>, Result<List<MealComment>, CommentError.Read>> { dtos ->
                Result.success(
                    dtos.mapNotNull { dto -> (dto.toDomain(crewId, mealId) as? Result.Ok)?.value }
                )
            }
            .catch { emit(Result.failure(CommentError.Read.Unavailable)) }
            .flowOn(dispatchers.io)

    override suspend fun post(
        crewId: CrewId,
        mealId: MealId,
        text: CommentText,
    ): Result<Unit, CommentError.Write> = withContext(dispatchers.io) {
        val user = auth.currentUser
            ?: return@withContext Result.failure(CommentError.Write.Unauthorized)
        runCatching {
            ds.create(
                crewId, mealId,
                CommentDto(
                    authorId = user.uid,
                    authorName = user.displayName.orEmpty(),
                    authorAvatarUrl = user.photoURL,
                    text = text.value,
                    createdAtEpochMs = clock.now().toEpochMilliseconds(),
                ),
            )
            Result.success(Unit)
        }.getOrElse { Result.failure(CommentError.Write.Unavailable) }
    }
}
```

Koin binding registered in `mealModule` and exposed as `MealCommentPort`.

## 5. UI layer (`:feature:feed`, MealDetailScreen)

### 5.1 ViewModel state additions

```kotlin
data class MealDetailState(
    // …existing fields…
    val comments: List<MealComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentInput: String = "",
    val isPosting: Boolean = false,
    val commentReadError: CommentError.Read? = null,
    val commentWriteError: CommentError.Write? = null,
)
```

### 5.2 Intents

```kotlin
sealed interface MealDetailIntent {
    // …existing intents…
    data class CommentInputChanged(val value: String) : MealDetailIntent
    data object PostComment : MealDetailIntent
}
```

### 5.3 ViewModel wiring

- On `init`: subscribe to `commentPort.observe(crewId, mealId)` → update `comments` + `commentsLoading` / `commentReadError`.
- On `PostComment`:
  1. Validate `CommentText.of(state.commentInput)` — surface `Blank` / `TooLong` to `commentWriteError`.
  2. Set `isPosting = true`, clear `commentWriteError`.
  3. `commentPort.post(crewId, mealId, validatedText)`.
  4. On success: clear `commentInput`, `isPosting = false`. Listener will reflect the new comment automatically.
  5. On failure: set `commentWriteError`, `isPosting = false`. Keep `commentInput` so the user can retry.

### 5.4 Composables

- New molecule `FrCommentRow(displayName, avatarUrl, text, relativeTimestamp)` in `:core:designsystem/molecules/`. Avatar (FrAvatar) on the left, two-line content (name + relative ts on first line, text on second) on the right.
- In `MealDetailScreen`, below the existing meal content, render:
  - Section title `resolve(MealStringKey.CommentsTitle)`
  - Loading state (small spinner) when `commentsLoading && comments.isEmpty()`
  - Empty state (`FrEmptyState`) when `!commentsLoading && comments.isEmpty() && commentReadError == null` — text from `MealStringKey.CommentsEmpty`
  - Otherwise: `LazyColumn` (inside the existing scroll? or `Column` of `FrCommentRow`s — depends on existing layout; pick simplest)
  - Below the list: an `FrTextField` bound to `commentInput` + a send `IconButton` (paper-plane / send icon from FrIcons; if missing, use existing `FrIcons.Camera` placeholder convention) disabled when `isPosting` or `commentInput.isBlank()`.
  - `FrErrorBanner` above the input when `commentWriteError != null`.

### 5.5 Relative timestamp util

```kotlin
fun Instant.toRelativeKey(now: Instant): StringKey = when (val secs = (now - this).inWholeSeconds) {
    in 0..59         -> MealStringKey.CommentsRelativeJustNow
    in 60..3599      -> MealStringKey.CommentsRelativeMinutes // arg: secs / 60
    in 3600..86399   -> MealStringKey.CommentsRelativeHours   // arg: secs / 3600
    else             -> MealStringKey.CommentsRelativeDays    // arg: secs / 86400, falls back to absolute date after 7d
}
```

Lives in `:feature:feed/presentation/components/`. Tested in `commonTest`.

## 6. i18n

Add to `MealStringKey` (in `:feature:meal/i18n/`):

- `CommentsTitle` — "Comentarios" / "Comments"
- `CommentsEmpty` — "Aún no hay comentarios. Sé el primero." / "No comments yet. Be the first."
- `CommentsInputPlaceholder` — "Escribe un comentario…" / "Write a comment…"
- `CommentsSendCta` — "Enviar" / "Send"
- `CommentsRelativeJustNow` — "ahora" / "just now"
- `CommentsRelativeMinutes` — "hace %1$d min" / "%1$d min ago"
- `CommentsRelativeHours` — "hace %1$d h" / "%1$d h ago"
- `CommentsRelativeDays` — "hace %1$d d" / "%1$d d ago"
- `CommentsErrorBlank` — "El comentario está vacío" / "Comment is empty"
- `CommentsErrorTooLong` — "Máximo 500 caracteres" / "Max 500 characters"
- `CommentsErrorUnavailable` — "No se pudo publicar. Intenta de nuevo." / "Couldn't post. Try again."
- `CommentsErrorUnauthorized` — "No tienes permiso para comentar" / "You can't comment on this meal"

`CommentError.toStringKey()` extension in `:feature:feed/presentation/` (since presentation owns the error→i18n mapping per project convention).

## 7. Security rules

```
match /crews/{crewId}/meals/{mealId}/comments/{commentId} {
  allow read: if request.auth != null
              && request.auth.uid in get(/databases/$(database)/documents/crews/$(crewId)).data.memberIds;

  allow create: if request.auth != null
                && request.auth.uid in get(/databases/$(database)/documents/crews/$(crewId)).data.memberIds
                && request.auth.uid == request.resource.data.authorId
                && request.resource.data.text is string
                && request.resource.data.text.size() >= 1
                && request.resource.data.text.size() <= 500
                && request.resource.data.createdAtEpochMs is int
                && request.resource.data.createdAtEpochMs <= request.time.toMillis() + 60000
                && request.resource.data.createdAtEpochMs >= request.time.toMillis() - 60000;

  // No update, no delete — comments are immutable and nobody can delete (decision #2).
}
```

The ±60 s window on `createdAtEpochMs` prevents clients from back-dating or future-dating comments.

## 8. Module impact

| Module | What changes |
|---|---|
| `:core:domain` | New: `MealComment`, `MealCommentId`, `CommentText`, `CommentAuthor`, `CommentValidationError`, `CommentError`, `MealCommentPort`. Konsist must continue to pass (no forbidden imports). |
| `:feature:meal` | New: `CommentDto`, `CommentFirestoreDataSource`, `CommentMapper`, `FirebaseCommentRepository`. New string keys + en/es strings. Koin: `singleOf(::CommentFirestoreDataSource)`, `single<MealCommentPort> { FirebaseCommentRepository(...) }`. |
| `:feature:feed` | `MealDetailViewModel` + `MealDetailScreen` + state types extended. New `FrCommentRow` molecule (or place in `:feature:feed/presentation/components/` since it depends on the i18n relative-timestamp resolver). `CommentError.toStringKey()` mapper + test. |
| `:core:designsystem` | If `FrCommentRow` is generic enough (avatar + name + text + ts), it lives here. Catalog entry required. Otherwise put it in `:feature:feed/presentation/components/`. |
| `firestore.rules` | New `match /crews/{crewId}/meals/{mealId}/comments/{commentId}` block. |

## 9. Testing

- `CommentTextTest` (commonTest, `:core:domain`): boundary cases — blank, trim-blank, 1 char, 500 chars, 501 chars, normal.
- `CommentMapperTest` (commonTest, `:feature:meal`): happy path, missing id, missing authorId, blank text dto → all error/null variants.
- `FirebaseCommentRepositoryTest` (commonTest, `:feature:meal`): observe success + failure, post success + failure, dispatcher hop.
- `CommentErrorToStringKeyTest` (commonTest, `:feature:feed`): exhaustive `when` over the sealed tree.
- `MealDetailViewModelTest` (commonTest, `:feature:feed`): existing + new `PostComment` happy path, validation error path, write error path. Use Turbine + UnconfinedTestDispatcher per project pattern.
- `RelativeTimestampTest` (commonTest, `:feature:feed`): bucketing — 0/59/60/3599/3600/86399/86400/seven days.
- Konsist: `:core:domain` still passes the import rule (`MealCommentPort` only imports `kotlin.stdlib`, `kotlinx-datetime`, `kotlinx-coroutines-core`).

## 10. Out of scope (explicit)

- Comment threads / replies — flat list only.
- Edit / delete — none, by user decision.
- Comment count or preview in Feed — none, by user decision.
- Notifications when a crew member comments — out (could be a follow-up, but not now).
- Mentions (@username) — out.
- Media (images/GIFs) in comments — out.

## 11. Rollout

1. Land code + tests on a feature branch (12 commits expected, plan in `docs/superpowers/plans/`).
2. Deploy security rules: `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`.
3. Install on device, smoke test: open a meal detail → post a comment → confirm in Firebase Console that `crews/test-crew-1/meals/{mid}/comments/{auto}` exists.
4. Sign in as another crew member, open the same meal, confirm the comment is visible without manual refresh.
