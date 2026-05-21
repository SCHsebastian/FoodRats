# :feature:notifications

Notification permission gateway, FCM token registration, and streak-nudge local notifications via WorkManager (Android) / `UNUserNotificationCenter` (iOS).

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §11 (Notifications), §11.2 (permission gateway), §11.4 (streak nudges = local notifications), §11.5 (incoming push handling).
- Module README — `feature/notifications/README.md`.
- Root `CLAUDE.md` — "Module graph", "Active tech debt" (i18n for WorkManager — `PublishMealViewModel` resolves nudge title/body via `getString(NotificationStringKey.Streak{Title,Body}.resourceId)`, wrapped in try/catch so unit tests without bundled resources stay green).

## Local rules

- Streak nudges are **local notifications**, scheduled via WorkManager on Android. Don't reach for FCM data messages for the same job — FCM is only for incoming pushes from the server.
- iOS Firebase Messaging delegate is wired in `AppDelegate.swift` (APNS registration + `Messaging.delegate`). Don't duplicate that wiring in Kotlin.
- JVM target **17** (Firebase BOM).

## Test

`./gradlew :feature:notifications:testAndroidHostTest`.
