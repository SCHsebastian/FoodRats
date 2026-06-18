# HUMAN — manual steps only you can do

Everything in this file is work the orchestrated build CANNOT do from code (deploys, cloud IAM,
store-console declarations, on-device smoke walks, provisioning). The build is green in the repo;
these are the gates before any of it is live or shippable. Grouped by type, deduplicated, with the
task that surfaced each in parentheses. **This file is appended to as new tasks land.**

> Suggested order: **(A) deploy backend → (B) cloud IAM → (C) indexes → (D) store/privacy → (E) on-device smoke.**

> **✅ 2026-06-18 — Blocks A, B, C are ALL DONE on prod foodrats-de4ec** (functions, firestore rules+indexes,
> storage rules, hosting, IAM Token Creator on the Gen-2 compute SA, catalog seed). Evidence:
> `docs/store-release/DEPLOY-LOG.md`. CI is green on `develop`; the R8 release AAB builds.
> Remaining = blocks **D (store/privacy)** + **E (on-device smoke)** + accounts/signing — see
> `docs/store-release/RELEASE-CHECKLIST.md`.

---

## A. Deploys (run from repo root; use `pnpm dlx firebase-tools`)

- [ ] **Deploy Cloud Functions** — adds `deleteAccount`, `exportMyData`, `streakNudge`, `onPlateImageFinalized` (+ refactored `weeklyDigest`).
  `pnpm --dir functions deploy`  *(or `pnpm dlx firebase-tools deploy --only functions --project foodrats-de4ec`)*
  Note: `onPlateImageFinalized` is a NEW Storage `onObjectFinalized` trigger that needs Storage READ (download plate) + WRITE (upload `_thumb.jpg`) + Firestore WRITE (`thumbHash`/`thumbnailPath` on the meal); the default Functions runtime service account already holds these, but if you tightened it, grant `roles/storage.objectAdmin` + `roles/datastore.user`. It bundles `sharp` (a native dep) — the Gen-2 Linux build image compiles it automatically on deploy. The watched bucket is pinned to `foodrats-de4ec.firebasestorage.app` in code.
  (w0-account-deletion-data, w0-data-export-function, w1-streak-nudges-function, w5-image-pipeline-function)
- [ ] **Deploy Firestore rules** — required for: account-deletion `nudges` deny, blind-voting owner write, export Storage path.
  `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`
  (w1-streak-nudges-function, w1-blind-voting-data/presentation, w1-reactions-data)
- [ ] **Deploy Storage rules** — required for data-export upload path `exports/{uid}/…`.
  `pnpm dlx firebase-tools deploy --only storage --project foodrats-de4ec`  *(note: `--only storage`, not `storage:rules` — see CLAUDE.md)*
  (w0-data-export-function)
- [ ] **Deploy order matters** for functions↔rules: per `docs/cicd-runbook.md` §6, deploy functions BEFORE rules where a rule references a new resource. Follow the runbook.

- [ ] **Run the catalog seed** (now also uploads `cuisines` + `dishCuisineMap` alongside ingredients) — until run, the cuisine passport (and ingredient picker) read empty catalogs.
  `GOOGLE_APPLICATION_CREDENTIALS=<admin-sa.json> pnpm --dir functions seed:catalog`
  (w2-cuisine-passport-seed; also the pre-existing meal-ai ingredient seed)

## B. Cloud IAM

- [ ] **Grant the Functions service account the `Service Account Token Creator` role** — without it, `exportMyData` V4 signed download URLs fail (no image/data download). (w0-data-export-function, w0-data-export-presentation)

## C. Firestore indexes

- [ ] **Create any flagged collection-group indexes on `authorId`** — `deleteAccount` and `exportMyData` query the caller's meals/comments/votes across crews. Run the function once; Firestore's error gives the exact index-creation link, or pre-create per the function code. (w0-account-deletion-data, w0-data-export-function)

## D. Store / privacy declarations (required for submission)

- [ ] **Google Play Data Safety form** — declare analytics collection (consent-gated), account deletion in-app + the deletion URL, data export. (w0-consent-ui, account-deletion)
- [ ] **iOS `PrivacyInfo.xcprivacy`** — add the privacy manifest with `NSPrivacyTracking=false` and the collected-data types. (w0-consent-ui-presentation)
- [ ] **Manifest / plist collection-disabled defaults** — analytics SDK defaults to collection-off until consent (Android manifest meta-data + iOS Info.plist). (w0-consent-ui-presentation)
- [ ] **16-KB page size** — already FIXED in code (MediaPipe 0.10.35, `.so` now 16-KB aligned); no action, just confirm the uploaded AAB passes Play's pre-launch 16-KB check. (w0-16kb-alignment)

## D3. Deep-linked invites — hosting & web unfurl (w3-deep-linked-invites-presentation)

- [ ] **Host the well-known association files for the invite host** — `https://foodrats.app/.well-known/assetlinks.json` (Android App Links, with the Play/upload SHA-256 fingerprints) and `apple-app-site-association` (iOS Universal Links), and wire **Associated Domains** (`applinks:foodrats.app`) into the `iosApp` Xcode target. Until hosted, the `https://foodrats.app/invite/{code}` link still works via the `foodrats://app/invite/{code}` custom-scheme fallback, and tapping the https link opens the browser instead of the app. The contract files already live under `deeplinks/`; the invite arm adds no new host/scheme (path-only), so no manifest change was needed. (Shared with the meal/crew/digest deep links — same association files.)
- [ ] **Web unfurl / OG page for `/invite/{code}`** — the spec §3.2 "rich previews" wants a Firebase Hosting page (static or Cloud Function) at `/invite/{code}` that serves OG/Twitter meta (crew name) so the link unfurls in chat apps AND gives a recipient *without* the app a "get the app" landing page. NOT built in this task (the in-app rich preview screen IS built and live). Implement when ready; it's purely server/hosting.
- [ ] **(No new third-party dependency)** — QR generation uses a self-contained pure-Kotlin encoder in `:core:designsystem` (`atoms/qr/`), not a library, so there is nothing to vet/add to `libs.versions.toml`. Noted here only so a future audit doesn't go looking for one.

## D2. iOS Xcode wiring (share cards)

- [ ] **Add `iosApp/iosApp/StoryShareBridge.swift` to the `iosApp` Xcode target** — it's a NEW Swift file; Xcode must compile it (the Swift glue is not xcodebuild-verified in this env). It mirrors `ShareBridge.swift` and calls the verified-exported `MainViewController(... storyShare:)` symbol + standard UIKit APIs. (w3-shareable-cards-platform)
- [ ] **Confirm `Info.plist`'s `LSApplicationQueriesSchemes` (`instagram-stories`, `instagram`) is in the built app's plist** — without it `canOpenURL` returns false and every share falls back to the system sheet. Already added to `iosApp/iosApp/Info.plist`; just verify it survives the build. (w3-shareable-cards-platform)

## E. On-device smoke walks (the only thing that confirms real end-to-end behavior)

- [ ] **Account deletion** — sign in → Profile → delete account → confirm cascade (meals/comments/votes gone, signed out, analytics reset). (account-deletion trio)
- [ ] **Analytics consent** — first run shows consent gate; Deny → analytics stays no-op; Profile toggle revoke/re-grant works; current-version Deny does NOT re-prompt. (w0-consent-ui)
- [ ] **Data export** — Profile → "Export my data" → receive signed URL → download → verify JSON has only YOUR data (no other members' PII). (w0-data-export)
- [ ] **Streak/social nudge** — AFTER functions deploy: with a crew where someone posted today and you didn't, confirm the hourly `streakNudge` push arrives (opens Feed), and you are NOT double-nudged (local `DailyInactivityWorker` is now disabled). ⚠️ Until functions are deployed there is NO daily nudge at all. (w1-streak-nudges)
- [ ] **Blind voting** — owner enables the CrewSettings toggle → in feed, other members' meal authors are hidden (name+avatar) until you rate, then revealed; your own meals always show. (w1-blind-voting)
- [ ] **Image pipeline — feed placeholder + on-device compression** (w5-image-pipeline-presentation) — the ONLY verification of the real ThumbHash blur placeholder and the on-device JPEG downscale (no host test can assert pixels or codec output). AFTER the functions deploy (`onPlateImageFinalized` trigger — see §A) so `thumbHash`/`thumbnailPath` actually get written: (1) Publish a meal; within a few seconds scroll the feed and confirm each card briefly shows a **blurred placeholder** that resolves into the small thumbnail (vs. the old flat grey box). Open meal detail and confirm the **full** image loads (blur placeholder first, then sharp). (2) Confirm a freshly-published meal (before the trigger runs) still loads via the full image with NO crash (null thumbHash → flat placeholder fallback). (3) Compression: publish a large-resolution capture and confirm in the Firebase Storage console that the uploaded `crews/{crewId}/meals/{mealId}.jpg` is downscaled (longest edge ≤ 1600px, ~80% JPEG quality) vs. the raw camera bytes. Walk on BOTH Android and iPhone — the downscale/re-encode is per-platform (Android `Bitmap`, iOS `UIImage`). Until functions deploy, feed shows the full image for every card (the correct fallback) and there is no thumbnail/placeholder to see yet.
- [ ] **Shareable story cards** — the ONLY verification of the real Instagram intent / URL-scheme + the off-screen card rasterization (no host test can assert pixels). On Android + iPhone WITH Instagram installed (needs the presentation task's share button): Share → confirm the Story opens with the branded card as the full-screen background. Then on a device WITHOUT Instagram → confirm the system share sheet appears with the PNG attached. Requires `androidApp` `FileProvider` (already in manifest + `res/xml/file_paths.xml`) and the iOS Xcode wiring above. Walk it from ALL FOUR surfaces: meal-detail (plate), stats best-plate (award), stats streak (streak), AND **the weekly-recap story player** — open the recap (notification tap or stats "See your week"), advance to a shareable scene (top-meal / streak / your-week), tap **"Share this recap"** in the overlay action bar, confirm the card rasterizes and the share opens. (w3-shareable-cards-platform, w3-recap-share-cta)
- [ ] **Generate + commit the Baseline Profile** (w5-baseline-profiles-CI) — the profile that makes cold start faster is NOT in the repo yet; it must be recorded on a real device / emulator and committed, because the macrobenchmark cannot run in this environment (no device) NOR on a free GitHub runner (no KVM). On a machine with a connected device or a booted emulator (or a CI runner with KVM):
  - Generate (writes `androidApp/src/release/generated/baselineProfiles/baseline-prof.txt`, which the `androidx.baselineprofile` plugin then bakes into the release AAB):
    `./gradlew :androidApp:generateBaselineProfile` (uses the module's `pixel6Api34` Gradle Managed Device — downloads/boots a Pixel 6 / API 34 AOSP-ATD emulator; needs KVM) — **OR** with a device/emulator already connected: `./gradlew :baselineprofile:connectedNonMinifiedReleaseAndroidTest` then `:androidApp:generateBaselineProfile`.
    Then **commit** the generated `baseline-prof.txt` (+ `startup-prof.txt`) so every release ships it.
  - Measure the speed-up (StartupTimingMetric, None vs BaselineProfile): `./gradlew :baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest` (GMD) or `:baselineprofile:connectedBenchmarkReleaseAndroidTest` (connected). Quote the median cold-start figures in the PR.
  - NOTE the app gates everything behind Google Sign-In, so `BaselineProfileGenerator` only walks cold-start → first screen (Splash/SignIn). To profile the feed/composer journey, extend the generator with a signed-in test account or a debug sign-in bypass first.
- [ ] **Offline-first compose** — the ONLY verification of real WorkManager + real connectivity (host tests use fakes). On Android AND iPhone: enable **airplane mode** → compose & publish a plate → confirm it is **queued** (feed top bar shows a pending count once the presentation task lands; the plate does NOT silently fail). Kill the app while still offline, relaunch, confirm the queued draft **survived** (still pending, not lost — process-death durability). Re-enable network → confirm the queued plate **auto-publishes** within a few seconds (Android: WorkManager `NetworkType.CONNECTED` fires; iOS: on next foreground reconnect — iOS background is best-effort, no `BGTaskScheduler` wired). Then confirm **no duplicate meal** appears in the feed (idempotent deterministic `MealId` overwrite). Bonus: keep failing the publish (e.g. bad rules) to confirm backoff retries and, after the attempt budget (5) is exhausted, a **terminal failed** entry surfaces for retry/dismiss rather than retrying forever. (w5-offline-compose-data)

---

## F. CI emulator / release-health gate (w5-baseline-profiles-CI)

- [ ] **(Optional) Run the `baseline-profile` CI job on a KVM-capable runner.** It's wired in `.github/workflows/ci.yml` but is `workflow_dispatch`-only + `continue-on-error` because GitHub's FREE `ubuntu-latest` runners do NOT expose `/dev/kvm`, so the managed-device emulator can't boot there (zero-paid-infra constraint). To actually exercise it in CI: trigger it manually on a **larger** GitHub runner (paid, has KVM) or a **self-hosted Linux** host with KVM, then download the `baseline-profile` artifact and commit `baseline-prof.txt`. Until then, generate it locally (§E above).
- [ ] **Crash-free-rate release gate (roadmap §5.3, NOT built — needs a product decision + creds).** The roadmap calls for a step in `release-production.yml` that reads the Crashlytics crash-free rate (or Play release-health) and HOLDS/FAILS below an SLO. This task scoped only Baseline Profiles + startup macrobenchmarks; the crash-free gate is unbuilt because it needs (1) the **SLO threshold decision** (roadmap open decision #8 — e.g. ≥ 99.5% sessions) and (2) Firebase/Play API credentials in CI. Decide the threshold, then add a job to `release-production.yml` (the protected `production` environment already provides the human-approval control).

## Optional follow-ups (nice-to-have, not blocking)

- [ ] Account deletion: delete the now-dead `CrewError…NotImplemented.RemoveMember`-style leftover + add a CLAUDE.md "Account deletion" decision entry post-deploy. (w0-account-deletion-presentation)
- [ ] imagepicker shutter sound: file an upstream issue for a mute toggle; when it lands, delete the shadow file + the release-only AGP CLASSES transform; add a CI assert that the dex carries a single empty `playShutterSound`. (w0-imagepicker-dup-class)
- [ ] Streak nudge: no server-side analytics sink exists — `streak_nudge_sent` is only `logger.info` today. Consider a server analytics path if you want it in GA4. (w1-streak-nudges-function)
