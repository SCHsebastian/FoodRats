# :feature:meal-ai

On-device food classifier adapter. Runs a MediaPipe Tasks image-classification model (Food-101, 101 classes) against a `Plate` image to produce a ranked list of `IngredientSuggestion`s. Consumes the `.tflite` model asset bundled via `composeResources/files/` and exposes results through `ClassifyPlateUseCase` → `MealAiPort` (declared in `:core:domain`).

## Authoritative references

- Spec — `docs/specs/2026-05-24-meal-ai-ingredient-classification-design.md` §6 (meal-ai module, MediaPipe adapter, model asset, port binding).
- Root `CLAUDE.md` — "Module graph", "Architectural rules" (sealed-interface errors, vendor SDKs only in adapter layers, i18n for all user-visible text).

## Local rules

- JVM target **17**: Koin transitively links shared modules that touch Firebase; inline functions compiled at JVM 17 would be rejected by a JVM 11 target.
- `.tflite` model asset lives in `src/commonMain/composeResources/files/` — never checked in as a raw `assets/` entry.
- No direct Firebase imports in this module. If added, include the BOM in `androidMain.dependencies` (see comment in `build.gradle.kts`).

## Test

`./gradlew :feature:meal-ai:testAndroidHostTest`
