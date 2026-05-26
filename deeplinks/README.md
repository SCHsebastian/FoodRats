# Deep links / Universal Links / App Links

FoodRats opens to a specific screen from a URL on both platforms. The Kotlin + Swift wiring is
**done and built green**; what lives here is the **deploy/config** that the OS verifiers require.

## URL contract

The route discriminator is always the **first path segment** (so parsing is scheme/host-agnostic —
see `shared/.../app/navigation/DeepLink.kt`):

| URL | Route |
|---|---|
| `https://foodrats.app/meal/{mealId}/{dayIso}` | `Route.MealDetail` |
| `https://foodrats.app/crew/{crewId}` | `Route.CrewSettings` |
| `foodrats://app/meal/{mealId}/{dayIso}` | `Route.MealDetail` (custom-scheme fallback) |
| `foodrats://app/crew/{crewId}` | `Route.CrewSettings` (custom-scheme fallback) |

Unrecognised URLs are a no-op (parser returns null). All deep-link targets are `Route.Protected`,
so a link opened while signed-out is **stashed and resumed after sign-in** (`RootNavViewModel`).

## How it flows (already implemented)

```
Android: <intent-filter> → MainActivity.onCreate/onNewIntent → DeepLinkBus.publish(uri)
iOS:     onOpenURL / continue:userActivity → IosDeepLinkBridge.shared.receive(uri:) → DeepLinkBus.publish(uri)
                                          ↓
                 RootNavViewModel collects → parseDeepLink(uri) → navigate now | stash until Ready
```

## Notification taps reuse the same contract

FCM pushes carry the canonical deep link in their `data.link` (built server-side by
`functions/.../fcm/push.ts#mealDeepLink`, kept in sync with the table above). A **meal post** or
**comment** push links to `foodrats://app/meal/{mealId}/{dayKey}`; a **reminder** (streak nudge,
weekly digest) carries **no** link, so tapping just opens the app to Feed.

```
Android tap: tray notification → MainActivity gets data as intent extras → publishDeepLink reads "link" → DeepLinkBus
             (local streak nudge: DailyInactivityWorker setContentIntent = launcher intent → opens Feed)
iOS tap:     UNUserNotificationCenter didReceive → userInfo["link"] → IosDeepLinkBridge.receive(uri:) → DeepLinkBus
```

Because the link is a `Route.Protected` target, a tap on a cold-started/signed-out app is stashed
and resumed after sign-in — same intercept-then-resume path as web links.

## Deploy steps (NOT code — must be done by a human)

### 1. Host the association files at the domain root, over HTTPS, no redirects
- `https://foodrats.app/.well-known/apple-app-site-association` ← this folder's `apple-app-site-association`
  - Serve as `Content-Type: application/json`, **no `.json` extension**.
- `https://foodrats.app/.well-known/assetlinks.json` ← this folder's `assetlinks.json`

### 2. Fill in the Android cert fingerprints in `assetlinks.json`
Play App Signing is used, so include **both** the Play app-signing key and the upload key SHA-256:
- Play Console → your app → **Setup → App integrity → App signing**: copy the
  "App signing key certificate" **SHA-256** and the "Upload key certificate" **SHA-256**.
- Or from a keystore locally: `keytool -list -v -keystore <upload.jks> -alias <alias> | grep SHA256`.

Verify after deploy:
`https://developers.google.com/digital-asset-links/tools/generator` or
`adb shell pm verify-app-links --re-verify es.schsebastian.foodrats`.

### 3. Wire the iOS Associated Domains entitlement (one-time, in Xcode)
- `iosApp/iosApp/iosApp.entitlements` already declares `applinks:foodrats.app`.
- In Xcode → target `iosApp` → **Signing & Capabilities** → **+ Capability → Associated Domains**
  (this sets `CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements` and registers the capability on
  the App ID). Build with `-allowProvisioningUpdates` so the profile picks it up.
- AASA is fetched by Apple's CDN at install; test with the link on a real device (Notes → tap link).

The custom scheme (`foodrats://app/...`) needs **no** entitlement — it's already registered in
`Info.plist` `CFBundleURLTypes` and works as a fallback.

## Local testing

- **Android:** `adb shell am start -a android.intent.action.VIEW -d "https://foodrats.app/meal/abc/2026-05-26" es.schsebastian.foodrats`
  (use `foodrats://app/meal/abc/2026-05-26` to test the custom scheme without App Links verification).
- **iOS sim:** `xcrun simctl openurl booted "foodrats://app/crew/c-1"` (custom scheme; Universal
  Links require the hosted AASA + a real device for full verification).
