# Offline-first test lane — AI-free re-runs

Two scripts, no AI involved. Born from the 2026-07-15 orchestrated offline-first hardening
(`docs/session/2026-07-14-code-cleaning/OFFLINE-FIRST-PLAN.md` + `REPORT-offline-first.md`).

## `run-host-tests.sh`
Runs every module test task with offline-first coverage (outbox runner/store/repo/migration/pruner,
draft queue + retry runner, meal/crew sync engines + local stores, outbox command handlers
auth/crew/meal, connectivity VM, queue-UI Robolectric tests) and prints PASS/FAIL.

```sh
scripts/test/offline-first/run-host-tests.sh              # full module suites (recommended)
scripts/test/offline-first/run-host-tests.sh --filtered   # only the offline-first classes
```

## `emulator-smoke.sh`
Drives a booted emulator through the real offline flows via adb + uiautomator text assertions:
- **S1** go offline (airplane mode **plus** `svc wifi/data disable` — airplane alone does NOT cut
  emulator connectivity) → edit crew tagline → "waiting to sync" pill on Feed → reconnect → drain
  (asserted via pill disappearing + `FR/Outbox replayed … (Success)` logcat marker).
- **S3** force-stop mid-queue while offline → relaunch → pill restored from disk → reconnect → drain.
- **S2** (offline meal publish → "waiting to publish" → auto-publish on reconnect) is verified but
  NOT scripted — the composer path (camera shutter, scroll, dish text, confirm) is too brittle for
  unattended sh. Manual recipe in `docs/session/2026-07-14-code-cleaning/REPORT-offline-first.md`.

```sh
SERIAL=emulator-5554 scripts/test/offline-first/emulator-smoke.sh
```

Preconditions: emulator booted (`emulator -avd pixel_7_pro_36 -no-window`), app installed
(`./gradlew :androidApp:installDebug`), **signed in once manually** (owner@a.com / 123456,
crew "walk crew") — sign-in state persists in the AVD. The script restores connectivity and
reverts its tagline edit on exit.

## UI text markers (stable assertion anchors)
- Offline banner: `You're offline — changes sync when you're back.`
- Outbox pill (Feed only): `N waiting to sync`
- Draft-publish pill (Feed only): `N waiting to publish`
- Logcat drain: `FR/Outbox replayed <Cmd> (Success); removing <id>`, `FR/DraftQueue published queued draft <id>; removing`
- Process-death recovery: `FR/Outbox ⚠ reconciling stale Uploading row <id> (<Cmd>) back to Pending at startup`
