# AndroidManifest + build.gradle store-readiness audit (2026-06-18)

Scope: `androidApp/src/main/AndroidManifest.xml` + `androidApp/build.gradle.kts`
(+ `gradle/libs.versions.toml`, `proguard-rules.pro`, merged release manifest)
against what AAA+ teams ship to Google Play.

Overall verdict: **already strong**. Fundamentals are correct; gaps are polish/size,
not blockers. One headline fix applied (locale handling); the rest are judgment calls.

## PASS — already correct (no change needed)

| Area | Evidence |
|---|---|
| All components `exported` explicit | MainActivity `exported=true`; FCM service + FileProvider `exported=false` |
| Minimal permissions | INTERNET, POST_NOTIFICATIONS, ACCESS_COARSE_LOCATION only; camera/location features `required=false` |
| `allowBackup="false"` | Deliberate; cloud auto-backup off (no sensitive prefs leak via D2D/backup) |
| Verified App Links | `autoVerify="true"` for `https://foodrats-de4ec.web.app` (live host; `foodrats.app` removed — hosts nothing) |
| Custom-scheme deep link | `foodrats://app/...` (host=app keeps route discriminator in path) |
| `<queries>` for pkg visibility | Instagram story-share intent declared (Android 11+) |
| FileProvider scoped | Only `cache/share_cards/`, per-share `FLAG_GRANT_READ_URI_PERMISSION` |
| Analytics privacy defaults | GA4 collection OFF + Consent Mode DENIED in manifest meta-data; flipped on only post-consent |
| Release **not** debuggable | merged release manifest has no `debuggable` flag |
| 16-KB page alignment | `extractNativeLibs=false` (merged manifest) + `useLegacyPackaging=false` + MediaPipe 0.10.35 |
| `supportsRtl=true` | present |
| `singleTop` launch mode | correct for `onNewIntent` deep-link delivery |
| Version injection | `versionCode/versionName` from `-P` props; CI computes monotonic codes |
| R8 + resource shrink ON | `isMinifyEnabled` + `isShrinkResources` on release |
| Cleartext blocked | targetSdk 36 ⇒ `usesCleartextTraffic` defaults false (no plaintext HTTP) |
| Predictive back | targetSdk 36 (Android 16) ⇒ enabled by default; no manifest opt-in needed |
| ProGuard correctness | kotlinx-serialization `$$serializer`/Companion/`@Serializable` fields kept; Crashlytics SourceFile/LineNumberTable kept |
| Signing from env | `signingConfigs.release` from env; unsigned fallback locally; Play App Signing model |

## APPLIED this session (low-risk, AAA+, verified by build)

### Locale filtering  — `build.gradle.kts` `androidResources { localeFilters += listOf("en","es") }`
- Stops the AAB from bundling ~80 transitive-dep locales (androidx/play-services/
  MediaPipe) the app doesn't use. Real download-size reduction; replaces the deprecated
  `resConfigs`. Compose-resource translations (core/i18n) are unaffected.
- Verified: `:androidApp:assembleRelease` → `BUILD SUCCESSFUL in 1m 25s` (VERIFY.log).

### Tried + reverted: `generateLocaleConfig`
- First pass set `generateLocaleConfig = true` + `res/resources.properties`. Build was
  green BUT the generated config listed **only `en-US`** — AGP scans android `values-XX`
  folders, and this app's es translations live in Compose resources, not `values-es/`.
  That would advertise an **English-only** per-app language picker. Reverted; see R-locale.

### Notification small icon + FCM display defaults  (was R1 + R2)
- **`res/drawable/ic_stat_notification.xml`** — monochrome chef's-toque silhouette (echoes the
  chef-rat brand). Notification small icons render alpha-only + system-tinted; the old
  `setSmallIcon(applicationInfo.icon)` showed the full-color launcher icon as a white square.
- **`DailyInactivityWorker.kt`** — now `setSmallIcon(ic_stat_notification)` + `setColor(accent)`,
  resolved by name at runtime (no compile-time dep on `:androidApp`'s R), with a launcher-icon
  fallback so a resource rename can't crash the daily worker.
- **Manifest FCM meta-data** — `default_notification_icon` (mono icon), `_color` (olive accent),
  `_channel_id` (`fr_nudge`). Only the system-displayed notification-message path uses these;
  the server's data messages still go through `onMessageReceived`. Note: `fr_nudge` is the
  streak-nudge channel — add a dedicated general channel if the server starts sending
  notification-type messages.
- `res/values/colors.xml` adds `notification_accent` (#4F6E2B) + `splash_background` (+night).

### Branded splash screen  (was R3)
- `androidx.core:core-splashscreen:1.0.1` added (catalog + `:androidApp`).
- `res/values/themes.xml` — `Theme.FoodRats.Splash` (parent `Theme.SplashScreen`, brand
  `windowSplashScreenBackground`, `postSplashScreenTheme=Theme.FoodRats`) + `Theme.FoodRats`
  (NoActionBar host, brand `windowBackground` to kill the pre-Compose flash). Light Concrete /
  dark charcoal via `values-night`.
- Manifest: `MainActivity` theme `@android:style/Theme.Material.NoActionBar` → `@style/Theme.FoodRats.Splash`.
- `MainActivity.onCreate` calls `installSplashScreen()` first.
- **Verification boundary:** build/merge verified here; the visual splash→content handoff
  and the toque silhouette's on-device rendering need a device/emulator check (no UI run in this env).

## RECOMMENDED — still open (deferred by decision / verification cost)

| # | Finding | Severity | Why it matters |
|---|---|---|---|
| R1 | ~~Notification small icon = launcher icon~~ | — | **DONE** (see above). |
| R2 | ~~No FCM default-notification metadata~~ | — | **DONE** (see above). |
| R3 | ~~No branded splash~~ | — | **DONE** (see above). |
| R4 | **No native debug symbols in bundle** (`ndk { debugSymbolLevel = "FULL" }`) | Low | App ships MediaPipe `.so`; without symbols, native crashes in Play Console aren't symbolicated. NOT applied: needs an NDK toolchain present — can break the build if NDK isn't configured. Decide before relying on native crash stacks. |
| R5 | **No explicit `dataExtractionRules`** | Info | `allowBackup=false` already disables cloud backup; D2D transfer rules are the only remaining lever. Current state is a defensible conservative default. |
| R-locale | **Per-app language picker** (Android 13+) not wired | Low | Needs a hand-authored `res/xml/locales_config.xml` (en+es) + `android:localeConfig`, AND on-device proof that Compose resources follow the system per-app locale. Can't auto-generate (Compose-resource i18n). Deferred until device-verifiable. |

## NOT recommended (deliberate defaults left as-is)
- `dependenciesInfo { includeInBundle=false }` — leaving the dependency metadata ON
  powers Play Console's SDK/vulnerability scanning; keep the Google-recommended default.
- `bundle { language/density/abi }` splits — already all-on by default; no change.

## Verification
- Locale filter: `:androidApp:assembleRelease` → `BUILD SUCCESSFUL in 1m 25s` (VERIFY.log);
  en-only localeConfig confirmed gone (grep count 0).
- Icon + splash + worker: `:feature:notifications:testAndroidHostTest :androidApp:assembleRelease
  -PcrashlyticsMappingUpload=false` → `BUILD SUCCESSFUL in 2m 6s` (VERIFY-icon-splash.log).
  `lintVitalRelease` passed (no abort). Merged release manifest confirmed carrying
  `Theme.FoodRats.Splash` + all three FCM default-notification meta-data entries.
- **Not verified here (no device/emulator in env):** visual splash→content handoff and the
  toque silhouette rendering at 24dp. Run the on-device smoke walk before store submission.
