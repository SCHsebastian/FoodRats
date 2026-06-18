# Session: language change + map + configurable reminders (2026-06-18)

Three user asks:
1. Repair in-app **language change** (doesn't work).
2. Repair **map component** (doesn't show correctly).
3. Let user **configure the daily reminder time** (was fixed 14:00), pick **any hour**, and have **up to 3** reminders.

## Findings (from parallel Explore agents + direct diagnosis)

### Language — BROKEN
- Whole persist+observe chain works (`LocalePort`→`LocaleRepository`→DataStore `locale_tag`, observed into `ProfileViewModel`).
- The MISSING link: code references a `LocalAppLocale` CompositionLocal ("Compose Resources honors it") that is **never declared/provided anywhere**. So `resolve()`→`stringResource` always resolves against the OS locale; picking En/Es persists but UI never changes.
- No `recreate()`, no `setApplicationLocales`, no locale CompositionLocal at the root (`FoodRatsApp` observes theme but not locale).
- Fix: implement `LocalAppLocale`/`ProvideAppLocale` (expect/actual) + wire at root keyed on observed `AppLocale`, NavController kept above the `key()` so back stack survives.

### Map — 403 from Google (console issue), NOT empty key
- `googleMapsApiKey` IS set in `~/.gradle/gradle.properties` (39 chars). BuildConfig wiring is correct.
- Direct curl of the Static Maps URL with that key → **HTTP 403 text/plain**: "This API key is not authorized to use this service or API ... check the API restrictions settings."
- Root cause: the **Maps Static API** is not enabled / not in the key's API-restriction allowlist. (Also: a key restricted to "Android apps" can't call the Static Maps **web service** — needs None/IP.)
- Code is otherwise correct; Coil rendered the 398-byte 403 text as a broken image.
- Fix: (a) tell user the console steps; (b) make Android `FrLocationMap` fall back to **OpenStreetMap tiles** (no key) when Google errors, so a real map always shows. iOS already uses MapKit (fine).

### Reminders — feature exists, hardcoded 14:00, currently NOT triggered
- `:feature:notifications` has `LocalReminderScheduler` port + `ScheduleDailyInactivityReminderUseCase` + `AndroidLocalReminderScheduler`(WorkManager periodic) + `IosLocalReminderScheduler`(UNCalendarTrigger).
- Hour hardcoded in 3 places (use case const 14, iOS const 14L). Android honors `Reminder.deliverAt`; **iOS ignores it** (bug to fix).
- Single `DAILY_INACTIVITY_REMINDER_ID`; schedule/cancel are id-keyed → multiple reminders already supported by the plumbing.
- The only former trigger (BackgroundMealUploadCoordinator) was intentionally removed; nothing enqueues today. Must re-add a trigger from settings-save + app launch.
- No reminder-time persistence. Settings live in Profile (`:feature:auth`).

## Plan
- A) Language: LocalAppLocale provider + root wiring. Verify on device R7AX10SF67D.
- B) Map: OSM fallback + console instructions to user.
- C) Reminders: persist List<LocalTime> (max 3) via new port+repo; schedule-all use case; fix iOS deliverAt; re-enqueue on launch; Profile "Meal reminders" editor screen; strings; tests.

## Map key — DIAGNOSED (user said "key is configured")
- Key IS set (`~/.gradle/gradle.properties`, 39 chars). Direct curl → **403 "This API key is not authorized to use this service or API"** → Maps Static API not enabled / not in key's API-restriction allowlist (and Android-app application-restriction blocks web-service calls).
- Action for user: enable Maps Static API + allow it on the key (told them). Code now falls back to OSM so it works regardless.

## Implementation — DONE (pending build verify)
- A) Language: `shared/app/locale/AppLocaleProvider.kt` (+android/+ios actuals), wired in `FoodRatsApp` (NavController above `key`). iOS unchanged (still deep-links). 
- B) Map: `FrLocationMap.android.kt` → Google primary, OSM tile fallback on error/empty key; iOS untouched.
- C) Reminders:
  - core.domain `MealReminderSchedulePort` (+error, MAX=3, DEFAULT=[14:00]); FrLog `Notifications` tag.
  - core.data `MealReminderScheduleRepository` (CSV HH:mm), Keys.MealReminderTimes, CoreDataModule binding.
  - notifications: generalized `ScheduleDailyInactivityReminderUseCase(time,id defaults)`; reactive `MealReminderScheduler` observer (createdAtStart, combines enabled+times); iOS `IosLocalReminderScheduler` now honors deliverAt; DI scope + verify-test extraType.
  - auth: `ProfileError.Reminders.PersistFailed` + mapper; `SetMealRemindersUseCase`; AuthStringKey + en/es; ProfileViewModel (state/intents/observe/handlers); ProfileScreen reminders editor + hour picker; ProfileErrorToStringKey(+test); AuthModule + verify extraType; ProfileViewModelTest fakes + 5 new tests.

## VERIFIED (build + tests + device)
- `:androidApp:assembleDebug` GREEN; iOS `linkDebugFrameworkIosSimulatorArm64` GREEN.
- Host tests GREEN: ProfileViewModelTest 13/13, ProfileErrorToStringKeyTest 15/15, AuthModuleVerifyTest 1/1, ScheduleDailyInactivityReminderUseCaseTest 10/10, NotificationsModuleVerifyTest 1/1, designsystem host tests GREEN.
- **Device (R7AX10SF67D), end-to-end:**
  - Language: switched Español→English in Profile → entire UI flipped instantly, stayed on same screen (back stack preserved). FIX CONFIRMED.
  - Reminders: launch log `[MealReminderScheduler] applied enabled=true times=[14:00]`; WorkManager diag showed `meal-reminder-0 ENQUEUED`, `meal-reminder-1/2 CANCELLED`. Added 08:00 → `set(meal_reminder_times)=08:00,14:00` + reschedule; both rows shown. FIX CONFIRMED (was never scheduled before).

## Follow-up fix: picker not scrollable (user report)
- `FrSettingsPicker` used a plain Column in ModalBottomSheet → 24-hour list overflowed off-screen.
- Fix: `heightIn(max=480.dp)` + `verticalScroll` on the options Column (short theme/language lists still wrap).
- Device-verified: picker scrolls 00:00→23:00; selecting 23:00 → `set(meal_reminder_times)=14:00,23:00` + reschedule; UI shows 14:00 & 23:00.

## Map (3rd ask) — DONE, user-confirmed
- 403 = Maps Static API not authorized for the key (console fix told to user). Android `FrLocationMap` now falls back to key-free OSM tiles on Google error/empty key. **User confirmed it works.**
- Note: LocationSection only renders when the meal has coordinates; several test meals had none (location not captured at compose time).

## Test-left device state (introduced during verification — user can revert)
- App language set to **English** (was System=Spanish) — Profile → Language → System default to restore.
- Meal reminders left at **14:00 + 23:00** (default was 14:00 only).

## Status: ALL DONE & verified — language, map (OSM fallback), configurable reminders (any hour, up to 3), and picker scroll fix.
