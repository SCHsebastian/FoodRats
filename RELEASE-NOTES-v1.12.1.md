# FoodRats v1.12.1 — release notes

Beta shipped 2026-07-20 via the develop→main release PR (release-beta on merge). Paste sources for Play Console / App Store Connect below.

## What's new (store paste — EN)

```
• My plates: a monthly calendar of everything you've cooked — find it on your profile
• Sturdier buttons: accidental double-taps no longer trigger duplicate actions
• Notification taps now open the right crew
• Fixed a rare crash when opening the app from a notification on iOS
• Security hardening across the app
• Performance and stability improvements
```

## Novedades (store paste — ES)

```
• Mis platos: un calendario mensual con todo lo que has cocinado — lo tienes en tu perfil
• Botones más robustos: los toques dobles accidentales ya no duplican acciones
• Tocar una notificación ahora abre la cuadrilla correcta
• Arreglado un cierre inesperado al abrir la app desde una notificación en iOS
• Refuerzo de seguridad en la app
• Mejoras de rendimiento y estabilidad
```

## Full changelog (internal)

- **My Plates monthly calendar** (`573c835`): month grid of your own meals in the active crew,
  day drill-down, Profile entry point. Past months limited to the local ~30-day sync mirror.
- **Double-tap throttle** (`a605c72`): leading-edge 500ms click throttle on `FrButton` +
  `FrGlassButton` — double-taps no longer race two concurrent MVI intents.
- **`data_consent_version` user property** (`be05273`): stamped on consent grant at the
  `ConsentGatedAnalytics` choke point (spec §3/§7 gap).
- **Security remediation wave 2026-07-19** (`c65a908`→`1fa0eaa`, `9a65e58`, `3d16ce8`,
  `6214201`): npm/gem/Gradle transitive bumps (websocket-driver, undici, faraday, excon,
  guava 33.4.8-android, play-services-basement 18.4.0), hardened `firestore.rules`
  (field whitelists, length caps, members-map pinning — deployed).
- **Crew membership delta writes** (`b362e50`): membership writes match deployed rules;
  active-crew invalidation.
- **Comment/author fixes** (`5e26dc1`, `370a893`): wire authorName capped at rules limit,
  comment enqueue failures surfaced, functions resolve real author names + localized fallbacks.
- **Notifications** (`0957e41`, `b9922e0`): FCM token rotation, deep-link replay, banner
  buffering; meal push links carry `crewId` and switch the active crew on tap (functions
  deploy deliberately held for this client release — old installs break on 4-segment links).
- **iOS cold-start fix** (`f80ff4d`): Swift→Kotlin bridges gated until Koin starts —
  notification-tap crash on cold start.
- **Auth** (`f36ca35`): local account data wiped on revoked-session sign-out.
