# feature-notifications repair report

## notif-05 (MEDIUM) — IosLocalReminderScheduler: bridge completion handler via suspendCancellableCoroutine

**Changed:** `IosLocalReminderScheduler.schedule()` now suspends on the `addNotificationRequest` completion handler via `suspendCancellableCoroutine`. If the `NSError` is non-null, returns `Result.failure(NotificationError.Schedule.Failed)` instead of silently returning `Result.success(Unit)`.

**Tests added:** None. `IosLocalReminderScheduler` lives in `iosMain` and calls `UNUserNotificationCenter` platform APIs — there is no test infrastructure in commonTest or androidHostTest that can stub these. Error propagation at the use-case level is already locked by `ScheduleDailyInactivityReminderUseCaseTest.propagates_scheduler_error`.

## notif-02 (LOW) — PermissionLauncherHolder: compareAndSet guard against concurrent requests

**Changed:** `requestAsync()` now uses `pending.compareAndSet(null, deferred)` — if a request is already in flight the new call returns `false` immediately without launching a second OS prompt. Exposed `isRequesting: Boolean` computed property for callers (e.g. to disable the Allow button while pending).

**Tests added:** None required (LOW).

## notif-03 (LOW) — AndroidLocalReminderScheduler: inject Clock

**Changed:** Constructor now takes `clock: Clock`. `schedule()` replaces `KxClock.System.now()` with `clock.now()`. Koin binding in `NotificationsAndroidModule` updated to `AndroidLocalReminderScheduler(androidContext(), get())`.

**Tests added:** None required (LOW).

## notif-06 (LOW) — IosFcmTokenProvider: remove println

**Changed:** `IosFcmTokenProvider` now accepts `crashReporter: CrashReporter` in its constructor. The `println(...)` in the catch block replaced with `crashReporter.log(...)`. Koin binding in `NotificationsIosModule` updated to `IosFcmTokenProvider(get())`.

**Tests added:** None required (LOW).

## notif-07 (LOW cleanup) — Delete DeliveryWindow.kt + StreakTitle/StreakBody

**Grep result:** `DeliveryWindow` — zero callers outside its own file. `StreakTitle`/`StreakBody` — only referenced in the `NotificationStringKey` enum itself; no call sites anywhere in the codebase.

**Changed:**
- Deleted `feature/notifications/src/commonMain/kotlin/.../domain/model/DeliveryWindow.kt`
- Removed `StreakTitle` and `StreakBody` entries from `NotificationStringKey.kt` and their two imports
- Removed `notifications_streak_title`/`notifications_streak_body` from both `values/strings.xml` and `values-es/strings.xml`

**Tests added:** None required (LOW cleanup).

## Skipped

- **notif-01** (deferred by orchestrator): permission Denied/DeniedForever needs a persisted has-requested flag — design work.
- **notif-04** (deferred by orchestrator): channel-name i18n needs an ensure() signature change.

## Build risks

- `NotificationsModuleVerifyTest` references `Clock::class` — confirm it's already in extraTypes (it's in the Koin graph via `coreDataModule`). If it's missing from `extraTypes`, the verify test will fail with an unresolved binding assertion.
- iOS: `IosFcmTokenProvider(get())` requires `CrashReporter` to be bound in the iOS Koin graph. It is — the iOS crash module registers `IosCrashReporter` as `CrashReporter` in `shared/iosMain`.
