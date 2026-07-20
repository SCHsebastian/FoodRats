import { beforeEach, describe, expect, it, vi } from "vitest";

// ---------------------------------------------------------------------------
// The trigger body is not dependency-injected — mock the Firestore read and the
// push fan-out (mealDeepLink stays REAL via the importOriginal spread) and drive
// the handler through the v2 CloudFunction's `.run(event)` seam.
// ---------------------------------------------------------------------------

const h = vi.hoisted(() => ({
  /** doc path → document data (undefined ⇒ missing doc). */
  docs: new Map<string, Record<string, unknown>>(),
  /** When true, any `accounts/…` doc read throws (simulates a failed displayName lookup). */
  accountReadThrows: false,
  sendToUid: vi.fn(async () => undefined),
}));

vi.mock("../src/fcm/push", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../src/fcm/push")>()),
  sendToUid: h.sendToUid,
}));

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: () => ({
    doc: (path: string) => ({
      get: async () => {
        if (h.accountReadThrows && path.startsWith("accounts/")) throw new Error("firestore down");
        return { data: () => h.docs.get(path) };
      },
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
  h.accountReadThrows = false;
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
      link: mealDeepLink(CREW, MEAL, "2026-06-14"),
    });
    expect(payload.data.link).toBe(`foodrats://app/meal/${CREW}/${MEAL}/2026-06-14`);
  });

  it("resolves the commenter name from accounts/{authorId} when the comment doc lacks authorName (pre-mentions clients)", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    h.docs.set("accounts/bob", { displayName: "Bob R." });
    await onCommentCreated.run(commentEvent({ authorId: "bob" })); // no authorName on the doc

    const { payload } = sentTo();
    expect(payload.notificationTitle).toBe("Bob R. commented on your paella");
    expect(payload.data.commenterName).toBe("Bob R.");
  });

  it("a blank authorName snapshot also falls through to the account lookup", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    h.docs.set("accounts/bob", { displayName: "Bob R." });
    await onCommentCreated.run(commentEvent({ authorId: "bob", authorName: "  " }));

    expect(sentTo().payload.data.commenterName).toBe("Bob R.");
  });

  it("keeps names EMPTY in data when unresolvable — localized fallback happens at send time, and the push still goes out", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dayKey: "2026-06-14" }); // no dishName
    await onCommentCreated.run(commentEvent({ authorId: "bob" })); // no authorName, no account doc

    const { payload } = sentTo();
    // EN default applies EN fallback words; the ES group re-localizes from the EMPTY data params
    // ("Alguien comentó tu comida") — the old code baked "Someone"/"your meal" into data, which
    // leaked English into ES pushes (2026-07-15 bug 2) and rendered "…on your your meal".
    expect(payload.notificationTitle).toBe("Someone commented on your meal");
    expect(payload.data.commenterName).toBe("");
    expect(payload.data.dishName).toBe("");
  });

  it("a FAILED account lookup degrades to the empty name — never drops the push", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    h.accountReadThrows = true;
    await onCommentCreated.run(commentEvent({ authorId: "bob" }));

    const { payload } = sentTo();
    expect(payload.notificationTitle).toBe("Someone commented on your paella");
    expect(payload.data.commenterName).toBe("");
  });

  it("omits dayKey AND link when the meal has no dayKey (linkless push, app opens Feed)", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella" });
    await onCommentCreated.run(commentEvent({ authorId: "bob", authorName: "Bob" }));

    const { payload } = sentTo();
    expect(payload.data).not.toHaveProperty("dayKey");
    expect(payload.data).not.toHaveProperty("link");
  });
});

describe("onCommentCreated — @-mention fan-out", () => {
  const CREW_DOC = `crews/${CREW}`;

  it("pushes a CommentMention to a mentioned crew member (not the meal author)", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    h.docs.set(CREW_DOC, { memberIds: ["alice", "bob", "carol"] });
    await onCommentCreated.run(
      commentEvent({ authorId: "bob", authorName: "Bob", mentions: ["carol"] }),
    );

    expect(h.sendToUid).toHaveBeenCalledTimes(2); // owner push + mention push
    const [uid, payload] = h.sendToUid.mock.calls[1] as [string, PushPayload];
    expect(uid).toBe("carol");
    expect(payload.kind).toBe("CommentMention");
    expect(payload.key).toBe("comment_mention");
    expect(payload.notificationTitle).toBe("Bob mentioned you on paella");
    expect(payload.notificationBody).toBe("Tap to read");
    expect(payload.data).toEqual({
      crewId: CREW,
      mealId: MEAL,
      commentId: COMMENT,
      commenterName: "Bob",
      dishName: "paella",
      dayKey: "2026-06-14",
      link: mealDeepLink(CREW, MEAL, "2026-06-14"),
    });
  });

  it("meal owner mentioned gets only the NewComment push, never a second mention push", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    h.docs.set(CREW_DOC, { memberIds: ["alice", "bob"] });
    await onCommentCreated.run(
      commentEvent({ authorId: "bob", authorName: "Bob", mentions: ["alice"] }),
    );

    expect(h.sendToUid).toHaveBeenCalledTimes(1);
    const [uid, payload] = h.sendToUid.mock.calls[0] as [string, PushPayload];
    expect(uid).toBe("alice");
    expect(payload.kind).toBe("NewComment");
  });

  it("comment author mentioning themself gets no push at all for the self-mention", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    h.docs.set(CREW_DOC, { memberIds: ["alice", "bob"] });
    await onCommentCreated.run(
      commentEvent({ authorId: "bob", authorName: "Bob", mentions: ["bob"] }),
    );

    // Only the owner (alice) push fires; bob (author) is excluded from mention fan-out.
    expect(h.sendToUid).toHaveBeenCalledTimes(1);
    const [uid] = h.sendToUid.mock.calls[0] as [string, PushPayload];
    expect(uid).toBe("alice");
  });

  it("filters out a mentioned uid that is not in crew.memberIds (anti-spam)", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    h.docs.set(CREW_DOC, { memberIds: ["alice", "bob"] });
    await onCommentCreated.run(
      commentEvent({ authorId: "bob", authorName: "Bob", mentions: ["outsider"] }),
    );

    expect(h.sendToUid).toHaveBeenCalledTimes(1); // owner push only
    const [uid] = h.sendToUid.mock.calls[0] as [string, PushPayload];
    expect(uid).toBe("alice");
  });

  it("no mentions field: existing behavior untouched (owner push only)", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    h.docs.set(CREW_DOC, { memberIds: ["alice", "bob"] });
    await onCommentCreated.run(commentEvent({ authorId: "bob", authorName: "Bob" }));

    expect(h.sendToUid).toHaveBeenCalledTimes(1);
  });

  it("empty mentions array: no mention pushes, no crew read needed", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    // Deliberately no crew doc set — proves the empty-mentions short-circuit skips the read.
    await onCommentCreated.run(
      commentEvent({ authorId: "bob", authorName: "Bob", mentions: [] }),
    );

    expect(h.sendToUid).toHaveBeenCalledTimes(1);
  });

  it("owner comments on their own meal, mentioning a member: member gets the mention push, owner gets nothing", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    h.docs.set(CREW_DOC, { memberIds: ["alice", "bob", "carol"] });
    await onCommentCreated.run(
      commentEvent({ authorId: "alice", authorName: "Alice", mentions: ["carol"] }),
    );

    expect(h.sendToUid).toHaveBeenCalledTimes(1); // mention push only, no self owner push
    const [uid, payload] = h.sendToUid.mock.calls[0] as [string, PushPayload];
    expect(uid).toBe("carol");
    expect(payload.kind).toBe("CommentMention");
  });

  it("caps mention recipients at 10 and dedupes repeated uids", async () => {
    const members = ["alice", ...Array.from({ length: 12 }, (_, i) => `m${i}`)];
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    h.docs.set(CREW_DOC, { memberIds: members });
    const mentions = [...members.filter((m) => m !== "alice"), "m0"]; // m0 duplicated
    await onCommentCreated.run(
      commentEvent({ authorId: "bob", authorName: "Bob", mentions }),
    );

    // 1 owner push + at most 10 mention pushes.
    expect(h.sendToUid.mock.calls.length).toBeLessThanOrEqual(11);
    expect(h.sendToUid.mock.calls.length).toBe(11);
  });

  it("crew doc missing: mention fan-out is skipped without throwing (owner push still sent)", async () => {
    h.docs.set(MEAL_DOC, { authorId: "alice", dishName: "paella", dayKey: "2026-06-14" });
    // No crew doc set.
    await onCommentCreated.run(
      commentEvent({ authorId: "bob", authorName: "Bob", mentions: ["carol"] }),
    );

    expect(h.sendToUid).toHaveBeenCalledTimes(1); // owner push only
  });
});
