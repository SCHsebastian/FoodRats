# FoodRats — Structural Redesign · design spec

**Date:** 2026-06-21 · **Status:** committed design spec (concept exploration — the production app is
unchanged) · **Deliverable:** browser-viewable HTML mockups of all 19 app screens in a new "Structural"
aesthetic, built on the real Iron & Ember tokens. **Open `index.html`** (live gallery; each tile is the
screen rendered in an iframe — click to open and scroll it). Usage: `USE-THIS-DESIGN.md`.

> **This is a design spec, not shipped product.** It defines an *opt-in* aesthetic. The production
> design system stays matte-concrete Iron & Ember; use this only when "the structural look" is explicitly
> requested. A Compose component layer that productionizes this exists separately in
> `core/designsystem/src/commonMain/.../structural/` (catalogued under `CatalogGroup.STRUCTURAL`).

---

## The brief → the system

"The Structural Master Prompt" asked for five things. Each maps to a concrete mechanism in
`structural.css`:

| # | Brief | How it's built |
|---|-------|----------------|
| 1 | **Continuous edge-to-edge media layer as the absolute foundation** | `.media` is a fixed, device-filling layer behind everything (`z-index:0`). On photo screens it's the plate itself (sharp on meal-detail, blurred on feed/stats); on chrome-only screens (`sign-in`, `settings`) it's an atmospheric Iron & Ember `.media.field`. The content plane (`.screen`) scrolls *over* it — the floor never moves. |
| 2 | **Floating components on a distinct Z-axis; variable transparency + structural depth, not containment borders** | `.tile` strata are frosted (`backdrop-filter: blur + saturate`), border-less, separated only by depth: `.deep` (more blur, lower opacity → recedes), `.near` (more opaque, bigger shadow → advances). A 1px inner top-light models a glass edge-catch, never a box outline. |
| 3 | **Asymmetric, non-linear bento grid; dimensions scale to data priority** | `.bento` is a 6-column grid; tiles span `.c2…c6` by priority. On the feed the top plate (9.2) is the biggest tile, the mid plate (8.4) is medium, the low plate (7.0) is the smallest — size *is* the ranking. Stats fans the same way: streak hero `c6`, headline metrics `c3`, compact metrics `c2`. |
| 4 | **Extreme typographic contrast — oversized metrics vs microscopic metadata** | `.metric` (44–128px, weight 800, tabular) sits directly against `.micro` / `.micro-row` (10px, uppercase, 0.14em tracking, mono for figures). The score, the streak count, the recap number all blow up; the cook · slot · time · votes arrays shrink to dense captions. |
| 5 | **Zero-chrome — no bounding boxes, no solid dividers; delineate by layering, alignment, depth** | No top app-bar fills. Titles become oversized type in the content plane. List rows separate with a 1px *light* (`inset 0 1px 0 rgba(255,255,255,.05)`), never a rule. The nav is a floating frosted `.dock`; back/share/close are floating `.glass-btn`s — both already brand-sanctioned (the real app floats its bottom bar + uses blur in exactly two places; this extends that seed everywhere). |

## What was kept (brand DNA — non-negotiable)

- **Iron & Ember palette** verbatim from `tokens.css` — olive primary, ember/streak-hot accents, charcoal-olive dark surfaces. No new hues.
- **Type:** Plus Jakarta Sans + JetBrains Mono (for codes/figures), the real Material 3 ramp.
- **Ubiquitous language:** Meal · Plate · Score · Crew · Member. Never post/rating/group/user.
- **Voice:** second person, sentence case, plain verbs ("Post to keep your 7-day streak alive", "Roast", "Pin where you ate").
- **Emoji as content only:** 🔥 streak, 🏆 challenge, ✓ posted, 🍴 First Plate. Never in chrome.
- **`Fr*` vocabulary** mapped 1:1: `FrScoreBadge`→`.score`, `FrAvatar`→`.avatar`, `FrFlameBadge`→`.flame`, `FrFilterChip`→`.chip`, `FrBottomBar`→`.dock`, `FrTextField`→`.field`, etc.

## The one deliberate departure

The current design system says *matte concrete cards, no frosted glass, no gradient containers*.
The brief asks for the opposite — translucent strata floating on a Z-axis. **The brief wins here by
design**; frosted glass is the literal expression of "variable transparency and structural depth."
Everything else about the brand is preserved, so this reads as the same product wearing a bolder structure.

## Content model

The real app's current test data is sparse (1-member "Review Crew", a meal named "Fútbol", a photo of
a door). A redesign mock must demonstrate the priority-scaled bento, so it uses the UI kit's **intended
demo crew** — Saturday Brunch, four members, multiple scored plates — while honoring the real app's
**structure and copy** (sign-in fields, section labels, badge taxonomy, moderation reasons, settings
rows, dialog text), all extracted verbatim from the 50 screenshots in `../2026-06-21-screenshots/`.

## Theme

Dark-first. A media floor with white-on-photo type and frosted strata is fundamentally a dark-immersive
look; `structural.css` also ships a `.device.light` variant (concrete floor, ink type) for completeness.

## Screen inventory (19)

**Core flow** — sign-in · crew-picker · crew-create · crew-join · feed · feed-empty · meal-detail · composer
**Stats & recap** — stats · weekly-recap
**Crew & moderation** — crew-settings · score-style-picker · qr-invite · moderation
**Profile & settings** — app-settings · badges · theme-picker · blocked-users · delete-account

## Files

```
README.md           this spec — open it for the rationale
index.html          live gallery — open this to see all 19 screens
USE-THIS-DESIGN.md  how to make any AI build in this language
structural.css      the new design language (the spec's core)
tokens.css          Iron & Ember tokens (mirror of core/designsystem theme, unchanged)
screens/*.html      19 self-contained screens
assets/*.svg        brand logos
```

## Verification

Every screen was rendered headless at 390×844 (`/Applications/Google Chrome.app … --screenshot`,
`_verify/shot.sh`) and visually checked. The seven hero screens were authored and verified by hand;
the long-tail screens were built by constrained sub-agents against the locked CSS + exemplars and
self-verified the same way, then reviewed.

## How to take this to production (if pursued)

The structure is achievable in Compose Multiplatform: the media floor is a `Box` with a background
`Image` + `Modifier.blur`; tiles are `Surface`s with a translucent color over a `Modifier.blur`
backdrop (or a pre-blurred snapshot on older APIs); the bento is a `LazyVerticalGrid` with per-item
`span`. The token layer (`core/designsystem/theme/*`) is untouched — only new `Fr*` molecules
(`FrGlassTile`, `FrMediaFloor`, `FrBentoGrid`, `FrMetric`) would be added. Frosted glass on iOS is
`UIBlurEffect`-cheap; on Android, `RenderEffect.createBlurEffect` (API 31+) with a tonal-scrim fallback.
