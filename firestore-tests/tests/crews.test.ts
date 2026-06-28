import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv } from "./helpers";

let env: RulesTestEnvironment;

const seedCrew = async (e: RulesTestEnvironment, id: string, data: Record<string, unknown>) =>
  e.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `crews/${id}`), data);
  });

beforeAll(async () => {
  env = await makeEnv();
});
afterAll(async () => {
  await env.cleanup();
});
beforeEach(async () => {
  await env.clearFirestore();
});

describe("crews — create + read posture", () => {
  it("a user can create a crew with themselves as the sole member", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(db, "crews/new1"), {
        ownerId: "alice",
        name: "Mine",
        memberIds: ["alice"],
        members: { alice: {} },
      }),
    );
  });

  it("any authenticated user can read a crew doc (soft-token posture)", async () => {
    await seedCrew(env, "c1", { ownerId: "alice", name: "C1", memberIds: ["alice"], members: { alice: {} } });
    const db = env.authenticatedContext("stranger").firestore();
    await assertSucceeds(getDoc(doc(db, "crews/c1")));
  });

  // Security #1 — create must pin ownerId to the creator + reject mass-assignment.
  it("a user CANNOT create a crew owned by someone else", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, "crews/evil1"), {
        ownerId: "victim",
        name: "Mine",
        memberIds: ["alice"],
        members: { alice: {} },
      }),
    );
  });

  it("a user CANNOT seed another account into the new crew's members map", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, "crews/evil2"), {
        ownerId: "alice",
        name: "Mine",
        memberIds: ["alice"],
        members: { alice: {}, bob: {} },
      }),
    );
  });

  it("a user CANNOT inject an unknown field at create", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, "crews/evil3"), {
        ownerId: "alice",
        name: "Mine",
        memberIds: ["alice"],
        members: { alice: {} },
        isPremium: true,
      }),
    );
  });

  it("a user CANNOT create a crew with an over-long name", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, "crews/evil4"), {
        ownerId: "alice",
        name: "x".repeat(101),
        memberIds: ["alice"],
        members: { alice: {} },
      }),
    );
  });

  it("a full CrewDto create by the founder still succeeds", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(db, "crews/ok1"), {
        id: "ok1",
        name: "Saturday Brunch",
        code: "ABC123",
        ownerId: "alice",
        createdAtEpochMs: 1700000000000,
        memberIds: ["alice"],
        members: { alice: { joinedAtEpochMs: 1700000000000 } },
        blindVoting: false,
        tagline: null,
        welcomeMessage: null,
        weeklyChallenge: null,
        weeklyChallengeSetAtMillis: null,
        scoreStyle: "stars",
        bannerPath: null,
        bannerToken: null,
        bannerFocalY: null,
      }),
    );
  });
});

// Reproduces the real client write path. CrewFirestoreDataSource commits join/leave/
// remove inside a transaction with `set(crewRef, crew.copy(...))` — a FULL-DOCUMENT
// write. GitLive `.set(dto)` re-serializes every CrewDto field with encodeDefaults=true,
// so a crew created before a field existed (scoreStyle, bannerPath, …) receives that
// field as a newly-ADDED key, which the old hasOnly(['memberIds','members']) counted as a
// forbidden change and rejected. These tests write the full document the way the app does.
const fullCrewDoc = (memberIds: string[], extra: Record<string, unknown> = {}) => ({
  id: "c1",
  name: "C1",
  code: "ABC123",
  ownerId: "alice",
  createdAtEpochMs: 1700000000000,
  memberIds,
  members: Object.fromEntries(memberIds.map((u) => [u, { joinedAtEpochMs: 1700000000000 }])),
  blindVoting: false,
  tagline: null,
  welcomeMessage: null,
  weeklyChallenge: null,
  weeklyChallengeSetAtMillis: null,
  scoreStyle: "stars",
  bannerPath: null,
  bannerToken: null,
  bannerFocalY: null,
  ...extra,
});

describe("crews — full-document membership writes (real client `set()` path)", () => {
  it("the owner can approve (add a member) on a CURRENT-schema crew via a full-document set()", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice"]));
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"])));
  });

  it("the owner can approve on an OLDER crew missing the newer fields (encodeDefaults regression lock)", async () => {
    // Crew created before scoreStyle / banner* / tagline / etc. existed.
    await seedCrew(env, "c1", {
      id: "c1",
      name: "C1",
      code: "ABC123",
      ownerId: "alice",
      createdAtEpochMs: 1700000000000,
      memberIds: ["alice"],
      members: { alice: { joinedAtEpochMs: 1700000000000 } },
    });
    // The client re-serializes the WHOLE DTO, so the newer fields arrive as added defaults.
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"])));
  });

  it("a NON-MEMBER cannot self-join via a full-document set() — owner approval is required", async () => {
    // The core behavior change: instant join is gone. A non-member can never add themselves;
    // they file a joinRequests/{uid} doc and the owner approves.
    await seedCrew(env, "c1", fullCrewDoc(["alice"]));
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"])));
  });

  it("a non-owner member cannot add another member (only the owner approves)", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice", "bob"]));
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "bob", "charlie"])));
  });

  it("a member can leave an OLDER crew via a full-document set()", async () => {
    await seedCrew(env, "c1", {
      id: "c1",
      name: "C1",
      code: "ABC123",
      ownerId: "alice",
      createdAtEpochMs: 1700000000000,
      memberIds: ["alice", "bob"],
      members: {
        alice: { joinedAtEpochMs: 1700000000000 },
        bob: { joinedAtEpochMs: 1700000000000 },
      },
    });
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice"])));
  });

  it("the owner can remove a member from an OLDER crew via a full-document set()", async () => {
    await seedCrew(env, "c1", {
      id: "c1",
      name: "C1",
      code: "ABC123",
      ownerId: "alice",
      createdAtEpochMs: 1700000000000,
      memberIds: ["alice", "bob"],
      members: {
        alice: { joinedAtEpochMs: 1700000000000 },
        bob: { joinedAtEpochMs: 1700000000000 },
      },
    });
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice"])));
  });

  it("approving CANNOT also seize ownership in the same full-document set()", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice"]));
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"], { ownerId: "charlie" })),
    );
  });

  it("approving CANNOT rotate the invite code in the same full-document set()", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice"]));
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"], { code: "HIJACK" })),
    );
  });

  it("approving CANNOT rename the crew in the same full-document set()", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice"]));
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"], { name: "Hijacked" })),
    );
  });

  it("approving CANNOT flip an owner-only policy (blindVoting) in the same write", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice"]));
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"], { blindVoting: true })),
    );
  });

  it("approving CANNOT evict an existing member while adding a new one", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice", "bob"]));
    const db = env.authenticatedContext("alice").firestore();
    // Drops bob and adds charlie — net size unchanged, but an existing member is gone.
    await assertFails(setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"])));
  });
});

describe("crews — membership cap invariant (max 8)", () => {
  it("the owner can approve a member when there is room", async () => {
    await seedCrew(env, "c1", { ownerId: "alice", name: "C1", memberIds: ["alice"], members: { alice: {} } });
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "charlie"],
        members: { alice: {}, charlie: {} },
      }),
    );
  });

  it("approval is REJECTED when the crew is already full (8 members)", async () => {
    const eight = ["u1", "u2", "u3", "u4", "u5", "u6", "u7", "u8"];
    await seedCrew(env, "full", {
      ownerId: "u1",
      name: "Full",
      memberIds: eight,
      members: Object.fromEntries(eight.map((u) => [u, {}])),
    });
    const db = env.authenticatedContext("u1").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/full"), {
        memberIds: [...eight, "u9"],
        members: Object.fromEntries([...eight, "u9"].map((u) => [u, {}])),
      }),
    );
  });
});

describe("crews — rename authorization", () => {
  beforeEach(async () => {
    await seedCrew(env, "c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
    });
  });

  it("the owner can rename the crew", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { name: "Renamed" }));
  });

  it("a non-owner member CANNOT rename the crew", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { name: "Hacked" }));
  });
});

describe("crews — owner removes a member", () => {
  beforeEach(async () => {
    await seedCrew(env, "c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice", "bob", "carol"],
      members: { alice: {}, bob: {}, carol: {} },
    });
  });

  it("the owner can remove another member", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "carol"],
        members: { alice: {}, carol: {} },
      }),
    );
  });

  it("a non-owner member CANNOT remove another member", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "bob"],
        members: { alice: {}, bob: {} },
      }),
    );
  });

  it("the remove-member path CANNOT also reassign ownerId", async () => {
    // Smuggling an ownerId change alongside the membership shrink is rejected: branch (6) is
    // gated by hasOnly(['memberIds','members']), so touching ownerId fails every update branch.
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        ownerId: "bob",
        memberIds: ["alice", "carol"],
        members: { alice: {}, carol: {} },
      }),
    );
  });

  it("a stranger (non-member) CANNOT remove a member", async () => {
    const db = env.authenticatedContext("mallory").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "carol"],
        members: { alice: {}, carol: {} },
      }),
    );
  });

  it("the owner CANNOT remove themselves via the remove-member path", async () => {
    // Branch (6) requires the owner to remain in the new memberIds; dropping self plus
    // another member shrinks the set by two, so it also misses the leave branch (5)
    // (which only permits removing exactly self). All update branches reject it.
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["carol"],
        members: { carol: {} },
      }),
    );
  });
});

describe("crews — tagline (owner-only, branch 7)", () => {
  beforeEach(async () => {
    await seedCrew(env, "c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
    });
  });

  it("the owner can set the tagline", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { tagline: "only home-cooked" }));
  });

  it("the owner can clear the tagline (null)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { tagline: null }));
  });

  it("a non-owner member CANNOT set the tagline", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { tagline: "hijacked" }));
  });

  it("a stranger CANNOT set the tagline", async () => {
    const db = env.authenticatedContext("mallory").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { tagline: "hijacked" }));
  });

  it("a non-string, non-null tagline is rejected", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { tagline: 42 }));
  });

  it("the tagline branch CANNOT also smuggle an ownerId change", async () => {
    // Branch (7) is gated by hasOnly(['tagline']); touching ownerId fails every update branch.
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), { tagline: "ok", ownerId: "bob" }),
    );
  });
});

describe("crews — welcomeMessage (owner-only, branch 8)", () => {
  beforeEach(async () => {
    await seedCrew(env, "c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
    });
  });

  it("the owner can set the welcome message", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), { welcomeMessage: "Cook before 22:00!" }),
    );
  });

  it("the owner can clear the welcome message (null)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { welcomeMessage: null }));
  });

  it("a non-owner member CANNOT set the welcome message", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { welcomeMessage: "hijacked" }));
  });

  it("a stranger CANNOT set the welcome message", async () => {
    const db = env.authenticatedContext("mallory").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { welcomeMessage: "hijacked" }));
  });

  it("a non-string, non-null welcome message is rejected", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { welcomeMessage: 42 }));
  });

  it("the welcomeMessage branch CANNOT also smuggle an ownerId change", async () => {
    // Branch (8) is gated by hasOnly(['welcomeMessage']); touching ownerId fails every update branch.
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), { welcomeMessage: "ok", ownerId: "bob" }),
    );
  });
});

describe("crews — weeklyChallenge (owner-only, branch 9)", () => {
  beforeEach(async () => {
    await seedCrew(env, "c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
    });
  });

  it("the owner can set the weekly challenge (both fields together)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), {
        weeklyChallenge: "Taco Tuesday",
        weeklyChallengeSetAtMillis: 1700000000000,
      }),
    );
  });

  it("the owner can clear the weekly challenge (both fields null)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), {
        weeklyChallenge: null,
        weeklyChallengeSetAtMillis: null,
      }),
    );
  });

  it("a non-owner member CANNOT set the weekly challenge", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        weeklyChallenge: "hijacked",
        weeklyChallengeSetAtMillis: 1700000000000,
      }),
    );
  });

  it("a stranger CANNOT set the weekly challenge", async () => {
    const db = env.authenticatedContext("mallory").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        weeklyChallenge: "hijacked",
        weeklyChallengeSetAtMillis: 1700000000000,
      }),
    );
  });

  it("writing only weeklyChallenge without the timestamp is rejected", async () => {
    // Branch (9) requires hasOnly(['weeklyChallenge','weeklyChallengeSetAtMillis']) — missing one field fails.
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { weeklyChallenge: "Soup week" }));
  });

  it("a non-string, non-null weeklyChallenge is rejected", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        weeklyChallenge: 42,
        weeklyChallengeSetAtMillis: 1700000000000,
      }),
    );
  });

  it("the weeklyChallenge branch CANNOT also smuggle an ownerId change", async () => {
    // Branch (9) is gated by hasOnly(['weeklyChallenge','weeklyChallengeSetAtMillis']); touching ownerId fails.
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        weeklyChallenge: "ok",
        weeklyChallengeSetAtMillis: 1700000000000,
        ownerId: "bob",
      }),
    );
  });
});

describe("crews — scoreStyle (owner-only, branch 10)", () => {
  beforeEach(async () => {
    await seedCrew(env, "c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
      scoreStyle: "stars",
    });
  });

  it("the owner can set scoreStyle to 'emoji'", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { scoreStyle: "emoji" }));
  });

  it("the owner can set scoreStyle to 'numeric'", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { scoreStyle: "numeric" }));
  });

  it("the owner can set scoreStyle back to 'stars'", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { scoreStyle: "stars" }));
  });

  it("a non-owner member CANNOT set the scoreStyle", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { scoreStyle: "emoji" }));
  });

  it("a stranger CANNOT set the scoreStyle", async () => {
    const db = env.authenticatedContext("mallory").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { scoreStyle: "emoji" }));
  });

  it("an unknown scoreStyle value is rejected", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { scoreStyle: "hearts" }));
  });

  it("a non-string scoreStyle is rejected", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { scoreStyle: 1 }));
  });

  it("the scoreStyle branch CANNOT also smuggle an ownerId change", async () => {
    // Branch (10) is gated by hasOnly(['scoreStyle']); touching ownerId fails every update branch.
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), { scoreStyle: "emoji", ownerId: "bob" }),
    );
  });
});

describe("crews — bannerPath (owner-only, branch 11)", () => {
  beforeEach(async () => {
    await seedCrew(env, "c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
    });
  });

  it("the owner can set the bannerPath to a Storage path string", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), { bannerPath: "crew_banners/c1/banner.jpg" }),
    );
  });

  it("the owner can clear the bannerPath (set null = remove)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { bannerPath: null }));
  });

  it("a non-owner member CANNOT set the bannerPath", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), { bannerPath: "crew_banners/c1/banner.jpg" }),
    );
  });

  it("a stranger CANNOT set the bannerPath", async () => {
    const db = env.authenticatedContext("mallory").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), { bannerPath: "crew_banners/c1/banner.jpg" }),
    );
  });

  it("a non-string, non-null bannerPath is rejected", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { bannerPath: 1 }));
  });

  it("the bannerPath branch CANNOT also smuggle an ownerId change", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        bannerPath: "crew_banners/c1/banner.jpg",
        ownerId: "bob",
      }),
    );
  });
});

describe("crews — transfer ownership (branch 14)", () => {
  beforeEach(async () => {
    await seedCrew(env, "c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice", "bob", "carol"],
      members: { alice: {}, bob: {}, carol: {} },
    });
  });

  it("the owner can transfer ownership to another member", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(db, "crews/c1"), { ownerId: "bob" }));
  });

  it("the owner CANNOT transfer ownership to a non-member", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { ownerId: "mallory" }));
  });

  it("a non-owner member CANNOT transfer ownership", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { ownerId: "bob" }));
  });

  it("a stranger CANNOT transfer ownership", async () => {
    const db = env.authenticatedContext("mallory").firestore();
    await assertFails(updateDoc(doc(db, "crews/c1"), { ownerId: "mallory" }));
  });

  it("transferring CANNOT also change another field (name) in the same write", async () => {
    // Branch (14) is gated by hasOnly(['ownerId']); touching anything else fails.
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), { ownerId: "bob", name: "Sneaky" }),
    );
  });
});

describe("crews — owner leaves & reassigns (branch 15)", () => {
  const ownerLeftDoc = (memberIds: string[], newOwner: string) => ({
    id: "c1",
    name: "C1",
    code: "ABC123",
    ownerId: newOwner,
    createdAtEpochMs: 1700000000000,
    memberIds,
    members: Object.fromEntries(memberIds.map((u) => [u, { joinedAtEpochMs: 1700000000000 }])),
    blindVoting: false,
    tagline: null,
    welcomeMessage: null,
    weeklyChallenge: null,
    weeklyChallengeSetAtMillis: null,
    scoreStyle: "stars",
    bannerPath: null,
    bannerFocalY: null,
  });

  beforeEach(async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice", "bob", "carol"]));
  });

  it("the owner can leave and hand ownership to a remaining member (full-document set)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, "crews/c1"), ownerLeftDoc(["bob", "carol"], "bob")));
  });

  it("the owner-leave-reassign CANNOT name a new owner who isn't a surviving member", async () => {
    const db = env.authenticatedContext("alice").firestore();
    // alice leaves but names carol as owner while ALSO dropping carol — new owner not present.
    await assertFails(setDoc(doc(db, "crews/c1"), ownerLeftDoc(["bob"], "carol")));
  });

  it("the owner-leave-reassign CANNOT evict another member in the same write", async () => {
    const db = env.authenticatedContext("alice").firestore();
    // alice leaves AND drops carol — size down by two, an extra member evicted.
    await assertFails(setDoc(doc(db, "crews/c1"), ownerLeftDoc(["bob"], "bob")));
  });

  it("a non-owner member leaving CANNOT also reassign ownerId", async () => {
    // bob (not owner) leaves and tries to grab ownership for carol — the leave branch (4) pins
    // ownerId and the owner-leave branch (15) requires bob to BE the owner. Both reject.
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, "crews/c1"), ownerLeftDoc(["alice", "carol"], "carol")));
  });
});

const seedJoinRequest = async (
  e: RulesTestEnvironment,
  crewId: string,
  requesterId: string,
) =>
  e.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `crews/${crewId}/joinRequests/${requesterId}`), {
      accountId: requesterId,
      requestedAtEpochMs: 1700000000000,
    });
  });

describe("crews — join requests subcollection", () => {
  beforeEach(async () => {
    await seedCrew(env, "c1", {
      ownerId: "alice",
      name: "C1",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
    });
  });

  it("a non-member can file their own join request", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    await assertSucceeds(
      setDoc(doc(db, "crews/c1/joinRequests/charlie"), {
        accountId: "charlie",
        requestedAtEpochMs: 1700000000000,
      }),
    );
  });

  it("a user CANNOT file a request under someone else's id", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1/joinRequests/dave"), {
        accountId: "dave",
        requestedAtEpochMs: 1700000000000,
      }),
    );
  });

  it("a request whose accountId does not match the doc id is rejected", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1/joinRequests/charlie"), {
        accountId: "someone-else",
        requestedAtEpochMs: 1700000000000,
      }),
    );
  });

  it("an existing member CANNOT file a join request", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1/joinRequests/bob"), {
        accountId: "bob",
        requestedAtEpochMs: 1700000000000,
      }),
    );
  });

  it("a request smuggling an extra field is rejected (keys whitelist)", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1/joinRequests/charlie"), {
        accountId: "charlie",
        requestedAtEpochMs: 1700000000000,
        role: "owner",
      }),
    );
  });

  it("the owner can read pending requests", async () => {
    await seedJoinRequest(env, "c1", "charlie");
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(getDoc(doc(db, "crews/c1/joinRequests/charlie")));
  });

  it("the requester can read their own request", async () => {
    await seedJoinRequest(env, "c1", "charlie");
    const db = env.authenticatedContext("charlie").firestore();
    await assertSucceeds(getDoc(doc(db, "crews/c1/joinRequests/charlie")));
  });

  it("a non-owner stranger CANNOT read someone else's request", async () => {
    await seedJoinRequest(env, "c1", "charlie");
    const db = env.authenticatedContext("dave").firestore();
    await assertFails(getDoc(doc(db, "crews/c1/joinRequests/charlie")));
  });

  it("the owner can delete (decline) a request", async () => {
    await seedJoinRequest(env, "c1", "charlie");
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(deleteDoc(doc(db, "crews/c1/joinRequests/charlie")));
  });

  it("the requester can delete (cancel) their own request", async () => {
    await seedJoinRequest(env, "c1", "charlie");
    const db = env.authenticatedContext("charlie").firestore();
    await assertSucceeds(deleteDoc(doc(db, "crews/c1/joinRequests/charlie")));
  });

  it("a non-owner stranger CANNOT delete someone else's request", async () => {
    await seedJoinRequest(env, "c1", "charlie");
    const db = env.authenticatedContext("dave").firestore();
    await assertFails(deleteDoc(doc(db, "crews/c1/joinRequests/charlie")));
  });

  it("a request cannot be updated once filed (immutable)", async () => {
    await seedJoinRequest(env, "c1", "charlie");
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1/joinRequests/charlie"), { requestedAtEpochMs: 1800000000000 }),
    );
  });
});
