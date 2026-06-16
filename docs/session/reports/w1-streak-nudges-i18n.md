# Report — `w1-streak-nudges-i18n`

CLIENT side of the server social-proof streak nudge (roadmap §1.1): localize the `streakNudge`
Cloud Function push, and resolve the double-nudge with the legacy `DailyInactivityWorker`.

No prior/interrupted work existed on disk (no report, no partial edits).

## 1. What was localized

Added two i18n entries for the social/streak nudge, templated with the server's `postedCount` /
`crewSize` params, populated in BOTH locale files (no hardcoded English in Kotlin).

- `feature/notifications/.../i18n/NotificationStringKey.kt` — new enum entries `SocialNudgeTitle`,
  `SocialNudgeBody` (+ their `Res.string.*` imports).
- `feature/notifications/.../composeResources/values/strings.xml`:
  - `notifications_social_nudge_title` = `Your crew is eating 👀`
  - `notifications_social_nudge_body` = `%1$d of %2$d crewmates already posted today — your turn`
    (matches §1.1's `%1$d of %2$d posted` shape).
- `feature/notifications/.../composeResources/values-es/strings.xml`:
  - `notifications_social_nudge_title` = `Tu crew está comiendo 👀`
  - `notifications_social_nudge_body` = `%1$d de %2$d compañeros ya han publicado hoy — te toca`

## 2. The mapper branch

`feature/notifications/.../data/push/PushPayloadMapper.kt` — matches the existing
NewComment/NewMealPost/WeeklyDigest pattern exactly:

- `companion` const `KEY_SOCIAL_NUDGE = "social_nudge"` (matches the server `KEY_SOCIAL_NUDGE`).
- New `PushContent.SocialNudge(id, postedCount: Int, crewSize: Int)` leaf; `kind =
  ReminderKind.SocialNudge`; `payload = ReminderPayload.None` (no crew/meal id in the push → no deep
  target → a tap just opens the app to Feed, matching the weekly-digest / inactivity convention).
- `parse()` branch `KEY_SOCIAL_NUDGE -> socialNudge(data)`; the parser reads
  `postedCount`/`crewSize` via `?.toIntOrNull()` and returns `null` if either is absent/non-numeric
  (an untemplatable body → push ignored in-app, same discipline as the other branches). The push
  has no per-send id in the contract, so `id` is fixed `"social-nudge"` — a same-day re-send
  replaces the prior banner rather than stacking.
- `toReminder()` arm resolves title via `SocialNudgeTitle` and body via `getString(
  SocialNudgeBody.resourceId, content.postedCount, content.crewSize)`.

New enum value `ReminderKind.SocialNudge` added in `ReminderKind.kt`. Verified there is NO exhaustive
`when (ReminderKind)` / `when (PushContent)` / `when (ReminderPayload)` anywhere else in the repo, so
the new enum value + content leaf are non-breaking. The Android FCM service
(`FoodRatsFirebaseMessagingService`) and the iOS `IosNotificationBridge` both call
`mapper.toReminder(data)`, so the branch is wired end-to-end on both platforms with no edit to
either — `androidApp` push parsing was NOT touched.

## 3. Double-nudge resolution — exactly what was disabled and why it's safe

§1.1 / the handoff: the server-scheduled `streakNudge` function is the **preferred** "go post"
channel; the local WorkManager `DailyInactivityWorker` would co-fire a second daily nudge.

The local streak nudge was scheduled from ONE place: `BackgroundMealUploadCoordinator` (in
`:feature:meal`) called `streakNotifications.scheduleStreakNudge()` after every successful publish.
That single call site was **removed** — the conservative "don't enqueue it" option from the brief.

Touch points:
- `feature/meal/.../data/upload/BackgroundMealUploadCoordinator.kt` — removed the
  `runCatching { streakNotifications.scheduleStreakNudge() }` call (replaced with a comment
  explaining the supersession + how to restore it); removed the now-unused `streakNotifications:
  StreakNotificationPort` constructor param and its import.
- `feature/meal/.../di/MealModule.kt` — dropped `streakNotifications = get()` from the
  `BackgroundMealUploadCoordinator(...)` construction.
- `feature/meal/.../di/MealModuleVerifyTest.kt` — dropped `StreakNotificationPort::class` from
  `extraTypes` (+ its import; KDoc updated). `mealModule.verify()` stays green.

**Why it's safe / reversible:**
- Nothing else schedules the local nudge — `scheduleStreakNudge` / `StreakNotificationPort` had
  exactly one production caller (the coordinator). With that call gone, `DailyInactivityWorker` is
  never enqueued, so users get only the server push.
- I deliberately did NOT delete `DailyInactivityWorker`, `AndroidLocalReminderScheduler`,
  `StreakNotificationAdapter`, the `StreakNotificationPort` interface, the
  `ScheduleDailyInactivityReminderUseCase`, or the `Streak*`/`Inactivity*` i18n keys. They all
  remain defined and bindable (the `single<StreakNotificationPort>` in `NotificationsModule` is now
  unconsumed but still resolvable — harmless for Koin). Re-adding one call line in the coordinator
  restores the local channel if the server function is ever rolled back. The brief's preference was
  exactly this: "prefer NOT enqueuing it over deleting the whole class."
- The `Streak*`/`Inactivity*` strings were kept because the worker class + adapter still reference
  them and they're cheap to keep — no dead-key cleanup was warranted.

iOS: there is no `UNUserNotificationCenter` local-streak scheduling in the codebase (the iOS
`MealUploadScheduler` is an in-process no-op), so there was nothing iOS-side to disable.

## 4. Tests

`feature/notifications/.../data/push/PushPayloadMapperTest.kt` — added 3 parse-level tests (the
existing pattern: `parse` is pure and unit-testable; `toReminder` needs bundled resources so its
formatting isn't exercised here):
- `social_nudge_payload_parses_to_SocialNudge_content` — asserts kind, id `"social-nudge"`,
  `postedCount=3`, `crewSize=5`, `payload == ReminderPayload.None`.
- `social_nudge_with_non_numeric_count_returns_null`.
- `social_nudge_missing_crew_size_returns_null`.

## 5. Verify (quoted)

`./gradlew :feature:notifications:testAndroidHostTest`:
```
> Task :feature:notifications:testAndroidHostTest

BUILD SUCCESSFUL in 4s
90 actionable tasks: 20 executed, 70 up-to-date
```
`PushPayloadMapperTest` ran `tests="9"` (was 6; +3 new) — all passed.

`./gradlew :feature:meal:testAndroidHostTest` (because the double-nudge fix edits `:feature:meal`):
```
> Task :feature:meal:testAndroidHostTest

BUILD SUCCESSFUL in 4s
90 actionable tasks: 11 executed, 79 up-to-date
```
`MealModuleVerifyTest` passes — confirms the Koin graph is still complete after removing the
`StreakNotificationPort` dependency.

`androidApp:assembleDebug` was NOT run: I did not touch `androidApp` push parsing (the FCM service is
unchanged; it consumes the mapper via DI). The mapper + i18n changes are confined to
`:feature:notifications`, and the double-nudge fix is confined to `:feature:meal` — both verified
green by their host-test tasks.

## 6. Decisions
- Push `id` is fixed `"social-nudge"` (no per-send id in the contract) → same-day re-send replaces,
  doesn't stack.
- Non-numeric / missing count → `parse` returns `null` (push ignored), matching the other branches'
  "malformed → null" behavior. The FCM fallback then shows the server's OS `notification` text.
- Disabled the local nudge by removing the single schedule call + cleaning the now-unused dependency
  (constructor + Koin + verify test), rather than deleting the worker/port — reversible, minimal.

## 7. Not done here (out of scope / needs user)
- §1.1 optional client `notification_opened` analytics on tap — not wired (no generic
  notification-opened event exists today; the handoff marked it non-blocking).
- Server deploy steps (from the function handoff): `pnpm --dir functions deploy`, firestore-rules
  deploy. Until the server function is deployed, users get NO daily "go post" nudge at all (the
  local one is now disabled) — deploy is the activation step.
