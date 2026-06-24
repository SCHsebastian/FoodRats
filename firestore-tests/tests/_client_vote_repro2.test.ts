import { assertFails, assertSucceeds, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv, mealId } from "./helpers";

let env: RulesTestEnvironment;
const CREW = "c1";
const DAY = "2026-06-13";
const ID = mealId(CREW, "alice", DAY, "lunch");
const PATH = `crews/${CREW}/meals/${ID}`;
const baseMeal = (o: Record<string, unknown> = {}) => ({
  id: ID, authorId: "alice", crewId: CREW, dayKey: DAY, slot: "lunch",
  platePath: "x", publishedAtEpochMs: Date.now(), ratings: {}, ratingSum: 0, voterCount: 0, ...o,
});
beforeAll(async () => { env = await makeEnv(); });
afterAll(async () => { await env.cleanup(); });

async function seed(meal: Record<string, unknown>) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "crews/c1"), { ownerId: "alice", name: "C", memberIds: ["alice","bob","carol"], members: {} });
    await setDoc(doc(ctx.firestore(), PATH), meal);
  });
}

describe("realistic vote scenarios (client recomputes sum/count over WHOLE map)", () => {
  it("bob's FIRST vote when carol already voted (sum/count include carol)", async () => {
    await seed(baseMeal({ ratings: { carol: { score: 4, atMs: 1, edited: false } }, ratingSum: 4, voterCount: 1 }));
    const db = env.authenticatedContext("bob").firestore();
    // client: newRatings = {carol:4, bob:5}; newSum=9; voterCount=2
    await assertSucceeds(updateDoc(doc(db, PATH), {
      ratings: { carol: { score: 4, atMs: 1, edited: false }, bob: { score: 5, atMs: 2, edited: false } },
      ratingSum: 9, voterCount: 2,
    }));
  });

  it("bob CHANGE vote (3->5) when carol present; client edited:true, recompute over map", async () => {
    await seed(baseMeal({
      ratings: { carol: { score: 4, atMs: 1, edited: false }, bob: { score: 3, atMs: 2, edited: false } },
      ratingSum: 7, voterCount: 2,
    }));
    const db = env.authenticatedContext("bob").firestore();
    // client: newRatings={carol:4, bob:5(edited:true)}; newSum=9; voterCount=2
    await assertSucceeds(updateDoc(doc(db, PATH), {
      ratings: { carol: { score: 4, atMs: 1, edited: false }, bob: { score: 5, atMs: 3, edited: true } },
      ratingSum: 9, voterCount: 2,
    }));
  });
});
