import {
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

/** The actual production rules under test — repo-root `firestore.rules`. */
export const RULES = readFileSync(resolve(here, "..", "..", "firestore.rules"), "utf8");

/**
 * Connects to the Firestore emulator started by `firebase emulators:exec`
 * (which exports FIRESTORE_EMULATOR_HOST) and loads the production rules.
 * Uses a `demo-` project id so the emulator runs fully offline (no creds).
 */
export function makeEnv(): Promise<RulesTestEnvironment> {
  return initializeTestEnvironment({
    projectId: "demo-foodrats",
    firestore: { rules: RULES },
  });
}

/**
 * A meal id matching the rule's deterministic format `crew_uid_day_token`. The 4th part is the
 * per-post token (a photo hash in production); the rule treats it as opaque, so tests pass any
 * non-`_` string here.
 */
export const mealId = (crew: string, uid: string, day: string, token: string) =>
  `${crew}_${uid}_${day}_${token}`;
