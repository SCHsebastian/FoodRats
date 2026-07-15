import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteDoc, doc, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv, mealId } from "./helpers";

let env: RulesTestEnvironment;

const CREW = "c1";
const DAY = "2026-06-24";
const ID = mealId(CREW, "alice", DAY, "lunch");
const MEAL_PATH = `crews/${CREW}/meals/${ID}`;
const commentPath = (commentId: string) => `${MEAL_PATH}/comments/${commentId}`;

/** A valid comment payload as the client writes it on create. */
const validComment = (authorUid: string, overrides: Record<string, unknown> = {}) => ({
  id: "cmt-1",
  authorId: authorUid,
  text: "hola crew",
  createdAtEpochMs: Date.now(),
  ...overrides,
});

/** Seeds a comment doc authored by [authorUid] with rules disabled, so update/delete tests have a target. */
async function seedComment(authorUid: string, overrides: Record<string, unknown> = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), commentPath("cmt-1")), validComment(authorUid, overrides));
  });
}

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
    await setDoc(doc(db, MEAL_PATH), {
      id: ID,
      authorId: "alice",
      crewId: CREW,
      dayKey: DAY,
      slot: "lunch",
      platePath: `crews/c1/meals/${ID}.jpg`,
      publishedAtEpochMs: Date.now(),
      ratings: {},
      ratingSum: 0,
      voterCount: 0,
    });
  });
});

describe("comments — create", () => {
  it("a member can create their own comment", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(setDoc(doc(db, commentPath("cmt-1")), validComment("bob")));
  });

  it("a non-member CANNOT create a comment", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(setDoc(doc(db, commentPath("cmt-1")), validComment("charlie")));
  });

  it("authorId CANNOT be spoofed to another uid", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, commentPath("cmt-1")), validComment("bob", { authorId: "alice" })));
  });

  it("REJECTS empty text and text over 500 chars", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, commentPath("cmt-1")), validComment("bob", { text: "" })));
    await assertFails(setDoc(doc(db, commentPath("cmt-1")), validComment("bob", { text: "x".repeat(501) })));
  });

  it("ALLOWS explicit null mentions/authorName (GitLive encodeDefaults=true on create)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      setDoc(doc(db, commentPath("cmt-1")), validComment("bob", { mentions: null, authorName: null })),
    );
  });

  it("ALLOWS up to 10 mentions", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      setDoc(
        doc(db, commentPath("cmt-1")),
        validComment("bob", { mentions: Array.from({ length: 10 }, (_, i) => `uid${i}`) }),
      ),
    );
  });

  it("REJECTS more than 10 mentions", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(
        doc(db, commentPath("cmt-1")),
        validComment("bob", { mentions: Array.from({ length: 11 }, (_, i) => `uid${i}`) }),
      ),
    );
  });

  it("REJECTS mentions that isn't a list", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, commentPath("cmt-1")), validComment("bob", { mentions: "uid1" })));
  });

  it("REJECTS authorName over 120 chars", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(db, commentPath("cmt-1")), validComment("bob", { authorName: "x".repeat(121) })),
    );
  });

  it("ALLOWS an authorName within 120 chars", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      setDoc(doc(db, commentPath("cmt-1")), validComment("bob", { authorName: "Bob Ross" })),
    );
  });
});

describe("comments — edit (update)", () => {
  it("the author can edit their own comment's text", async () => {
    await seedComment("bob");
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      updateDoc(doc(db, commentPath("cmt-1")), { text: "edited words", editedAtEpochMs: Date.now() }),
    );
  });

  it("the idempotent self-replay (same text) is still allowed", async () => {
    await seedComment("bob");
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(setDoc(doc(db, commentPath("cmt-1")), validComment("bob")));
  });

  it("a non-author member (even the crew owner) CANNOT edit someone else's comment", async () => {
    await seedComment("bob");
    const owner = env.authenticatedContext("alice").firestore(); // alice owns the crew but didn't write it
    await assertFails(updateDoc(doc(owner, commentPath("cmt-1")), { text: "owner override" }));
  });

  it("a non-member CANNOT edit a comment", async () => {
    await seedComment("bob");
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(updateDoc(doc(db, commentPath("cmt-1")), { text: "nope" }));
  });

  it("REJECTS an edit that re-attributes the author", async () => {
    await seedComment("bob");
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(db, commentPath("cmt-1")), { authorId: "alice", text: "x" }));
  });

  it("REJECTS an edit to empty / over-500-char text", async () => {
    await seedComment("bob");
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(db, commentPath("cmt-1")), { text: "" }));
    await assertFails(updateDoc(doc(db, commentPath("cmt-1")), { text: "x".repeat(501) }));
  });

  it("the author can update mentions on edit (partial update always carries the field)", async () => {
    await seedComment("bob");
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      updateDoc(doc(db, commentPath("cmt-1")), {
        text: "edited words",
        editedAtEpochMs: Date.now(),
        mentions: ["alice"],
      }),
    );
    // An empty list (no mentions left after edit) is also valid — the partial
    // update never sends `null`, only a (possibly empty) list.
    await assertSucceeds(
      updateDoc(doc(db, commentPath("cmt-1")), {
        text: "edited again",
        editedAtEpochMs: Date.now(),
        mentions: [],
      }),
    );
  });

  it("REJECTS an edit with more than 10 mentions", async () => {
    await seedComment("bob");
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, commentPath("cmt-1")), {
        text: "edited words",
        mentions: Array.from({ length: 11 }, (_, i) => `uid${i}`),
      }),
    );
  });
});

describe("comments — delete", () => {
  it("the author can delete their own comment", async () => {
    await seedComment("bob");
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(deleteDoc(doc(db, commentPath("cmt-1"))));
  });

  it("the crew owner can delete any comment (moderation)", async () => {
    await seedComment("bob");
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(deleteDoc(doc(db, commentPath("cmt-1"))));
  });

  it("a non-author non-owner member CANNOT delete a comment", async () => {
    await seedComment("alice");
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(deleteDoc(doc(db, commentPath("cmt-1"))));
  });
});
