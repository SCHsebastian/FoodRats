import { beforeEach, describe, expect, it, vi } from "vitest";

// ---------------------------------------------------------------------------
// Module mocks — the send pipeline (sendToUid/sendToCrew) calls getFirestore()
// and getMessaging() directly, so we substitute controllable fakes. All state
// lives in the hoisted `h` so the factories (hoisted by vitest) can reach it.
// ---------------------------------------------------------------------------

const h = vi.hoisted(() => ({
  /** doc path → document data (undefined ⇒ missing doc). */
  docs: new Map<string, Record<string, unknown>>(),
  /** collection path → array of { id, data } docs. */
  collections: new Map<string, Array<{ id: string; data: Record<string, unknown> }>>(),
  /** Recorded firestore doc deletes (token prunes). */
  deleted: [] as string[],
  /** The messaging fan-out mock; per-test responses via mockImplementation. */
  sendEachForMulticast: vi.fn(),
}));

vi.mock("firebase-admin/messaging", () => ({
  getMessaging: () => ({ sendEachForMulticast: h.sendEachForMulticast }),
}));

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: () => ({
    doc: (path: string) => ({
      get: async () => ({
        data: () => h.docs.get(path),
        exists: h.docs.has(path),
      }),
      delete: async () => {
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

import {
  buildMulticastMessage,
  mealDeepLink,
  digestDeepLink,
  sendToUid,
  sendToCrew,
  type PushPayload,
} from "../src/fcm/push";

const allSuccess = (tokenCount: number) => ({
  successCount: tokenCount,
  failureCount: 0,
  responses: Array.from({ length: tokenCount }, () => ({ success: true })),
});

beforeEach(() => {
  h.docs.clear();
  h.collections.clear();
  h.deleted.length = 0;
  h.sendEachForMulticast.mockReset();
  h.sendEachForMulticast.mockImplementation(async (msg: { tokens: string[] }) =>
    allSuccess(msg.tokens.length),
  );
});

function seedDevices(uid: string, devices: Array<{ token: string; languageTag?: string }>) {
  h.collections.set(
    `accounts/${uid}/devices`,
    devices.map((d) => ({
      id: d.token,
      data: d.languageTag === undefined ? {} : { languageTag: d.languageTag },
    })),
  );
}

function sentMessages(): Array<{
  tokens: string[];
  notification: { title: string; body: string };
  data: Record<string, string>;
}> {
  return h.sendEachForMulticast.mock.calls.map((c) => c[0]);
}

const commentPayload: PushPayload = {
  kind: "NewComment",
  key: "new_comment",
  notificationTitle: "Ana commented on your paella",
  notificationBody: "Tap to read",
  data: { commenterName: "Ana", dishName: "paella", mealId: "m1" },
};

// ---------------------------------------------------------------------------
// Deep-link builders — the client URL contract (shared/.../navigation/DeepLink.kt)
// ---------------------------------------------------------------------------

describe("mealDeepLink — client deep-link contract", () => {
  it("builds foodrats://app/meal/{mealId}/{dayKey} exactly", () => {
    expect(mealDeepLink("c1_alice_2026-06-14_lunch", "2026-06-14")).toBe(
      "foodrats://app/meal/c1_alice_2026-06-14_lunch/2026-06-14",
    );
  });

  it("keeps 'meal' as the first path segment (the parser's discriminator)", () => {
    const url = new URL(mealDeepLink("m1", "2026-06-14"));
    expect(url.protocol).toBe("foodrats:");
    expect(url.host).toBe("app");
    expect(url.pathname.split("/").filter(Boolean)[0]).toBe("meal");
  });

  it("digestDeepLink uses 'digest' as its discriminator segment", () => {
    const url = new URL(digestDeepLink("2026-06-01"));
    expect(url.pathname.split("/").filter(Boolean)).toEqual(["digest", "2026-06-01"]);
  });
});

// ---------------------------------------------------------------------------
// buildMulticastMessage — iOS (APNs) delivery
// ---------------------------------------------------------------------------

describe("buildMulticastMessage — iOS (APNs) delivery", () => {
  const msg = buildMulticastMessage(
    ["tok-a", "tok-b"],
    "New comment",
    "Sebas commented on your lasagna",
    { kind: "NewComment", key: "new_comment", mealId: "m1" },
  );

  it("targets the given tokens", () => {
    expect(msg.tokens).toEqual(["tok-a", "tok-b"]);
  });

  it("carries the localized notification block", () => {
    expect(msg.notification).toEqual({
      title: "New comment",
      body: "Sebas commented on your lasagna",
    });
  });

  it("carries the data block verbatim", () => {
    expect(msg.data).toEqual({
      kind: "NewComment",
      key: "new_comment",
      mealId: "m1",
    });
  });

  it("sets apns-push-type:alert so APNs accepts and displays the push on iOS 13+", () => {
    expect(msg.apns.headers["apns-push-type"]).toBe("alert");
  });

  it("sets apns-priority:10 for immediate delivery", () => {
    expect(msg.apns.headers["apns-priority"]).toBe("10");
  });

  it("sets aps.sound so the push is NOT silent on iOS when backgrounded", () => {
    expect(msg.apns.payload.aps.sound).toBe("default");
  });

  it("mirrors title/body into aps.alert (explicit, not relying on FCM synthesis)", () => {
    expect(msg.apns.payload.aps.alert).toEqual({
      title: "New comment",
      body: "Sebas commented on your lasagna",
    });
  });

  it("marks the Android counterpart high-priority", () => {
    expect(msg.android.priority).toBe("high");
  });
});

// ---------------------------------------------------------------------------
// sendToUid — token read, per-language grouping, localization, data block
// ---------------------------------------------------------------------------

describe("sendToUid — per-device-language localization", () => {
  it("skips entirely when the uid has no registered devices", async () => {
    seedDevices("u1", []);
    await sendToUid("u1", commentPayload);
    expect(h.sendEachForMulticast).not.toHaveBeenCalled();
  });

  it("skips when the devices subcollection does not exist at all", async () => {
    await sendToUid("ghost", commentPayload);
    expect(h.sendEachForMulticast).not.toHaveBeenCalled();
  });

  it("groups tokens by normalized language and localizes each group's OS notification", async () => {
    seedDevices("u1", [
      { token: "tok-en", languageTag: "en" },
      { token: "tok-es", languageTag: "es" },
      { token: "tok-none" }, // no languageTag → English fallback
      { token: "tok-esmx", languageTag: "es-MX" }, // regional variant → es
      { token: "tok-fr", languageTag: "fr" }, // unsupported → English fallback
    ]);

    await sendToUid("u1", commentPayload);

    const msgs = sentMessages();
    expect(msgs).toHaveLength(2);

    const en = msgs.find((m) => m.tokens.includes("tok-en"))!;
    const es = msgs.find((m) => m.tokens.includes("tok-es"))!;
    expect(en.tokens.sort()).toEqual(["tok-en", "tok-fr", "tok-none"]);
    expect(es.tokens.sort()).toEqual(["tok-es", "tok-esmx"]);

    // OS-rendered block localized per group; strings must match the i18n table.
    expect(en.notification).toEqual({ title: "Ana commented on your paella", body: "Tap to read" });
    expect(es.notification).toEqual({ title: "Ana comentó tu paella", body: "Pulsa para leer" });

    // The data block is identical for every group (client re-localizes in foreground).
    const expectedData = {
      kind: "NewComment",
      key: "new_comment",
      commenterName: "Ana",
      dishName: "paella",
      mealId: "m1",
    };
    expect(en.data).toEqual(expectedData);
    expect(es.data).toEqual(expectedData);
  });

  it("falls back to the payload's English default text for a key without a localizer", async () => {
    seedDevices("u1", [{ token: "tok-es", languageTag: "es" }]);
    await sendToUid("u1", {
      kind: "NewComment",
      key: "some_future_key",
      notificationTitle: "Default title",
      notificationBody: "Default body",
      data: {},
    });

    const [msg] = sentMessages();
    expect(msg.notification).toEqual({ title: "Default title", body: "Default body" });
  });
});

// ---------------------------------------------------------------------------
// Token pruning — FCM failure responses
// ---------------------------------------------------------------------------

describe("sendToUid — stale-token pruning on FCM failures", () => {
  it("prunes not-registered and invalid tokens, keeps transient failures", async () => {
    seedDevices("u1", [
      { token: "tok-ok" },
      { token: "tok-gone" },
      { token: "tok-bad" },
      { token: "tok-flaky" },
    ]);
    h.sendEachForMulticast.mockResolvedValueOnce({
      successCount: 1,
      failureCount: 3,
      responses: [
        { success: true },
        { success: false, error: { code: "messaging/registration-token-not-registered" } },
        { success: false, error: { code: "messaging/invalid-registration-token" } },
        { success: false, error: { code: "messaging/internal-error" } },
      ],
    });

    await sendToUid("u1", commentPayload);

    expect(h.deleted.sort()).toEqual([
      "accounts/u1/devices/tok-bad",
      "accounts/u1/devices/tok-gone",
    ]);
    // The transient failure is NOT pruned — the device can still receive later.
    expect(h.deleted).not.toContain("accounts/u1/devices/tok-flaky");
  });

  it("tolerates a failure response with no error object (no crash, no prune)", async () => {
    seedDevices("u1", [{ token: "tok-a" }]);
    h.sendEachForMulticast.mockResolvedValueOnce({
      successCount: 0,
      failureCount: 1,
      responses: [{ success: false }],
    });
    await sendToUid("u1", commentPayload);
    expect(h.deleted).toEqual([]);
  });

  it("prunes per language group with the correct token indexes", async () => {
    // Two groups; only the ES group's single token fails → exactly that token is pruned.
    seedDevices("u1", [
      { token: "tok-en", languageTag: "en" },
      { token: "tok-es", languageTag: "es" },
    ]);
    h.sendEachForMulticast.mockImplementation(async (msg: { tokens: string[] }) => {
      if (msg.tokens.includes("tok-es")) {
        return {
          successCount: 0,
          failureCount: 1,
          responses: [
            { success: false, error: { code: "messaging/registration-token-not-registered" } },
          ],
        };
      }
      return allSuccess(msg.tokens.length);
    });

    await sendToUid("u1", commentPayload);
    expect(h.deleted).toEqual(["accounts/u1/devices/tok-es"]);
  });
});

// ---------------------------------------------------------------------------
// sendToCrew — membership fan-out
// ---------------------------------------------------------------------------

describe("sendToCrew — crew fan-out", () => {
  it("does nothing when the crew doc is missing (deleted crew)", async () => {
    await sendToCrew("gone-crew", null, commentPayload);
    expect(h.sendEachForMulticast).not.toHaveBeenCalled();
  });

  it("treats a crew doc without memberIds as an empty crew (no crash, no sends)", async () => {
    h.docs.set("crews/c1", { name: "Walk Crew" }); // malformed: memberIds absent
    await sendToCrew("c1", null, commentPayload);
    expect(h.sendEachForMulticast).not.toHaveBeenCalled();
  });

  it("excludes the excepted uid (the author) and skips tokenless members", async () => {
    h.docs.set("crews/c1", { memberIds: ["author", "bob", "carol"] });
    seedDevices("author", [{ token: "tok-author" }]);
    seedDevices("bob", [{ token: "tok-bob" }]);
    // carol has no devices.

    await sendToCrew("c1", "author", commentPayload);

    const msgs = sentMessages();
    expect(msgs).toHaveLength(1);
    expect(msgs[0].tokens).toEqual(["tok-bob"]);
  });

  it("sends to EVERY member when exceptUid is null (weekly digest semantics)", async () => {
    h.docs.set("crews/c1", { memberIds: ["a", "b"] });
    seedDevices("a", [{ token: "tok-a" }]);
    seedDevices("b", [{ token: "tok-b" }]);

    await sendToCrew("c1", null, commentPayload);

    const allTokens = sentMessages().flatMap((m) => m.tokens).sort();
    expect(allTokens).toEqual(["tok-a", "tok-b"]);
  });

  it("localizes per member device language across the crew", async () => {
    h.docs.set("crews/c1", { memberIds: ["a", "b"] });
    seedDevices("a", [{ token: "tok-a", languageTag: "es" }]);
    seedDevices("b", [{ token: "tok-b", languageTag: "en" }]);

    await sendToCrew("c1", null, commentPayload);

    const msgs = sentMessages();
    const esMsg = msgs.find((m) => m.tokens.includes("tok-a"))!;
    const enMsg = msgs.find((m) => m.tokens.includes("tok-b"))!;
    expect(esMsg.notification.title).toBe("Ana comentó tu paella");
    expect(enMsg.notification.title).toBe("Ana commented on your paella");
  });
});
