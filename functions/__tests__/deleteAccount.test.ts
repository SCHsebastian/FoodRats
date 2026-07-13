import { describe, expect, it, vi } from "vitest";
import {
  deleteAccountCore,
  planCrewReassignment,
  type CrewSnap,
  type DeletionDeps,
  type DocRef,
  type MealRef,
} from "../src/callables/deleteAccount";

const UID = "ana";

/** A recording set of fakes — every delete is recorded with a monotonic call order. */
function recordingDeps(overrides: Partial<DeletionDeps> = {}): {
  deps: DeletionDeps;
  calls: string[];
  recursiveDeleted: string[];
  blobsDeleted: string[];
  blobPrefixesDeleted: string[];
  ratingsRemoved: Array<{ path: string; uid: string }>;
  crewsHandled: string[];
  authDeleted: string[];
} {
  const calls: string[] = [];
  const recursiveDeleted: string[] = [];
  const blobsDeleted: string[] = [];
  const blobPrefixesDeleted: string[] = [];
  const ratingsRemoved: Array<{ path: string; uid: string }> = [];
  const crewsHandled: string[] = [];
  const authDeleted: string[] = [];

  const deps: DeletionDeps = {
    expectedPhrase: async () => "DELETE Ana",
    authoredMeals: async () => [],
    authoredComments: async () => [],
    votedMeals: async () => [],
    memberCrews: async () => [],
    recursiveDelete: async (p) => {
      calls.push(`recursiveDelete:${p}`);
      recursiveDeleted.push(p);
    },
    deleteBlob: async (p) => {
      calls.push(`deleteBlob:${p}`);
      blobsDeleted.push(p);
    },
    deleteBlobPrefix: async (prefix) => {
      calls.push(`deleteBlobPrefix:${prefix}`);
      blobPrefixesDeleted.push(prefix);
    },
    removeRating: async (path, uid) => {
      calls.push(`removeRating:${path}`);
      ratingsRemoved.push({ path, uid });
    },
    reassignOrDeleteCrew: async (crew) => {
      calls.push(`reassignOrDeleteCrew:${crew.crewId}`);
      crewsHandled.push(crew.crewId);
    },
    deleteAuthUser: async (uid) => {
      calls.push(`deleteAuthUser:${uid}`);
      authDeleted.push(uid);
    },
    ...overrides,
  };

  return {
    deps,
    calls,
    recursiveDeleted,
    blobsDeleted,
    blobPrefixesDeleted,
    ratingsRemoved,
    crewsHandled,
    authDeleted,
  };
}

async function codeOf(fn: () => Promise<unknown>): Promise<string | undefined> {
  try {
    await fn();
    return undefined;
  } catch (e) {
    return (e as { code?: string }).code;
  }
}

describe("deleteAccountCore — auth + phrase gates (§14.1)", () => {
  it("rejects an unauthenticated caller and touches no deps", async () => {
    const { deps, calls } = recordingDeps();
    const phrase = vi.fn(deps.expectedPhrase);
    expect(
      await codeOf(() =>
        deleteAccountCore({ ...deps, expectedPhrase: phrase }, undefined, { confirmation: "x" }),
      ),
    ).toBe("unauthenticated");
    expect(phrase).not.toHaveBeenCalled();
    expect(calls).toEqual([]);
  });

  it("rejects a mismatched phrase and destroys nothing", async () => {
    const { deps, calls } = recordingDeps({ expectedPhrase: async () => "DELETE Ana" });
    expect(
      await codeOf(() => deleteAccountCore(deps, UID, { confirmation: "delete ana" })),
    ).toBe("failed-precondition");
    expect(calls).toEqual([]); // nothing deleted on a bad phrase.
  });

  it("trims whitespace before comparing the phrase", async () => {
    const { deps, authDeleted } = recordingDeps({ expectedPhrase: async () => "DELETE Ana" });
    const res = await deleteAccountCore(deps, UID, { confirmation: "  DELETE Ana  " });
    expect(res).toEqual({ deleted: true });
    expect(authDeleted).toEqual([UID]);
  });

  it("rejects a MISSING confirmation field (nullish → empty string) and destroys nothing", async () => {
    const { deps, calls } = recordingDeps({ expectedPhrase: async () => "DELETE Ana" });
    expect(
      await codeOf(() =>
        deleteAccountCore(deps, UID, { confirmation: undefined as unknown as string }),
      ),
    ).toBe("failed-precondition");
    expect(calls).toEqual([]);
  });

  it("phrase match is case-sensitive (\"delete\" ≠ \"DELETE\")", async () => {
    const { deps, calls } = recordingDeps({ expectedPhrase: async () => "DELETE" });
    expect(await codeOf(() => deleteAccountCore(deps, UID, { confirmation: "delete" }))).toBe(
      "failed-precondition",
    );
    expect(calls).toEqual([]);
  });
});

describe("deleteAccountCore — happy-path cascade (§14.1)", () => {
  const authoredMeals: MealRef[] = [
    { path: "crews/c1/meals/m1", platePaths: ["crews/c1/meals/m1.jpg"] },
    { path: "crews/c2/meals/m2", platePaths: ["crews/c2/meals/m2.jpg"] },
  ];
  const authoredComments: DocRef[] = [
    { path: "crews/c1/meals/x/comments/cm1" },
    { path: "crews/c1/meals/y/comments/cm2" },
    { path: "crews/c2/meals/z/comments/cm3" },
  ];
  const votedMeals: MealRef[] = [
    { path: "crews/c1/meals/voted1", platePaths: ["crews/c1/meals/voted1.jpg"] },
  ];
  const memberCrews: CrewSnap[] = [
    { crewId: "c1", ownerId: UID, memberIds: [UID], members: { [UID]: { joinedAt: 1 } }, code: "AAA" },
    {
      crewId: "c2",
      ownerId: UID,
      memberIds: [UID, "bob"],
      members: { [UID]: { joinedAt: 1 }, bob: { joinedAt: 2 } },
      code: "BBB",
    },
  ];

  function fullDeps() {
    return recordingDeps({
      expectedPhrase: async () => "DELETE Ana",
      authoredMeals: async () => authoredMeals,
      authoredComments: async () => authoredComments,
      votedMeals: async () => votedMeals,
      memberCrews: async () => memberCrews,
    });
  }

  it("deletes every authored meal + plate, every comment, the voted-meal rating, both crews, and identity", async () => {
    const r = fullDeps();
    const res = await deleteAccountCore(r.deps, UID, { confirmation: "DELETE Ana" });

    expect(res).toEqual({ deleted: true });
    // 1+2 authored meals + plates
    expect(r.recursiveDeleted).toContain("crews/c1/meals/m1");
    expect(r.recursiveDeleted).toContain("crews/c2/meals/m2");
    expect(r.blobsDeleted).toContain("crews/c1/meals/m1.jpg");
    expect(r.blobsDeleted).toContain("crews/c2/meals/m2.jpg");
    // 3 comments on others' meals
    expect(r.recursiveDeleted).toEqual(
      expect.arrayContaining([
        "crews/c1/meals/x/comments/cm1",
        "crews/c1/meals/y/comments/cm2",
        "crews/c2/meals/z/comments/cm3",
      ]),
    );
    // 4 ratings on others' meals
    expect(r.ratingsRemoved).toEqual([{ path: "crews/c1/meals/voted1", uid: UID }]);
    // 5 crews
    expect(r.crewsHandled).toEqual(["c1", "c2"]);
    // 6+7+8 identity, avatar (versioned prefix sweep + legacy fallback), top-level devices
    expect(r.recursiveDeleted).toContain(`accounts/${UID}`);
    expect(r.blobPrefixesDeleted).toContain(`avatars/${UID}/`);
    expect(r.blobsDeleted).toContain(`avatars/${UID}.jpg`);
    expect(r.recursiveDeleted).toContain(`devices/${UID}`);
    // 10 auth user
    expect(r.authDeleted).toEqual([UID]);
  });

  it("sweeps the content-versioned avatar prefix THEN the legacy fixed path (C1 — no orphaned blob)", async () => {
    const r = fullDeps();
    await deleteAccountCore(r.deps, UID, { confirmation: "DELETE Ana" });

    // The live avatar lives at `avatars/{uid}/{token}.jpg`, so the whole prefix must be swept;
    // the `exports/{uid}/` GDPR data-export archives are swept in the same pass (#7).
    expect(r.blobPrefixesDeleted).toEqual([`avatars/${UID}/`, `exports/${UID}/`]);
    // The legacy single-object delete is still attempted (best-effort for un-migrated users).
    expect(r.blobsDeleted).toContain(`avatars/${UID}.jpg`);
    // GDPR erasure: the export archives must be gone before the Auth user is deleted.
    expect(r.calls.indexOf(`deleteBlobPrefix:exports/${UID}/`)).toBeLessThan(
      r.calls.indexOf(`deleteAuthUser:${UID}`),
    );
    // Prefix sweep happens before the legacy fallback, and both before the Auth-user delete.
    expect(r.calls.indexOf(`deleteBlobPrefix:avatars/${UID}/`)).toBeLessThan(
      r.calls.indexOf(`deleteBlob:avatars/${UID}.jpg`),
    );
    expect(r.calls.indexOf(`deleteBlob:avatars/${UID}.jpg`)).toBeLessThan(
      r.calls.indexOf(`deleteAuthUser:${UID}`),
    );
  });

  it("deletes the Auth user exactly once and strictly LAST (after every data delete)", async () => {
    const r = fullDeps();
    await deleteAccountCore(r.deps, UID, { confirmation: "DELETE Ana" });

    const authCalls = r.calls.filter((c) => c.startsWith("deleteAuthUser:"));
    expect(authCalls).toEqual([`deleteAuthUser:${UID}`]);
    expect(r.calls[r.calls.length - 1]).toBe(`deleteAuthUser:${UID}`);
  });

  it("cascades in spec order: meals → comments → ratings → crews → identity", async () => {
    const r = fullDeps();
    await deleteAccountCore(r.deps, UID, { confirmation: "DELETE Ana" });

    const idx = (c: string) => r.calls.indexOf(c);
    expect(idx("recursiveDelete:crews/c2/meals/m2")).toBeLessThan(
      idx("recursiveDelete:crews/c1/meals/x/comments/cm1"),
    );
    expect(idx("recursiveDelete:crews/c2/meals/z/comments/cm3")).toBeLessThan(
      idx("removeRating:crews/c1/meals/voted1"),
    );
    expect(idx("removeRating:crews/c1/meals/voted1")).toBeLessThan(
      idx("reassignOrDeleteCrew:c1"),
    );
    expect(idx("reassignOrDeleteCrew:c2")).toBeLessThan(idx(`recursiveDelete:accounts/${UID}`));
  });

  it("deletes each authored meal's plate blob right after its doc", async () => {
    const r = fullDeps();
    await deleteAccountCore(r.deps, UID, { confirmation: "DELETE Ana" });
    expect(r.calls.indexOf("deleteBlob:crews/c1/meals/m1.jpg")).toBe(
      r.calls.indexOf("recursiveDelete:crews/c1/meals/m1") + 1,
    );
  });

  it("skips the blob delete for an authored meal with an unresolvable photo path (empty platePaths)", async () => {
    const r = recordingDeps({
      expectedPhrase: async () => "DELETE Ana",
      authoredMeals: async () => [{ path: "weird/path", platePaths: [] }],
    });
    await deleteAccountCore(r.deps, UID, { confirmation: "DELETE Ana" });
    expect(r.recursiveDeleted).toContain("weird/path");
    // No plate blob delete for the unresolvable meal; the fixed identity blobs are still swept.
    expect(r.blobsDeleted).toEqual([`avatars/${UID}.jpg`]);
  });

  it("deletes EVERY photo of a multi-photo authored meal, all right after its doc delete", async () => {
    const r = recordingDeps({
      expectedPhrase: async () => "DELETE Ana",
      authoredMeals: async () => [
        {
          path: "crews/c1/meals/m1",
          platePaths: [
            "crews/c1/meals/m1.jpg",
            "crews/c1/meals/m1_p1.jpg",
            "crews/c1/meals/m1_p2.jpg",
          ],
        },
      ],
    });
    await deleteAccountCore(r.deps, UID, { confirmation: "DELETE Ana" });

    expect(r.blobsDeleted).toEqual(
      expect.arrayContaining([
        "crews/c1/meals/m1.jpg",
        "crews/c1/meals/m1_p1.jpg",
        "crews/c1/meals/m1_p2.jpg",
      ]),
    );
    const docIdx = r.calls.indexOf("recursiveDelete:crews/c1/meals/m1");
    expect(r.calls.indexOf("deleteBlob:crews/c1/meals/m1.jpg")).toBe(docIdx + 1);
    expect(r.calls.indexOf("deleteBlob:crews/c1/meals/m1_p1.jpg")).toBe(docIdx + 2);
    expect(r.calls.indexOf("deleteBlob:crews/c1/meals/m1_p2.jpg")).toBe(docIdx + 3);
  });

  it("deletes ALL paths for a MIXED authoredMeals list: one legacy meal + one 10-photo meal", async () => {
    const legacyMeal: MealRef = {
      path: "crews/c1/meals/legacy1",
      platePaths: ["crews/c1/meals/legacy1.jpg"],
    };
    const bigMealPaths = [
      "crews/c2/meals/big1.jpg",
      ...Array.from({ length: 9 }, (_, i) => `crews/c2/meals/big1_p${i + 1}.jpg`),
    ];
    const bigMeal: MealRef = { path: "crews/c2/meals/big1", platePaths: bigMealPaths };
    expect(bigMealPaths).toHaveLength(10); // MealPublishPolicy.MAX_PHOTOS_PER_MEAL

    const r = recordingDeps({
      expectedPhrase: async () => "DELETE Ana",
      authoredMeals: async () => [legacyMeal, bigMeal],
    });

    await deleteAccountCore(r.deps, UID, { confirmation: "DELETE Ana" });

    expect(r.blobsDeleted).toEqual(
      expect.arrayContaining([...legacyMeal.platePaths, ...bigMealPaths]),
    );
    // Every photo of the 10-photo meal is deleted right after its own doc, all 10 contiguous.
    const docIdx = r.calls.indexOf("recursiveDelete:crews/c2/meals/big1");
    bigMealPaths.forEach((p, i) => {
      expect(r.calls.indexOf(`deleteBlob:${p}`)).toBe(docIdx + 1 + i);
    });
  });
});

describe("planCrewReassignment — §6 owned-crew policy", () => {
  it("a non-owner just drops out", () => {
    const crew: CrewSnap = {
      crewId: "c1",
      ownerId: "bob",
      memberIds: ["bob", UID],
      members: { bob: { joinedAt: 1 }, [UID]: { joinedAt: 2 } },
      code: "AAA",
    };
    expect(planCrewReassignment(crew, UID)).toEqual({ kind: "drop" });
  });

  it("a sole-member owner deletes the crew", () => {
    const crew: CrewSnap = {
      crewId: "c1",
      ownerId: UID,
      memberIds: [UID],
      members: { [UID]: { joinedAt: 1 } },
      code: "AAA",
    };
    expect(planCrewReassignment(crew, UID)).toEqual({ kind: "delete" });
  });

  it("an owner with others reassigns to the earliest-joinedAt member", () => {
    const crew: CrewSnap = {
      crewId: "c1",
      ownerId: UID,
      memberIds: [UID, "late", "early"],
      members: { [UID]: { joinedAt: 1 }, late: { joinedAt: 30 }, early: { joinedAt: 10 } },
      code: "AAA",
    };
    expect(planCrewReassignment(crew, UID)).toEqual({ kind: "reassign", newOwnerId: "early" });
  });

  it("breaks a joinedAt tie deterministically by accountId ascending", () => {
    const crew: CrewSnap = {
      crewId: "c1",
      ownerId: UID,
      memberIds: [UID, "zoe", "amy"],
      members: { [UID]: { joinedAt: 1 }, zoe: { joinedAt: 5 }, amy: { joinedAt: 5 } },
      code: "AAA",
    };
    expect(planCrewReassignment(crew, UID)).toEqual({ kind: "reassign", newOwnerId: "amy" });
  });

  it("a member MISSING from the members map sorts last but is still eligible when alone", () => {
    const crew: CrewSnap = {
      crewId: "c1",
      ownerId: UID,
      memberIds: [UID, "orphan"],
      members: { [UID]: { joinedAt: 1 } }, // orphan has no membership entry
      code: null,
    };
    expect(planCrewReassignment(crew, UID)).toEqual({ kind: "reassign", newOwnerId: "orphan" });
  });

  it("prefers a member WITH a joinedAt over one missing from the members map", () => {
    const crew: CrewSnap = {
      crewId: "c1",
      ownerId: UID,
      // "aaa" sorts first alphabetically but has no members entry → +Infinity joinedAt → loses.
      memberIds: [UID, "aaa", "zoe"],
      members: { [UID]: { joinedAt: 1 }, zoe: { joinedAt: 999 } },
      code: null,
    };
    expect(planCrewReassignment(crew, UID)).toEqual({ kind: "reassign", newOwnerId: "zoe" });
  });

  it("an owner listed alone in memberIds deletes the crew even if the members map has ghosts", () => {
    const crew: CrewSnap = {
      crewId: "c1",
      ownerId: UID,
      memberIds: [UID], // memberIds is authoritative for "who remains"
      members: { [UID]: { joinedAt: 1 }, ghost: { joinedAt: 2 } },
      code: "AAA",
    };
    expect(planCrewReassignment(crew, UID)).toEqual({ kind: "delete" });
  });
});

describe("deleteAccountCore — reassign failure preserves the account (§14.1)", () => {
  it("throws aborted and NEVER deletes the Auth user when a crew reassign rejects", async () => {
    const crew: CrewSnap = {
      crewId: "c1",
      ownerId: UID,
      memberIds: [UID, "bob"],
      members: { [UID]: { joinedAt: 1 }, bob: { joinedAt: 2 } },
      code: "AAA",
    };
    const r = recordingDeps({
      expectedPhrase: async () => "DELETE Ana",
      memberCrews: async () => [crew],
      reassignOrDeleteCrew: async () => {
        throw new Error("malformed members map");
      },
    });

    expect(await codeOf(() => deleteAccountCore(r.deps, UID, { confirmation: "DELETE Ana" }))).toBe(
      "aborted",
    );
    expect(r.authDeleted).toEqual([]); // account survives a reassignment failure.
    expect(r.calls.some((c) => c.startsWith("deleteAuthUser:"))).toBe(false);
  });
});

describe("deleteAccountCore — idempotent re-run (§14.1)", () => {
  it("completes over an already-half-deleted fixture without throwing", async () => {
    // not-found-tolerant fakes: blob/doc deletes silently no-op (the real deps use
    // ignoreNotFound / recursiveDelete which is safe on missing paths).
    const r = recordingDeps({
      expectedPhrase: async () => "DELETE Ana",
      authoredMeals: async () => [
        { path: "crews/c1/meals/m1", platePaths: ["crews/c1/meals/m1.jpg"] },
      ],
      memberCrews: async () => [
        { crewId: "c1", ownerId: "bob", memberIds: ["bob"], members: { bob: { joinedAt: 1 } }, code: null },
      ],
      recursiveDelete: async () => undefined,
      deleteBlob: async () => undefined,
      reassignOrDeleteCrew: async () => undefined,
    });

    const res = await deleteAccountCore(r.deps, UID, { confirmation: "DELETE Ana" });
    expect(res).toEqual({ deleted: true });
    expect(r.authDeleted).toEqual([UID]);
  });
});
