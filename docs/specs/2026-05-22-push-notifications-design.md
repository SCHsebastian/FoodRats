# Push notifications & cron triggers — design spec

**Status**: ready for plan
**Date**: 2026-05-22
**Author**: Sebastián (with Claude Code)

## 1. Goal

Wire end-to-end push notifications across Android + iOS for FoodRats with four distinct triggers:

1. **New comment** on a meal → push to the meal's author (unless author commented themselves).
2. **New meal** posted in a crew → push to every other member of that crew.
3. **Weekly digest** (Monday 09:00 local time per crew) → push the previous ISO week's five awards (best meal, best cook, most prolific, most voted, most criticized) to every crew member.
4. **Daily inactivity reminder** (14:00 device-local time) → if the user has not posted any meal in their active crew for *today*, fire a local push reminding them.

Triggers 1–3 run server-side via **Firebase Cloud Functions v2 (Node 20) on Blaze**. Trigger 4 runs **purely client-side** via the existing `LocalReminderScheduler` (Android WorkManager / iOS `UNCalendarNotificationTrigger`).

## 2. Decisions taken during brainstorm

| # | Decision | Choice |
|---|---|---|
| 1 | Backend platform | **Firebase Cloud Functions v2 (Node 20) on Blaze plan.** WhatsApp-shaped: client writes a Firestore doc, an `onCreate` trigger fans out to FCM. Free-tier inside Blaze covers MVP scale at $0/mo. |
| 2 | Weekly digest contents | **All five awards in a single notification.** Body lists best meal / best cook / most prolific / most voted / most criticized; one push per crew per week. Skipped awards (e.g., no eligible cook) are simply omitted from the body. |
| 3 | Inactivity reminder mechanism | **Local-scheduled, client-side has-posted check.** Daily at 14:00 device-local; the worker queries the active crew for today's meals by the current user just before firing and skips if found. Zero backend cost, works offline, true device-local time. Replaces the existing 19:00 streak nudge. |
| 4 | New-post fan-out scope | **Crew-scoped.** Members of crew X get notified of new posts in crew X. App-global broadcast would be incoherent for a closed-group app. |
| 5 | Self-suppression | Author never gets a push for their own action (own comment, own meal, own inactivity is N/A). |
| 6 | Multi-device fan-out | One push per registered token in `accounts/{uid}/devices/*`. Stale tokens are pruned on `UnregisteredError` from FCM. |
| 7 | Server-side weekly winner computation | **Yes, replicates stats logic.** The Cloud Function reimplements `ComputeWindow` in TypeScript over the previous ISO week — single source of truth for *display* stays the client; the function only needs the same window for *announcing*. |
| 8 | Backend trust boundary | Cloud Functions run as Firebase Admin (bypass Firestore rules) and use the Admin SDK to look up tokens + send pushes. No client-callable HTTPS endpoints — every trigger is event-driven (Firestore onCreate, Pub/Sub Scheduler). |
| 9 | i18n on backend pushes | Backend pushes ship **resource keys + interpolation params** as FCM `data` payload, not pre-localized strings. The client resolves to user-locale text via `resolve(StringKey)`. This sidesteps storing per-user locale on the server. |

## 3. Architecture overview

```
┌────────────────────────── Client ───────────────────────────┐    ┌───────────── Firebase ────────────┐
│                                                              │    │                                    │
│  FrCommentRow.send  ──► FirestoreCommentRepo.write ──────────┼───►│  /crews/{c}/meals/{m}/comments/{x}│
│                                                              │    │                ▼                   │
│                                                              │    │  CF: onCommentCreated              │
│                                                              │    │    → read meal.authorId            │
│  PublishMealUseCase  ──► FirestoreMealRepo.write ────────────┼───►│  /crews/{c}/meals/{m}              │
│                                                              │    │                ▼                   │
│                                                              │    │  CF: onMealCreated                 │
│                                                              │    │    → read crew.memberIds           │
│  (Pub/Sub Scheduler "0 9 * * 1") ───────────────────────────►│    │  CF: weeklyDigest                  │
│                                                              │    │    → walk all crews, compute       │
│  AndroidLocalReminderScheduler / IosLocalReminderScheduler   │    │                ▼                   │
│   (14:00 device-local, daily)                                │    │  FCM Admin SDK                     │
│       │                                                      │    │   → sendEachForMulticast(tokens)   │
│       ▼ pre-fire check                                       │    │                                    │
│  HasPostedTodayUseCase (active crew, today)                  │    └────────────────────────────────────┘
│       │                                                      │                       │
│       ▼ if false                                             │                       ▼ delivery
│  NotificationCompat.notify / UNUserNotificationCenter.add    │            FCM payload arrives at device
│                                                              │                       │
│  RootComposable ◄──── NotificationBus.stream ◄──── HandleIncomingPushUseCase ◄── platform receiver
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Architectural rules this design preserves

- `:core:domain` stays free of Firebase, Android, Compose. Backend is a sibling sub-project, not a Gradle module — domain doesn't know it exists.
- All new client errors are sealed-interface trees with `data object` leaves; `*ErrorToStringKey` mappers stay exhaustive.
- One `withContext(dispatchers.io)` per public repository method.
- All user-visible strings via `resolve(StringKey)`. Backend sends keys, not text.
- Features depend on each other only through ports — `:feature:notifications` reads from `MealReadPort` and a new `ActiveAccountProvider` (or reuses `Session.activeAccountId` from auth via the existing `SessionPort`).

## 4. Backend — Cloud Functions (TypeScript)

### 4.1 Project layout

```
functions/                          # new sub-project at repo root, NOT a Gradle module
├── package.json                    pnpm-managed; deps: firebase-admin, firebase-functions, luxon
├── tsconfig.json                   strict, target ES2022
├── .eslintrc.cjs                   eslint:recommended + @typescript-eslint
├── src/
│   ├── index.ts                    exports: onCommentCreated, onMealCreated, weeklyDigest
│   ├── triggers/
│   │   ├── onCommentCreated.ts     Firestore onCreate at crews/{crewId}/meals/{mealId}/comments/{commentId}
│   │   ├── onMealCreated.ts        Firestore onCreate at crews/{crewId}/meals/{mealId}
│   │   └── weeklyDigest.ts         onSchedule "0 9 * * 1" UTC, fans out per crew
│   ├── fcm/
│   │   ├── push.ts                 sendToUid(uid, payload), sendToCrew(crewId, exceptUid, payload)
│   │   └── tokens.ts               readTokens(uid), pruneToken(uid, token)
│   ├── stats/
│   │   └── computeWindow.ts        port of Kotlin ComputeWindow.kt for previous ISO week
│   └── i18n/
│       └── keys.ts                 exported constants: NEW_COMMENT_TITLE, NEW_MEAL_TITLE, …
├── .gitignore                      ignores lib/, node_modules/
└── README.md                       deploy commands, secret setup
```

### 4.2 Trigger: `onCommentCreated`

- **Path**: `crews/{crewId}/meals/{mealId}/comments/{commentId}`
- **Logic**:
  1. Read the parent meal (`crews/{crewId}/meals/{mealId}`) → `authorId`, `dishName`.
  2. If `commentDoc.authorId === meal.authorId`, return early (self-comment, no push).
  3. Look up `accounts/{meal.authorId}/devices/*` → token list.
  4. Build FCM payload:
     ```json
     {
       "notification": { "title": "<localized later>", "body": "<localized later>" },
       "data": {
         "kind": "NewComment",
         "key": "new_comment",
         "crewId": "<c>",
         "mealId": "<m>",
         "commentId": "<x>",
         "commenterName": "<denormalized authorName>",
         "dishName": "<denormalized>"
       }
     }
     ```
     (`notification` block populated server-side from English fallback so OS-level lock-screen text isn't blank; client-side resolution via `data.key` is the source of truth for in-app banners.)
  5. `sendEachForMulticast` to all tokens; on `messaging/registration-token-not-registered`, delete the token doc.

### 4.3 Trigger: `onMealCreated`

- **Path**: `crews/{crewId}/meals/{mealId}`
- **Logic**:
  1. Read the parent crew (`crews/{crewId}`) → `memberIds`.
  2. Recipient set = `memberIds - {meal.authorId}`.
  3. Look up `accounts/{recipient}/devices/*` for each recipient → flat token list.
  4. Build FCM payload with `kind: "NewMealPost"`, `data.dishName`, `data.authorName`, `data.crewName` (denormalized from the meal doc), `crewId`, `mealId`.
  5. `sendEachForMulticast` in one batched call; prune unregistered tokens.

### 4.4 Trigger: `weeklyDigest` (scheduled)

- **Schedule**: `0 9 * * 1` (Pub/Sub Scheduler) in **UTC**. See §4.5 for the timezone trade-off.
- **Logic**:
  1. Compute previous ISO week boundary `[mondayUtc 00:00, mondayUtc 00:00 + 7d)`.
  2. List all crews (`/crews`). For each crew:
     - Read meals where `dayKey >= prevWeekStart && dayKey <= prevWeekEnd && crewId == c`.
     - Run `computeWindow(meals)` → `{ bestMeal, bestCook, mostProlific, mostVoted, mostCriticized }` (each is `{ dishName | authorName, score | count } | null`).
     - If all five are null (no posts that week), skip the crew.
     - Build payload:
       ```json
       {
         "notification": { "title": "...", "body": "..." },
         "data": {
           "kind": "WeeklyDigest",
           "key": "weekly_digest",
           "crewId": "<c>",
           "weekStartIso": "2026-05-12",
           "bestMealDishName": "...",         // null fields omitted
           "bestMealScore": "4.7",
           "bestCookName": "...",
           "bestCookAvg": "4.5",
           "mostProlificName": "...",
           "mostProlificCount": "9",
           "mostVotedDishName": "...",
           "mostVotedVoterCount": "5",
           "mostCriticizedName": "...",
           "mostCriticizedAvg": "2.1"
         }
       }
       ```
     - `sendEachForMulticast` to every member's tokens.

### 4.5 Weekly digest — timezone trade-off

Cloud Scheduler fires once globally. We do **not** persist per-user timezones (privacy + complexity). The job fires Monday 09:00 **UTC** which is:
- 11:00 in Madrid (CET/CEST) — fine.
- 02:00 in California — bad.
- 18:00 in Sydney — fine.

For MVP this is **acceptable**: the user is in Spain. When the app scales beyond CET/CEST users, evolution is straightforward: split the job into one Scheduler entry per supported timezone (`0 9 * * 1` × N timezones), each populating a `targetTz` field; the function filters `accounts` by `accounts/{uid}.timezone == targetTz`. Document this as deferred.

### 4.6 Trigger code shape (TypeScript)

```ts
// src/triggers/onCommentCreated.ts
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { sendToUid } from "../fcm/push";

export const onCommentCreated = onDocumentCreated(
  "crews/{crewId}/meals/{mealId}/comments/{commentId}",
  async (event) => {
    const comment = event.data?.data();
    if (!comment) return;
    const { crewId, mealId } = event.params;
    const mealRef = getFirestore().doc(`crews/${crewId}/meals/${mealId}`);
    const meal = (await mealRef.get()).data();
    if (!meal) return;
    if (meal.authorId === comment.authorId) return; // self-suppression

    await sendToUid(meal.authorId, {
      kind: "NewComment",
      key: "new_comment",
      data: {
        crewId,
        mealId,
        commentId: event.params.commentId,
        commenterName: comment.authorName,
        dishName: meal.dishName,
      },
    });
  }
);
```

(The other two triggers follow the same shape — see plan for full code.)

### 4.7 Deploy & secrets

- `firebase.json` gains a `"functions"` block pointing at `functions/`.
- `pnpm dlx firebase-tools deploy --only functions --project foodrats-de4ec` to deploy.
- No secrets required — `firebase-admin` initializes from the runtime service account.
- Cloud Scheduler entry created automatically when `onSchedule` is deployed; visible in Cloud Console under "Cloud Scheduler".
- `.firebaserc` already targets `foodrats-de4ec`; no change.

## 5. Client — Kotlin (`:feature:notifications`)

### 5.1 Domain additions

**`ReminderKind`** — extend the existing enum:

```kotlin
enum class ReminderKind {
    // existing
    StreakAtRisk,
    WeeklyRoundupReady,
    // new
    NewComment,
    NewMealPost,
    WeeklyDigest,
    DailyInactivity,  // alias for StreakAtRisk semantics — see §5.4
}
```

Actually, since `StreakAtRisk` already names the same concept as "DailyInactivity" (the 14:00 nudge), **we reuse `StreakAtRisk`** and add only the three server-driven kinds: `NewComment`, `NewMealPost`, `WeeklyDigest`. `WeeklyRoundupReady` is removed — it was speculative and never fired; `WeeklyDigest` replaces it.

**Final `ReminderKind`:**
```kotlin
enum class ReminderKind {
    StreakAtRisk,    // local — 14:00 daily inactivity reminder
    NewComment,      // push — someone commented on your meal
    NewMealPost,     // push — a crewmate published a meal
    WeeklyDigest,    // push — Monday recap of last week
}
```

**`Reminder`** gains a typed payload union — but keeping it flat to honor the "data object leaves" pattern, we extend the data class with optional fields:

```kotlin
data class Reminder(
    val id: String,
    val kind: ReminderKind,
    val deliverAt: Instant,
    val title: String,
    val body: String,
    val payload: ReminderPayload = ReminderPayload.None,
)

sealed interface ReminderPayload {
    data object None : ReminderPayload
    data class Comment(val crewId: String, val mealId: String, val commentId: String) : ReminderPayload
    data class Meal(val crewId: String, val mealId: String) : ReminderPayload
    data class WeeklyDigest(val crewId: String, val weekStartIso: String) : ReminderPayload
}
```

The payload is **read but never written** in v1 — deep linking from notification tap is a follow-up. For now `RootComposable` displays an in-app banner with `title`/`body` only.

### 5.2 i18n — new keys

Add to `NotificationStringKey`:

```kotlin
NewCommentTitle(Res.string.notifications_new_comment_title),       // "%1$s commented on your %2$s"
NewCommentBody(Res.string.notifications_new_comment_body),         // "Tap to read"
NewMealPostTitle(Res.string.notifications_new_meal_post_title),    // "%1$s posted a meal"
NewMealPostBody(Res.string.notifications_new_meal_post_body),      // "%1$s — tap to view"
WeeklyDigestTitle(Res.string.notifications_weekly_digest_title),   // "Your week in food"
WeeklyDigestBody(Res.string.notifications_weekly_digest_body),     // multi-line with all five
InactivityReminderTitle(Res.string.notifications_inactivity_title),// "Hungry?"
InactivityReminderBody(Res.string.notifications_inactivity_body),  // "You haven't posted a meal today. Snap one before dinner."
```

en + es entries added to both `composeResources/values/strings.xml` and `composeResources/values-es/strings.xml`. Plurals not required (single-author titles; the weekly digest body is parameterized).

### 5.3 Incoming-push payload mapping

A new `PushPayloadMapper` (`:feature:notifications` commonMain) parses the FCM `data` map (which arrives as `Map<String, String>` on both platforms via the existing platform receivers) into a `Reminder`:

```kotlin
class PushPayloadMapper(private val clock: Clock) {
    fun toReminder(data: Map<String, String>): Reminder? {
        val key = data["key"] ?: return null
        return when (key) {
            "new_comment" -> Reminder(
                id = data["commentId"].orEmpty(),
                kind = ReminderKind.NewComment,
                deliverAt = clock.now(),
                title = "%1\$s commented on your %2\$s"
                    .format(data["commenterName"].orEmpty(), data["dishName"].orEmpty()),
                body = "Tap to read",
                payload = ReminderPayload.Comment(
                    crewId = data["crewId"].orEmpty(),
                    mealId = data["mealId"].orEmpty(),
                    commentId = data["commentId"].orEmpty(),
                ),
            )
            "new_meal_post" -> Reminder(/* … */)
            "weekly_digest" -> Reminder(/* … */)
            else -> null
        }
    }
}
```

For the MVP the title/body are **English fallback** mirroring the server payload (the OS notification will already have the localized text from the server's `notification` block; the in-app banner shows the same text via `Reminder.title` / `Reminder.body`). Full client-side localization via `StringKey` is a follow-up.

The Android `FirebaseMessagingService` and iOS Firebase Messaging delegate already call `HandleIncomingPushUseCase`; they're updated to consult `PushPayloadMapper` instead of the current no-op stub.

### 5.4 Daily inactivity reminder (local)

**New use case**: `ScheduleDailyInactivityReminderUseCase` — schedules a daily-recurring local notification at 14:00 device-local. Replaces `ScheduleStreakNudgeUseCase` (which was one-shot at 19:00).

**Trigger time**: 14:00 device-local, fired by:
- **Android**: WorkManager `PeriodicWorkRequest` with `flexInterval = 15min` and initial delay computed to land at the next 14:00 (subsequent runs auto-repeat every 24h).
- **iOS**: `UNCalendarNotificationTrigger` with `DateComponents(hour: 14, minute: 0)` and `repeats: true`.

**Pre-fire has-posted check** — the worker queries the active crew for today's meals by the current user:

- New port in `:core:domain`: `HasPostedTodayPort.hasPosted(accountId: AccountId, crewId: CrewId, day: MealDay): Result<Boolean, MealReadError>` — adapter in `:feature:meal/data/firebase` issuing a Firestore `documentId == crewId + "_" + uid + "_" + dayKey + "_*"` query, or simply listing the three known slot IDs and checking existence.
- The worker reads `Session.activeAccountId` + `Session.activeCrewId` from the existing `SessionPort`; if either is null, no-op.
- If `hasPosted == true`, return without posting the notification. If `false` (or query fails), post the notification anyway — false negatives are better than missed nudges.

**Existing `StreakNudgeWorker` is renamed** to `DailyInactivityWorker` and gains the pre-fire check.

### 5.5 Notification permission flow

Unchanged. The existing `NotificationPermissionScreen` already covers Android 13+ `POST_NOTIFICATIONS` and iOS `UNUserNotificationCenter.requestAuthorization`. No new permission is required for FCM data messages.

### 5.6 Token registration

Unchanged. The existing `RegisterDeviceTokenUseCase` writes to `accounts/{uid}/devices/{token}`. Tokens automatically include their platform (`"android"` or `"ios"`), which the Cloud Function uses to pick the right FCM channel.

## 6. Firestore schema additions / changes

No new collections. New denormalization on existing docs to keep Cloud Function reads cheap:

- **`MealDto`** — already has `authorName`, `dishName`. Add `crewName` (currently not on the meal doc; the function would otherwise need an extra read of `/crews/{c}`). Backfill is **not** required — Cloud Functions tolerate `null` and fall back to "your crew" in the push body. New posts after deploy will populate the field.
- **`crewCodes` / `crews`** — no change.
- **`MealCommentDto`** — already has `authorId`, `authorName`. No change.

## 7. Errors

New leaves added to `NotificationError`:

```kotlin
sealed interface NotificationError {
    sealed interface Permission : NotificationError { /* existing */ }
    sealed interface Token       : NotificationError { /* existing */ }
    sealed interface Schedule    : NotificationError {
        // existing
        data object Failed : Schedule
        // new
        data object HasPostedCheckFailed : Schedule  // pre-fire check threw; we'll fire anyway
    }
    sealed interface PayloadParse : NotificationError {  // new group
        data object MissingKey : PayloadParse
        data object UnknownKind : PayloadParse
        data object MalformedFields : PayloadParse
    }
}
```

`NotificationErrorToStringKey` extended exhaustively; `NotificationErrorToStringKeyTest` updated.

## 8. Testing

### 8.1 Client commonTest

- `PushPayloadMapperTest` — `data` map with all three `key`s → correct `Reminder`; unknown key → null; missing required fields → null.
- `ScheduleDailyInactivityReminderUseCaseTest` — uses `FakeLocalReminderScheduler` + Turbine; verifies schedule call with `kind = StreakAtRisk` and `deliverAt` at next 14:00 local.
- `HasPostedTodayCheckTest` — fake `MealReadPort` returning empty / non-empty; verifies worker decision.
- `NotificationErrorToStringKeyTest` — extended to cover new error leaves (exhaustiveness enforced by `when` over sealed interface).

### 8.2 Backend tests

Cloud Functions use the Firebase Emulator Suite for local testing. **Out of scope for v1 spec** — manual end-to-end smoke test via real device after deploy is the verification. A follow-up adds `firebase-functions-test` units.

### 8.3 Verification commands

| Layer | Command |
|---|---|
| Client domain + presentation | `./gradlew :feature:notifications:testAndroidHostTest :core:domain:testAndroidHostTest` |
| Architecture | `./gradlew :core:domain:testAndroidHostTest --tests "*KonsistRulesTest*"` |
| Backend lint | `cd functions && pnpm run lint && pnpm run build` |
| Backend deploy (manual) | `pnpm dlx firebase-tools deploy --only functions --project foodrats-de4ec` |
| Smoke test on device | (1) install signed debug APK on Android + run iOS sim. (2) Account A comments on Account B's meal → verify B receives push within 5s. (3) Account A publishes meal → verify B, C, D in same crew receive push. (4) Force-fire `weeklyDigest` from Cloud Console → verify all crew members receive it. (5) Skip posting all day → verify 14:00 reminder fires on device. |

## 9. Out of scope (deferred)

- Deep linking from notification tap → in-app destination (payload is parsed but not consumed in v1).
- Per-user timezone for the weekly digest (UTC global fire is fine until non-CET users join).
- Full client-side localization of incoming push titles/bodies (server's `notification` block uses English fallback for OS-level display in v1; the `data.key` + interpolation params are sent so the client can localize later).
- Notification bundling / grouping (multiple comments on same meal → single push with count).
- Quiet hours / per-kind mute settings.
- Cloud Functions unit tests via `firebase-functions-test` (manual smoke covers v1).
- Reply-to-comment threading (comments stay flat).
- Push for new ratings (only comments and meals).

## 10. Rollout & runbook

1. Upgrade Firebase project to **Blaze plan** (one-time, manual in Firebase Console; account requires billing card).
2. From repo root: `cd functions && pnpm install && pnpm run build` — verifies TypeScript compiles.
3. Deploy: `pnpm dlx firebase-tools deploy --only functions --project foodrats-de4ec`. Expected output: three function URLs + one scheduled job. Quote the final `✔  Deploy complete!` line.
4. Verify Cloud Scheduler shows the weekly job at `0 9 * * 1` (Cloud Console → Cloud Scheduler).
5. Install Android + iOS dev builds with the new client changes.
6. Run the §8.3 smoke test sequence.
7. If smoke passes, merge the worktree branch to `main` and tag.

A documentation file `docs/cicd-runbook.md` already exists for CI/CD; this rollout is one-time and lives inline in this spec rather than in the runbook.

## 11. Spec self-review notes

- **Placeholders**: none.
- **Internal consistency**: `ReminderKind` final list (§5.1) matches `PushPayloadMapper` keys (§5.3) and Cloud Function `kind` values (§4.2 / §4.3 / §4.4).
- **Scope**: single implementation plan reachable — backend code, client domain/data/i18n changes, and the local-reminder swap fit one PR.
- **Ambiguity check**: the "all five awards in one push" body length is bounded by FCM's 4 KB data message limit and the OS notification body limit (~512 chars on Android, ~200 visible on iOS lock screen); the v1 body intentionally truncates to "Best meal: X (4.7★) · Best cook: Y · Most prolific: Z (9 posts) · Most voted: A · Most criticized: B" — about 150 chars worst case. Plan task includes a unit test on body assembly length.
