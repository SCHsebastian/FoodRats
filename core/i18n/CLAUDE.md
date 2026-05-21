# :core:i18n

Shared i18n machinery: the sealed `StringKey` interface, the `@Composable resolve(StringKey)` resolver, and cross-feature common strings (en/es) under `composeResources/`. Each feature defines its own `<Feature>StringKey` enum implementing `StringKey` in its own module.

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §7 (i18n strategy), §7.2 (pattern), §7.3 (error → string mapping).
- Root `CLAUDE.md` — "Architectural rules" (all user-visible text via `resolve(StringKey)` — **including punctuation and glyph separators** like `★`, `•`, parentheses).
- Recent change — "i18n covers separator glyphs" (commit `fbf5e40`).

## Local rules

- This module owns only **cross-feature** strings (app name, common errors). Feature-specific strings live in the feature's own `composeResources/values/{,-es}/strings.xml`.
- Separators that combine values (e.g. `"%1$s ★ · %2$d"`) are parameterised strings, not Kotlin concatenation — keeps RTL/locale glyph variants out of code.

## Test

No tests in this module — `<Feature>ErrorToStringKeyTest` exhaustiveness lives in each consuming feature's `commonTest`.
