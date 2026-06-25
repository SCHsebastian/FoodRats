import { describe, expect, it, vi } from "vitest";
import {
  authorizedPaths,
  buildSignedUrls,
  ownAvatarPaths,
  URL_TTL_MS,
  MAX_PATHS,
  type ReadCrew,
  type SignReadUrl,
} from "../src/callables/mintPlateUrls";

const NOW = 1_700_000_000_000;

/** Crew `c1` with members alice + bob. Any other crew id resolves to "missing". */
const readCrew: ReadCrew = async (crewId) =>
  crewId === "c1" ? { memberIds: ["alice", "bob"] } : null;

/** Deterministic fake signer so URLs are assertable. */
const sign: SignReadUrl = async (path, expiresAtMs) =>
  `https://signed.example/${path}?exp=${expiresAtMs}`;

const deps = { readCrew, sign, nowMs: NOW };

async function codeOf(fn: () => Promise<unknown>): Promise<string | undefined> {
  try {
    await fn();
    return undefined;
  } catch (e) {
    return (e as { code?: string }).code;
  }
}

describe("authorizedPaths — crew-scoped allow-list (#15)", () => {
  it("keeps this crew's plate photos and members' avatars (legacy + versioned)", () => {
    const paths = [
      "crews/c1/meals/c1_alice_2026-06-14_lunch.jpg",
      "avatars/alice.jpg", // legacy fixed path
      "avatars/bob/9f3c1a2b.jpg", // content-versioned path
    ];
    expect(authorizedPaths("c1", ["alice", "bob"], paths)).toEqual(paths);
  });

  it("keeps a member's content-versioned avatar and drops a non-member's", () => {
    const paths = ["avatars/alice/abc123.jpg", "avatars/carol/def456.jpg"];
    expect(authorizedPaths("c1", ["alice", "bob"], paths)).toEqual([
      "avatars/alice/abc123.jpg",
    ]);
  });

  it("drops other crews' plates, non-member avatars, and junk", () => {
    const paths = [
      "crews/c2/meals/c2_alice_2026-06-14_lunch.jpg", // foreign crew
      "avatars/carol.jpg", // not a member
      "secret/keys.txt", // not an image path
      "crews/c1/meals/x.png", // wrong extension
    ];
    expect(authorizedPaths("c1", ["alice", "bob"], paths)).toEqual([]);
  });

  it("de-duplicates repeated paths", () => {
    const paths = ["avatars/alice.jpg", "avatars/alice.jpg"];
    expect(authorizedPaths("c1", ["alice", "bob"], paths)).toEqual(["avatars/alice.jpg"]);
  });

  // C9 — crew banner image
  it("allows the crew banner path for a member", () => {
    const paths = ["crew_banners/c1/banner.jpg"];
    expect(authorizedPaths("c1", ["alice", "bob"], paths)).toEqual(paths);
  });

  it("drops another crew's banner path", () => {
    const paths = ["crew_banners/c2/banner.jpg"];
    expect(authorizedPaths("c1", ["alice", "bob"], paths)).toEqual([]);
  });
});

describe("ownAvatarPaths — self-avatar allow-list (H2)", () => {
  it("keeps the caller's own avatar (versioned + legacy) and drops everything else", () => {
    const paths = [
      "avatars/alice/abc123.jpg", // own versioned
      "avatars/alice.jpg", // own legacy
      "avatars/bob/def456.jpg", // foreign uid
      "crews/c1/meals/c1_alice_2026-06-14_lunch.jpg", // plate
      "crew_banners/c1/banner.jpg", // banner
    ];
    expect(ownAvatarPaths("alice", paths)).toEqual([
      "avatars/alice/abc123.jpg",
      "avatars/alice.jpg",
    ]);
  });

  it("de-duplicates repeated own-avatar paths", () => {
    expect(ownAvatarPaths("alice", ["avatars/alice.jpg", "avatars/alice.jpg"])).toEqual([
      "avatars/alice.jpg",
    ]);
  });
});

describe("buildSignedUrls — membership-checked minting (#15)", () => {
  it("signs the authorized subset for a member, with a 15-min TTL", async () => {
    const res = await buildSignedUrls(deps, "alice", {
      crewId: "c1",
      paths: [
        "crews/c1/meals/c1_bob_2026-06-14_dinner.jpg",
        "avatars/bob.jpg",
        "avatars/carol.jpg", // dropped: not a member
      ],
    });

    expect(res.expiresAtMs).toBe(NOW + URL_TTL_MS);
    expect(res.expiresAtMs - NOW).toBe(15 * 60 * 1000);
    expect(Object.keys(res.urls).sort()).toEqual([
      "avatars/bob.jpg",
      "crews/c1/meals/c1_bob_2026-06-14_dinner.jpg",
    ]);
    expect(res.urls["avatars/bob.jpg"]).toBe(
      `https://signed.example/avatars/bob.jpg?exp=${NOW + URL_TTL_MS}`,
    );
  });

  it("rejects a non-member with permission-denied", async () => {
    expect(
      await codeOf(() => buildSignedUrls(deps, "carol", { crewId: "c1", paths: [] })),
    ).toBe("permission-denied");
  });

  it("rejects an unknown crew with permission-denied (no existence leak)", async () => {
    expect(
      await codeOf(() => buildSignedUrls(deps, "alice", { crewId: "nope", paths: [] })),
    ).toBe("permission-denied");
  });

  it("rejects an unauthenticated caller", async () => {
    expect(
      await codeOf(() => buildSignedUrls(deps, undefined, { crewId: "c1", paths: [] })),
    ).toBe("unauthenticated");
  });

  it("signs the caller's own avatar for an empty crewId WITHOUT a crew lookup (H2)", async () => {
    const readCrewSpy = vi.fn(readCrew);
    const res = await buildSignedUrls(
      { readCrew: readCrewSpy, sign, nowMs: NOW },
      "carol", // not a member of any crew
      {
        crewId: "",
        paths: [
          "avatars/carol/9f3c1a2b.jpg", // own versioned avatar
          "avatars/alice.jpg", // dropped: another user's avatar
          "crews/c1/meals/c1_alice_2026-06-14_lunch.jpg", // dropped: a plate
        ],
      },
    );

    expect(readCrewSpy).not.toHaveBeenCalled(); // no crew lookup / membership check
    expect(res.expiresAtMs).toBe(NOW + URL_TTL_MS);
    expect(Object.keys(res.urls)).toEqual(["avatars/carol/9f3c1a2b.jpg"]);
    expect(res.urls["avatars/carol/9f3c1a2b.jpg"]).toBe(
      `https://signed.example/avatars/carol/9f3c1a2b.jpg?exp=${NOW + URL_TTL_MS}`,
    );
  });

  it("treats a whitespace-only crewId as a self-avatar request (H2)", async () => {
    const readCrewSpy = vi.fn(readCrew);
    const res = await buildSignedUrls(
      { readCrew: readCrewSpy, sign, nowMs: NOW },
      "alice",
      { crewId: "  ", paths: ["avatars/alice.jpg"] },
    );
    expect(readCrewSpy).not.toHaveBeenCalled();
    expect(Object.keys(res.urls)).toEqual(["avatars/alice.jpg"]);
  });

  it("rejects an unauthenticated empty-crewId self-avatar request (H2)", async () => {
    expect(
      await codeOf(() => buildSignedUrls(deps, undefined, { crewId: "", paths: ["avatars/x.jpg"] })),
    ).toBe("unauthenticated");
  });

  it("caps requests at MAX_PATHS paths (functions-03)", async () => {
    // Build MAX_PATHS + 10 plate paths for crew c1; the response should contain at most MAX_PATHS.
    const extraPaths = Array.from(
      { length: MAX_PATHS + 10 },
      (_, i) => `crews/c1/meals/c1_alice_2026-06-14_${i}.jpg`,
    );
    const res = await buildSignedUrls(deps, "alice", { crewId: "c1", paths: extraPaths });
    expect(Object.keys(res.urls).length).toBeLessThanOrEqual(MAX_PATHS);
  });
});
