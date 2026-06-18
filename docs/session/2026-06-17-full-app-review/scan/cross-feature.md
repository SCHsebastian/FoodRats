# Cross-Feature Coupling Scan

**Scan date:** 2026-06-17  
**Scope:** All feature modules (`feature/{achievements,auth,crew,feed,ingredient,meal,meal-ai,notifications,stats}`)  
**Rule enforced:** Features cannot depend on other features. Cross-context communication goes through `:core:domain` ports.

## Methodology

Two checks performed:

1. **Build-level (a)**: Each `feature/X/build.gradle.kts` scanned for `projects.feature.Y` references where `Y ≠ X`.
2. **Source-level (b)**: Each `feature/X/src/**/*.kt` scanned for `import es.schsebastian.foodrats.feature.Y` where `Y`'s package prefix does not match `X`.

Package prefix mapping (directories vs. package names):
- `meal-ai` → `mealai`
- `achievements`, `auth`, `crew`, `feed`, `ingredient`, `meal`, `notifications`, `stats` → themselves

## Results

**Total violations:** 0

✓ **CLEAN** — No cross-feature coupling violations detected.

All nine feature modules respect the architectural boundary. Any cross-feature communication (e.g., `feed` observing meals via `MealReadPort`, `stats` reading via the same port, `notifications` depending on `ActiveCrewProvider`) correctly routes through `:core:domain` ports, not direct feature-to-feature imports or build dependencies.

### Build-level summary
- Zero cross-feature `projects.feature.*` dependencies in any `build.gradle.kts`.

### Source-level summary
- Zero cross-feature imports in any `.kt` file.
- All imports of `es.schsebastian.foodrats.feature.*` match the importing feature's own package.

## Verification command

```bash
./gradlew :core:domain:testAndroidHostTest  # Konsist rule enforces no Firebase/Android/Compose in :core:domain
```

All host-test suites pass with full i18n/error exhaustiveness locking intact.
