# feature-auth repair report (2026-06-17)

## rename (pairs with core-domain-01): SessionError.FirebaseUnavailable -> ProviderUnavailable
- `data/repository/AuthSignOutPort.kt`: updated the KDoc reference (`[SessionError.FirebaseUnavailable]` -> `[SessionError.ProviderUnavailable]`) and the mapping arm (GoogleSignIn/EmailPassword leaves now collapse to `SessionError.ProviderUnavailable`).
- Grepped the whole `feature/auth` tree for `FirebaseUnavailable` afterward: zero remaining. No test referenced it.
- Risk: this compiles only once the core-domain agent renames the `SessionError` declaration. If that rename lands, fine; if it does not, this is a broken reference. Build should watch the join with core-domain-01.

## core-domain-02 + auth-04: dead-leaf removal (NotImplemented)
- Confirmed dead first: grepped every `.kt` in `feature/auth` + `core/domain` (and whole repo). Nothing PRODUCES `AccountDeletionError.Backend.NotImplemented` or `ProfileError.Delete.NotImplemented`. `FirebaseAccountDeletionPort.toAccountDeletionError()` maps `failed-precondition -> Validation.PhraseMismatch`, `aborted -> Deletion.OwnerReassignFailed`, else `Backend.Unavailable` — never NotImplemented. Safe to remove.
- `core/domain/.../account/AccountDeletionPort.kt`: removed `data object NotImplemented : Backend` leaf + its KDoc.
- `feature/auth/.../domain/error/ProfileError.kt`: removed `data object NotImplemented : Delete` leaf and the mapping arm `AccountDeletionError.Backend.NotImplemented -> ProfileError.Delete.NotImplemented`.
- `feature/auth/.../presentation/profile/ProfileErrorToStringKey.kt`: removed the `ProfileError.Delete.NotImplemented -> ...` arm.
- `feature/auth/.../i18n/AuthStringKey.kt`: removed `DeleteAccountErrorNotImplemented` enum entry + its generated-resource import.
- en/es strings: removed `auth_delete_account_error_not_implemented` from both `values/strings.xml` and `values-es/strings.xml`.
- Tests: removed `delete_not_implemented_maps_to_not_implemented_key` from `ProfileErrorToStringKeyTest.kt`. No reference existed in `FirebaseAccountDeletionPortTest` / `DeleteMyAccountUseCaseTest` / `ProfileViewModelTest` (verified by grep).
- Risk: exhaustive `when` over `AccountDeletionError` lives in BOTH modules; core-domain agent owns the declaration removal but I removed the leaf here per assignment. The two consuming `when`s (ProfileError mapper) are now correct. If the core-domain agent ALSO edits AccountDeletionPort.kt there will be a conflict — I edited it as instructed (assigned to me).

## auth-01 (HIGH-VALUE BUG): locale-correct delete confirmation phrase
- Root cause: VM computed the expected phrase from a hardcoded English `DELETE_VERB`, but the ES screen displays "BORRAR <name>", so Spanish users could never match it.
- `presentation/profile/ProfileViewModel.kt`:
  - `ProfileIntent.DeleteDialogConfirm` is now `data class DeleteDialogConfirm(val expectedPhrase: String)` (was `data object`).
  - `handle()` arm: `is ProfileIntent.DeleteDialogConfirm -> doDeleteAccount(intent.expectedPhrase)`.
  - `doDeleteAccount(expected: String)` uses the carried phrase; removed `expectedDeletePhrase()` and the `DELETE_VERB` companion constant entirely.
- `presentation/profile/DeleteAccountScreen.kt`: `onDialogConfirm` is now `(expectedPhrase: String) -> Unit`; the dialog's `onConfirm` invokes it with the screen's `expectedPhrase`.
- `presentation/profile/ProfileScreen.kt`: computes `expectedPhrase = resolve(AuthStringKey.DeleteAccountPhraseTemplate, displayName).trim()` (same template + display name the screen shows), passes it to `DeleteAccountScreen`, and the confirm callback forwards the phrase into `DeleteDialogConfirm(phrase)`. `.trim()` preserves the old empty-name behavior (template leaves a trailing space when name is blank; field is disabled then anyway).
- Tests (`ProfileViewModelTest.kt`):
  - Updated the two existing `DeleteDialogConfirm` call sites to pass the phrase.
  - Added `spanish_phrase_confirms_deletion` (typing/confirming "BORRAR Ana" deletes end-to-end: AccountDeleted event + signOut fire).
  - Added `wrong_phrase_does_not_confirm_deletion` (typed input != displayed phrase -> `DeleteAccountErrorPhrase`, no teardown).

## auth-02: FrIconButton in DeleteAccountScreen
- Replaced the raw `IconButton { Icon(...) }` top-bar nav icon with `FrIconButton(icon = FrIcons.Back, onClick = onBack, contentDescription = resolve(AuthStringKey.ProfileBackCta))`. Added the `FrIconButton` import; removed the now-unused `androidx.compose.material3.IconButton` import (`Icon` is still used for the warning glyphs).

## auth-03: drop println in ProfileScreen
- Replaced `println("[ProfileScreen] avatar picker error: ...")` with `FrLog.w(FrLog.Tags.Auth) { "[ProfileScreen] avatar picker error: ${r.exception.message}" }`. Added the `FrLog` import.

## auth-05: dedicated session-expired string for TokenExpired
- `i18n/AuthStringKey.kt`: added `ErrorSessionExpired(Res.string.auth_error_session_expired)` + its generated-resource import.
- en/es strings: added `auth_error_session_expired` ("Your session expired. Sign in again." / "Tu sesión ha caducado. Inicia sesión de nuevo.").
- `presentation/AuthErrorToStringKey.kt`: `AuthError.Firebase.TokenExpired` now maps to `ErrorSessionExpired` instead of `ErrorUnknown`.
- Test: updated `AuthErrorToStringKeyTest` (`firebaseTokenExpired_maps_to_sessionExpired_string`).

## Build watch
- Cross-module join with core-domain-01 (SessionError rename) and core-domain-02 (AccountDeletionError.Backend.NotImplemented removal): I edited the auth-side references and the assigned `AccountDeletionPort.kt`. If the core-domain agent also touches `AccountDeletionPort.kt` (it was listed under core-domain-02 but reassigned to me), reconcile to a single edit.
- New Compose Resources strings (`auth_error_session_expired`) require resource regeneration on build — standard for this module.
- DIRTY files untouched: FirebaseAuthRepository.kt and all *CLAUDE.md left as-is.
