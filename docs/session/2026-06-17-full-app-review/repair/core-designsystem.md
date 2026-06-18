# core-designsystem repair report

## ds-01 — FrStoryScaffold.kt close-button tint

**What changed:** Added `import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors`. Changed `IconButtonDefaults.iconButtonColors(contentColor = Color.White)` to `contentColor = LocalFrSemanticColors.current.onScrim` (read inside the `@Composable` body, no public-API change).

**Tests added:** None (LOW mechanical fix; no logic path changed).

**Risk:** None — `onScrim` is `Color(0xFFFFFFFF)` in both light and dark themes, so runtime rendering is identical today but will now track theme changes correctly.

## ds-02 — SKIPPED (deferred as instructed)

Removing the `?: "Close"` fallback and making `closeContentDescription` non-null requires updating 3+ callers outside the module.

## ds-03 — FrBadge.kt earned-icon contentColor

**What changed:** Added `import LocalFrSemanticColors`. Inside `FrBadge`, read `val semantic = LocalFrSemanticColors.current` before computing `iconColor`. Passed `semantic.onScrim`, `MaterialTheme.colorScheme.primary`, and `MaterialTheme.colorScheme.onPrimary` as explicit params into the private `contentColorFor` helper (which became non-`@Composable` and pure). The helper now returns `onPrimary` when `tint == primaryColor` (same guard as before) and `onScrim` otherwise (was `Color.White`). No public-signature change.

**Tests added:** None (LOW mechanical fix; helper logic is unchanged in structure, only the fallback color source moves from a literal to a semantic token).

**Risk:** `onScrim` is currently `Color(0xFFFFFFFF)` so runtime appearance is identical today. Future theme changes will now propagate correctly.
