# HANDOFF — store-release finalize (2026-06-18)

Pick up here in a new chat. Goal was: "complete everything remaining to upload to App Store + Play Store."

## What's DONE this session (all verified)
1. **Working tree finalized + verified + pushed.** The 119-file accumulated dirty tree
   (minotaur easter egg + full-app-review auto-repairs + crew remove-member + share-card CTAs)
   is committed as `740e746` on `develop` and **pushed to origin/develop**.
   - Fixed 5 auto-repair breakages that broke the KMP build (Native + tests):
     ingredient `remember`-in-LazyListScope; `LocationPermissionLauncherHolderTest` fake;
     `FrLog` `kotlin.concurrent.Volatile`; stats `dayTicker` infinite-loop (reverted, kept
     `HistoricResult`); `StatsViewModelTest` streak `item_id` → Text.
   - **Verified:** 14 host-test suites + `:androidApp:assembleDebug` + `:catalogApp:assembleDebug`
     + `:shared:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL in 34s; functions tsc + vitest 114/114.
2. **Backend fully deployed to prod (foodrats-de4ec)** — see `docs/store-release/DEPLOY-LOG.md`:
   Cloud Functions ✅, Firestore rules ✅, Firestore indexes ✅, Storage rules ✅, Hosting ✅
   (endpoints HTTP-200 verified), IAM Token Creator on the compute runtime SA ✅.
3. **Store-prep docs written:**
   - `docs/store-release/RELEASE-CHECKLIST.md` — master from-scratch runbook (accounts→signing→secrets→privacy→submit→smoke).
   - `docs/store-release/LISTING-COPY.md` — en/es Play + App Store listing copy, ready to paste.
   - `docs/store-release/PUBLICATION.md` (pre-existing) — Data Safety / privacy answers.

## What's LEFT (all human/account/device-gated — NOT codeable)
The user has **no store accounts yet** and wants everything ready. Remaining, in order
(full detail in `docs/store-release/RELEASE-CHECKLIST.md`):
1. **Catalog seed** (the only leftover backend step): `gcloud auth application-default login`
   then `pnpm --dir functions seed:catalog`. (Needs ADC — interactive login.)
2. **Phase 1 accounts**: pay Apple ($99/yr) + Google Play ($25); create app records (`es.schsebastian.foodrats`).
3. **Phase 2 signing→CI**: upload keystore + match certs + ASC API key + Play SA JSON → GitHub Environment secrets
   (table in `docs/cicd-runbook.md` §0.4). Restrict Maps API key. Stand up the self-hosted Mac runner.
4. **Phase 3 Xcode** (once): SPM packages, add `StoryShareBridge.swift` + `PrivacyInfo.xcprivacy` to target,
   Associated Domains capability.
5. **Phase 4 privacy/listing**: Play Data Safety + ASC App Privacy (PUBLICATION.md); paste LISTING-COPY;
   capture 5 screenshots on device; fill assetlinks SHA-256 → redeploy hosting.
6. **Phase 5 first upload**: manual first AAB to Play; generate+commit Baseline Profile; then merge→main (beta) / tag (prod).
7. **Phase 6 on-device smoke** (both platforms, minified release): full critical path — `docs/session/human.md` §E.

## Key facts for the next session
- firebase-tools + gcloud are authenticated locally (project foodrats-de4ec). ADC is NOT set up.
- Gen-2 functions runtime SA = `475840003160-compute@developer.gserviceaccount.com` (use for any IAM, NOT @appspot).
- Do NOT run parallel `./gradlew` (daemon/config-cache contention) — verify serially.
- iOS link + host suite together ~35s when warm; if a stats test "hangs" at 100% CPU it's an infinite ticker flow.
- The deploys ran without `--force` (no function deletions). Don't add `--force` blindly.

## Pending git
The doc updates from this session (DEPLOY-LOG, HANDOFF, updated PROGRESS, CLAUDE.md) are uncommitted
after `740e746` — commit them if you want them in history.
