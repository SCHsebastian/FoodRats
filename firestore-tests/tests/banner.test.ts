import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteObject, ref, uploadBytes } from "firebase/storage";
import { doc, setDoc, updateDoc } from "firebase/firestore";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

const here = dirname(fileURLToPath(import.meta.url));
const STORAGE_RULES = readFileSync(resolve(here, "..", "..", "storage.rules"), "utf8");
const FIRESTORE_RULES = readFileSync(resolve(here, "..", "..", "firestore.rules"), "utf8");

const BANNER = "crew_banners/c1/banner.jpg";
// IMAGE-2 — content-versioned banner object name (`{token}.jpg`).
const VERSIONED_BANNER = "crew_banners/c1/9f3c1a2b.jpg";
const VERSIONED_TOKEN = "9f3c1a2b";
const jpeg = (n = 16) => new Uint8Array(n).fill(1);

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-foodrats",
    firestore: { rules: FIRESTORE_RULES },
    storage: { rules: STORAGE_RULES },
  });
});
afterAll(async () => {
  await env.cleanup();
});
beforeEach(async () => {
  await env.clearStorage();
  await env.clearFirestore();
  // Seed crew c1 — owner alice, members alice+bob. The storage banner rule authorizes via a
  // cross-service firestore.get of crews/c1.ownerId, so the doc must exist for the write rule.
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "crews/c1"), {
      id: "c1",
      name: "C1",
      code: "ABC123",
      ownerId: "alice",
      createdAtEpochMs: 1700000000000,
      memberIds: ["alice", "bob"],
      members: { alice: { joinedAtEpochMs: 1 }, bob: { joinedAtEpochMs: 1 } },
      blindVoting: false,
      tagline: null,
      welcomeMessage: null,
      weeklyChallenge: null,
      weeklyChallengeSetAtMillis: null,
      scoreStyle: "stars",
      bannerPath: null,
      bannerToken: null,
      bannerFocalY: null,
    });
  });
});

describe("storage.rules — crew banner upload (C9)", () => {
  it("the crew OWNER can upload a jpeg banner to the deterministic path", async () => {
    const storage = env.authenticatedContext("alice").storage();
    await assertSucceeds(uploadBytes(ref(storage, BANNER), jpeg(), { contentType: "image/jpeg" }));
  });

  it("a non-owner member CANNOT upload the banner", async () => {
    const storage = env.authenticatedContext("bob").storage();
    await assertFails(uploadBytes(ref(storage, BANNER), jpeg(), { contentType: "image/jpeg" }));
  });

  it("an unauthenticated user CANNOT upload the banner", async () => {
    const storage = env.unauthenticatedContext().storage();
    await assertFails(uploadBytes(ref(storage, BANNER), jpeg(), { contentType: "image/jpeg" }));
  });

  it("rejects a non-jpeg content type", async () => {
    const storage = env.authenticatedContext("alice").storage();
    await assertFails(uploadBytes(ref(storage, BANNER), jpeg(), { contentType: "image/png" }));
  });

  it("rejects an oversized (>2MB) banner", async () => {
    const storage = env.authenticatedContext("alice").storage();
    await assertFails(
      uploadBytes(ref(storage, BANNER), jpeg(2 * 1024 * 1024 + 1), { contentType: "image/jpeg" }),
    );
  });

  // IMAGE-2 — content-versioned object name `{token}.jpg`.
  it("the crew OWNER can upload a content-versioned banner (IMAGE-2)", async () => {
    const storage = env.authenticatedContext("alice").storage();
    await assertSucceeds(
      uploadBytes(ref(storage, VERSIONED_BANNER), jpeg(), { contentType: "image/jpeg" }),
    );
  });

  it("a non-owner member CANNOT upload a content-versioned banner", async () => {
    const storage = env.authenticatedContext("bob").storage();
    await assertFails(
      uploadBytes(ref(storage, VERSIONED_BANNER), jpeg(), { contentType: "image/jpeg" }),
    );
  });

  it("rejects an oversized (>2MB) content-versioned banner", async () => {
    const storage = env.authenticatedContext("alice").storage();
    await assertFails(
      uploadBytes(ref(storage, VERSIONED_BANNER), jpeg(2 * 1024 * 1024 + 1), { contentType: "image/jpeg" }),
    );
  });
});

describe("firestore.rules — crew bannerPath field (C9)", () => {
  it("the owner can set bannerPath via a single-field update", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { bannerPath: BANNER }));
  });

  it("a non-owner member CANNOT set bannerPath", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { bannerPath: BANNER }));
  });

  it("the owner can clear bannerPath (remove banner)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { bannerPath: null }));
  });

  // IMAGE-2 — bannerPath + bannerToken are written together.
  it("the owner can set bannerPath + bannerToken together (IMAGE-2)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), { bannerPath: VERSIONED_BANNER, bannerToken: VERSIONED_TOKEN }),
    );
  });

  it("a non-owner member CANNOT set bannerPath + bannerToken", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), { bannerPath: VERSIONED_BANNER, bannerToken: VERSIONED_TOKEN }),
    );
  });

  it("the owner can clear bannerPath + bannerToken together (remove versioned banner)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), { bannerPath: null, bannerToken: null }),
    );
  });
});

// A production-scale crew: the max 15 members, each with the rich personalization sub-fields, and
// every optional top-level field populated. `diff(resource.data)` deep-compares the `members` map,
// so each rule arm that diffs is far more expensive here. The pre-refactor rule recomputed that diff
// in EVERY owner arm (name, blindVoting, tagline, welcomeMessage, weeklyChallenge, scoreStyle,
// bannerPath, …) — for a real crew this can exhaust Firestore's hard 1000-expression evaluation cap
// before the bannerPath arm is reached, so a legitimate owner banner write is denied. THIS is the
// "crew image does not get uploaded" bug. After the refactor the owner path costs a single diff.
const RICH_MEMBER = (i: number) => ({
  joinedAtEpochMs: 1700000000000 + i,
  displayName: `Member Number ${i} With A Fairly Long Display Name`,
  bio: `This is member ${i}'s biography line, long enough to make the members map a heavy value.`,
  badge: "founder",
  accentColor: "#B0561E",
});
const bigCrew = (owner: string) => {
  const uids = Array.from({ length: 15 }, (_, i) => (i === 0 ? owner : `member${i}`));
  return {
    id: "big",
    name: "Production Scale Crew",
    code: "BIG999",
    ownerId: owner,
    createdAtEpochMs: 1700000000000,
    memberIds: uids,
    members: Object.fromEntries(uids.map((u, i) => [u, RICH_MEMBER(i)])),
    blindVoting: true,
    tagline: "A tagline that takes up some space in the document",
    welcomeMessage: "Welcome to the crew! Please read the rules and have fun cooking.",
    weeklyChallenge: "Cook something with at least five ingredients this week",
    weeklyChallengeSetAtMillis: 1700000000000,
    scoreStyle: "emoji",
    bannerPath: null,
    bannerToken: null,
    bannerFocalY: 0.5,
  };
};

describe("firestore.rules — owner banner write at production scale (15 members)", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "crews/big"), bigCrew("alice"));
    });
  });

  it("the OWNER can set bannerPath on a full 15-member crew (must not hit the 1000-expr cap)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/big"), { bannerPath: "crew_banners/big/banner.jpg" }),
    );
  });

  it("the OWNER can set bannerPath + bannerToken on a full 15-member crew (IMAGE-2, no 1000-expr cap)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/big"), {
        bannerPath: "crew_banners/big/9f3c1a2b.jpg",
        bannerToken: "9f3c1a2b",
      }),
    );
  });
});

// Security #4 — plate upload now requires crew membership (crewId path segment was unconstrained).
// Crew c1 (seeded in the top beforeEach): owner alice, members alice + bob; carol is a non-member.
describe("storage.rules — plate upload requires crew membership (#4)", () => {
  const platePath = (uid: string) => `crews/c1/meals/c1_${uid}_2026-06-14_lunch.jpg`;

  it("a crew MEMBER can upload their own plate", async () => {
    const storage = env.authenticatedContext("bob").storage();
    await assertSucceeds(
      uploadBytes(ref(storage, platePath("bob")), jpeg(), { contentType: "image/jpeg" }),
    );
  });

  it("a NON-member CANNOT upload a plate into the crew", async () => {
    const storage = env.authenticatedContext("carol").storage();
    await assertFails(
      uploadBytes(ref(storage, platePath("carol")), jpeg(), { contentType: "image/jpeg" }),
    );
  });

  it("a member still cannot upload a plate naming ANOTHER user's uid", async () => {
    // alice is a member, but the filename embeds bob's uid → the author-scope clause rejects it.
    const storage = env.authenticatedContext("alice").storage();
    await assertFails(
      uploadBytes(ref(storage, platePath("bob")), jpeg(), { contentType: "image/jpeg" }),
    );
  });
});

// Multi-photo filenames carry a `_p{n}` suffix (crew15 multi-photo build), e.g.
// `crews/{crewId}/meals/{crewId}_{uid}_{dayKey}_{token}_p1.jpg`. The storage rule's own regex
// (`filename.matches(crewId + '_' + uid + '_.*\\.jpg')`) tolerates ANY content between the uid and
// `.jpg`, so these lock the ACTUAL (already-passing) contract for the new filename shape — not a
// rule change. Crew c1 (seeded in the top beforeEach): owner alice, members alice + bob; carol is
// a non-member.
describe("storage.rules — multi-photo plate filenames (_p{n} suffix)", () => {
  const multiPlate = (uid: string, n: number) => `crews/c1/meals/c1_${uid}_2026-06-14_tok_p${n}.jpg`;
  const multiThumb = (uid: string, n: number) =>
    `crews/c1/meals/c1_${uid}_2026-06-14_tok_p${n}_thumb.jpg`;

  it("a crew MEMBER can upload their own multi-photo plate (_p1 suffix)", async () => {
    const storage = env.authenticatedContext("bob").storage();
    await assertSucceeds(
      uploadBytes(ref(storage, multiPlate("bob", 1)), jpeg(), { contentType: "image/jpeg" }),
    );
  });

  it("REJECTS a client-written multi-photo thumbnail (_p1_thumb suffix is server-only, Admin SDK)", async () => {
    const storage = env.authenticatedContext("bob").storage();
    await assertFails(
      uploadBytes(ref(storage, multiThumb("bob", 1)), jpeg(), { contentType: "image/jpeg" }),
    );
  });

  it("a member still cannot upload a multi-photo plate naming ANOTHER user's uid", async () => {
    const storage = env.authenticatedContext("alice").storage();
    await assertFails(
      uploadBytes(ref(storage, multiPlate("bob", 1)), jpeg(), { contentType: "image/jpeg" }),
    );
  });

  it("a NON-member CANNOT upload a multi-photo plate even with a valid-looking (self-named) filename", async () => {
    const storage = env.authenticatedContext("carol").storage();
    await assertFails(
      uploadBytes(ref(storage, multiPlate("carol", 1)), jpeg(), { contentType: "image/jpeg" }),
    );
  });

  it("the author can delete their own multi-photo plate (_p1 suffix)", async () => {
    // Seed the object under disabled rules so the assertion below exercises the DELETE rule only.
    await env.withSecurityRulesDisabled(async (ctx) => {
      await uploadBytes(ref(ctx.storage(), multiPlate("bob", 1)), jpeg(), {
        contentType: "image/jpeg",
      });
    });
    const storage = env.authenticatedContext("bob").storage();
    await assertSucceeds(deleteObject(ref(storage, multiPlate("bob", 1))));
  });
});
