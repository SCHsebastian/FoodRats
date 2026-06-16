# Report — `w0-data-export-presentation`

GDPR data-export (Art. 20) client side: a "Export my data" control in the Profile/Settings
screen that invokes the `exportMyData` callable, handles the async result, and surfaces the
15-minute signed download URL to the user (opens in browser via `LocalUriHandler`).

## Status

DONE and verified green. No prior client-side export work existed on disk; implemented from
scratch following the account-deletion client trio as the pattern.

## What was built (layering-correct, mirrors account deletion)

### Domain (`:core:domain`)
- `core/domain/.../account/DataExportPort.kt` — new vendor-free port `DataExportPort.exportMyData():
  Result<ExportReady, DataExportError>`. `ExportReady(downloadUrl: String, expiresAtMs: Long)` is
  the success type. `DataExportError` is a sealed tree with a single retryable leaf
  `Backend.Unavailable` (read-only op → no `failed-precondition`/`aborted`, per the function
  handoff). Placed in `account/` alongside `AccountDeletionPort` (account-scoped). Konsist-clean
  (no Firebase/Android/Compose).

### Data adapter (`:feature:auth/data/firebase`)
- `FirebaseDataExportPort.kt` — implements the port over the `exportMyData` callable, region
  `europe-west3`. Exactly one `withContext(dispatchers.io)`. `Firebase.functions(region)
  .httpsCallable("exportMyData").invoke(EmptyReq).data<Resp>()` then maps `{ downloadUrl,
  expiresAtMs }` → `ExportReady`; any `HttpsError` → `Backend.Unavailable` (read-only ⇒ everything
  is retryable). Request DTO carries no caller fields (uid derived server-side).

### Domain use case (`:feature:auth`)
- `domain/usecase/profile/ExportMyDataUseCase.kt` — pure orchestration; forwards to the port and
  maps `DataExportError → ProfileError` via `toProfileError()`. No I/O.

### Error wiring
- `ProfileError.kt` — new leaf `ProfileError.Export.Unavailable` + `DataExportError.toProfileError()`
  mapper.
- `ProfileErrorToStringKey.kt` — new exhaustive arm `Export.Unavailable → ExportDataErrorBackend`.
- `ProfileErrorToStringKeyTest.kt` — added `export_unavailable_maps_to_export_backend_key` (locks
  exhaustiveness; the `when` is exhaustive, so a missing arm would fail to compile).

### Presentation (MVI single source of truth)
- `ProfileViewModel.kt` — added state fields `isExportingData`, `exportDownloadUrl`,
  `exportExpiresAtMs`, `exportError` (StringKey); intents `ExportMyData` / `DismissExportResult`;
  handler `doExportMyData()` (calls the use case OUTSIDE the `update{}` reducer — same shape as
  `doSaveDisplayName`). New ctor param `exportMyData: ExportMyDataUseCase`. No `withContext` in VM.
- `ProfileScreen.kt` — new `DataExportSection` (under the existing "Account" section title) with an
  `FrSettingsRow` (in-progress subtitle while running), an `FrErrorBanner` on failure, and on
  success a hint line + a secondary `FrButton("Download")` that opens the URL via
  `LocalUriHandler.current.openUri(url)` then dismisses the result. All `Fr*` components; no raw
  Material3 controls added.

### i18n
- `AuthStringKey.kt` + both `values/strings.xml` and `values-es/strings.xml`: `auth_export_data_row`,
  `_subtitle`, `_in_flight`, `_ready_subtitle`, `_ready_cta`, `_error_backend` (EN + ES). The
  "valid for ~15 min" hint is surfaced in `_ready_subtitle`.

### Koin
- `AuthModule.kt` — `single<DataExportPort> { FirebaseDataExportPort(dispatchers = get()) }`,
  `factoryOf(::ExportMyDataUseCase)`, and `exportMyData = get()` added to the EXPLICIT
  `ProfileViewModel` `viewModel { }` binding.
- `AuthModuleVerifyTest.kt` — **no change needed.** `DataExportPort` is BOUND inside `authModule`
  (mirroring `AccountDeletionPort`, which is likewise not in `extraTypes`); `extraTypes` is only for
  cross-module bindings auth consumes but does not provide. Its only dependency,
  `DispatcherProvider`, is already listed. Graph verified by the passing test.

## Analytics

Skipped, per the brief. No `data_exported` (or similar) leaf exists in `AnalyticsEvent` — confirmed
by grep over the taxonomy. Per CHARTER #9 / the function handoff we do NOT invent a PII-risking
event. Adding one later = a new `AnalyticsEvent` leaf fired in `doExportMyData()` AFTER `Result.Ok`.

## Verify

```
./gradlew :core:domain:testAndroidHostTest :feature:auth:testAndroidHostTest
```
`:core:domain:testAndroidHostTest` — BUILD SUCCESSFUL (Konsist domain rules pass with the new port).
`:feature:auth:testAndroidHostTest` — BUILD SUCCESSFUL.

Last lines:
```
> Task :feature:auth:testAndroidHostTest

BUILD SUCCESSFUL in 4s
90 actionable tasks: 11 executed, 79 up-to-date
```
Aggregate auth host results: total tests 91, total failures 0. New/changed test files:
`ProfileViewModelTest` 6/6 (added `export_ok_exposes_download_url_and_clears_in_flight` +
`export_err_sets_error_and_no_url`), `ProfileErrorToStringKeyTest` 15/15 (added the Export arm).

## Decisions

- Open the download URL with Compose `LocalUriHandler` (cross-platform, works on Android + iOS) —
  no new expect/actual needed, unlike the language-settings deep link.
- Section placed under the existing "Account" title (non-destructive ⇒ not in the Danger Zone, which
  holds sign-out + delete).
- `DataExportError` kept to one leaf — the handoff says the server returns only `unauthenticated`
  (shouldn't happen behind the gate) and `internal`, both retryable.

## Blockers

None.

## MANUAL deploy steps (restated from `docs/session/handoffs/w0-data-export-function.md`)

The USER must run these before release; nothing here is codeable in this environment:

1. `pnpm dlx firebase-tools deploy --only functions,storage --project foodrats-de4ec`
   (functions first, then storage rules — the new `exports/{uid}/{filename}` read rule).
2. The Functions runtime service account needs the **Service Account Token Creator** role (V4
   signed-URL minting — same prerequisite as `mintPlateUrls`; if image loading works, this does too).
3. If the first invocation hits `FAILED_PRECONDITION: index required`, add `authorId`
   collection-group single-field indexes for `meals` + `comments` and deploy `firestore:indexes`
   (same as `deleteAccount`).
4. Optional: a Storage lifecycle rule to GC `exports/**` after N days.
