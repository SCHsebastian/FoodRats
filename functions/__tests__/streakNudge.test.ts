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

  it("rolls BACK to the previous UTC day when the local zone is already past midnight", () => {
    // 00:30 on the 15th in UTC+2 is 22:30 UTC on the 14th — the UTC day is authoritative.
    const local = DateTime.fromObject(
      { year: 2026, month: 6, day: 15, hour: 0, minute: 30 },
      { zone: "UTC+2" },
    );
    expect(todayKey(local)).toBe("2026-06-14");
  });

  it("rolls FORWARD to the next UTC day for a late-evening western-zone instant", () => {
    // 22:30 UTC-5 on the 14th is 03:30 UTC on the 15th.
    const local = DateTime.fromObject(
      { year: 2026, month: 6, day: 14, hour: 22, minute: 30 },
      { zone: "UTC-5" },
    );
    expect(todayKey(local)).toBe("2026-06-15");
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

  it("returns null for an empty crew (deleted-crew / malformed memberIds)", () => {
    expect(planCrewNudge([], new Set(["ghost"]))).toBeNull();
  });

  it("works at the minimum viable crew size of exactly 2 members", () => {
    const plan = planCrewNudge(["a", "b"], new Set(["a"]));
    expect(plan).not.toBeNull();
    expect(plan!.crewSize).toBe(2);
    expect(plan!.nonPosterIds).toEqual(["b"]);
  });

  it("returns null when ONLY ex-members posted (no in-crew social proof)", () => {
    expect(planCrewNudge(["a", "b"], new Set(["ghost"]))).toBeNull();
  });
});

/** A recording fake of the injectable deps; defaults make everyone a nudge-able non-poster. */
function fakeDeps(overrides: Partial<NudgeDeps> = {}): {
  deps: NudgeDeps;
  sent: Array<{ uid: string; postedCount: number; crewSize: number }>;
  marked: string[];
} {
  const sent: Array<{ uid: string; postedCount: number; crewSize: number }> = [];
  const marked: string[] = [];
  const deps: NudgeDeps = {
    readMemberIds: async () => ["a", "b", "c"],
    readTodayPosters: async () => new Set(["a"]),
    hasToken: async () => true,
    wasNudgedToday: async () => false,
    markNudged: async (uid) => {
      marked.push(uid);
    },
    sendNudge: async (uid, postedCount, crewSize) => {
      sent.push({ uid, postedCount, crewSize });
    },
    ...overrides,
  };
  return { deps, sent, marked };
}

describe("processCrewNudge — fan-out, dedupe, token/permission gating", () => {
  it("sends to every tokened non-poster and records the dedupe marker", async () => {
    const { deps, sent, marked } = fakeDeps();
    const count = await processCrewNudge("crew-1", "2026-06-14", deps);

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
    const count = await processCrewNudge("crew-1", "2026-06-14", deps);

    expect(count).toBe(1);
    expect(sent.map((s) => s.uid)).toEqual(["c"]);
  });

  it("skips a non-poster with NO live token (and burns no dedupe record)", async () => {
    const { deps, sent, marked } = fakeDeps({
      hasToken: async (uid) => uid !== "c",
    });
    const count = await processCrewNudge("crew-1", "2026-06-14", deps);

    expect(count).toBe(1);
    expect(sent.map((s) => s.uid)).toEqual(["b"]);
    // 'c' had no token → not sent AND not marked (so a later token re-registration can still nudge).
    expect(marked).toEqual(["b"]);
  });

  it("sends nothing when nobody posted today", async () => {
    const { deps, sent } = fakeDeps({ readTodayPosters: async () => new Set() });
    const count = await processCrewNudge("crew-1", "2026-06-14", deps);

    expect(count).toBe(0);
    expect(sent).toEqual([]);
  });

  it("sends nothing when everyone already posted", async () => {
    const { deps, sent } = fakeDeps({
      readTodayPosters: async () => new Set(["a", "b", "c"]),
    });
    const count = await processCrewNudge("crew-1", "2026-06-14", deps);

    expect(count).toBe(0);
    expect(sent).toEqual([]);
  });

  it("records the dedupe marker only AFTER the send succeeds (per recipient)", async () => {
    const order: string[] = [];
    const { deps } = fakeDeps({
      sendNudge: async (uid) => {
        order.push(`send:${uid}`);
      },
      markNudged: async (uid) => {
        order.push(`mark:${uid}`);
      },
    });
    await processCrewNudge("crew-1", "2026-06-14", deps);
    expect(order).toEqual(["send:b", "mark:b", "send:c", "mark:c"]);
  });

  it("a failed send does NOT burn the dedupe marker (retry can still nudge)", async () => {
    const { deps, marked } = fakeDeps({
      sendNudge: async (uid) => {
        if (uid === "b") throw new Error("fcm down");
      },
    });
    // Current behavior: the error propagates (the crew scan surfaces it), and 'b' is not marked.
    await expect(processCrewNudge("crew-1", "2026-06-14", deps)).rejects.toThrow("fcm down");
    expect(marked).not.toContain("b");
  });

  it("dedupes ACROSS crews: a shared member nudged for crew-1 is skipped for crew-2", async () => {
    // Shared in-memory `nudges/{uid}/{dayKey}` store, exactly like Firestore backs the real deps.
    const nudged = new Set<string>();
    const sent: string[] = [];
    const depsFor = (memberIds: string[]): NudgeDeps => ({
      readMemberIds: async () => memberIds,
      readTodayPosters: async () => new Set([memberIds[0]]),
      hasToken: async () => true,
      wasNudgedToday: async (uid, dayKey) => nudged.has(`${uid}|${dayKey}`),
      markNudged: async (uid, dayKey) => {
        nudged.add(`${uid}|${dayKey}`);
      },
      sendNudge: async (uid) => {
        sent.push(uid);
      },
    });

    // "b" is a non-poster in BOTH crews.
    const first = await processCrewNudge("crew-1", "2026-06-14", depsFor(["a", "b"]));
    const second = await processCrewNudge("crew-2", "2026-06-14", depsFor(["z", "b"]));

    expect(first).toBe(1);
    expect(second).toBe(0); // b already nudged today via crew-1
    expect(sent).toEqual(["b"]);
  });

  it("the dedupe is per-DAY: the same member is nudgeable again on the next dayKey", async () => {
    const nudged = new Set<string>();
    const sent: string[] = [];
    const deps: NudgeDeps = {
      readMemberIds: async () => ["a", "b"],
      readTodayPosters: async () => new Set(["a"]),
      hasToken: async () => true,
      wasNudgedToday: async (uid, dayKey) => nudged.has(`${uid}|${dayKey}`),
      markNudged: async (uid, dayKey) => {
        nudged.add(`${uid}|${dayKey}`);
      },
      sendNudge: async (uid) => {
        sent.push(uid);
      },
    };

    await processCrewNudge("crew-1", "2026-06-14", deps);
    await processCrewNudge("crew-1", "2026-06-14", deps); // same day → deduped
    await processCrewNudge("crew-1", "2026-06-15", deps); // next day → nudged again

    expect(sent).toEqual(["b", "b"]);
  });
});
