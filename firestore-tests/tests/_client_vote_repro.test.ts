import { assertFails, assertSucceeds, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv, mealId } from "./helpers";

let env: RulesTestEnvironment;
const CREW = "c1";
const DAY = "2026-06-13";
const ID = mealId(CREW, "alice", DAY, "lunch");
const PATH = `crews/${CREW}/meals/${ID}`;

const baseMeal = (overrides: Record<string, unknown> = {}) => ({
  id: ID, authorId: "alice", crewId: CREW, dayKey: DAY, slot: "lunch",
  platePath: "crews/c1/meals/x.jpg", publishedAtEpochMs: Date.now(),
  ratings: {}, ratingSum: 0, voterCount: 0, ...overrides,
});

beforeAll(async () => { env = await makeEnv(); });
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "crews/c1"), {
      ownerId: "alice", name: "Crew One", memberIds: ["alice", "bob"], members: { alice: {}, bob: {} },
    });
    await setDoc(doc(ctx.firestore(), PATH), baseMeal());
  });
});

describe("CLIENT vote repro — exact RatingEntryDto shape (encodeDefaults=true)", () => {
  // This is EXACTLY what the Kotlin transaction writes for a FIRST vote:
  // RatingEntryDto(score=5, atMs=<now>, edited=false) → { score, atMs, edited:false }
  it("FIRST vote — full RatingEntryDto {score, atMs, edited:false}", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      updateDoc(doc(db, PATH), {
        ratings: { bob: { score: 5, atMs: Date.now(), edited: false } },
        ratingSum: 5,
        voterCount: 1,
      }),
    );
  });
});
