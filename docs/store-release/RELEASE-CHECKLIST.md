# FoodRats — master release checklist (from zero to "live on both stores")

Single ordered runbook. Everything the repo can do is automated (CI/CD pipeline + this session's
deploys); everything else is a human/console/device step **only you can do** (accounts cost money,
consoles need your login, smoke walks need a device). Detailed references in parentheses.

Legend: ✅ done in repo · 🤖 I ran it this session · 👤 you must do it · ⏳ blocked on a prior step.

> Cost: **Apple Developer Program 99 USD/yr + Google Play 25 USD once.** Nothing else (CI is free
> Linux + your Mac as the iOS runner).

---

## Phase 0 — Backend live (no store account needed; makes the app actually work)

- 🤖/👤 **Deploy Cloud Functions, Firestore rules, Storage rules, Firestore indexes, Hosting** — see
  `docs/store-release/DEPLOY-LOG.md` for exactly what ran this session and any leftover commands.
  Order matters: functions → rules → storage → indexes → hosting (`docs/cicd-runbook.md` §6).
- 🤖 **IAM: Service Account Token Creator** on `foodrats-de4ec@appspot.gserviceaccount.com` (signed
  image URLs / data export). (cicd-runbook §6)
- 👤 **Seed the catalog** (ingredients + cuisines) — needs Application Default Credentials, which I
  can't create headlessly. Run once:
  ```bash
  gcloud auth application-default login        # one-time, interactive (browser)
  pnpm --dir functions seed:catalog
  ```
  Until run, the ingredient picker + cuisine passport read empty catalogs (app still works).

## Phase 1 — Store accounts & app records (👤, the money + identity gate)

**Google Play**
- 👤 Pay the 25 USD developer registration; create the app with package `es.schsebastian.foodrats`.
- 👤 Enroll in **Play App Signing** (Google holds the app key; you hold only the upload key).
- 👤 Generate the upload key locally (store the `.jks` + passwords in a password manager, NOT the repo):
  ```bash
  keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
  ```
- 👤 Create a Google Cloud **service account** for Play, grant it release access in Play Console →
  Users & permissions, download its JSON. (cicd-runbook §0.1)

**Apple**
- 👤 Pay the 99 USD/yr Apple Developer Program.
- 👤 Create App ID `es.schsebastian.foodrats` + the app record in App Store Connect.
- 👤 Create an **App Store Connect API Key** (App Manager role), download the `.p8` (one chance),
  note Key ID + Issuer ID.
- 👤 Create a private empty repo for fastlane **match**, then from your Mac:
  ```bash
  bundle install && bundle exec fastlane match appstore   # MATCH_GIT_URL + MATCH_PASSWORD in env
  ```

## Phase 2 — Signing material into CI (👤)

- 👤 Add the GitHub **Environments** `beta` and `production` (production = required reviewers,
  restrict to `v*` tags). (cicd-runbook §0.4)
- 👤 Load the secrets table from `docs/cicd-runbook.md` §0.4 into the environments
  (`GOOGLE_SERVICES_JSON`, `ANDROID_KEYSTORE_*`, `PLAY_SERVICE_ACCOUNT_JSON`,
  `GOOGLE_SERVICE_INFO_PLIST`, `ASC_*`, `MATCH_*`, …).
- 👤 **Restrict the Maps API key** in Google Cloud Console (package + SHA-1 + Static Maps API only)
  — it ships in the AAB. (cicd-runbook §0.4 "Restricción de la Maps API key")
- 👤 Stand up the **self-hosted Mac runner** labelled `macos` (Xcode + JDK 21 + Ruby 3.3).
  (cicd-runbook §0.3)
- ✅ Real `google-services.json` (Android) + `GoogleService-Info.plist` (iOS) — placeholders on disk;
  swap in the real ones from the Firebase project before the first signed build. (root CLAUDE.md)

## Phase 3 — Xcode wiring you must do once (👤, commit `project.pbxproj`)

- 👤 Add SPM packages: Firebase (Auth/Firestore/Storage/Crashlytics/Functions/Messaging) +
  GoogleSignIn; link `CoreLocation.framework`; add MediaPipe via CocoaPods (already integrated).
  (cicd-runbook §0.2, root CLAUDE.md "iOS status")
- 👤 Add to the `iosApp` target: `StoryShareBridge.swift` (Compile Sources) and
  `PrivacyInfo.xcprivacy` (**Copy Bundle Resources** — Apple rejects the build without it).
  (PUBLICATION.md D2)
- 👤 Signing & Capabilities → **Associated Domains** (`applinks:foodrats-de4ec.web.app`); build with
  `-allowProvisioningUpdates`. (PUBLICATION.md D3)
- 👤 Confirm `LSApplicationQueriesSchemes` (`instagram-stories`, `instagram`) survives the build.

## Phase 4 — Privacy & store-content declarations (👤, required to submit)

- 👤 **Google Play Data Safety** form — exact answers in `docs/store-release/PUBLICATION.md` D1.
- 👤 **App Store Connect App Privacy** labels — same data types (PUBLICATION.md "App Store Connect").
- 👤 **Account-deletion URL** in both consoles: https://foodrats-de4ec.web.app/account-deletion ✅ live.
- 👤 **Listing copy + screenshots + feature graphic** — paste from `docs/store-release/LISTING-COPY.md`;
  capture the 5 screenshots on a device/emulator (LISTING-COPY "Screenshots checklist").
- 👤 Content rating questionnaire (Play) + Age rating (ASC) → target Everyone / 4+.
- ⏳ **Android App Links verification**: fill the 2 SHA-256 fingerprints in
  `website/.well-known/assetlinks.json` (from Play Console → App integrity → App signing), then
  `pnpm dlx firebase-tools deploy --only hosting --project foodrats-de4ec`. Until then https invite
  links open the browser (custom-scheme fallback still works). (PUBLICATION.md D3)

## Phase 5 — First upload & release (👤 trigger, 🤖 pipeline builds)

- 👤 **First Android AAB must be uploaded manually** once (Play requires it before the API works):
  ```bash
  ANDROID_KEYSTORE_PATH=$PWD/upload.jks ANDROID_KEYSTORE_PASSWORD=… ANDROID_KEY_ALIAS=upload \
  ANDROID_KEY_PASSWORD=… ./gradlew :androidApp:bundleRelease -PversionName=0.1.0 -PversionCode=10001
  ```
- 👤 **Generate + commit the Baseline Profile** on a device/emulator (faster cold start; not in repo):
  `./gradlew :androidApp:generateBaselineProfile` then commit `baseline-prof.txt`. (human.md §E)
- 👤 **Paste "What's new" release notes into each console, every release** — no lane uploads them
  (every `fastlane/Fastfile` lane sets `skip_upload_metadata`/`skip_metadata`). Source text (en/es,
  paste-ready): `docs/store-release/RELEASE-NOTES-vX.Y.Z.md` (current release:
  `docs/store-release/RELEASE-NOTES-v1.11.0.md`) — Play Console release notes, App Store Connect
  "What's New in This Version", and TestFlight "Test Details", all per-locale.
- ✅ **Beta**: merge to `main` → `release-beta.yml` ships Play Internal + TestFlight automatically.
- ✅ **Production**: `git tag vX.Y.Z && git push origin vX.Y.Z` → `release-production.yml`, held for
  your approval in the `production` environment, then Play 20% staged + App Store phased. (cicd-runbook)
- 👤 **FIRST production release only (bootstrap — learned from the v1.11.0 attempt, 2026-07-14):**
  a never-published Play app rejects staged rollouts ("Precondition check failed"), and the first
  App Store review submission fails until the ASC listing is complete (pricing, age-rating
  questionnaire, copyright, screenshots incl. iPad Pro 12.9 — the binary upload itself succeeds).
  The runner's `~/.config/foodrats/signing.env` carries two bootstrap flags the lanes honor:
  `PLAY_RELEASE_STATUS=draft` (AAB lands as a DRAFT release on the production track — publish it
  in the Play Console, which is also where Google's first-publish review starts) and
  `IOS_SUBMIT_FOR_REVIEW=false` (build uploads + attaches to the version; complete the listing and
  press "Submit for Review" in ASC). **Remove both flags after each store has its first published
  release** so tagged releases resume the fully automatic staged/phased flow.

## Phase 6 — On-device smoke before trusting the release (👤, the only real end-to-end check)

Run the full checklist in `docs/session/human.md` §E on **both** Android + iPhone, on the **minified
release** build (R8 can break things host tests can't see). Critical path: sign-in → crew →
publish meal → feed → stats → notification → account deletion → data export → offline compose →
share card. Quote results.

---

## Pointers
- Backend deploy detail + what I ran: `docs/store-release/DEPLOY-LOG.md`
- Privacy/data-safety answers: `docs/store-release/PUBLICATION.md`
- Listing copy (en/es): `docs/store-release/LISTING-COPY.md`
- Per-release "What's new" copy (en/es, paste-ready, one file per version): `docs/store-release/
  RELEASE-NOTES-vX.Y.Z.md` — current: `docs/store-release/RELEASE-NOTES-v1.11.0.md`
- Full manual-gate list: `docs/session/human.md`
- Pipeline operations: `docs/cicd-runbook.md`
