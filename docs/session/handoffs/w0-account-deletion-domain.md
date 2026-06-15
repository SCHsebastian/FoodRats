# Handoff — `w0-account-deletion-domain` → data + presentation

Exact domain signatures the next two tasks implement against. All landed and verified green.

## Port (`:core:domain`, vendor-free — DO NOT change the surface)

`core/domain/src/commonMain/.../account/AccountDeletionPort.kt`

```kotlin
interface AccountDeletionPort {
    suspend fun requestDeletion(
        accountId: AccountId,
        confirmation: String,
    ): Result<Unit, AccountDeletionError>   // es.schsebastian.foodrats.core.domain.result.Result
}

sealed interface AccountDeletionError {
    sealed interface Validation : AccountDeletionError {
        data object PhraseMismatch : Validation          // server "failed-precondition"
    }
    sealed interface Backend : AccountDeletionError {
        data object NotImplemented : Backend             // dead-but-kept one release (stub era)
        data object Unavailable : Backend                // retryable; session still valid
    }
    sealed interface Deletion : AccountDeletionError {
        data object OwnerReassignFailed : Deletion       // server "aborted"; account NOT deleted
    }
}
```

Semantics: synchronous cascade. `Ok` only when the server reports completion. No pending-deletion
marker. `confirmation` is forwarded for server re-validation; the adapter derives the caller uid
server-side (request carries NO accountId — a client may only delete itself).

### Adapter contract for `w0-account-deletion-data`
HttpsError-code → error mapping (string-match on `Throwable.message`, mirror
`FirebaseImageUrlResolver.toImageUrlError()`):

| HttpsError code | → AccountDeletionError |
|---|---|
| `failed-precondition` | `Validation.PhraseMismatch` |
| `aborted` | `Deletion.OwnerReassignFailed` |
| `unauthenticated` / `internal` / anything else | `Backend.Unavailable` |

## Feature error tree (`:feature:auth`)

`feature/auth/src/commonMain/.../domain/error/ProfileError.kt`

```kotlin
sealed interface Delete : ProfileError {
    data object PhraseMismatch : Delete
    data object NotImplemented : Delete          // dead-but-kept one release
    data object Unavailable : Delete
    data object OwnerReassignFailed : Delete      // replaced OwnerOfActiveCrew
}

internal fun AccountDeletionError.toProfileError(): ProfileError = when (this) {
    AccountDeletionError.Validation.PhraseMismatch     -> ProfileError.Delete.PhraseMismatch
    AccountDeletionError.Backend.NotImplemented        -> ProfileError.Delete.NotImplemented
    AccountDeletionError.Backend.Unavailable           -> ProfileError.Delete.Unavailable
    AccountDeletionError.Deletion.OwnerReassignFailed  -> ProfileError.Delete.OwnerReassignFailed
}
```

Mapper (already updated, lives in presentation):
`ProfileError.Delete.OwnerReassignFailed -> AuthStringKey.DeleteAccountErrorOwnership`.

## Analytics leaf (`:core:domain`)

`core/domain/src/commonMain/.../analytics/AnalyticsEvent.kt`

```kotlin
data object AccountDeleted : AnalyticsEvent {
    override val name = "account_deleted"
    override val params = emptyMap<String, AnalyticsValue>()
}
```

For `w0-account-deletion-presentation`: fire once in `ProfileViewModel.doDeleteAccount()` AFTER the
use case returns `Ok` and BEFORE `analytics.setUserId(null)` / `analytics.resetData()`. Already
registered in `AnalyticsTaxonomyTest.allEvents`.

## Verify command (both pass on this branch)
`./gradlew :core:domain:testAndroidHostTest :feature:auth:testAndroidHostTest`
