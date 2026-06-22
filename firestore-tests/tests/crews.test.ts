import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
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
  bannerFocalY: null,
  ...extra,
});

describe("crews — full-document membership writes (real client `set()` path)", () => {
  it("a non-member can join a CURRENT-schema crew via a full-document set()", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice"]));
    const db = env.authenticatedContext("charlie").firestore();
    await assertSucceeds(setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"])));
  });

  it("a non-member can join an OLDER crew missing the newer fields (the reported bug)", async () => {
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
    const db = env.authenticatedContext("charlie").firestore();
    await assertSucceeds(setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"])));
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

  it("a joiner CANNOT seize ownership in the same full-document set()", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice"]));
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"], { ownerId: "charlie" })),
    );
  });

  it("a joiner CANNOT rotate the invite code in the same full-document set()", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice"]));
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"], { code: "HIJACK" })),
    );
  });

  it("a joiner CANNOT rename the crew in the same full-document set()", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice"]));
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"], { name: "Hijacked" })),
    );
  });

  it("a joiner CANNOT flip an owner-only policy (blindVoting) while joining", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice"]));
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"], { blindVoting: true })),
    );
  });

  it("a joiner CANNOT evict an existing member while joining", async () => {
    await seedCrew(env, "c1", fullCrewDoc(["alice", "bob"]));
    const db = env.authenticatedContext("charlie").firestore();
    // Drops bob and adds charlie — net size unchanged, but an existing member is gone.
    await assertFails(setDoc(doc(db, "crews/c1"), fullCrewDoc(["alice", "charlie"])));
  });
});

describe("crews — membership cap invariant (max 8)", () => {
  it("a non-member can join when there is room", async () => {
    await seedCrew(env, "c1", { ownerId: "alice", name: "C1", memberIds: ["alice"], members: { alice: {} } });
    const db = env.authenticatedContext("charlie").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "charlie"],
        members: { alice: {}, charlie: {} },
      }),
    );
  });

  it("joining is REJECTED when the crew is already full (8 members)", async () => {
    const eight = ["u1", "u2", "u3", "u4", "u5", "u6", "u7", "u8"];
    await seedCrew(env, "full", {
      ownerId: "u1",
      name: "Full",
      memberIds: eight,
      members: Object.fromEntries(eight.map((u) => [u, {}])),
    });
    const db = env.authenticatedContext("u9").firestore();
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
