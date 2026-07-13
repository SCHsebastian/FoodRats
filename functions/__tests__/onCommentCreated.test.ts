import { beforeEach, describe, expect, it, vi } from "vitest";

// ---------------------------------------------------------------------------
// The trigger body is not dependency-injected — mock the Firestore read and the
// push fan-out (mealDeepLink stays REAL via the importOriginal spread) and drive
// the handler through the v2 CloudFunction's `.run(event)` seam.
// ---------------------------------------------------------------------------

const h = vi.hoisted(() => ({
  /** doc path → document data (undefined ⇒ missing doc). */
  docs: new Map<string, Record<string, unknown>>(),
  sendToUid: vi.fn(async () => undefined),
}));

vi.mock("../src/fcm/push", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../src/fcm/push")>()),
  sendToUid: h.sendToUid,
}));

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: () => ({
    doc: (path: string) => ({
      get: async () => ({ data: () => h.docs.get(path) }),
    }),
  }),
}));

import { onCommentCreated } from "../src/triggers/onCommentCreated";
import { mealDeepLink, type PushPayload } from "../src/fcm/push";

const CREW = "c1";
const MEAL = "c1_alice_2026-06-14_lunch";
const COMMENT = "cmt1";
const MEAL_DOC = `crews/${CREW}/meals/${MEAL}`;

type CommentEvent = Parameters<typeof onCommentCreated.run>[0];

function commentEvent(comment: Record<string, unknown> | undefined): CommentEvent {
  return {
    data: comment === undefined ? undefined : { data: () => comment },
    params: { crewId: CREW, mealId: MEAL, commentId: COMMENT },
  } as unknown as CommentEvent;
}

beforeEach(() => {
  h.docs.clear();
  h.sendToUid.mockClear();
});

function sentTo(): { uid: string; payload: PushPayload } {
  expect(h.sendToUid).toHaveBeenCalledTimes(1);
  const [uid, payload] = h.sendToUid.mock.calls[0] as [string, PushPayload];
  return { uid, payload };
}

describe("onCommentCreated — comment push to the meal author", () => {
  it("ignores an event with no snapshot", async () => {
    await onCommentCreated.run(commentEvent(undefined));
    expect(h.sendToUid).not.toHaveBeenCalled();
  });

  it("skips when the parent meal is gone (deleted between comment and trigger)", async () => {
    await onCommentCreated.run(commentEvent({ authorId: "bob", authorName: "Bob" }));
    expect(h.sendToUid).not.toHaveBeenCalled();
  });

  it("skips a self-comment (no push to yourself)", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    await onCommentCreated.run(commentEvent({ authorId: "alice", authorName: "Alice" }));
    expect(h.sendToUid).not.toHaveBeenCalled();
  });

  it("pushes to the meal author with the full data block + meal deep link", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    await onCommentCreated.run(commentEvent({ authorId: "bob", authorName: "Bob" }));

    const { uid, payload } = sentTo();
    expect(uid).toBe("alice");
    expect(payload.kind).toBe("NewComment");
    expect(payload.key).toBe("new_comment");
    expect(payload.notificationTitle).toBe("Bob commented on your paella");
    expect(payload.notificationBody).toBe("Tap to read");
    expect(payload.data).toEqual({
      crewId: CREW,
      mealId: MEAL,
      commentId: COMMENT,
      commenterName: "Bob",
      dishName: "paella",
      dayKey: "2026-06-14",
      link: mealDeepLink(MEAL, "2026-06-14"),
    });
    expect(payload.data.link).toBe(`foodrats://app/meal/${MEAL}/2026-06-14`);
  });

  it("falls back to 'Someone' / 'your meal' for missing names (malformed docs)", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dayKey: "2026-06-14" }); // no dishName
    await onCommentCreated.run(commentEvent({ authorId: "bob" })); // no authorName

    const { payload } = sentTo();
    expect(payload.notificationTitle).toBe("Someone commented on your your meal");
    expect(payload.data.commenterName).toBe("Someone");
    expect(payload.data.dishName).toBe("your meal");
  });

  it("omits dayKey AND link when the meal has no dayKey (linkless push, app opens Feed)", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella" });
    await onCommentCreated.run(commentEvent({ authorId: "bob", authorName: "Bob" }));

    const { payload } = sentTo();
    expect(payload.data).not.toHaveProperty("dayKey");
    expect(payload.data).not.toHaveProperty("link");
  });
});
