# :core:designsystem

Atomic-Design system: `Fr*` atoms / molecules / templates, `FoodRatsTheme`, tokens (colors, typography, shapes, sizes, motion), `FrSemanticColors`. Iron & Ember palette (deep-olive primary, ember-copper secondary, rust tertiary). Consumed by every feature and by `:catalogApp`.

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §4.1 (`:core:designsystem`), §4.3 (where domain-aware composables live), §4.4 (rules).
- Root `CLAUDE.md` — "Architectural rules" (Fr* prefix, no domain types in atoms/molecules, `FrSemanticColors` for meaning roles, catalog entry required for every public `Fr*`).
- Recent change — "Design system v3 — Iron & Ember refresh" (2026-05-20).

## Local rules

- Atoms/molecules accept **primitives or presentation enums only** — never domain types (those live in feature `presentation/components/`).
- Every public `Fr*` ships with a `:catalogApp` entry in `stories/{Foundation,Atom,Molecule,Template}Stories.kt`. IDs are `<group>.<name>` lowercase.
- For meaning use `LocalFrSemanticColors.current` (`success`, `warning`, `danger`, `info`, `celebration`, `streakHot`) — never alias `MaterialTheme.colorScheme` brand roles for meaning, never reach for raw `Color(0x…)`.
- iOS: `material-icons-extended` is **not** published — use `material-icons-core` and vendor missing glyphs into `atoms/FrIcons.kt` via the `materialIcon { materialPath { … } }` DSL.

## Test

`./gradlew :core:designsystem:testAndroidHostTest` — Robolectric-backed Compose UI tests (`createComposeRule` v2) with `sdk=33` and display qualifiers configured in `src/androidHostTest/resources/robolectric.properties`.
