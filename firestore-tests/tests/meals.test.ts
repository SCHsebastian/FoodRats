import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv, mealId } from "./helpers";

let env: RulesTestEnvironment;

const CREW = "c1";
const DAY = "2026-06-13";
const ID = mealId(CREW, "alice", DAY, "lunch");
const PATH = `crews/${CREW}/meals/${ID}`;

/** A valid, zero-aggregate meal payload as the client publishes it. */
const validMeal = (overrides: Record<string, unknown> = {}) => ({
  id: ID,
  authorId: "alice",
  crewId: CREW,
  dayKey: DAY,
  slot: "lunch",
  platePath: "crews/c1/meals/c1_alice_2026-06-14_lunch.jpg",
  publishedAtEpochMs: Date.now(),
  ratings: {},
  ratingSum: 0,
  voterCount: 0,
  ...overrides,
});

/** N well-formed multi-photo plate entries — the ordered `{path, source}` shape the client publishes. */
const platesOf = (n: number) =>
  Array.from({ length: n }, (_, i) => ({
    path: `crews/${CREW}/meals/${ID}_${i}.jpg`,
    source: i % 2 === 0 ? "camera" : "gallery",
  }));

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
  });
});

describe("meals create — author + membership", () => {
  it("a member can publish a meal with zero aggregates", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, PATH), validMeal()));
  });

  it("a member can publish with aggregate fields ABSENT (defaults omitted)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    const { ratings, ratingSum, voterCount, ...noAggregates } = validMeal();
    await assertSucceeds(setDoc(doc(db, PATH), noAggregates));
  });

  it("a non-member cannot publish", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    const cid = mealId(CREW, "charlie", DAY, "lunch");
    await assertFails(setDoc(doc(db, `crews/${CREW}/meals/${cid}`), validMeal({ id: cid, authorId: "charlie" })));
  });

  it("cannot publish under someone else's authorId / mealId", async () => {
    const db = env.authenticatedContext("bob").firestore();
    // bob tries to write alice's deterministic meal id
    await assertFails(setDoc(doc(db, PATH), validMeal()));
  });
});

describe("meals create — optional slot + token id shape", () => {
  it("publishes with NO slot (empty string)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    const id = mealId(CREW, "alice", DAY, "t1");
    await assertSucceeds(setDoc(doc(db, `crews/${CREW}/meals/${id}`), validMeal({ id, slot: "" })));
  });

  it("publishes the new slots (brunch / snack / merienda)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    for (const slot of ["brunch", "snack", "merienda"]) {
      const id = mealId(CREW, "alice", DAY, `tok-${slot}`);
      await assertSucceeds(setDoc(doc(db, `crews/${CREW}/meals/${id}`), validMeal({ id, slot })));
    }
  });

  it("REJECTS an unknown slot value", async () => {
    const db = env.authenticatedContext("alice").firestore();
    const id = mealId(CREW, "alice", DAY, "t2");
    await assertFails(setDoc(doc(db, `crews/${CREW}/meals/${id}`), validMeal({ id, slot: "brinner" })));
  });

  it("REJECTS an id whose crew segment doesn't match the path crew", async () => {
    const db = env.authenticatedContext("alice").firestore();
    const id = mealId("other", "alice", DAY, "t3"); // split[0] != CREW
    await assertFails(setDoc(doc(db, `crews/${CREW}/meals/${id}`), validMeal({ id })));
  });

  it("REJECTS a malformed id (wrong number of '_' parts)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    const id = `${CREW}_alice_${DAY}_a_b`; // 5 parts after split
    await assertFails(setDoc(doc(db, `crews/${CREW}/meals/${id}`), validMeal({ id })));
  });
});

describe("meals create — award-aggregate self-stuffing (the P1 fix)", () => {
  it("REJECTS create with ratingSum > 0", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, PATH), validMeal({ ratingSum: 9999, voterCount: 0 })));
  });

  it("REJECTS create with voterCount > 0", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, PATH), validMeal({ voterCount: 9 })));
  });

  it("REJECTS create with a pre-populated ratings map", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, PATH), validMeal({ ratings: { bob: { score: 5 } }, ratingSum: 0, voterCount: 0 })),
    );
  });

  it("REJECTS create with a far-future publishedAt (digest-window dodge)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    const tenDays = Date.now() + 10 * 24 * 60 * 60 * 1000;
    await assertFails(setDoc(doc(db, PATH), validMeal({ publishedAtEpochMs: tenDays })));
  });

  it("REJECTS create with a server-owned field (thumbHash spoof)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, PATH), validMeal({ thumbHash: "forged" })));
  });

  it("REJECTS create with an unknown field (field whitelist)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, PATH), validMeal({ photoUrl: "https://x/p.jpg" })));
  });
});

describe("meals create — multi-photo plates (Wave 2 whitelist addition)", () => {
  it("publishes with plates: [] (new clients send the empty list)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, PATH), validMeal({ plates: [] })));
  });

  it("publishes with plates: null (encodeDefaults null case)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, PATH), validMeal({ plates: null })));
  });

  it("publishes with 10 well-formed plate entries (at the cap)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, PATH), validMeal({ plates: platesOf(10) })));
  });

  it("REJECTS 11 plate entries (over the cap)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, PATH), validMeal({ plates: platesOf(11) })));
  });

  it("REJECTS plates as a non-list value", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, PATH), validMeal({ plates: "not-a-list" })));
  });

  it("publishes a legacy-shaped meal with NO plates key at all (old behavior locked)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, PATH), validMeal()));
  });
});

describe("meals update — plates is immutable post-create", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), PATH), validMeal({ plates: platesOf(2) }));
    });
  });

  it("REJECTS an update that changes plates alongside an otherwise-valid rating write", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, PATH), {
        ratings: { bob: { score: 5 } },
        ratingSum: 5,
        voterCount: 1,
        plates: platesOf(3),
      }),
    );
  });

  it("ACCEPTS an update that re-sends plates with the IDENTICAL current value (diff sees no change)", async () => {
    // The update rule gates on `request.resource.data.diff(resource.data).affectedKeys()
    // .hasOnly(['ratings','ratingSum','voterCount'])`. `diff()` compares by VALUE, so resending the
    // exact same ordered `plates` array (same paths, same order) is not an "affected key" even
    // though the client included it in the write payload — locks the ACTUAL (accepted) behavior.
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      updateDoc(doc(db, PATH), {
        ratings: { bob: { score: 5 } },
        ratingSum: 5,
        voterCount: 1,
        plates: platesOf(2),
      }),
    );
  });
});

describe("meals update — rating write on a meal with a populated plates array (regression)", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), PATH), validMeal({ plates: platesOf(3) }));
    });
  });

  it("a member can still rate a multi-photo meal without touching plates", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      updateDoc(doc(db, PATH), { ratings: { bob: { score: 5 } }, ratingSum: 5, voterCount: 1 }),
    );
  });
});

describe("meals create — plates edge cases (Wave 3 edge-rules hardening)", () => {
  it("ACCEPTS a plate entry carrying EXTRA keys (the rule only checks `is list` + size, never entry shape)", async () => {
    // firestore.rules L340-342: "Per-entry shape ... isn't validated further here — same trust
    // level as the single-photo platePath/plateSource fields above." hasOnly() is a TOP-LEVEL
    // document check and does not recurse into list entries, so a stray extra key inside one
    // {path, source} entry is not rejected. Locking the actual (accepted) behavior.
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(
        doc(db, PATH),
        validMeal({
          plates: [{ path: `crews/${CREW}/meals/${ID}_0.jpg`, source: "camera", thumb: "x" }],
        }),
      ),
    );
  });

  it("ACCEPTS a max-payload create: every whitelisted field populated + 10 plates (at the cap)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(
        doc(db, PATH),
        validMeal({
          authorName: "Alice A.",
          dishName: "Paella",
          description: "Weekend paella with the whole crew.",
          latitude: 40.4168,
          longitude: -3.7038,
          ingredients: ["rice", "shrimp", "chorizo"],
          classifierVersion: "food101-v3",
          cuisine: "spanish",
          kind: "solo",
          plateSource: "camera",
          thumbHash: null,
          thumbnailPath: null,
          plates: platesOf(10),
        }),
      ),
    );
  });

  it("REJECTS an unknown top-level field even when plates is otherwise valid (hasOnly still governs)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, PATH), validMeal({ plates: platesOf(2), photoUrl: "https://x/p.jpg" })),
    );
  });

  it("a non-member CANNOT publish even with an otherwise-valid plates array", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    const cid = mealId(CREW, "charlie", DAY, "lunch");
    await assertFails(
      setDoc(
        doc(db, `crews/${CREW}/meals/${cid}`),
        validMeal({ id: cid, authorId: "charlie", plates: platesOf(2) }),
      ),
    );
  });

  it("REJECTS plates as a map/object instead of a list", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(
        doc(db, PATH),
        validMeal({
          plates: { "0": { path: `crews/${CREW}/meals/${ID}_0.jpg`, source: "camera" } },
        }),
      ),
    );
  });
});

describe("meals read + rating", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), PATH), validMeal());
    });
  });

  it("a crew member can read a crew meal", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(getDoc(doc(db, PATH)));
  });

  it("a non-member cannot read a crew meal", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(getDoc(doc(db, PATH)));
  });

  it("a member (non-author) can add their own rating", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      updateDoc(doc(db, PATH), { ratings: { bob: { score: 5 } }, ratingSum: 5, voterCount: 1 }),
    );
  });

  it("the author CANNOT rate their own meal (self-vote)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(db, PATH), { ratings: { alice: { score: 5 } }, ratingSum: 5, voterCount: 1 }),
    );
  });

  it("a FIRST vote that already claims edited:true is rejected", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, PATH), {
        ratings: { bob: { score: 5, edited: true } },
        ratingSum: 5,
        voterCount: 1,
      }),
    );
  });
});

describe("meals — vote change (one edit)", () => {
  // Seed a meal bob has already rated 3, NOT yet edited.
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), PATH),
        validMeal({
          ratings: { bob: { score: 3, atMs: Date.now(), edited: false } },
          ratingSum: 3,
          voterCount: 1,
        }),
      );
    });
  });

  it("a voter can CHANGE their vote once (edited:false -> true)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      updateDoc(doc(db, PATH), {
        ratings: { bob: { score: 5, atMs: Date.now(), edited: true } },
        ratingSum: 5, // 3 - 3 + 5
        voterCount: 1,
      }),
    );
  });

  it("a change that does NOT mark edited:true is rejected", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, PATH), {
        ratings: { bob: { score: 5, atMs: Date.now(), edited: false } },
        ratingSum: 5,
        voterCount: 1,
      }),
    );
  });

  it("a change with an INCORRECT sum delta is rejected", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, PATH), {
        ratings: { bob: { score: 5, atMs: Date.now(), edited: true } },
        ratingSum: 8, // wrong: must be 5
        voterCount: 1,
      }),
    );
  });

  it("a voter CANNOT change again once the entry is edited", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), PATH),
        validMeal({
          ratings: { bob: { score: 5, atMs: Date.now(), edited: true } },
          ratingSum: 5,
          voterCount: 1,
        }),
      );
    });
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, PATH), {
        ratings: { bob: { score: 2, atMs: Date.now(), edited: true } },
        ratingSum: 2,
        voterCount: 1,
      }),
    );
  });
});
