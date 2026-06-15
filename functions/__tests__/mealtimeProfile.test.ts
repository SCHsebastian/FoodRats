import { describe, expect, it } from "vitest";
import { DateTime } from "luxon";
import {
  buildHourHistogram,
  computeMealtimeProfile,
  deriveActiveWindow,
  isHourInWindow,
  utcHourOf,
  FALLBACK_WINDOW,
  MIN_SAMPLES_FOR_PROFILE,
} from "../src/triggers/mealtimeProfile";

/** Epoch ms for a given UTC hour on a fixed day — keeps the histogram-by-hour intent obvious. */
function at(hour: number): number {
  return DateTime.utc(2026, 6, 10, hour, 0, 0).toMillis();
}

/** `n` timestamps at the same UTC hour. */
function many(hour: number, n: number): number[] {
  return Array.from({ length: n }, () => at(hour));
}

describe("utcHourOf — epoch → UTC hour", () => {
  it("returns the UTC hour regardless of the host machine's local zone", () => {
    expect(utcHourOf(at(13))).toBe(13);
    expect(utcHourOf(at(0))).toBe(0);
    expect(utcHourOf(at(23))).toBe(23);
  });
});

describe("buildHourHistogram — 24-slot UTC histogram", () => {
  it("counts samples into their UTC hour", () => {
    const h = buildHourHistogram([at(8), at(8), at(12), at(20)]);
    expect(h).toHaveLength(24);
    expect(h[8]).toBe(2);
    expect(h[12]).toBe(1);
    expect(h[20]).toBe(1);
    expect(h.reduce((a, b) => a + b, 0)).toBe(4);
  });

  it("ignores malformed timestamps (non-finite / negative)", () => {
    const h = buildHourHistogram([at(9), NaN, Infinity, -1, -1000]);
    expect(h.reduce((a, b) => a + b, 0)).toBe(1);
    expect(h[9]).toBe(1);
  });

  it("is an all-zero histogram for no samples", () => {
    expect(buildHourHistogram([]).every((v) => v === 0)).toBe(true);
  });
});

describe("deriveActiveWindow — distribution → window (roadmap §1.4)", () => {
  it("falls back to the midday window when below the sample threshold", () => {
    const sparse = buildHourHistogram(many(13, MIN_SAMPLES_FOR_PROFILE - 1));
    expect(deriveActiveWindow(sparse)).toEqual(FALLBACK_WINDOW);
  });

  it("learns a tight window around a single dominant posting hour", () => {
    const h = buildHourHistogram(many(13, 10));
    const w = deriveActiveWindow(h);
    expect(w.source).toBe("learned");
    // All mass is at hour 13 → the window is just hour 13 (coverage met immediately).
    expect(w.startHour).toBe(13);
    expect(w.endHour).toBe(13);
  });

  it("widens the window to cover a crew that eats across two slots", () => {
    // Lunch-heavy at 13 plus a real dinner cluster at 19.
    const h = buildHourHistogram([...many(13, 6), ...many(19, 6)]);
    const w = deriveActiveWindow(h);
    expect(w.source).toBe("learned");
    // The band must contain both eating hours to hit 75% coverage.
    expect(isHourInWindow(13, w)).toBe(true);
    expect(isHourInWindow(19, w)).toBe(true);
  });

  it("grows toward the heavier neighbour first", () => {
    // Peak at 13; the hour AFTER (14) is heavier than the hour BEFORE (12).
    const h = buildHourHistogram([...many(13, 6), ...many(14, 4), ...many(12, 1)]);
    const w = deriveActiveWindow(h);
    // 13 (6) + 14 (4) = 10 of 11 ≥ 75% → window is [13..14], 12 not needed.
    expect(w.startHour).toBe(13);
    expect(w.endHour).toBe(14);
  });

  it("produces a window that wraps past midnight for a late-night crew", () => {
    // Mass straddles midnight: 23, 0, 1.
    const h = buildHourHistogram([...many(23, 4), ...many(0, 4), ...many(1, 4)]);
    const w = deriveActiveWindow(h);
    expect(w.source).toBe("learned");
    // A wrapped window has startHour > endHour and contains the midnight hours.
    expect(isHourInWindow(23, w)).toBe(true);
    expect(isHourInWindow(0, w)).toBe(true);
    expect(isHourInWindow(1, w)).toBe(true);
    expect(isHourInWindow(12, w)).toBe(false);
  });
});

describe("isHourInWindow — inclusive, midnight-wrapping membership", () => {
  it("treats both edges of a normal window as inside", () => {
    const w = { startHour: 11, endHour: 14, source: "learned" as const };
    expect(isHourInWindow(11, w)).toBe(true);
    expect(isHourInWindow(14, w)).toBe(true);
    expect(isHourInWindow(13, w)).toBe(true);
    expect(isHourInWindow(10, w)).toBe(false);
    expect(isHourInWindow(15, w)).toBe(false);
  });

  it("handles a window that wraps past midnight", () => {
    const w = { startHour: 22, endHour: 2, source: "learned" as const };
    expect(isHourInWindow(22, w)).toBe(true);
    expect(isHourInWindow(23, w)).toBe(true);
    expect(isHourInWindow(0, w)).toBe(true);
    expect(isHourInWindow(2, w)).toBe(true);
    expect(isHourInWindow(3, w)).toBe(false);
    expect(isHourInWindow(12, w)).toBe(false);
  });
});

describe("computeMealtimeProfile — end-to-end assembly", () => {
  it("returns the histogram, sample count, and derived window together", () => {
    const profile = computeMealtimeProfile([...many(13, 8), at(19)]);
    expect(profile.sampleCount).toBe(9);
    expect(profile.histogram[13]).toBe(8);
    expect(profile.window.source).toBe("learned");
    expect(isHourInWindow(13, profile.window)).toBe(true);
  });

  it("uses the fallback window for an empty crew history", () => {
    const profile = computeMealtimeProfile([]);
    expect(profile.sampleCount).toBe(0);
    expect(profile.window).toEqual(FALLBACK_WINDOW);
  });
});
