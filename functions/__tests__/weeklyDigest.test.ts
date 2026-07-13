import { beforeEach, describe, expect, it, vi } from "vitest";
import { DateTime } from "luxon";

// ---------------------------------------------------------------------------
// Module mocks — processCrewDigest and firestoreCrewPager call getFirestore()
// and sendToCrew directly; substitute controllable fakes (pattern: push.test.ts).
// digestDeepLink stays REAL via the importOriginal spread.
// ---------------------------------------------------------------------------

const h = vi.hoisted(() => ({
  /** collection path (`crews/{id}/meals`) → meal doc datas. */
  mealsByCrew: new Map<string, Array<Record<string, unknown>>>(),
  /** All crew ids, in __name__ order, served by the fake `crews` collection. */
  crewIds: [] as string[],
  /** Recorded where() filters: [collectionPath, field, op, value]. */
  whereCalls: [] as Array<[string, string, string, unknown]>,
  sendToCrew: vi.fn(async () => undefined),
}));

vi.mock("../src/fcm/push", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../src/fcm/push")>()),
  sendToCrew: h.sendToCrew,
}));

vi.mock("firebase-admin/firestore", () => {
  const makeQuery = (path: string) => {
    const q = {
      _limit: Number.POSITIVE_INFINITY,
      _startAfterId: null as string | null,
      where(field: string, op: string, value: unknown) {
        h.whereCalls.push([path, field, op, value]);
        return q;
      },
      orderBy() {
        return q;
      },
      limit(n: number) {
        q._limit = n;
        return q;
      },
      startAfter(cursor: { id: string }) {
        q._startAfterId = cursor.id;
        return q;
      },
      async get() {
        if (path === "crews") {
          const start = q._startAfterId === null ? 0 : h.crewIds.indexOf(q._startAfterId) + 1;
          const ids = h.crewIds.slice(start, start + q._limit);
          return { empty: ids.length === 0, docs: ids.map((id) => ({ id })) };
        }
        const docs = h.mealsByCrew.get(path) ?? [];
        return { empty: docs.length === 0, docs: docs.map((d) => ({ data: () => d })) };
      },
    };
    return q;
  };
  return { getFirestore: () => ({ collection: makeQuery }) };
});

import {
  CrewPage,
  ListCrewPage,
  paginateCrews,
  processCrewDigest,
  digestWindow,
  CREWS_PAGE_SIZE,
} from "../src/triggers/weeklyDigest";
import { firestoreCrewPager } from "../src/triggers/crewScan";
import { digestDeepLink, type PushPayload } from "../src/fcm/push";

beforeEach(() => {
  h.mealsByCrew.clear();
  h.crewIds.length = 0;
  h.whereCalls.length = 0;
  h.sendToCrew.mockClear();
});

/**
 * Build a fake `ListCrewPage` over a fixed list of crew ids that hands them out
 * in pages of `pageSize`, mimicking a cursor-based Firestore scan. The "cursor"
 * is just the offset of the next id encoded into a throwaway object — `paginateCrews`
 * only ever passes it back, never inspects it.
 */
function fakePager(allIds: string[], pageSize: number): { list: ListCrewPage; calls: () => number } {
  let callCount = 0;
  const list: ListCrewPage = async (cursor) => {
    callCount += 1;
    const offset = cursor ? (cursor as unknown as { _offset: number })._offset : 0;
    const ids = allIds.slice(offset, offset + pageSize);
    const nextOffset = offset + ids.length;
    const exhausted = ids.length < pageSize;
    const page: CrewPage = {
      ids,
      // Mirror the real pager: null cursor once a short/last page is returned.
      cursor: exhausted ? null : ({ _offset: nextOffset } as never),
    };
    return page;
  };
  return { list, calls: () => callCount };
}

describe("paginateCrews — bounded crew scan (#19)", () => {
  it("processes every crew exactly once across multiple pages", async () => {
    const allIds = Array.from({ length: 503 }, (_, i) => `crew-${i}`);
    const { list, calls } = fakePager(allIds, 200);

    const seen: string[] = [];
    const processed = await paginateCrews(list, async (crewId) => {
      seen.push(crewId);
    });

    expect(processed).toBe(503);
    // No crew skipped, none duplicated, order preserved.
    expect(seen).toEqual(allIds);
    expect(new Set(seen).size).toBe(503);
    // 503 ids @ 200/page = pages of 200, 200, 103 → 3 reads (the 103 short page ends the loop).
    expect(calls()).toBe(3);
  });

  it("handles an exact-multiple boundary without dropping or duplicating crews", async () => {
    // 400 ids @ 200/page → two full pages, then a final empty read proves termination.
    const allIds = Array.from({ length: 400 }, (_, i) => `crew-${i}`);
    const { list, calls } = fakePager(allIds, 200);

    const seen: string[] = [];
    await paginateCrews(list, async (crewId) => {
      seen.push(crewId);
    });

    expect(seen).toEqual(allIds);
    expect(new Set(seen).size).toBe(400);
    // Two full pages return a non-null cursor, so a third (empty) read is needed to stop.
    expect(calls()).toBe(3);
  });

  it("processes a single sub-page without a second read", async () => {
    const allIds = ["a", "b", "c"];
    const { list, calls } = fakePager(allIds, 200);

    const seen: string[] = [];
    const processed = await paginateCrews(list, async (crewId) => {
      seen.push(crewId);
    });

    expect(processed).toBe(3);
    expect(seen).toEqual(allIds);
    expect(calls()).toBe(1);
  });

  it("does nothing for an empty crews collection", async () => {
    const { list, calls } = fakePager([], 200);
    let count = 0;
    const processed = await paginateCrews(list, async () => {
      count += 1;
    });
    expect(processed).toBe(0);
    expect(count).toBe(0);
    expect(calls()).toBe(1);
  });

  it("uses a sane bounded page size", () => {
    expect(CREWS_PAGE_SIZE).toBeGreaterThanOrEqual(100);
    expect(CREWS_PAGE_SIZE).toBeLessThanOrEqual(500);
  });
});

describe("digestWindow — previous-week ISO range", () => {
  it("spans the prior Monday..Sunday for a mid-week 'now'", () => {
    // Wednesday 2026-06-10 → previous week is Mon 2026-06-01 .. Sun 2026-06-07.
    const now = DateTime.utc(2026, 6, 10, 9, 0, 0);
    expect(digestWindow(now)).toEqual({
      prevStartKey: "2026-06-01",
      prevEndKey: "2026-06-07",
    });
  });

  it("on the scheduled Monday, the window is the week that just ended", () => {
    // The cron fires Monday 09:00 UTC; 2026-06-08 is a Monday → prev week 06-01..06-07.
    const now = DateTime.utc(2026, 6, 8, 9, 0, 0);
    expect(digestWindow(now)).toEqual({
      prevStartKey: "2026-06-01",
      prevEndKey: "2026-06-07",
    });
  });

  it("Monday at exactly 00:00 UTC already belongs to the NEW week (boundary instant)", () => {
    expect(digestWindow(DateTime.utc(2026, 6, 8, 0, 0, 0))).toEqual({
      prevStartKey: "2026-06-01",
      prevEndKey: "2026-06-07",
    });
  });

  it("Sunday 23:59 still reports the week BEFORE the one about to close", () => {
    // 2026-06-14 is a Sunday: its ISO week is 06-08..06-14, so the previous week is 06-01..06-07.
    expect(digestWindow(DateTime.utc(2026, 6, 14, 23, 59, 59))).toEqual({
      prevStartKey: "2026-06-01",
      prevEndKey: "2026-06-07",
    });
  });

  it("spans a year boundary correctly", () => {
    // 2026-01-01 is a Thursday → this ISO week starts Mon 2025-12-29 → prev week is
    // Mon 2025-12-22 .. Sun 2025-12-28, entirely in the previous year.
    expect(digestWindow(DateTime.utc(2026, 1, 1, 9, 0, 0))).toEqual({
      prevStartKey: "2025-12-22",
      prevEndKey: "2025-12-28",
    });
  });
});

describe("digestDeepLink — recap story deep link (roadmap §2.4)", () => {
  it("builds the custom-scheme /digest/{weekStart} link the client parser expects", () => {
    // Must match shared/.../navigation/DeepLink.kt SEGMENT_DIGEST → Route.WeeklyStory.
    expect(digestDeepLink("2026-06-01")).toBe("foodrats://app/digest/2026-06-01");
  });

  it("the digest window's prevStartKey is the link's week segment", () => {
    const window = digestWindow(DateTime.utc(2026, 6, 10, 9, 0, 0));
    expect(digestDeepLink(window.prevStartKey)).toBe("foodrats://app/digest/2026-06-01");
  });
});

// ---------------------------------------------------------------------------
// processCrewDigest — per-crew award computation + push payload
// ---------------------------------------------------------------------------

const WINDOW = { prevStartKey: "2026-06-01", prevEndKey: "2026-06-07" };

function sentPayload(): PushPayload {
  expect(h.sendToCrew).toHaveBeenCalledTimes(1);
  return h.sendToCrew.mock.calls[0][2] as PushPayload;
}

describe("processCrewDigest — award digest fan-out", () => {
  it("sends nothing for a crew with no meals in the window (empty collection)", async () => {
    await processCrewDigest("c1", WINDOW);
    expect(h.sendToCrew).not.toHaveBeenCalled();
  });

  it("queries exactly the previous-week dayKey range", async () => {
    await processCrewDigest("c1", WINDOW);
    expect(h.whereCalls).toEqual([
      ["crews/c1/meals", "dayKey", ">=", "2026-06-01"],
      ["crews/c1/meals", "dayKey", "<=", "2026-06-07"],
    ]);
  });

  it("sends the digest to the WHOLE crew (no excluded uid) with the recap deep link", async () => {
    h.mealsByCrew.set("crews/c1/meals", [
      {
        authorId: "a",
        authorName: "Ana",
        dishName: "paella",
        ratings: { u1: { score: 5 }, u2: { score: 4 } },
        publishedAtEpochMs: 100,
      },
    ]);

    await processCrewDigest("c1", WINDOW);

    expect(h.sendToCrew).toHaveBeenCalledTimes(1);
    const [crewId, exceptUid, payload] = h.sendToCrew.mock.calls[0] as [
      string,
      string | null,
      PushPayload,
    ];
    expect(crewId).toBe("c1");
    expect(exceptUid).toBeNull();
    expect(payload.kind).toBe("WeeklyDigest");
    expect(payload.key).toBe("weekly_digest");
    expect(payload.notificationTitle).toBe("Your week in food");
    expect(payload.data.crewId).toBe("c1");
    expect(payload.data.weekStartIso).toBe("2026-06-01");
    expect(payload.data.link).toBe(digestDeepLink("2026-06-01"));
    expect(payload.data.link).toBe("foodrats://app/digest/2026-06-01");
  });

  it("flattens the awards into the data block and joins the body parts", async () => {
    h.mealsByCrew.set("crews/c1/meals", [
      {
        authorId: "a",
        authorName: "Ana",
        dishName: "paella",
        ratings: { u1: { score: 5 } },
        publishedAtEpochMs: 100,
      },
      {
        authorId: "b",
        authorName: "Beto",
        dishName: "tacos",
        ratings: { u1: { score: 3 }, u2: { score: 3 } },
        publishedAtEpochMs: 200,
      },
    ]);

    await processCrewDigest("c1", WINDOW);
    const payload = sentPayload();

    // bestMeal = paella (5.0); mostVoted = tacos (2 voters); mostProlific tie → Ana (name asc).
    expect(payload.data.bestMealDishName).toBe("paella");
    expect(payload.data.bestMealScore).toBe("5.00");
    expect(payload.data.mostVotedDishName).toBe("tacos");
    expect(payload.data.mostVotedVoterCount).toBe("2");
    expect(payload.data.mostProlificName).toBe("Ana");
    expect(payload.data.mostProlificCount).toBe("1");
    // No cook qualifies (< 3 rated plates) → no bestCook/mostCriticized keys at all.
    expect(payload.data.bestCookName).toBeUndefined();
    expect(payload.data.mostCriticizedName).toBeUndefined();
    // Body: award parts joined with " · ".
    expect(payload.notificationBody).toBe(
      "Best meal: paella (5.0★) · Most prolific: Ana (1) · Most voted: tacos",
    );
  });

  it("recomputes aggregates from the ratings map — a forged ratingSum cannot win an award", async () => {
    h.mealsByCrew.set("crews/c1/meals", [
      {
        authorId: "attacker",
        authorName: "Mallory",
        dishName: "forged dish",
        ratings: { attacker: { score: 1 } },
        ratingSum: 9999, // client-writable, must be ignored
        voterCount: 42, // client-writable, must be ignored
        publishedAtEpochMs: 100,
      },
      {
        authorId: "honest",
        authorName: "Ana",
        dishName: "paella",
        ratings: { u1: { score: 5 }, u2: { score: 5 } },
        publishedAtEpochMs: 200,
      },
    ]);

    await processCrewDigest("c1", WINDOW);
    const payload = sentPayload();

    expect(payload.data.bestMealDishName).toBe("paella");
    expect(payload.data.bestMealScore).toBe("5.00");
    expect(payload.data.mostVotedDishName).toBe("paella");
    expect(payload.data.mostVotedVoterCount).toBe("2"); // real voters, not the forged 42
  });

  it("tolerates meal docs with missing fields (defaults, no crash) and still sends", async () => {
    // A malformed doc: no authorName/dishName/publishedAtEpochMs/ratings at all.
    h.mealsByCrew.set("crews/c1/meals", [{ authorId: "a" }, {}]);

    await processCrewDigest("c1", WINDOW);
    const payload = sentPayload();

    // Unrated week → only the mostProlific award, with the "Someone" fallback name.
    expect(payload.data.mostProlificName).toBe("Someone");
    expect(payload.data.bestMealDishName).toBeUndefined();
    expect(payload.notificationBody).toContain("Most prolific: Someone");
  });
});

// ---------------------------------------------------------------------------
// firestoreCrewPager — real cursor derivation over the mocked crews collection
// ---------------------------------------------------------------------------

describe("firestoreCrewPager — cursor-based crews scan", () => {
  it("a short page ends the scan (null cursor)", async () => {
    h.crewIds.push("a", "b", "c");
    const page = await firestoreCrewPager()(null);
    expect(page.ids).toEqual(["a", "b", "c"]);
    expect(page.cursor).toBeNull();
  });

  it("a FULL page returns the last doc as cursor and the next page resumes after it", async () => {
    const all = Array.from({ length: CREWS_PAGE_SIZE + 3 }, (_, i) =>
      `crew-${String(i).padStart(3, "0")}`,
    );
    h.crewIds.push(...all);
    const pager = firestoreCrewPager();

    const first = await pager(null);
    expect(first.ids).toHaveLength(CREWS_PAGE_SIZE);
    expect(first.cursor).not.toBeNull();

    const second = await pager(first.cursor);
    expect(second.ids).toEqual(all.slice(CREWS_PAGE_SIZE));
    expect(second.cursor).toBeNull();
  });

  it("walks every crew exactly once end-to-end through paginateCrews", async () => {
    const all = Array.from({ length: CREWS_PAGE_SIZE * 2 + 1 }, (_, i) =>
      `crew-${String(i).padStart(4, "0")}`,
    );
    h.crewIds.push(...all);

    const seen: string[] = [];
    const processed = await paginateCrews(firestoreCrewPager(), async (crewId) => {
      seen.push(crewId);
    });
    expect(processed).toBe(all.length);
    expect(seen).toEqual(all);
  });

  it("an empty crews collection yields one empty page and no processing", async () => {
    const page = await firestoreCrewPager()(null);
    expect(page.ids).toEqual([]);
    expect(page.cursor).toBeNull();
  });
});

describe("paginateCrews — failure propagation (current behavior)", () => {
  it("a processCrew failure aborts the scan and propagates (remaining crews are skipped)", async () => {
    // Documented current behavior: there is no per-crew isolation — one bad crew halts the run.
    // The scheduler retries the whole scan; per-crew work must therefore stay idempotent.
    const { list } = fakePagerFor(["a", "b", "c"]);
    const seen: string[] = [];
    await expect(
      paginateCrews(list, async (crewId) => {
        seen.push(crewId);
        if (crewId === "b") throw new Error("crew b exploded");
      }),
    ).rejects.toThrow("crew b exploded");
    expect(seen).toEqual(["a", "b"]);
  });
});

/** Minimal single-page pager for the failure-propagation test. */
function fakePagerFor(ids: string[]): { list: ListCrewPage } {
  const list: ListCrewPage = async () => ({ ids, cursor: null });
  return { list };
}
