# Hygiene Scan Report — 2026-06-17

Comprehensive grep across `core/`, `feature/`, `shared/`, `androidApp/`, `functions/` for code smells.

---

## Summary

| Category | Count | Status |
|----------|-------|--------|
| TODO comments | 2 | Minor |
| FIXME comments | 0 | ✓ Clean |
| HACK comments | 0 | ✓ Clean |
| XXX comments | 0 | ✓ Clean |
| println() calls | 8 | Actionable |
| FrLog.d/v/e (structured logging) | 40 | ✓ Intentional |
| console.log/error/warn | 2 | Actionable |
| @Suppress annotations | 12 | ✓ Mostly justified |
| Commented-out code blocks | None detected | ✓ Clean |

---

## Details by Category

### 1. TODO Comments (2)

**Status:** Minor — both are architectural notes, not blocking.

- `core/data/src/iosMain/kotlin/es/schsebastian/foodrats/core/data/di/ConfigIosModule.kt:10`
  - Comment: `iOS ships without a Remote Config adapter for now (TODO(RemoteConfig): wire the native`
  - Action: No change needed; iOS Remote Config wiring is deferred by design.

- `feature/feed/src/commonMain/kotlin/es/schsebastian/foodrats/feature/feed/presentation/MealDeleteErrorToStringKey.kt:7`
  - Comment: `// TODO(i18n): dedicated copy for meal-delete errors. Reusing the comment-error`
  - Action: Low priority; meal-delete error strings currently reuse comment-error keys.

---

### 2. Debug Logging — println() (8)

**Status:** Actionable — three files contain ad-hoc println() that should route through FrLog.

#### Offending sites:

1. `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/presentation/profile/ProfileScreen.kt:70`
   ```
   println("[ProfileScreen] avatar picker error: ${r.exception.message}")
   ```

2. `feature/meal/src/commonMain/kotlin/es/schsebastian/foodrats/feature/meal/presentation/capture/CaptureMealViewModel.kt:25`
   ```
   println("[CaptureMealViewModel] session error: ${r.error}")
   ```

3. `feature/meal/src/commonMain/kotlin/es/schsebastian/foodrats/feature/meal/presentation/capture/CaptureMealViewModel.kt:30`
   ```
   println("[CaptureMealViewModel] no crews")
   ```

4. `feature/meal/src/commonMain/kotlin/es/schsebastian/foodrats/feature/meal/presentation/capture/CaptureMealViewModel.kt:32`
   ```
   println("[CaptureMealViewModel] startDraft error: ${result.error}")
   ```

5. `feature/meal/src/commonMain/kotlin/es/schsebastian/foodrats/feature/meal/presentation/capture/CaptureMealViewModel.kt:40`
   ```
   println("[CaptureMealViewModel] updateDraft error: ${r.error}")
   ```

6. `feature/notifications/src/iosMain/kotlin/es/schsebastian/foodrats/feature/notifications/platform/IosFcmTokenProvider.kt:20`
   ```
   println("[IosFcmTokenProvider] FCM token unavailable: ${t.message}")
   ```

#### Legitimate uses:
7–8. `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/telemetry/FrLog.kt:88,97`
   - These are **intentional**: FrLog's debug backend uses `println()` as a fallback when logging is enabled. No action.

---

### 3. Structured Logging — FrLog.d/v/e (40)

**Status:** ✓ Intentional — all calls route through the centralized FrLog framework.

**Pattern:** All 40 calls follow the pattern `FrLog.d(tag) { lambda }`, which is the correct approach (lazy evaluation, switchable per tag). This is the design-compliant logging layer.

**Sample sites (all legitimate):**
- `core/data/src/commonMain/kotlin/es/schsebastian/foodrats/core/data/datastore/AppPreferences.kt:16,20,30,42`
- `core/presentation/src/commonMain/kotlin/es/schsebastian/foodrats/core/presentation/mvi/MviViewModel.kt:34,42,46`
- `shared/src/commonMain/kotlin/es/schsebastian/foodrats/app/root/RootNavViewModel.kt:83–126` (23 calls — deep nav tracing)

No action needed.

---

### 4. Node.js Debug Logging — console.log/error/warn (2)

**Status:** Actionable — seed script only; not a production concern.

1. `functions/scripts/seed-catalog.ts:134`
   ```
   console.log(...)
   ```
   - Context: Seed-catalog admin script; output is informational.

2. `functions/scripts/seed-catalog.ts:141`
   ```
   console.error(e)
   ```
   - Context: Error reporting in seed script; acceptable for admin tooling.

**Action:** Leave as-is; these are not part of the deployed Cloud Functions (`functions/src/`).

---

### 5. @Suppress Annotations (12)

**Status:** ✓ Mostly justified — all have either a KDoc or are covering legitimate edge cases.

#### Justified suppressions:

1. `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/molecules/FrSettingsSection.kt:91`
   - `@Suppress("UnusedReceiverParameter")` — Lambda DSL receiver.

2. `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/atoms/FrLogo.kt:39`
   - `@Suppress("UNUSED_EXPRESSION")` — Logo SVG path rendering artifact.

3. `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/image/ThumbHash.kt:213`
   - `@Suppress("unused")` — Byte-level bit manipulation utility.

4. `core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/theme/MinotaurUnlock.kt:23`
   - `@Suppress("UNREACHABLE_CODE")` — Easter-egg conditional.

5. `core/data/src/androidMain/kotlin/es/schsebastian/foodrats/core/data/location/AndroidLocationProvider.kt:59`
   - `@Suppress("MissingPermission")` ✓ Gated by `hasPermission()` check.

6–7. `feature/achievements/src/commonMain/kotlin/es/schsebastian/foodrats/feature/achievements/presentation/components/EpochDayFormat.kt:17,19`
   - `@Suppress("DEPRECATION")` — Kotlin `LocalDate` deprecation (valid for backward compat).

8. `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/data/firebase/FirebaseAccountDeletionPort.kt:41`
   - `@Suppress("UNUSED_PARAMETER")` ✓ KDoc: `required by AccountDeletionPort; server derives uid from request.auth.uid`.

9–10. `feature/stats/src/commonMain/kotlin/es/schsebastian/foodrats/feature/stats/presentation/components/CollectionFormatting.kt:15,17`
   - `@Suppress("DEPRECATION")` — Kotlin `LocalDate` deprecation.

11–12. `shared/src/iosMain/kotlin/es/schsebastian/foodrats/IosDeepLinkBridge.kt:14` & `IosNotificationBridge.kt:21`
   - `@Suppress("unused")` ✓ KDoc: iOS bridge exports called from Swift.

No action needed.

---

### 6. Commented-Out Code Blocks

**Status:** ✓ Clean — no large commented-out code blocks detected.

**Note:** The search for multi-line comments (`/* ... */`) and 5+ consecutive `//` lines flagged only KDoc comments and legitimate comment chains (e.g., architecture rules in tests, multi-line descriptions). All are documentation, not dead code.

---

## Actionable Remediation

### High Priority
1. **Replace 6 × println() with FrLog.d()** in:
   - `feature/auth/src/commonMain/kotlin/es/schsebastian/foodrats/feature/auth/presentation/profile/ProfileScreen.kt:70`
   - `feature/meal/src/commonMain/kotlin/es/schsebastian/foodrats/feature/meal/presentation/capture/CaptureMealViewModel.kt:25,30,32,40`
   - `feature/notifications/src/iosMain/kotlin/es/schsebastian/foodrats/feature/notifications/platform/IosFcmTokenProvider.kt:20`
   
   Pattern: Replace `println("[Tag] msg")` with `FrLog.d("Tag") { "msg" }`.

### Low Priority
2. **Optional:** Add dedicated `MealDeleteStringKey` to replace the TODO in feed error mapper (minor i18n cleanup).

---

## Conclusion

**Overall hygiene: A−** (one small logging cleanup stands out). No architecture violations, no buried dead code, no suspicious suppressions. The two TODOs are intentional defers, not oversights.
