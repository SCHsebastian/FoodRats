# Web invite landing page — 2026-06-18

Goal: make crew invitation links usable from the **web**, so a recipient without the app
gets a real page (download + open-in-app), not a dead custom-scheme link.

## Decisions
- **Reverses commit `f9f7a9e`'s "app-installed-only" invite contract.** User explicitly asked
  for web invites. `DeepLinks.inviteUrl` now emits the **https** hosting URL, not `foodrats://`.
- **Canonical invite host = `foodrats-de4ec.web.app`** (the live Firebase Hosting domain that
  serves the page + AASA/assetlinks). `foodrats.app` stays the aspirational vanity domain (in
  manifest/entitlements but not yet hosting anything).
- **Static page, no backend reads.** Firestore rules gate `/crews` + `/crewCodes` behind
  `request.auth != null`, so a public page can't resolve the crew name without a Cloud Function.
  Built the functional static page now; dynamic crew-name + rich OG unfurl deferred (needs a
  function + exposes crew names to anyone holding a code).
- **No auto-redirect to custom scheme** (avoids Safari "address invalid" interstitial). Explicit
  "Open in FoodRats" button (user-gesture custom-scheme) + store CTAs. App/Universal Links handle
  the installed+verified case automatically; the button covers "have app, link opened browser".

## How the fallback works pre-verification
- App installed + App/Universal Links verified → OS opens app directly (never hits the page).
- App installed but links NOT yet verified (assetlinks SHA-256 still placeholders / iOS Associated
  Domains not wired in Xcode) → browser loads page → user taps "Open in FoodRats" → custom-scheme
  intent-filter (no verification needed) opens the app.
- No app → page shows the invite code + store badges.

## Steps
- [x] website/invite/index.html (branded landing, open-in-app button, store CTAs, static OG)
- [x] website/index.html — marketing home page (added on user follow-up "do a home page")
- [x] firebase.json rewrite /invite/** -> /invite/index.html
- [x] DeepLinks.inviteUrl -> https hosting URL + KDoc + HOSTING_HOST const
- [x] DeepLinkParserTest invite-builder assertion
- [x] verify :shared:testAndroidHostTest
- [x] deploy hosting + curl HTTP 200
- [x] docs: deeplinks/README updated

## Verification log
- `:shared:testAndroidHostTest --tests *DeepLinkParserTest*` → BUILD SUCCESSFUL;
  `tests="16" skipped="0" failures="0" errors="0"`.
- `python3 -m json.tool firebase.json` → OK (valid JSON).
- Hosting deploy → "Deploy complete!" (5 files), https://foodrats-de4ec.web.app.
- Live curls:
  - `/`                            → HTTP 200, `<title>FoodRats — a private table for your crew</title>`
  - `/invite/AB2K9P`               → HTTP 200, `<title>You're invited to a FoodRats crew</title>` (rewrite OK)
  - `/invite`                      → HTTP 200, invite page (generic, no code)
  - `/account-deletion`            → HTTP 200, unchanged (regression OK)
  - `/.well-known/assetlinks.json` → HTTP 200, `content-type=application/json` (static still wins over rewrite)

## Not done (flagged to user, needs their call / their access)
- **App Store URL is a placeholder** (`idXXXXXXXXXX`) in both pages — no App Store record exists yet.
  Replace once App Store Connect assigns the numeric ID. Play URL is final.
- **Dynamic crew name + rich OG unfurl**: deferred. Needs a Cloud Function (Firestore rules gate
  /crews + /crewCodes behind auth, so a public page can't read the crew). Would also expose crew
  names to anyone holding a code.
- **App Links auto-verification** still pending the assetlinks SHA-256s (Play console) + iOS
  Associated Domains in Xcode — until then the installed-app path relies on the custom-scheme button.
- **Uncommitted** on develop (web + DeepLink.kt + test + firebase.json + docs). Awaiting user go-ahead.
