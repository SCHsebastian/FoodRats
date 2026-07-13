import { describe, expect, it, vi } from "vitest";
import {
  EXPORT_SCHEMA_VERSION,
  EXPORT_URL_TTL_MS,
  buildExportArchive,
  exportMyDataCore,
  type CrewSnap,
  type DocSnap,
  type ExportDeps,
  type MealSnap,
  type VoteSnap,
} from "../src/callables/exportMyData";

const UID = "ana";
const NOW = 1_700_000_000_000;

/** Recording fakes so we can assert what was uploaded + signed without the Admin SDK. */
function recordingDeps(overrides: Partial<ExportDeps> = {}): {
  deps: ExportDeps;
  uploaded: string[];
  signed: string[];
} {
  const uploaded: string[] = [];
  const signed: string[] = [];

  const deps: ExportDeps = {
    account: async () => ({ id: UID, displayName: "Ana" }),
    privateDocs: async () => [],
    deviceDocs: async () => [],
    crews: async () => [],
    authoredMeals: async () => [],
    authoredComments: async () => [],
    castVotes: async () => [],
    signUrl: async (path, expiresAtMs) => {
      signed.push(path);
      return `https://signed.example/${path}?exp=${expiresAtMs}`;
    },
    uploadArchive: async (uid, json) => {
      const path = `exports/${uid}/export.json`;
      uploaded.push(json);
      return path;
    },
    nowMs: NOW,
    ...overrides,
  };

  return { deps, uploaded, signed };
}

async function codeOf(fn: () => Promise<unknown>): Promise<string | undefined> {
  try {
    await fn();
    return undefined;
  } catch (e) {
    return (e as { code?: string }).code;
  }
}

describe("exportMyDataCore — auth gate (§0.4)", () => {
  it("rejects an unauthenticated caller and gathers nothing", async () => {
    const { deps } = recordingDeps();
    const account = vi.fn(deps.account);
    expect(await codeOf(() => exportMyDataCore({ ...deps, account }, undefined, {}))).toBe(
      "unauthenticated",
    );
    expect(account).not.toHaveBeenCalled();
  });
});

describe("exportMyDataCore — assembly + signed URL (§0.4)", () => {
  const meals: MealSnap[] = [
    {
      path: "crews/c1/meals/m1",
      platePath: "crews/c1/meals/m1.jpg",
      data: { authorId: UID, dishName: "Lasagna" },
    },
    {
      path: "crews/c2/meals/m2",
      platePath: "crews/c2/meals/m2.jpg",
      data: { authorId: UID, dishName: "Tacos" },
    },
  ];
  const comments: DocSnap[] = [{ path: "crews/c1/meals/x/comments/cm1", data: { text: "yum" } }];
  const votes: VoteSnap[] = [
    { mealPath: "crews/c1/meals/voted1", crewId: "c1", vote: { score: 4 } },
  ];
  const crews: CrewSnap[] = [
    { crewId: "c1", name: "Crew One", ownerId: UID, myMembership: { joinedAt: 1 } },
  ];
  const devices: DocSnap[] = [{ path: `accounts/${UID}/devices/tok1`, data: { token: "tok1" } }];
  const consent: DocSnap[] = [
    { path: `accounts/${UID}/private/consent`, data: { version: 1 } },
  ];

  function fullDeps() {
    return recordingDeps({
      authoredMeals: async () => meals,
      authoredComments: async () => comments,
      castVotes: async () => votes,
      crews: async () => crews,
      deviceDocs: async () => devices,
      privateDocs: async () => consent,
    });
  }

  it("returns a 15-min download URL and uploads exactly one archive", async () => {
    const r = fullDeps();
    const res = await exportMyDataCore(r.deps, UID, {});

    expect(res.expiresAtMs).toBe(NOW + EXPORT_URL_TTL_MS);
    expect(res.expiresAtMs - NOW).toBe(15 * 60 * 1000);
    expect(res.downloadUrl).toBe(
      `https://signed.example/exports/${UID}/export.json?exp=${NOW + EXPORT_URL_TTL_MS}`,
    );
    expect(r.uploaded.length).toBe(1);
  });

  it("signs each plate image plus the archive object", async () => {
    const r = fullDeps();
    await exportMyDataCore(r.deps, UID, {});
    // both plate paths + the uploaded archive path were signed.
    expect(r.signed).toEqual(
      expect.arrayContaining([
        "crews/c1/meals/m1.jpg",
        "crews/c2/meals/m2.jpg",
        `exports/${UID}/export.json`,
      ]),
    );
  });

  it("assembles the caller's account, consent, devices, crews, meals, comments and votes", async () => {
    const r = fullDeps();
    await exportMyDataCore(r.deps, UID, {});
    const archive = JSON.parse(r.uploaded[0]);

    expect(archive.schemaVersion).toBe(EXPORT_SCHEMA_VERSION);
    expect(archive.accountId).toBe(UID);
    expect(archive.account).toEqual({ id: UID, displayName: "Ana" });
    expect(archive.consent).toEqual(consent);
    expect(archive.devices).toEqual(devices);
    expect(archive.crews).toEqual(crews);
    expect(archive.meals.map((m: { path: string }) => m.path)).toEqual([
      "crews/c1/meals/m1",
      "crews/c2/meals/m2",
    ]);
    expect(archive.comments).toEqual(comments);
    expect(archive.votes).toEqual(votes);
    expect(archive.plates.map((p: { path: string }) => p.path)).toEqual([
      "crews/c1/meals/m1.jpg",
      "crews/c2/meals/m2.jpg",
    ]);
  });

  it("handles a missing account doc + an account with no data gracefully", async () => {
    const r = recordingDeps({ account: async () => null });
    const res = await exportMyDataCore(r.deps, UID, {});
    expect(res.downloadUrl).toContain("exports/");
    const archive = JSON.parse(r.uploaded[0]);
    expect(archive.account).toBeNull();
    expect(archive.meals).toEqual([]);
    expect(archive.plates).toEqual([]);
  });

  it("skips plate signing for meals with a null or empty platePath (no delete-at-guessed-path)", async () => {
    const r = recordingDeps({
      authoredMeals: async () => [
        { path: "crews/c1/meals/m1", platePath: null, data: {} },
        { path: "crews/c1/meals/m2", platePath: "", data: {} },
        { path: "crews/c1/meals/m3", platePath: "crews/c1/meals/m3.jpg", data: {} },
      ],
    });
    await exportMyDataCore(r.deps, UID, {});
    // Only the real plate + the archive itself get signed — never "" or a guessed path.
    expect(r.signed).toEqual(["crews/c1/meals/m3.jpg", `exports/${UID}/export.json`]);
    const archive = JSON.parse(r.uploaded[0]);
    expect(archive.plates.map((p: { path: string }) => p.path)).toEqual([
      "crews/c1/meals/m3.jpg",
    ]);
    // The meals themselves still export, plate or not.
    expect(archive.meals).toHaveLength(3);
  });

  it("signs a duplicated platePath only ONCE (de-dup before the signing fan-out)", async () => {
    const r = recordingDeps({
      authoredMeals: async () => [
        { path: "crews/c1/meals/m1", platePath: "crews/c1/meals/shared.jpg", data: {} },
        { path: "crews/c1/meals/m2", platePath: "crews/c1/meals/shared.jpg", data: {} },
      ],
    });
    await exportMyDataCore(r.deps, UID, {});
    expect(r.signed.filter((p) => p === "crews/c1/meals/shared.jpg")).toHaveLength(1);
    const archive = JSON.parse(r.uploaded[0]);
    expect(archive.plates).toHaveLength(1);
  });

  it("a failing gather dependency propagates (the callable wrapper maps it to 'internal')", async () => {
    const r = recordingDeps({
      authoredMeals: async () => {
        throw new Error("firestore down");
      },
    });
    await expect(exportMyDataCore(r.deps, UID, {})).rejects.toThrow("firestore down");
    expect(r.uploaded).toEqual([]); // nothing uploaded when the gather fails.
  });
});

describe("exportMyDataCore — excludes other members' PII (§0.4)", () => {
  it("exports only the caller's OWN crew membership, never other members'", async () => {
    // The crews dep is the projection boundary: it already drops other members' entries. Assert the
    // archive carries only `myMembership` + shared crew identity, with no `members` map at all.
    const crews: CrewSnap[] = [
      { crewId: "c1", name: "Crew One", ownerId: "bob", myMembership: { joinedAt: 5 } },
    ];
    const r = recordingDeps({ crews: async () => crews });
    await exportMyDataCore(r.deps, UID, {});
    const archive = JSON.parse(r.uploaded[0]);

    expect(archive.crews).toEqual(crews);
    expect(archive.crews[0]).not.toHaveProperty("members");
    // No other uid leaks into the serialized crew entry.
    expect(JSON.stringify(archive.crews)).not.toContain("carol");
  });

  it("excludes self-authored meals from votes (those live under `meals`)", async () => {
    // castVotes is responsible for skipping self-authored meals; the archive must mirror that.
    const votes: VoteSnap[] = [{ mealPath: "crews/c1/meals/bobsMeal", crewId: "c1", vote: { score: 3 } }];
    const r = recordingDeps({ castVotes: async () => votes });
    await exportMyDataCore(r.deps, UID, {});
    const archive = JSON.parse(r.uploaded[0]);
    expect(archive.votes).toEqual(votes);
  });
});

describe("buildExportArchive — pure projection (§0.4)", () => {
  it("de-duplicates plate manifest entries by path", () => {
    const archive = buildExportArchive({
      uid: UID,
      account: null,
      consent: [],
      devices: [],
      crews: [],
      meals: [],
      comments: [],
      votes: [],
      plates: [
        { path: "crews/c1/meals/m1.jpg", url: "u1" },
        { path: "crews/c1/meals/m1.jpg", url: "u1-dup" },
        { path: "crews/c2/meals/m2.jpg", url: "u2" },
      ],
      exportedAtMs: NOW,
    });
    expect(archive.plates.map((p) => p.path)).toEqual([
      "crews/c1/meals/m1.jpg",
      "crews/c2/meals/m2.jpg",
    ]);
    // First-seen entry wins — the duplicate's URL is discarded, not merged.
    expect(archive.plates[0].url).toBe("u1");
  });

  it("stamps the schema version + an ISO export timestamp", () => {
    const archive = buildExportArchive({
      uid: UID,
      account: null,
      consent: [],
      devices: [],
      crews: [],
      meals: [],
      comments: [],
      votes: [],
      plates: [],
      exportedAtMs: NOW,
    });
    expect(archive.schemaVersion).toBe(EXPORT_SCHEMA_VERSION);
    expect(archive.exportedAt).toBe(new Date(NOW).toISOString());
  });

  it("projects meals to path + data only (no internal platePath leak)", () => {
    const archive = buildExportArchive({
      uid: UID,
      account: null,
      consent: [],
      devices: [],
      crews: [],
      meals: [{ path: "crews/c1/meals/m1", platePath: "crews/c1/meals/m1.jpg", data: { x: 1 } }],
      comments: [],
      votes: [],
      plates: [],
      exportedAtMs: NOW,
    });
    expect(archive.meals).toEqual([{ path: "crews/c1/meals/m1", data: { x: 1 } }]);
    expect(archive.meals[0]).not.toHaveProperty("platePath");
  });
});
