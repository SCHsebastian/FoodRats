/**
 * Regression locks for the 2026-07-19 security sweep (docs/session/2026-07-19-security-review/
 * REPORT-code-sweep.md): field whitelists, string-length caps and members-map diff pins that
 * closed doc-bloat / forged-field gaps which had drifted in since the 2026-06-25 review.
 */
import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv, mealId } from "./helpers";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await makeEnv();
});
afterAll(async () => {
  await env.cleanup();
});
beforeEach(async () => {
  await env.clearFirestore();
});

// ─── accounts/{uid} — field whitelist + caps + avatarPath namespace pin ────────

describe("accounts — whitelist, caps, avatarPath pin (sweep)", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "accounts/alice"), {
        id: "alice",
        handle: "alice",
        displayName: "Alice",
      });
    });
  });

  it("owner CANNOT add an unknown field on update (no mass-assignment)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "accounts/alice"), { isAdmin: true }));
  });

  it("owner CANNOT create a profile with an unknown field", async () => {
    const db = env.authenticatedContext("newuser").firestore();
    await assertFails(
      setDoc(doc(db, "accounts/newuser"), { id: "newuser", displayName: "N", role: "admin" }),
    );
  });

  it("owner CANNOT write an oversized bio (doc-bloat cap)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "accounts/alice"), { bio: "x".repeat(10_000) }));
  });

  it("owner CANNOT write an oversized displayName", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "accounts/alice"), { displayName: "x".repeat(500) }));
  });

  it("owner CAN update displayName / bio / handle within caps", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "accounts/alice"), {
        displayName: "Alice B.",
        bio: "I cook.",
        handle: "aliceb",
      }),
    );
  });

  it("owner CAN set their own content-versioned avatarPath", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "accounts/alice"), { avatarPath: "avatars/alice/abc123.jpg" }),
    );
  });

  it("owner CAN set the legacy fixed avatarPath and clear it", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "accounts/alice"), { avatarPath: "avatars/alice.jpg" }));
    await assertSucceeds(updateDoc(doc(db, "accounts/alice"), { avatarPath: null }));
  });

  it("owner CANNOT point avatarPath at ANOTHER user's avatar object (impersonation pin)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(db, "accounts/alice"), { avatarPath: "avatars/bob/stolen.jpg" }),
    );
    await assertFails(updateDoc(doc(db, "accounts/alice"), { avatarPath: "avatars/bob.jpg" }));
  });

  it("full AccountDto create (the real sign-in write) still succeeds", async () => {
    const db = env.authenticatedContext("newuser").firestore();
    await assertSucceeds(
      setDoc(doc(db, "accounts/newuser"), {
        id: "newuser",
        handle: "newuser",
        displayName: "New User",
        avatarPath: null,
        bio: null,
        createdAtEpochMs: Date.now(),
        dataConsentVersion: 0,
        dataConsentGrantedAtEpochMs: null,
      }),
    );
  });

  it("a legacy doc with a stray since-renamed field can still be updated (diff whitelist, not post-state)", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "accounts/legacy"), {
        id: "legacy",
        displayName: "Old",
        avatarUrl: "https://x/a.jpg", // pre-rename field still on the doc
      });
    });
    const db = env.authenticatedContext("legacy").firestore();
    await assertSucceeds(updateDoc(doc(db, "accounts/legacy"), { displayName: "Still works" }));
  });
});

// ─── crews — settings caps + members-map diff pins ─────────────────────────────

const seedCrew = async (id: string, data: Record<string, unknown>) =>
  env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `crews/${id}`), data);
  });

describe("crews — settings length caps (sweep)", () => {
  beforeEach(async () => {
    await seedCrew("c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice", "bob"],
      members: { alice: { joinedAtEpochMs: 1 }, bob: { joinedAtEpochMs: 2 } },
    });
  });

  it("owner CANNOT rename the crew to an oversized name (update was uncapped)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { name: "x".repeat(2000) }));
  });

  it("owner CANNOT set a non-string name on update", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { name: 42 }));
  });

  it("owner CAN still rename within the cap", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { name: "Renamed" }));
  });

  it("owner CANNOT set an oversized tagline / welcomeMessage / weeklyChallenge", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { tagline: "x".repeat(301) }));
    await assertFails(updateDoc(doc(db, "crews/c1"), { welcomeMessage: "x".repeat(501) }));
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        weeklyChallenge: "x".repeat(201),
        weeklyChallengeSetAtMillis: Date.now(),
      }),
    );
  });

  it("owner CANNOT create a crew with an oversized tagline", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, "crews/new1"), {
        ownerId: "alice",
        name: "Mine",
        memberIds: ["alice"],
        members: { alice: {} },
        tagline: "x".repeat(301),
      }),
    );
  });
});

describe("crews — members-map diff pins (sweep)", () => {
  beforeEach(async () => {
    await seedCrew("c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice", "bob", "carol"],
      members: {
        alice: { joinedAtEpochMs: 1 },
        bob: { joinedAtEpochMs: 2 },
        carol: { joinedAtEpochMs: 3 },
      },
    });
  });

  it("a member CANNOT rewrite ANOTHER member's entry via a members-only write", async () => {
    // Previously allowed: memberMapWrite only required hasOnly(['members']). A member could
    // forge crew-mates' joinedAtEpochMs (which deleteAccount's ownership reassignment ranks by).
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        members: {
          alice: { joinedAtEpochMs: 9_999 }, // forged
          bob: { joinedAtEpochMs: 2 },
          carol: { joinedAtEpochMs: 3 },
        },
      }),
    );
  });

  it("a member CAN still touch only their OWN members entry", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), {
        members: {
          alice: { joinedAtEpochMs: 1 },
          bob: { joinedAtEpochMs: 22 },
          carol: { joinedAtEpochMs: 3 },
        },
      }),
    );
  });

  it("a member CANNOT stuff extra fields into their own members entry (MemberDto shape)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        members: {
          alice: { joinedAtEpochMs: 1 },
          bob: { joinedAtEpochMs: 2, blob: "x".repeat(1000) },
          carol: { joinedAtEpochMs: 3 },
        },
      }),
    );
  });

  it("a leaver cannot forge a surviving member's entry on the way out", async () => {
    // bob leaves (memberIds drops bob) but also rewrites alice's joinedAtEpochMs.
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "carol"],
        members: {
          alice: { joinedAtEpochMs: 9_999 }, // forged
          carol: { joinedAtEpochMs: 3 },
        },
      }),
    );
  });

  it("a clean leave (own entry removed, others untouched) still succeeds", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "carol"],
        members: {
          alice: { joinedAtEpochMs: 1 },
          carol: { joinedAtEpochMs: 3 },
        },
      }),
    );
  });
});

// ─── crewCodes — shape whitelist ───────────────────────────────────────────────

describe("crewCodes — shape whitelist (sweep)", () => {
  beforeEach(async () => {
    await seedCrew("c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice"],
      members: { alice: {} },
    });
  });

  it("a member can mint a code with the CrewCodeDto shape", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(db, "crewCodes/NEW123"), { crewId: "c1", createdAtEpochMs: Date.now() }),
    );
  });

  it("the crew-creation path (crew not yet committed) still works", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(db, "crewCodes/FRESH1"), { crewId: "not-yet-created", createdAtEpochMs: null }),
    );
  });

  it("a code doc CANNOT smuggle extra fields", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, "crewCodes/EVIL01"), {
        crewId: "c1",
        createdAtEpochMs: Date.now(),
        payload: "x".repeat(1000),
      }),
    );
  });

  it("a code doc without a string crewId is rejected", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, "crewCodes/EVIL02"), { crewId: 42 }));
  });
});

// ─── meals — string caps + rating-entry shape ──────────────────────────────────

const CREW = "c1";
const DAY = "2026-07-19";
const ID = mealId(CREW, "alice", DAY, "t0k3n");
const PATH = `crews/${CREW}/meals/${ID}`;

const validMeal = (overrides: Record<string, unknown> = {}) => ({
  id: ID,
  authorId: "alice",
  crewId: CREW,
  dayKey: DAY,
  slot: "lunch",
  platePath: `crews/${CREW}/meals/${ID}.jpg`,
  publishedAtEpochMs: Date.now(),
  ratings: {},
  ratingSum: 0,
  voterCount: 0,
  ...overrides,
});

describe("meals — client-string caps + vote-entry shape (sweep)", () => {
  beforeEach(async () => {
    await seedCrew(CREW, {
      ownerId: "alice",
      name: "Crew One",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
    });
  });

  it("a meal with in-cap authorName/dishName/description/ingredients publishes fine", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(
        doc(db, PATH),
        validMeal({
          authorName: "Alice",
          dishName: "Lentejas de la abuela",
          description: "Rich and smoky.",
          ingredients: ["lentils", "chorizo"],
          classifierVersion: "food101-v1",
          cuisine: "spanish",
        }),
      ),
    );
  });

  it("REJECTS an oversized dishName (FCM 4KB payload / doc-bloat DoS)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, PATH), validMeal({ dishName: "x".repeat(5000) })));
  });

  it("REJECTS an oversized authorName", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, PATH), validMeal({ authorName: "x".repeat(500) })));
  });

  it("REJECTS an oversized description", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, PATH), validMeal({ description: "x".repeat(5000) })));
  });

  it("REJECTS an ingredients list above the client cap (30)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, PATH), validMeal({ ingredients: Array.from({ length: 31 }, (_, i) => `i${i}`) })),
    );
  });

  it("a voter CANNOT stash extra payload inside their own rating entry", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), PATH), validMeal());
    });
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, PATH), {
        ratings: { bob: { score: 5, atMs: Date.now(), edited: false, blob: "x".repeat(1000) } },
        ratingSum: 5,
        voterCount: 1,
      }),
    );
  });

  it("a normal first vote (score/atMs/edited) still succeeds", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), PATH), validMeal());
    });
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

// ─── comments — update field whitelist ─────────────────────────────────────────

describe("comments — update whitelist (sweep)", () => {
  const COMMENT_PATH = `${PATH}/comments/cmt-1`;

  beforeEach(async () => {
    await seedCrew(CREW, {
      ownerId: "alice",
      name: "Crew One",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
    });
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, PATH), validMeal());
      await setDoc(doc(db, COMMENT_PATH), {
        id: "cmt-1",
        authorId: "bob",
        text: "hola crew",
        createdAtEpochMs: Date.now(),
      });
    });
  });

  it("the author CANNOT smuggle an extra field in via the edit path", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, COMMENT_PATH), {
        text: "edited",
        editedAtEpochMs: Date.now(),
        pinned: true,
      }),
    );
  });

  it("the author CANNOT rewrite authorName to an oversized value via the edit path", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, COMMENT_PATH), {
        text: "edited",
        editedAtEpochMs: Date.now(),
        authorName: "x".repeat(500),
      }),
    );
  });

  it("a normal edit still succeeds", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      updateDoc(doc(db, COMMENT_PATH), { text: "edited words", editedAtEpochMs: Date.now() }),
    );
  });
});

// ─── reactions — shape whitelist ───────────────────────────────────────────────

describe("reactions — shape whitelist (sweep)", () => {
  const REACTION_PATH = `${PATH}/reactions/bob`;

  beforeEach(async () => {
    await seedCrew(CREW, {
      ownerId: "alice",
      name: "Crew One",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
    });
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), PATH), validMeal());
    });
  });

  it("a reaction CANNOT smuggle extra fields", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(db, REACTION_PATH), {
        reactorId: "bob",
        kind: "fire",
        reactedAtEpochMs: Date.now(),
        blob: "x".repeat(1000),
      }),
    );
  });

  it("a normal reaction still succeeds", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      setDoc(doc(db, REACTION_PATH), {
        reactorId: "bob",
        kind: "fire",
        reactedAtEpochMs: Date.now(),
      }),
    );
  });
});
