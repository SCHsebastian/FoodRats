# feature-crew repair report

## crew-06 — APPLIED

**What changed:**
- `SectionEyebrow` in `CrewSettingsScreen.kt` (line 527): removed `text.uppercase()`, now passes `text` directly to `FrText`.
- Pre-uppercased the four section-header string values in both en (`values/strings.xml`) and es (`values-es/strings.xml`):
  - `crew_settings_title`: "Crew" → "CREW" / "Grupo" → "GRUPO"
  - `crew_settings_members_section`: "Members" → "MEMBERS" / "Miembros" → "MIEMBROS"
  - `crew_settings_danger_section`: "Danger zone" → "DANGER ZONE" / "Zona de peligro" → "ZONA DE PELIGRO"
  - `crew_settings_blind_voting_section`: "Blind voting" → "BLIND VOTING" / "Votación a ciegas" → "VOTACIÓN A CIEGAS"

**Why this approach:** `kotlin.text.String.uppercase()` in KMP commonMain (no Locale arg) delegates to the default platform locale on JVM, which corrupts `i` → `İ` in Turkish. `java.util.Locale` is not available in commonMain. Moving the uppercasing into the string resources is locale-safe and idiomatic (text displayed in a fixed visual style belongs in the resource, not the layout code). The four keys are only consumed by `SectionEyebrow` so no other call sites are affected.

**Tests added:** None — LOW mechanical fix; no behavioral logic change. The existing `testAndroidHostTest` suite covers string-key resolution exhaustiveness.

## Skipped fixes

- **crew-01**: DIRTY (`CrewFirestoreDataSource.kt`, `FirebaseCrewRepository.kt`) — files are in the DIRTY list; skipped as instructed.
- **crew-04**: DIRTY (`CrewFirestoreDataSource.kt`) — skipped as instructed.
- **crew-05**: DIRTY (`FirebaseCrewRepository.kt`) — skipped as instructed.
- **crew-03**: DEFERRED — `RemoveMemberUseCase` pre-flight removal depends on the contract of `FirebaseCrewRepository.removeMember`, which is mid-edit (DIRTY). Cannot safely assess whether the repository already handles all pre-flight errors post-edit. Will need re-evaluation once the DIRTY files are committed.

## Build risk

None expected. The XML changes are purely value edits; the Kotlin change removes a method call. The visual output is identical (strings were uppercased at runtime before; now they are pre-uppercased in XML). No API surface changed.
