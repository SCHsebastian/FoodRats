# 012 · w1-streak-nudges-i18n

**Status:** done

**Summary (≤6 lines):**
- Social-nudge push localized (en/es); client `PushPayloadMapper` branches on `key="social_nudge"`, renders localized title/body from `postedCount`/`crewSize`, opens Feed (no deep link). Double-nudge resolved.
- Files: `feature/notifications/.../data/push/PushPayloadMapper.kt` (+test), `.../i18n/NotificationStringKey.kt`, `domain/model/ReminderKind.kt`, `composeResources/values{,-es}/strings.xml`; `feature/meal/.../data/upload/BackgroundMealUploadCoordinator.kt`, `di/MealModule.kt`, `di/MealModuleVerifyTest.kt`.
- Decisions: `ReminderPayload.None` → opens Feed; fixed id `"social-nudge"`; non-numeric count → null. Double-nudge disabled by removing the ONE `scheduleStreakNudge` call + unused `StreakNotificationPort` dep — worker/port/i18n kept, reversible by re-adding one line.
- Blockers: none.

**Verify (quoted):**
```
./gradlew :feature:notifications:testAndroidHostTest → BUILD SUCCESSFUL in 4s (PushPayloadMapperTest 9, +3 new)
./gradlew :feature:meal:testAndroidHostTest → BUILD SUCCESSFUL (MealModuleVerifyTest green)
```

**NOTE for user:** until the server `streakNudge` function is deployed, there is NO daily nudge (local worker now off). Deploy `functions` to activate.

Report: `docs/session/reports/w1-streak-nudges-i18n.md`
