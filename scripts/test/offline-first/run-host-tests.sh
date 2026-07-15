#!/bin/sh
# run-host-tests.sh — AI-free runner for the offline-first host-test surface.
#
# Runs every module test task that contains offline-first coverage (outbox,
# draft queue, sync engines, local-first stores, connectivity, queue UI) and
# prints a PASS/FAIL summary. Safe to run on a clean checkout; no emulator,
# no AI, no network beyond Gradle's cache.
#
# Usage: scripts/test/offline-first/run-host-tests.sh [--filtered]
#   --filtered  run only the offline-first test classes (faster); default runs
#               the full task per module (safer signal, still quick).

set -u
cd "$(git rev-parse --show-toplevel)" || exit 2

TASKS=":core:domain:testAndroidHostTest :core:data:testAndroidHostTest :core:database:testAndroidHostTest :feature:meal:testAndroidHostTest :feature:crew:testAndroidHostTest :feature:auth:testAndroidHostTest :feature:feed:testAndroidHostTest :core:designsystem:testAndroidHostTest :shared:testAndroidHostTest"

# Offline-first test classes (kept in sync with docs/testing/TEST-MAP.md and
# docs/session/2026-07-14-code-cleaning/OFFLINE-FIRST-PLAN.md):
FILTER_CLASSES="*OutboxRunnerTest* *OutboxLocalStoreTest* *OutboxRepositoryTest* *OutboxJsonMigrationTest* *OutboxTerminalPrunerTest* *OutboxTableTest* *MigrationV1ToV2Test* *OutboxRetryPolicyTest* *OutboxTransitionsTest* *DraftRetryRunnerTest* *DraftQueueRepositoryTest* *DraftQueueLocalStoreTest* *BackgroundMealUploadCoordinatorTest* *MealLocalStoreTest* *MealSyncEngineTest* *CachePrunerTest* *MealOutboxCommandHandlerTest* *CrewSyncEngineTest* *CrewLocalStoreTest* *CrewOutboxCommandHandlerTest* *CrewSettingsViewModelTest* *AuthOutboxCommandHandlerTest* *FrUploadQueueBarTest* *FrSyncStatusBarTest* *FrOfflineBannerTest* *ConnectivityViewModelTest*"

MODE="full"
[ "${1:-}" = "--filtered" ] && MODE="filtered"

echo "offline-first host tests — mode: $MODE"
if [ "$MODE" = "filtered" ]; then
  # --tests applies per task; Gradle accepts repeated --tests flags globally
  # for multiple tasks as long as each task has at least one match, so run
  # per-module to avoid 'no tests found' hard failures on modules without a
  # given class.
  FAIL=0
  for task in $TASKS; do
    ARGS=""
    for c in $FILTER_CLASSES; do ARGS="$ARGS --tests $c"; done
    # shellcheck disable=SC2086
    if ./gradlew "$task" $ARGS >/dev/null 2>&1; then
      echo "PASS $task (filtered)"
    else
      # Distinguish 'no matching tests' from real failure by rerunning full.
      # shellcheck disable=SC2086
      OUT=$(./gradlew "$task" $ARGS 2>&1)
      if echo "$OUT" | grep -q "No tests found"; then
        echo "SKIP $task (no offline-first classes)"
      else
        echo "FAIL $task"; echo "$OUT" | grep -E "FAILED|tests completed" | head -5
        FAIL=1
      fi
    fi
  done
else
  # shellcheck disable=SC2086
  if ./gradlew $TASKS; then
    echo "PASS: all offline-first module suites green"
    FAIL=0
  else
    echo "FAIL: see gradle output above"
    FAIL=1
  fi
fi

exit $FAIL
