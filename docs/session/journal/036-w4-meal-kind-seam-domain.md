# 036 · w4-meal-kind-seam-domain

**Status:** done

**Summary (≤6 lines):**
- `MealKind` sealed seam in `:core:domain` with only the `Solo` leaf (behaviorally inert; `Together` deferred per §5/§13). `Meal.kind` added with default `Solo` (old data reads as Solo, no migration). Exhaustive `when` with no `else` locks the one-leaf seam so `Together` is additive.
- Files: `core/domain/.../meal/MealKind.kt` (new), `core/domain/.../meal/Meal.kt` (+defaulted `kind`), commonTest `MealKindTest` (new).
- Decisions: no new error leaf (§4 has none); `MealDraft` untouched (§4.3 — Solo is the publish default); Solo invariant as pure `MealKind.authorIds(authorId)`.
- Blockers: none.

**Verify (quoted):**
```
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 17s
(MealKindTest 3/3, Konsist green)
```

**Data handoff:** `MealDto.kind: String = "solo"` discriminator + tolerant `MealMapper` arm + `publish()` stamp + `MealMapperTest`.

Report: `docs/session/reports/w4-meal-kind-seam-domain.md` · Handoff: `docs/session/handoffs/w4-meal-kind-seam-domain.md`
