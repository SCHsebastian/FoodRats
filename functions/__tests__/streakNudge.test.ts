import { describe, expect, it } from "vitest";
import { DateTime } from "luxon";
import {
  todayKey,
  planCrewNudge,
  processCrewNudge,
  NudgeDeps,
} from "../src/triggers/streakNudge";

describe("todayKey — UTC day key", () => {
  it("formats the UTC calendar day as yyyy-LL-dd", () => {
    expect(todayKey(DateTime.utc(2026, 6, 14, 13, 30, 0))).toBe("2026-06-14");
  });

  it("uses the UTC day even for a late-evening non-UTC instant", () => {
    // 23:30 in UTC+2 is 21:30 UTC the same day.
    const local = DateTime.fromObject(
      { year: 2026, month: 6, day: 14, hour: 23, minute: 30 },
      { zone: "UTC+2" },
    );
    expect(todayKey(local)).toBe("2026-06-14");
  });
});

describe("planCrewNudge — at-risk selection (roadmap §1.1)", () => {
  it("nudges the members who haven't posted when someone else has", () => {
    const plan = planCrewNudge(["a", "b", "c"], new Set(["a"]));
    expect(plan).not.toBeNull();
    expect(plan!.crewSize).toBe(3);
    expect(plan!.nonPosterIds.sort()).toEqual(["b", "c"]);
    // postedCount is the social-proof number shown in the body.
    expect(plan!.posterIds.size).toBe(1);
  });

  it("returns null when NOBODY posted today (no social proof to show)", () => {
    expect(planCrewNudge(["a", "b", "c"], new Set())).toBeNull();
  });

  it("returns null when EVERYONE already posted (no non-posters)", () => {
    expect(planCrewNudge(["a", "b"], new Set(["a", "b"]))).toBeNull();
  });

  it("returns null for a solo crew (social proof needs another member)", () => {
    expect(planCrewNudge(["a"], new Set(["a"]))).toBeNull();
  });

  it("ignores posters who are no longer crew members", () => {
    // `ghost` posted but left the crew — must not inflate postedCount past the membership.
    const plan = planCrewNudge(["a", "b"], new Set(["a", "ghost"]));
    expect(plan).not.toBeNull();
    expect(plan!.nonPosterIds).toEqual(["b"]);
  });
});

// 13:00 UTC — inside the default profile's learned window for the test timestamps below,
// so the smart-mealtime gate (§1.4) is satisfied and the legacy fan-out/dedupe paths run.
const IN_WINDOW_HOUR = 13;

/** Posting timestamps that cluster at 13:00 UTC, so the learned window contains `IN_WINDOW_HOUR`. */
const NOON_TIMESTAMPS = Array.from({ length: 8 }, () =>
  DateTime.utc(2026, 6, 10, 13, 0, 0).toMillis(),
);

/** A recording fake of the injectable deps; defaults make everyone a nudge-able non-poster. */
function fakeDeps(overrides: Partial<NudgeDeps> = {}): {
  deps: NudgeDeps;
  sent: Array<{ uid: string; postedCount: number; crewSize: number; windowSource: string }>;
  marked: string[];
} {
  const sent: Array<{
    uid: string;
    postedCount: number;
    crewSize: number;
    windowSource: string;
  }> = [];
  const marked: string[] = [];
  const deps: NudgeDeps = {
    readMemberIds: async () => ["a", "b", "c"],
    readTodayPosters: async () => new Set(["a"]),
    readRecentPostingTimestamps: async () => NOON_TIMESTAMPS,
    hasToken: async () => true,
    wasNudgedToday: async () => false,
    markNudged: async (uid) => {
      marked.push(uid);
    },
    sendNudge: async (uid, postedCount, crewSize, windowSource) => {
      sent.push({ uid, postedCount, crewSize, windowSource });
    },
    ...overrides,
  };
  return { deps, sent, marked };
}

describe("processCrewNudge — fan-out, dedupe, token/permission gating", () => {
  it("sends to every tokened non-poster and records the dedupe marker", async () => {
    const { deps, sent, marked } = fakeDeps();
    const count = await processCrewNudge("crew-1", "2026-06-14", IN_WINDOW_HOUR, deps);

    expect(count).toBe(2);
    expect(sent.map((s) => s.uid).sort()).toEqual(["b", "c"]);
    // postedCount/crewSize carried for the client template "%1$d of %2$d posted".
    expect(sent[0]).toMatchObject({ postedCount: 1, crewSize: 3 });
    expect(marked.sort()).toEqual(["b", "c"]);
  });

  it("does NOT double-nudge a member already nudged today (dedupe)", async () => {
    const { deps, sent } = fakeDeps({
      wasNudgedToday: async (uid) => uid === "b",
    });
    const count = await processCrewNudge("crew-1", "2026-06-14", IN_WINDOW_HOUR, deps);

    expect(count).toBe(1);
    expect(sent.map((s) => s.uid)).toEqual(["c"]);
  });

  it("skips a non-poster with NO live token (and burns no dedupe record)", async () => {
    const { deps, sent, marked } = fakeDeps({
      hasToken: async (uid) => uid !== "c",
    });
    const count = await processCrewNudge("crew-1", "2026-06-14", IN_WINDOW_HOUR, deps);

    expect(count).toBe(1);
    expect(sent.map((s) => s.uid)).toEqual(["b"]);
    // 'c' had no token → not sent AND not marked (so a later token re-registration can still nudge).
    expect(marked).toEqual(["b"]);
  });

  it("sends nothing when nobody posted today", async () => {
    const { deps, sent } = fakeDeps({ readTodayPosters: async () => new Set() });
    const count = await processCrewNudge("crew-1", "2026-06-14", IN_WINDOW_HOUR, deps);

    expect(count).toBe(0);
    expect(sent).toEqual([]);
  });

  it("sends nothing when everyone already posted", async () => {
    const { deps, sent } = fakeDeps({
      readTodayPosters: async () => new Set(["a", "b", "c"]),
    });
    const count = await processCrewNudge("crew-1", "2026-06-14", IN_WINDOW_HOUR, deps);

    expect(count).toBe(0);
    expect(sent).toEqual([]);
  });
});

describe("processCrewNudge — smart mealtime gate (roadmap §1.4)", () => {
  it("sends inside the crew's learned window", async () => {
    const { deps, sent } = fakeDeps();
    // NOON_TIMESTAMPS cluster at 13:00 UTC → learned window contains hour 13.
    const count = await processCrewNudge("crew-1", "2026-06-14", 13, deps);

    expect(count).toBe(2);
    expect(sent.map((s) => s.uid).sort()).toEqual(["b", "c"]);
  });

  it("sends NOTHING outside the learned window — and touches no dedupe records", async () => {
    const { deps, sent, marked } = fakeDeps();
    // 03:00 UTC is far from the 13:00 posting cluster → outside the window.
    const count = await processCrewNudge("crew-1", "2026-06-14", 3, deps);

    expect(count).toBe(0);
    expect(sent).toEqual([]);
    // The out-of-window short-circuit must not mark anyone (so the real run later still can).
    expect(marked).toEqual([]);
  });

  it("falls back to the midday window when history is too sparse", async () => {
    // Two samples is below MIN_SAMPLES_FOR_PROFILE → fallback window 11..14, source "fallback".
    const { deps, sent } = fakeDeps({
      readRecentPostingTimestamps: async () => [
        DateTime.utc(2026, 6, 10, 13, 0, 0).toMillis(),
        DateTime.utc(2026, 6, 10, 13, 0, 0).toMillis(),
      ],
    });
    const inFallback = await processCrewNudge("crew-1", "2026-06-14", 12, deps);
    expect(inFallback).toBe(2);
    expect(sent.every((s) => s.windowSource === "fallback")).toBe(true);
  });

  it("does NOT fire the fallback window outside 11..14", async () => {
    const { deps, sent } = fakeDeps({
      readRecentPostingTimestamps: async () => [DateTime.utc(2026, 6, 10, 13, 0, 0).toMillis()],
    });
    // Hour 20 is outside the 11..14 fallback window.
    const count = await processCrewNudge("crew-1", "2026-06-14", 20, deps);
    expect(count).toBe(0);
    expect(sent).toEqual([]);
  });

  it("still dedupes inside the window (one nudge/uid/day even across runs)", async () => {
    const { deps, sent } = fakeDeps({ wasNudgedToday: async (uid) => uid === "b" });
    const count = await processCrewNudge("crew-1", "2026-06-14", 13, deps);

    expect(count).toBe(1);
    expect(sent.map((s) => s.uid)).toEqual(["c"]);
  });

  it("tags learned-window sends with windowSource 'learned' (for A/B, §1.4)", async () => {
    const { deps, sent } = fakeDeps();
    await processCrewNudge("crew-1", "2026-06-14", 13, deps);
    expect(sent.length).toBeGreaterThan(0);
    expect(sent.every((s) => s.windowSource === "learned")).toBe(true);
  });
});
