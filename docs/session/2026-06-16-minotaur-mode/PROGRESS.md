# Minotaur mode — execution ledger

Hidden cosmetic easter egg: a 3-finger ~1.5s long-press toggles "Minotaur mode", growing a
furry edge on `Fr*` atoms app-wide. Off by default, ephemeral (resets on relaunch).
Entirely inside `:core:designsystem` (+ one `:catalogApp` story). Source brief: `PROMPT.md`.

## Approach

7 independent single-file tasks (A–G) fanned out to parallel Sonnet agents (one file each,
no ordering dependency), then a compile/test gate run from the main thread. Each agent was
told to report `FAILED` rather than improvise on an old-string mismatch — none did.

## Task status

| Task | File | Type | Agent | Spot-check |
|---|---|---|---|---|
| A | `core/designsystem/.../theme/Fur.kt` | CREATE | DONE | (build) |
| B | `core/designsystem/.../theme/MinotaurUnlock.kt` | CREATE | DONE | (build) |
| C | `core/designsystem/.../theme/SemanticColors.kt` | EDIT ×3 | DONE | ✅ fur/onFur in data class + light + dark |
| D | `core/designsystem/.../theme/FoodRatsTheme.kt` | REWRITE | DONE | (build) |
| E | `core/designsystem/.../atoms/FrCard.kt` | EDIT ×2 | DONE | ✅ furModifier + .then(furModifier) |
| F | `core/designsystem/.../atoms/FrButton.kt` | EDIT ×2 | DONE | ✅ .fur(LocalMinotaurMode.current) |
| G | `catalogApp/.../stories/AtomStories.kt` | EDIT ×3 | DONE | ✅ imports + atom.card.fur entry + MinotaurCardStory() |

## Verification gate

Prompt's V gate is 3 commands; I added `:catalogApp:assembleDebug` because G lives in
catalogApp, which none of the 3 V commands compile.

- [x] `:core:designsystem:testAndroidHostTest` + `:androidApp:assembleDebug` + `:catalogApp:assembleDebug` → `BUILD SUCCESSFUL in 15s` (379 tasks)
- [x] `:shared:linkDebugFrameworkIosSimulatorArm64` → `BUILD SUCCESSFUL in 39s` (147 tasks)

**Both gates green.** Only pre-existing warnings (expect/actual beta, LocalClipboardManager /
UIKitView deprecations, bundleId inference) — none from these changes. Catalog device check
(`:catalogApp:installDebug` → open `atom.card.fur`, do the 3-finger long-press) remains manual.

## Update — fur richness upgrade (user feedback 2026-06-16)

User: the first pass (sparse 9px hair strokes) was "too simple"; wanted it to "feel like a minotaur"
(ref mock: thick dark pelt + neon-green rim glow + green-lit tips).

- **`Fur.kt` rewritten** into a real pelt: dark underfur band + dense tufts (every 4px, 4–6 hairs
  each, 3-segment dark-root→brown-body→cream/neon-green-tip, curl + sway), plus a pulsing green
  glow halo (8 fanned rounded-rect layers + crisp inner rim). Animated via one
  `rememberInfiniteTransition` per furred atom; the off path early-returns so default costs nothing
  (animation set up only when enabled).
- **`furGlow` semantic token added** (neon green; light `#35E84A`, dark `#5CFF73`).
- **Fur wired onto more atoms** so the whole frame reads minotaur: `FrCard`, `FrButton`,
  `FrTextField` (search bar), `FrIconButton` (bell / nav).
- **Catalog story fix:** `CatalogScene` re-wraps its content in a nested `FoodRatsTheme` (which
  provides `LocalMinotaurMode=false`), so the provider must sit *inside* the scene — moved it there.
  (The real app has a single root `FoodRatsTheme`, so its 3-finger toggle flips the local app-wide;
  only the catalog nests themes.)

**Visual verification:** physical phone is secure-locked (screencap returns black behind keyguard),
so captured on the `pixel_7_pro_36` emulator. Built+installed catalog, deep-linked to the fur story,
screencap → `fur-device.png` / `fur-zoom.png`. Confirmed a dense neon-backlit pelt rendering.

## Update 2 — fur as SURFACE, not edge strokes (user: "too ugly" + "use a fur texture as surface")

The procedural edge-stroke fur read as grass and the user rejected it twice. New approach: the
component **surface itself** is upholstered in fur.

- **`Fur.kt` rewritten again** → `fun Modifier.fur(enabled, shape)`. Generates a dense brushed-fur
  texture into an `ImageBitmap` (a grid of short tapered, gently-curved hairs all leaning one way,
  shaded dark-root→tan-tip), **built once per size via `drawWithCache`** (cached; steady-state cost
  is one `drawImage`). Clips the fur to the card `shape`, draws caller content on top, and strokes a
  neon-green rim-glow (wide→narrow, faint→bright) hugging the edge + a crisp rim line.
- **`FrCard` is minotaur-aware:** when on, `color = Transparent`, `contentColor = onFur` (cream),
  `shadowElevation = 0`, and the fur fills the surface. Off → byte-for-byte unchanged.
- **`onFur` dark-theme fixed to cream** (`#ECE0CC`) since the fur is always dark brown, so ink must
  be light in both themes.
- **Reverted edge-fur from `FrButton`/`FrTextField`/`FrIconButton`** — fur is now card-only, to keep
  the look clean. (Re-propagate later if wanted.)
- **On "fur SVG from Google":** generated the texture in-code instead of bundling a web image —
  licensing risk + CMP doesn't load `.svg` at runtime. Swap in a licensed asset via composeResources
  if desired.

**Device-verified** on `pixel_7_pro_36`: dense brown fur surface + neon-green rim + readable cream
text (`fur-device.png` / `fur-zoom.png`). Note: the one-time texture build (~10k paths) adds a brief
first-render hitch per card size — fine for an opt-in easter egg.

**Gates re-run green after update 2:**
- `:core:designsystem:testAndroidHostTest` + `:androidApp:assembleDebug` + `:catalogApp:assembleDebug`
  → `BUILD SUCCESSFUL in 8s` (existing FrCard/atom UI tests still pass — fur off by default).
- `:shared:linkDebugFrameworkIosSimulatorArm64` → `BUILD SUCCESSFUL in 33s` (ImageBitmap/Canvas/
  CanvasDrawScope/drawWithCache all link on Native).

## Notes / deviations from PROMPT.md

- V run from main thread (not a Bash agent) so failures are observed and fixed directly.
- Added `:catalogApp:assembleDebug` to the gate (G coverage).
- Optional persistence add-on (survive relaunch via a DataStore `StoreKey`) NOT done — out of
  the parallel scope; default ephemeral behavior is intended.
- Wiring `minotaur`/`onMinotaurToggle` from the shared root is NOT done — `FoodRatsTheme`'s new
  params default to off, so the in-app gesture toggles ephemerally on its own.
