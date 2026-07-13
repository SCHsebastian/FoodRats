import { beforeEach, describe, expect, it, vi } from "vitest";

// readTokens/pruneToken call getFirestore() directly — substitute a controllable fake
// (same pattern as push.test.ts).
const h = vi.hoisted(() => ({
  /** collection path → array of { id, data } docs. */
  collections: new Map<string, Array<{ id: string; data: Record<string, unknown> }>>(),
  deleted: [] as string[],
  deleteThrows: false,
}));

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: () => ({
    doc: (path: string) => ({
      delete: async () => {
        if (h.deleteThrows) throw new Error("firestore unavailable");
        h.deleted.push(path);
      },
    }),
    collection: (path: string) => ({
      get: async () => ({
        docs: (h.collections.get(path) ?? []).map((d) => ({ id: d.id, data: () => d.data })),
      }),
    }),
  }),
}));

import { readTokens, pruneToken } from "../src/fcm/tokens";

beforeEach(() => {
  h.collections.clear();
  h.deleted.length = 0;
  h.deleteThrows = false;
});

describe("readTokens — device-token projection", () => {
  it("maps doc id → token and carries platform + languageTag through", async () => {
    h.collections.set("accounts/u1/devices", [
      { id: "tok-ios", data: { platform: "ios", languageTag: "es" } },
      { id: "tok-android", data: { platform: "android", languageTag: "en" } },
    ]);
    expect(await readTokens("u1")).toEqual([
      { token: "tok-ios", platform: "ios", languageTag: "es" },
      { token: "tok-android", platform: "android", languageTag: "en" },
    ]);
  });

  it("defaults a missing platform to android and leaves languageTag undefined (pre-stamping docs)", async () => {
    h.collections.set("accounts/u1/devices", [{ id: "tok-legacy", data: {} }]);
    expect(await readTokens("u1")).toEqual([
      { token: "tok-legacy", platform: "android", languageTag: undefined },
    ]);
  });

  it("returns an empty list for a uid with no devices subcollection", async () => {
    expect(await readTokens("nobody")).toEqual([]);
  });
});

describe("pruneToken — stale-token cleanup", () => {
  it("deletes the device doc keyed by the token", async () => {
    await pruneToken("u1", "tok-gone");
    expect(h.deleted).toEqual(["accounts/u1/devices/tok-gone"]);
  });

  it("swallows a delete failure (best-effort cleanup must not break the send path)", async () => {
    h.deleteThrows = true;
    await expect(pruneToken("u1", "tok-gone")).resolves.toBeUndefined();
    expect(h.deleted).toEqual([]);
  });
});
