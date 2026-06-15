# 010 · w0-data-export-presentation

**Status:** done

**Summary (≤6 lines):**
- Client side of GDPR data export: `DataExportPort` (+ `ExportReady`, `DataExportError`) in `:core:domain`, `FirebaseDataExportPort` adapter (one `withContext(io)`, calls `exportMyData` callable), `ExportMyDataUseCase`, Profile "Export my data" row triggering it and opening the download URL.
- Files: `core/domain/.../account/DataExportPort.kt`, `feature/auth/.../data/firebase/FirebaseDataExportPort.kt`, `.../domain/usecase/profile/ExportMyDataUseCase.kt`, `.../domain/error/ProfileError.kt`, `.../presentation/profile/{ProfileErrorToStringKey,ProfileViewModel,ProfileScreen}.kt`, `.../i18n/AuthStringKey.kt`, `.../di/AuthModule.kt`, `composeResources/values{,-es}/strings.xml`, tests (+2 VM, +1 mapper).
- Decisions: opened URL via `LocalUriHandler` (no expect/actual); row under "Account" (non-destructive); analytics skipped (no `data_exported` leaf — PII risk).
- Blockers: none. MANUAL at release restated below.

**Verify (quoted):**
```
> Task :feature:auth:testAndroidHostTest
BUILD SUCCESSFUL in 4s
(auth host aggregate 91 tests / 0 failures; :core:domain also green)
```

**MANUAL (user, before release):** deploy `functions,storage`; grant Functions SA the Service Account Token Creator role (V4 signed URLs); create any flagged `authorId` collection-group indexes.

Report: `docs/session/reports/w0-data-export-presentation.md`
