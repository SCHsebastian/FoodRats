import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv } from "./helpers";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await makeEnv();
});
afterAll(async () => {
  await env.cleanup();
});
beforeEach(async () => {
  await env.clearFirestore();
  // Seed one already-unlocked achievement for alice (server/admin write bypasses rules).
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "accounts/alice/achievements/first_plate"), {
      unlockedAtEpochMs: 1_700_000_000_000,
    });
  });
});

describe("accounts/{uid}/achievements — owner-only unlock timestamps (badges §16)", () => {
  it("owner can read own unlocked achievement", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(getDoc(doc(db, "accounts/alice/achievements/first_plate")));
  });

  it("owner can write (client-derived earning) own achievement", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(db, "accounts/alice/achievements/meals_10"), {
        unlockedAtEpochMs: 1_700_000_100_000,
      }),
    );
  });

  it("a different authed user CANNOT read another's achievements (private)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(getDoc(doc(db, "accounts/alice/achievements/first_plate")));
  });

  it("a different authed user cannot write another's achievements", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(db, "accounts/alice/achievements/first_plate"), {
        unlockedAtEpochMs: 1,
      }),
    );
  });

  it("an UNAUTHENTICATED user cannot read achievements", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "accounts/alice/achievements/first_plate")));
  });
});
