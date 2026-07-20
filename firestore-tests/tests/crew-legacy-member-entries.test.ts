/**
 * P2 crew-lifecycle review probe (2026-07-20): the client commits approve / leave /
 * remove-member as a FULL-DOCUMENT `set(crewRef, crew.copy(...))` built from a decoded
 * CrewDto. MemberDto only models `joinedAtEpochMs`; legacy crew docs whose members
 * entries still carry the pre-refactor `displayName` / `avatarUrl` fields (tolerated on
 * decode, dropped on re-encode) get every SURVIVING member's entry rewritten (stripped)
 * by that set() — which the 2026-07-19 members-map delta pin
 * (`membersMapPinnedToMembershipDelta`) must reject, because the diff then touches keys
 * whose membership did not change.
 *
 * These tests pin down BOTH sides:
 *  - the full-document client shape FAILS on such a legacy crew (the bug), and
 *  - the delta-only update() shape (the fix) SUCCEEDS on the same doc.
 */
import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteField, doc, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv } from "./helpers";

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

/** A pre-roster-refactor crew: members entries still carry displayName/avatarUrl. */
const legacyCrew = {
  id: "c1",
  name: "C1",
  code: "ABC123",
  ownerId: "alice",
  createdAtEpochMs: 1700000000000,
  memberIds: ["alice", "bob", "carol"],
  members: {
    alice: { joinedAtEpochMs: 1, displayName: "Alice", avatarUrl: "https://x/a.jpg" },
    bob: { joinedAtEpochMs: 2, displayName: "Bob", avatarUrl: "https://x/b.jpg" },
    carol: { joinedAtEpochMs: 3, displayName: "Carol", avatarUrl: "https://x/c.jpg" },
  },
};

/** What the client's `set(crew.copy(...))` produces after a decode→encode round-trip:
 *  every surviving entry stripped to the MemberDto shape. */
const strippedFullDoc = (memberIds: string[], extra: Record<string, unknown> = {}) => ({
  id: "c1",
  name: "C1",
  code: "ABC123",
  ownerId: "alice",
  createdAtEpochMs: 1700000000000,
  memberIds,
  members: Object.fromEntries(
    memberIds.map((u, i) => [u, { joinedAtEpochMs: { alice: 1, bob: 2, carol: 3, dave: 99 }[u] ?? 99 }]),
  ),
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

const seed = async () =>
  env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "crews/c1"), legacyCrew);
  });

describe("crews — legacy member entries × full-document membership set() (the incompatibility)", () => {
  beforeEach(seed);

  it("REPRO: a member leaving via the stripping full-document set() is REJECTED", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, "crews/c1"), strippedFullDoc(["alice", "carol"])));
  });

  it("REPRO: the owner approving via the stripping full-document set() is REJECTED", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, "crews/c1"), strippedFullDoc(["alice", "bob", "carol", "dave"])),
    );
  });

  it("REPRO: the owner removing a member via the stripping full-document set() is REJECTED", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, "crews/c1"), strippedFullDoc(["alice", "carol"])));
  });
});

describe("crews — delta-only update() membership writes (the fix shape) on the same legacy doc", () => {
  beforeEach(seed);

  it("FIX: a member leaves via update(memberIds + members.<self> delete)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "carol"],
        "members.bob": deleteField(),
      }),
    );
  });

  it("FIX: the owner approves via update(memberIds + members.<new>.joinedAtEpochMs)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "bob", "carol", "dave"],
        "members.dave": { joinedAtEpochMs: 99 },
      }),
    );
  });

  it("FIX: the owner removes a member via update(memberIds + members.<target> delete)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "carol"],
        "members.bob": deleteField(),
      }),
    );
  });

  it("FIX: the owner leaves & reassigns via update(ownerId + memberIds + members.<self> delete)", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "crews/c1"), {
        ownerId: "bob",
        memberIds: ["bob", "carol"],
        "members.alice": deleteField(),
      }),
    );
  });

  it("delta update() still CANNOT forge a surviving member's entry", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "carol"],
        "members.bob": deleteField(),
        "members.alice.joinedAtEpochMs": 9999,
      }),
    );
  });

  it("delta update() still CANNOT let a non-member self-join", async () => {
    const db = env.authenticatedContext("mallory").firestore();
    await assertFails(
      updateDoc(doc(db, "crews/c1"), {
        memberIds: ["alice", "bob", "carol", "mallory"],
        "members.mallory": { joinedAtEpochMs: 99 },
      }),
    );
  });
});
