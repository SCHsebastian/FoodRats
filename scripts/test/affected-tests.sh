#!/usr/bin/env bash
# Maps changed file paths -> the test tasks that cover them.
#
# Usage: scripts/test/affected-tests.sh <file> [<file> ...]
# Paths may be absolute or repo-relative.
#
# Output: one "<task>\t<reason>" line per mapped task (deduped, order-preserving).
# Empty output = nothing mapped (docs, .claude, unknown paths).
#
# Consumed by .claude/hooks/test-map.sh (PostToolUse hook) and usable manually.
# The full test map + layer conventions live in docs/testing/TEST-MAP.md.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

OUT=()
add() { OUT+=("$1"); }

FULL_SUITE_NOTE=':core:domain is a shared kernel — if you changed a port/model/error, run the full host suite (docs/testing/TEST-MAP.md §Commands)'

for raw in "$@"; do
  f="${raw#"$ROOT"/}"
  case "$f" in
    docs/*|*.md|.claude/*|.github/*|.idea/*|fastlane/*|scripts/*) continue ;;
  esac

  layer=""
  case "$f" in
    */domain/*)                   layer="domain" ;;
    */data/*)                     layer="data" ;;
    */presentation/*|*/ui/*)      layer="presentation" ;;
    */di/*)                       layer="di (ModuleVerifyTest)" ;;
    */composeResources/*)         layer="i18n strings" ;;
  esac

  case "$f" in
    core/domain/*)
      add ":core:domain:testAndroidHostTest	core/domain ${layer:+[$layer] }— VO/policy tests + Konsist + ArchitectureFitness"
      add "NOTE	$FULL_SUITE_NOTE"
      ;;
    core/data/*)
      add ":core:data:testAndroidHostTest	core/data ${layer:+[$layer] }— outbox/preferences/analytics tests"
      ;;
    core/database/*)
      add ":core:database:testAndroidHostTest	core/database — schema/migration tests (schema change ⇒ add a MigrationV*Test)"
      ;;
    core/designsystem/*)
      add ":core:designsystem:testAndroidHostTest	designsystem — Robolectric Compose Fr* tests; public Fr* needs a catalogApp entry"
      ;;
    core/presentation/*)
      add ":core:presentation:testAndroidHostTest	MVI base — MviViewModelTest"
      ;;
    core/i18n/*)
      add ":core:i18n:testAndroidHostTest	i18n core"
      ;;
    feature/*)
      mod="${f#feature/}"; mod="${mod%%/*}"
      add ":feature:${mod}:testAndroidHostTest	feature/${mod} ${layer:+[$layer] }— commonTest + androidHostTest"
      case "$f" in
        */composeResources/*|*/strings*.xml)
          add "NOTE	strings changed — keep <Feature>StringKey + *ErrorToStringKeyTest + values-es in sync" ;;
      esac
      ;;
    shared/*)
      add ":shared:testAndroidHostTest	shared — root nav / deep-link / consent / recap tests"
      ;;
    androidApp/*)
      add ":androidApp:assembleDebug	androidApp has no test source set — build is the gate; release/minify changes need the device smoke on the minified AAB"
      ;;
    catalogApp/*)
      add ":catalogApp:assembleDebug	catalog — build gate"
      ;;
    iosApp/*)
      add ":shared:linkDebugFrameworkIosSimulatorArm64	iOS/Swift glue — framework link is the only local gate (no xcodebuild here)"
      ;;
    functions/*)
      add "pnpm --dir functions test	Cloud Functions (vitest)"
      add "pnpm --dir functions build	Cloud Functions typecheck (tsc)"
      ;;
    firestore.rules|storage.rules)
      add "NOTE	no local rules tests — re-run the data-layer tests of the touched collections; beware full-doc set() vs hasOnly regressions; deploy per CLAUDE.md"
      ;;
    *.gradle.kts|gradle/*|gradle.properties)
      add ":androidApp:assembleDebug	build script changed — configuration + build gate; rerun the touched module's testAndroidHostTest too"
      ;;
  esac
done

[ "${#OUT[@]}" -eq 0 ] && exit 0
printf '%s\n' "${OUT[@]}" | awk -F'\t' '!seen[$0]++'
