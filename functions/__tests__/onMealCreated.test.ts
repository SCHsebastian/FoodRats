import { beforeEach, describe, expect, it, vi } from "vitest";

// ---------------------------------------------------------------------------
// The trigger body is not dependency-injected — mock the crew read, the push
// fan-out, and the badge pipeline entry point; drive the handler through the
// v2 CloudFunction's `.run(event)` seam. mealDeepLink stays REAL.
// ---------------------------------------------------------------------------

const h = vi.hoisted(() => ({
  /** doc path → document data (undefined ⇒ missing doc). */
  docs: new Map<string, Record<string, unknown>>(),
  crewReadThrows: false,
  sendToCrew: vi.fn(async () => undefined),
  processBadgeMilestone: vi.fn(async () => null as string | null),
}));

vi.mock("../src/fcm/push", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../src/fcm/push")>()),
  sendToCrew: h.sendToCrew,
}));

vi.mock("../src/triggers/badgeMilestones", () => ({
  processBadgeMilestone: h.processBadgeMilestone,
}));

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: () => ({
    doc: (path: string) => ({
      get: async () => {
        if (h.crewReadThrows && path.startsWith("crews/")) throw new Error("firestore down");
        return { data: () => h.docs.get(path) };
      },
    }),
  }),
  FieldValue: { increment: (n: number) => ({ __increment: n }) },
}));

import { onMealCreated } from "../src/triggers/onMealCreated";
import { mealDeepLink, type PushPayload } from "../src/fcm/push";

const CREW = "c1";
const MEAL = "c1_alice_2026-06-14_lunch";

type MealEvent = Parameters<typeof onMealCreated.run>[0];

function mealEvent(meal: Record<string, unknown> | undefined): MealEvent {
  return {
    data: meal === undefined ? undefined : { data: () => meal },
    params: { crewId: CREW, mealId: MEAL },
  } as unknown as MealEvent;
}

const fullMeal = {
  authorId: "alice",
  authorName: "Alice",
  dishName: "paella",
  dayKey: "2026-06-14",
};

beforeEach(() => {
  h.docs.clear();
  h.crewReadThrows = false;
  h.sendToCrew.mockClear();
  h.sendToCrew.mockResolvedValue(undefined);
  h.processBadgeMilestone.mockClear();
  h.processBadgeMilestone.mockResolvedValue(null);
});

function sentPayload(): { crewId: string; exceptUid: string | null; payload: PushPayload } {
  expect(h.sendToCrew).toHaveBeenCalledTimes(1);
  const [crewId, exceptUid, payload] = h.sendToCrew.mock.calls[0] as [
    string,
    string | null,
    PushPayload,
  ];
  return { crewId, exceptUid, payload };
}

describe("onMealCreated — new-meal push to the crew", () => {
  it("ignores an event with no snapshot", async () => {
    await onMealCreated.run(mealEvent(undefined));
    expect(h.sendToCrew).not.toHaveBeenCalled();
    expect(h.processBadgeMilestone).not.toHaveBeenCalled();
  });

  it("fans out to the crew EXCLUDING the author, with crew name + meal deep link", async () => {
    h.docs.set(`crews/${CREW}`, { name: "Walk Crew" });
    await onMealCreated.run(mealEvent(fullMeal));

    const { crewId, exceptUid, payload } = sentPayload();
    expect(crewId).toBe(CREW);
    expect(exceptUid).toBe("alice");
    expect(payload.kind).toBe("NewMealPost");
    expect(payload.key).toBe("new_meal_post");
    expect(payload.notificationTitle).toBe("Alice posted a meal");
    expect(payload.notificationBody).toBe("paella — tap to view");
    expect(payload.data).toEqual({
      crewId: CREW,
      crewName: "Walk Crew",
      mealId: MEAL,
      authorName: "Alice",
      dishName: "paella",
      dayKey: "2026-06-14",
      link: mealDeepLink(MEAL, "2026-06-14"),
    });
    expect(payload.data.link).toBe(`foodrats://app/meal/${MEAL}/2026-06-14`);
  });

  it("falls back to 'your crew' when the crew doc is missing (deleted crew)", async () => {
    await onMealCreated.run(mealEvent(fullMeal));
    expect(sentPayload().payload.data.crewName).toBe("your crew");
  });

  it("falls back to 'your crew' when the crew read THROWS — the push still goes out", async () => {
    h.crewReadThrows = true;
    await onMealCreated.run(mealEvent(fullMeal));
    expect(sentPayload().payload.data.crewName).toBe("your crew");
  });

  it("falls back to 'A crewmate' / 'a meal' for missing author/dish names", async () => {
    await onMealCreated.run(mealEvent({ authorId: "alice", dayKey: "2026-06-14" }));
    const { payload } = sentPayload();
    expect(payload.notificationTitle).toBe("A crewmate posted a meal");
    expect(payload.notificationBody).toBe("a meal — tap to view");
  });

  it("omits dayKey AND link when the meal doc has no dayKey (linkless push)", async () => {
    await onMealCreated.run(mealEvent({ authorId: "alice", authorName: "Alice", dishName: "x" }));
    const { payload } = sentPayload();
    expect(payload.data).not.toHaveProperty("dayKey");
    expect(payload.data).not.toHaveProperty("link");
  });
});

describe("onMealCreated — badge milestone wiring", () => {
  it("invokes the badge pipeline with the author uid, crew and meal ids", async () => {
    await onMealCreated.run(mealEvent(fullMeal));
    expect(h.processBadgeMilestone).toHaveBeenCalledTimes(1);
    const [uid, crewId, mealId] = h.processBadgeMilestone.mock.calls[0] as [
      string,
      string,
      string,
      unknown,
    ];
    expect(uid).toBe("alice");
    expect(crewId).toBe(CREW);
    expect(mealId).toBe(MEAL);
  });

  it("a badge-pipeline failure is swallowed and does NOT drop the FCM push", async () => {
    h.processBadgeMilestone.mockRejectedValueOnce(new Error("badge backend down"));
    await expect(onMealCreated.run(mealEvent(fullMeal))).resolves.toBeUndefined();
    expect(h.sendToCrew).toHaveBeenCalledTimes(1);
  });

  it("a badge award resolving does not alter the push payload", async () => {
    h.processBadgeMilestone.mockResolvedValueOnce("first");
    await onMealCreated.run(mealEvent(fullMeal));
    expect(sentPayload().payload.key).toBe("new_meal_post");
  });
});
