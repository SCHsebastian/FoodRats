# catalogApp repair — 2026-06-17

## catalog-02 (LOW) — delete dead CatalogTag composable

**File:** `catalogApp/src/main/kotlin/es/schsebastian/foodrats/catalog/components/CatalogScene.kt`

**What changed:** Removed the `CatalogTag` composable (lines 168-183 before edit). A `grep -rn "CatalogTag"` across all `catalogApp` `.kt` files returned only the definition itself — zero call sites. Deleted the 16-line block including its KDoc. Verified `RoundedCornerShape`, `Radius.pill`, `labelSmall`, `secondaryContainer`, and `onSecondaryContainer` are all still used elsewhere in the file so no imports went dead.

**Tests added:** None — LOW mechanical fix with no logic change.

**Skipped:** catalog-01 (AtomStories.kt is in DIRTY_FILES.txt; not touched).

**Build risk:** None — pure deletion of an unreferenced symbol.
