# Handoff — `w1-streak-nudges-function` → `w1-streak-nudges-i18n` (client)

The SERVER `streakNudge` function is implemented, registered, and green. This is exactly what the
CLIENT i18n task must do to localize and display the nudge.

## 1. The message contract the server sends

The push is a **`notification` + `data` hybrid** (same shape as `onMealCreated`/`weeklyDigest`),
sent via the existing `sendToUid`. The client localizes from `data`, not from the OS `notification`.

`data` map keys the client receives:

| key          | value                | notes                                              |
|--------------|----------------------|----------------------------------------------------|
| `kind`       | `"SocialNudge"`      | added to server `PushPayload.kind`                 |
| `key`        | `"social_nudge"`     | the discriminator the client `PushPayloadMapper` matches on (server const `KEY_SOCIAL_NUDGE`) |
| `postedCount`| e.g. `"3"`           | how many crewmates posted today (string)           |
| `crewSize`   | e.g. `"5"`           | crew member count (string)                         |

**No `link`** — it's a reminder; tap just opens the app to Feed (matches the existing
`DailyInactivityWorker` / weekly-digest "no link" convention).

## 2. What the client i18n task MUST add

### a. `PushPayloadMapper` (feature/notifications/.../data/push/PushPayloadMapper.kt)

Today `parse()` returns `null` for `key == "social_nudge"` (unknown → push ignored in-app). Add:

- A `companion` const `KEY_SOCIAL_NUDGE = "social_nudge"` (matching the server).
- A `PushContent.SocialNudge(...)` leaf carrying `postedCount: Int`, `crewSize: Int`, and a
  `ReminderPayload` (introduce `ReminderPayload.SocialNudge` / `ReminderKind.SocialNudge`, or reuse a
  "just open Feed" payload — there is no crew/meal id in the push, so it has no deep target).
- A `parse()` branch `KEY_SOCIAL_NUDGE -> socialNudge(data)` that reads `postedCount`/`crewSize`
  (`?.toIntOrNull()`), and a `toReminder()` arm that resolves title/body via `getString(...)`.

### b. `NotificationStringKey` (feature/notifications/.../i18n/NotificationStringKey.kt) + strings

Add `SocialNudgeTitle` and `SocialNudgeBody` entries pointing at two new `Res.string.*` resources.

- **Body must be templated `"%1$d of %2$d posted"`** (§1.1's exact wording) — i.e.
  `getString(NotificationStringKey.SocialNudgeBody.resourceId, postedCount, crewSize)`.
- Populate BOTH `values/strings.xml` and `values-es/strings.xml` (house rule — including any glyphs).
  English body suggestion: `"%1$d of %2$d crewmates already posted today — your turn"`. Spanish: a
  matching `%1$d de %2$d …`. Title e.g. EN `"Your crew is eating 👀"` / ES equivalent.
- The server's English `FALLBACK.socialNudgeTitle/Body` is **only** the OS lock-screen fallback;
  the in-app `Reminder` text comes from these client resources, so they are the real copy.

### c. (Optional, §1.1) client analytics on tap

§1.1: "client logs `notification_opened` on tap." If a generic notification-opened event already
exists, fire it for the SocialNudge tap (after the use case / nav resolves `Ok`, per the analytics
rule). Not blocking for the i18n task.

## 3. DECISION the i18n task (or a follow-up) must make: the client `DailyInactivityWorker`

§1.1 open-decision #7: server-scheduled (this task) is the **preferred** channel over the client
WorkManager `DailyInactivityWorker`. The client job is a *local* streak reminder (different copy:
`NotificationStringKey.Streak*` / `Inactivity*`), so it is NOT a duplicate of THIS social-proof push
— but both are "you haven't posted, go post" nudges and will co-fire.

Recommendation: **disable / remove the client `DailyInactivityWorker` scheduling** once the server
nudge is deployed and verified, to avoid two daily "go post" notifications. This was NOT done in the
server task (cross-layer, and unsafe to delete client code from the functions task). Touch points if
you proceed:
- `feature/notifications/.../platform/DailyInactivityWorker.kt` (the worker itself)
- `feature/notifications/.../platform/AndroidLocalReminderScheduler.kt` (the scheduling site)
- `feature/notifications/.../data/adapter/StreakNotificationAdapter.kt` (`scheduleStreakNudge()`)
- iOS `UNUserNotificationCenter` local-streak scheduling, if any.

If you keep BOTH, ensure they don't both fire on the same day (e.g. gate the local worker off when
server nudges are enabled via a feature flag).

## 4. User / deploy steps (also in the report)

- `pnpm --dir functions deploy` — creates the `streakNudge` Cloud Scheduler job automatically.
- `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec` — for the new
  `accounts/{uid}/nudges/{dayKey}` deny block (hardening; not strictly required).
- No composite Firestore index needed (single-field `dayKey ==` query).
- `streak_nudge_sent` server-side analytics: there's no server analytics sink in `functions/` today;
  currently logged via `logger.info`. Adding a real server analytics event is a separate follow-up.
