import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, setDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv, mealId } from "./helpers";

let env: RulesTestEnvironment;

const CREW = "c1";
const DAY = "2026-06-13";
const ID = mealId(CREW, "alice", DAY, "lunch");
const MEAL_PATH = `crews/${CREW}/meals/${ID}`;
const reactionPath = (reactorUid: string) => `${MEAL_PATH}/reactions/${reactorUid}`;

/** A valid reaction payload as the client toggles it on. */
const validReaction = (reactorUid: string, overrides: Record<string, unknown> = {}) => ({
  reactorId: reactorUid,
  kind: "fire",
  reactedAtEpochMs: Date.now(),
  ...overrides,
});

beforeAll(async () => {
  env = await makeEnv();
});
afterAll(async () => {
  await env.cleanup();
});
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    // Crew c1: alice (owner) + bob are members; charlie is not.
    await setDoc(doc(db, "crews/c1"), {
      ownerId: "alice",
      name: "Crew One",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
    });
    // alice's lunch meal exists so reactions hang off a real meal.
    await setDoc(doc(db, MEAL_PATH), {
      id: ID,
      authorId: "alice",
      crewId: CREW,
      dayKey: DAY,
      slot: "lunch",
      platePath: "crews/c1/meals/c1_alice_2026-06-13_lunch.jpg",
      publishedAtEpochMs: Date.now(),
      ratings: {},
      ratingSum: 0,
      voterCount: 0,
    });
  });
});

describe("reactions — create (toggle on)", () => {
  it("a member can create their own reaction with a valid timestamp", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(setDoc(doc(db, reactionPath("bob")), validReaction("bob")));
  });

  it("the author can react to their own meal (no self-react ban)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, reactionPath("alice")), validReaction("alice")));
  });

  it("a non-member CANNOT create a reaction", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(setDoc(doc(db, reactionPath("charlie")), validReaction("charlie")));
  });

  it("a member CANNOT create a reaction under another member's doc id", async () => {
    // bob writes the reactions/alice doc — reactorUid mismatch.
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, reactionPath("alice")), validReaction("bob")));
  });

  it("reactorId payload CANNOT be spoofed to another uid", async () => {
    // bob owns the doc id but stamps alice as reactorId.
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, reactionPath("bob")), validReaction("bob", { reactorId: "alice" })));
  });

  it("REJECTS a reactedAtEpochMs in the far future (outside +60s)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(db, reactionPath("bob")), validReaction("bob", { reactedAtEpochMs: Date.now() + 5 * 60_000 })),
    );
  });

  it("REJECTS a reactedAtEpochMs in the far past (outside -60s)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(db, reactionPath("bob")), validReaction("bob", { reactedAtEpochMs: Date.now() - 5 * 60_000 })),
    );
  });

  it("REJECTS a missing kind field", async () => {
    const db = env.authenticatedContext("bob").firestore();
    const { kind, ...noKind } = validReaction("bob");
    await assertFails(setDoc(doc(db, reactionPath("bob")), noKind));
  });

  it("REJECTS a non-string kind", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, reactionPath("bob")), validReaction("bob", { kind: 7 })));
  });

  it("REJECTS an empty kind (below 1-char floor)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, reactionPath("bob")), validReaction("bob", { kind: "" })));
  });

  it("REJECTS a kind longer than 40 chars", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, reactionPath("bob")), validReaction("bob", { kind: "x".repeat(41) })));
  });

  it("REJECTS a missing reactedAtEpochMs field", async () => {
    const db = env.authenticatedContext("bob").firestore();
    const { reactedAtEpochMs, ...noTimestamp } = validReaction("bob");
    await assertFails(setDoc(doc(db, reactionPath("bob")), noTimestamp));
  });

  it("REJECTS a non-int reactedAtEpochMs", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, reactionPath("bob")), validReaction("bob", { reactedAtEpochMs: "now" })));
  });
});

describe("reactions — update (change kind)", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), reactionPath("bob")), {
        reactorId: "bob",
        kind: "fire",
        reactedAtEpochMs: Date.now(),
      });
    });
  });

  it("a member can overwrite their own reaction with a different kind", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(setDoc(doc(db, reactionPath("bob")), validReaction("bob", { kind: "heart" })));
  });

  it("a member CANNOT overwrite another member's reaction", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, reactionPath("bob")), validReaction("bob", { kind: "heart" })));
  });
});

describe("reactions — read", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), reactionPath("bob")), {
        reactorId: "bob",
        kind: "fire",
        reactedAtEpochMs: Date.now(),
      });
    });
  });

  it("a crew member can read a reaction", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(getDoc(doc(db, reactionPath("bob"))));
  });

  it("a non-member CANNOT read a reaction", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(getDoc(doc(db, reactionPath("bob"))));
  });
});

describe("reactions — delete (toggle off)", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), reactionPath("bob")), {
        reactorId: "bob",
        kind: "fire",
        reactedAtEpochMs: Date.now(),
      });
    });
  });

  it("a member can delete their own reaction", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(deleteDoc(doc(db, reactionPath("bob"))));
  });

  it("a member CANNOT delete another member's reaction", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(deleteDoc(doc(db, reactionPath("bob"))));
  });

  it("a non-member CANNOT delete a reaction", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(deleteDoc(doc(db, reactionPath("bob"))));
  });
});
