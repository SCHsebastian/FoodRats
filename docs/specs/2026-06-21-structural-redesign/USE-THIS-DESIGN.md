# How to make an AI build in the "Structural" language

Three ways, from zero-setup to permanent. Pick by which AI you're talking to.

---

## 1. Fastest — paste this into any chat (Claude Code, claude.ai, a teammate's AI)

> Build in the FoodRats **"Structural"** design language. Before writing anything, read these files and
> reuse them exactly — do NOT invent a new look:
> - `docs/session/2026-06-21-structural-redesign/structural.css`  ← the design language + class vocabulary
> - `docs/session/2026-06-21-structural-redesign/tokens.css`      ← Iron & Ember tokens
> - `docs/session/2026-06-21-structural-redesign/screens/feed.html` ← the exemplar to match
> - `docs/session/2026-06-21-structural-redesign/report.md`       ← the rules + rationale
>
> Rules (non-negotiable): (1) continuous edge-to-edge `.media` floor behind everything; (2) content
> floats as frosted `.tile` strata — depth, never borders/dividers; (3) `.bento` 6-col grid, tile size
> = data priority; (4) extreme type contrast — oversized `.metric` vs 10px `.micro`; (5) zero-chrome —
> no top-bar fills, floating `.dock`/`.glass-btn`. Keep the brand: Iron & Ember palette, Plus Jakarta
> Sans, voice (second person, sentence case), ubiquitous language Meal·Plate·Score·Crew·Member, emoji
> as content only. Match `feed.html`'s skeleton for any new screen. Verify by screenshotting at 390×844
> (`_verify/shot.sh`) and looking at the result.

That's the whole contract — it's the same brief the screens were built from, so it reproduces the look.

---

## 2. Permanent in this repo — so every Claude Code session knows it exists

Two small wirings (ask me to do them, or do them yourself):

- **`CLAUDE.md` pointer** — add one line under the design section pointing at this folder, so it
  auto-loads into every session's context: *"A 'Structural' redesign concept (zero-chrome / bento /
  frosted glass) lives in `docs/session/2026-06-21-structural-redesign/` — `structural.css` is the
  language, `report.md` the rules. Use it when asked for the structural look."*
- **Fold into the `designsystem` skill** — copy `structural.css` + 2–3 exemplar screens into
  `.claude/skills/designsystem/` and add a line to its `SKILL.md` so `/designsystem` can output
  structural-style mockups on request. Then you just say: *"/designsystem — make screen X in the
  structural style."*

---

## 3. For claude.ai/design (the design-agent that builds UI in the browser)

That agent builds with *components*, not a CSS file. To make it produce this look you'd sync a
component library to it (the `/design-sync` skill). The current FoodRats DS is Kotlin/Compose, so that
path first needs the components as a buildable web bundle — a separate, bigger task. Tell me if that's
the AI you meant and I'll scope it.

---

## Two production modes

- **More HTML mockups** → reuse `structural.css` directly; new screens are ~80 lines of markup.
- **Real app (Compose Multiplatform)** → `report.md` → "How to take this to production": the media
  floor = `Box` + blurred `Image`; tiles = translucent `Surface` over `Modifier.blur`; bento =
  `LazyVerticalGrid` with per-item span. Token layer untouched; add `FrGlassTile` / `FrMediaFloor` /
  `FrBentoGrid` / `FrMetric` molecules. Say the word and I'll prototype those.
