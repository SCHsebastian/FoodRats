# FoodRats v1.12.0 — release notes

Beta shipped 2026-07-15 (PR #40, main @ 1d25193). Paste sources for Play Console / App Store Connect below.

## What's new (store paste — EN)

```
• Mention your crew: tag members in comments with @ and they'll get a notification
• Comment notifications now show who actually commented
• Notifications follow your in-app language, including channel names
• Fixed photo uploads that could get stuck after closing the app mid-upload
• Performance and stability improvements
```

## Novedades (store paste — ES)

```
• Menciona a tu cuadrilla: etiqueta a miembros en los comentarios con @ y recibirán una notificación
• Las notificaciones de comentarios ahora muestran quién comentó
• Las notificaciones siguen el idioma de la app, incluidos los nombres de los canales
• Arreglada la subida de fotos que podía quedarse atascada al cerrar la app a mitad de subida
• Mejoras de rendimiento y estabilidad
```

## Full changelog (internal)

- **Comment @-mentions** (`1d25193`): @handle suggestions in the composer, mention highlight in
  comment rows, localized `comment_mention` push to tagged members (meal owner never double-notified),
  offline-first via outbox. Rules + functions deployed 2026-07-15.
- Comment pushes carry the real commenter name — "Someone" bug fixed (same commit).
- Meal-reminder copy resolves from composition and re-translates on language change (`8b3c84d`).
- Streak-nudge channel name localized and renamed with in-app language (`738cc48`).
- Draft queue recovers orphaned Uploading entries (`18a0ba6`).
- Internal: clean-wave refactor iterations 1–4 (`dd21ab5`, `7339ff7`, `54bbb87`, `27a7685`),
  offline-first test hardening +79 tests (`737cf53`), iOS CI signing-keychain reuse (`1ed8b2d`).
