import { describe, expect, it } from "vitest";
import {
  authorizedPaths,
  buildSignedUrls,
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
  it("keeps this crew's plate photos and members' avatars", () => {
    const paths = [
      "crews/c1/meals/c1_alice_2026-06-14_lunch.jpg",
      "avatars/alice.jpg",
      "avatars/bob.jpg",
    ];
    expect(authorizedPaths("c1", ["alice", "bob"], paths)).toEqual(paths);
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

  it("rejects a blank crewId with invalid-argument", async () => {
    expect(
      await codeOf(() => buildSignedUrls(deps, "alice", { crewId: "  ", paths: [] })),
    ).toBe("invalid-argument");
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
