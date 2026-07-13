# Release notes — v1.11.0 (ready to paste)

Source branch: `feat/multi-photo-crew15`. Latest tag at time of writing: `v1.10.4` → this release
tags `v1.11.0`. Versioning is tag-derived (`scripts/ci/compute_version.sh`) — nothing here feeds the
build automatically; **paste this text by hand** into each console at release time (see "Why this
file exists" below).

Covers two shipped features:
1. Meals can carry up to 10 ordered photos, mixing camera shots and gallery picks, with a swipeable
   photo pager on the feed tile and meal detail screen; photos added from the gallery carry a small
   provenance marker.
2. Crews now hold up to 15 members (was 8).

---

## Why this file exists

Neither release lane uploads store metadata from the repo:
- Android: `fastlane/Fastfile` `platform :android, lane :beta` **and** `lane :release` both call
  `upload_to_play_store(..., skip_upload_metadata: true, skip_upload_images: true,
  skip_upload_screenshots: true, ...)`.
- iOS: `platform :ios, lane :release` calls `upload_to_app_store(..., skip_metadata: true,
  skip_screenshots: true, ...)`. `lane :beta` (TestFlight) doesn't set `changelog:`/release-notes
  either.
- There is no `fastlane/metadata/android/**` or `fastlane/metadata/ios/**` directory in the repo
  (confirmed: `find fastlane -type d` returns only `fastlane` itself) — the conventional
  fastlane-supply / deliver changelog-file mechanism isn't wired up at all.
- The `github-release` job in `.github/workflows/release-production.yml` auto-generates GitHub
  Release notes from commit/PR history (`generate_release_notes: true`) — that's automatic, but it's
  a GitHub Release, not a store listing, and it isn't store-appropriate copy (internal commit
  messages, not user-facing text).

So: **every store "what's new" field is a manual console paste, every release.** This file is the
per-release source text, following the pattern already established by `LISTING-COPY.md`'s "Release
notes (What's new)" block (which only ever had the v1.0.0 first-release copy). Going forward, each
release gets its own `docs/store-release/RELEASE-NOTES-vX.Y.Z.md`.

---

## en-US

### Google Play — "What's new" (release notes, ≤500 characters per locale)

```
This update: meals now hold up to 10 photos — mix camera shots with gallery picks and swipe through them in the feed. Gallery photos are clearly marked. Plus, crews can grow up to 15 members (up from 8). Update and give it a try!
```
Length: 229 / 500 characters.

### TestFlight — "What to Test" (build notes)

```
What's new in this build:

• Multi-photo meals — add up to 10 photos per post, mixing camera captures and gallery picks in any order. Swipe through them in the feed tile and the meal detail screen.
• Gallery photos are marked so your crew can tell live shots from picked ones.
• Crews now hold up to 15 members (up from 8) — room for the whole group.

Please try: posting a meal with a mix of camera + gallery photos, reordering them before publishing, and swiping through a multi-photo meal in the feed. Let us know if anything looks off.
```
Length: 539 characters (Apple's "What to Test" / version "What's New" fields both allow up to 4000).

---

## es-ES

### Google Play — "Novedades" (notas de la versión, ≤500 caracteres por idioma)

```
Novedades: ahora cada comida admite hasta 10 fotos — combina cámara y galería, y deslízalas en el feed. Las fotos de galería quedan marcadas con claridad. Además, las cuadrillas ya admiten hasta 15 miembros (antes 8). ¡Actualiza y pruébalo!
```
Longitud: 240 / 500 caracteres.

### TestFlight — "Qué probar" (notas de compilación)

```
Novedades de esta compilación:

• Comidas con varias fotos — añade hasta 10 fotos por publicación, combinando cámara y galería en cualquier orden. Deslízalas en la miniatura del feed y en el detalle de la comida.
• Las fotos de galería se marcan para que tu cuadrilla distinga las tomadas en el momento de las elegidas.
• Las cuadrillas ahora admiten hasta 15 miembros (antes 8): sitio para todo el grupo.

Por favor, probad: publicar una comida combinando fotos de cámara y galería, reordenarlas antes de publicar, y deslizar por una comida con varias fotos en el feed. Avisadnos si algo falla.
```
Longitud: 595 caracteres.

---

## Terminology note

Spanish copy in this file uses **cuadrilla** for "crew" (per current in-app i18n convention — see
`feature/crew` `StringKey`s and the 2026-06-22 ES-crew-term decision). `docs/store-release/
LISTING-COPY.md`'s existing es-ES store description/keywords currently use the untranslated **crew**
throughout instead. That's a pre-existing inconsistency in the listing copy, not introduced here —
flagged as an open question in `docs/session/2026-07-13-multi-photo-crew15/track-release-data.md`
rather than silently changed, since the full listing copy was authored/verified by a different track
earlier today and a terminology change there needs an explicit decision, not a drive-by edit.

## Paste targets

| Field | Console path |
|---|---|
| Google Play "What's new" (en-US, es-ES) | Play Console → your app → Release → \[Internal testing \| Production\] → Create new release → Release notes, per language |
| TestFlight "Test Details" / "What to Test" (en-US, es-ES) | App Store Connect → TestFlight → \[Internal \| External\] Group → build → Test Details, per localization |
| App Store "What's New in This Version" (en-US, es-ES) | App Store Connect → App Store tab → version → "What's New in This Version" (only needed at the production tag step; use the same text as the TestFlight variant or shorten to match the Play tone — either is acceptable, Apple's limit is 4000 chars) |
