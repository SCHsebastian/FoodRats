import { describe, expect, it } from "vitest";
import { DateTime } from "luxon";
import {
  CrewPage,
  ListCrewPage,
  paginateCrews,
  digestWindow,
  CREWS_PAGE_SIZE,
} from "../src/triggers/weeklyDigest";
import { digestDeepLink } from "../src/fcm/push";

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
