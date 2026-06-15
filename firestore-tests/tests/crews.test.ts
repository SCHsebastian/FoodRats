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
