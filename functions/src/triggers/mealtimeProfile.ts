import { DateTime } from "luxon";

/**
 * Smart mealtime — the SERVER side of roadmap §1.4.
 *
 * Goal: stop firing the streak/social nudge (§1.1) at an arbitrary UTC hour and instead only fire
 * it during the hours a crew *actually* eats, learned from its own posting history. The hourly
 * scheduler (`0 * * * *`) keeps running, but each hourly run only nudges a crew whose learned
 * "active window" contains the current hour — so the net effect is one well-timed nudge/day per
 * recipient (still backed by the per-uid daily dedupe), not a 24x blast.
 *
 * ───────────────────────────────── TIMEZONE DECISION ─────────────────────────────────
 * A meal's only persisted temporal signal in Firestore is `publishedAtEpochMs` (a UTC epoch) plus
 * the `dayKey` (`YYYY-MM-DD`). There is NO per-user or per-crew timezone stored anywhere
 * (`AccountDto` / `CrewDto` carry none), and the device-local `publishedHour`/`publishedMinute`
 * the client shows are derived at read time and never written back. We therefore CANNOT compute a
 * true local mealtime on the server.
 *
 * Rather than invent a timezone, we take approach (a) from the brief: derive the active window from
 * the observed posting-hour distribution **in UTC**. For a closed crew of 3–8 friends — almost
 * always in one region — the dominant UTC posting hours track when they actually eat (their fixed
 * local offset is baked into every timestamp), so the window is "smart" relative to real behavior.
 * Capturing a real timezone (to nudge at a true local hour, and to support multi-region crews) is a
 * flagged client follow-up; see the handoff. Until then this is the honest, data-grounded best.
 * ──────────────────────────────────────────────────────────────────────────────────────
 */

/** Hours below this many distinct posting samples are too sparse to learn a window from. */
export const MIN_SAMPLES_FOR_PROFILE = 5;

/**
 * Fraction of a crew's posting mass an active window must cover. We grow the window outward from the
 * peak hour until it contains at least this share of all samples, so a crew that eats across (say)
 * an early and a late slot gets a window wide enough to catch both, while a tightly-clustered crew
 * gets a narrow one.
 */
export const WINDOW_COVERAGE = 0.75;

/**
 * The fixed fallback window used when a crew has too little history to learn from
 * (`< MIN_SAMPLES_FOR_PROFILE` samples). 11:00–14:00 UTC ≈ midday across western Europe, matching
 * the client's old fixed 14:00 `DailyInactivityWorker` intent of "around lunch".
 */
export const FALLBACK_WINDOW: ActiveWindow = { startHour: 11, endHour: 14, source: "fallback" };

/** A learned (or fallback) range of UTC hours [startHour..endHour] during which to nudge. */
export interface ActiveWindow {
  /** First UTC hour (0–23) of the window, inclusive. */
  startHour: number;
  /** Last UTC hour (0–23) of the window, inclusive. May wrap past midnight (endHour < startHour). */
  endHour: number;
  /** How the window was derived — `"learned"` from history, `"fallback"` when too sparse. */
  source: "learned" | "fallback";
}

/** A crew's mealtime profile: the per-UTC-hour posting histogram plus the derived active window. */
export interface MealtimeProfile {
  /** 24-slot histogram, index = UTC hour, value = number of posting samples in that hour. */
  histogram: number[];
  /** Total number of samples that went into the histogram. */
  sampleCount: number;
  /** The active window the nudge gate uses. */
  window: ActiveWindow;
}

/** Maps a UTC epoch-millisecond timestamp to its UTC hour (0–23). Skips non-finite inputs upstream. */
export function utcHourOf(epochMs: number): number {
  return DateTime.fromMillis(epochMs, { zone: "utc" }).hour;
}

/**
 * Build the 24-slot UTC-hour histogram from a list of `publishedAtEpochMs` timestamps. Invalid
 * entries (non-finite, negative) are ignored so a malformed meal doc can't corrupt the profile.
 */
export function buildHourHistogram(timestampsMs: number[]): number[] {
  const histogram = new Array<number>(24).fill(0);
  for (const ms of timestampsMs) {
    if (!Number.isFinite(ms) || ms < 0) continue;
    histogram[utcHourOf(ms)] += 1;
  }
  return histogram;
}

/**
 * Derive the active window from a UTC-hour histogram.
 *
 * Algorithm (pure, deterministic):
 *  1. If total samples < `MIN_SAMPLES_FOR_PROFILE`, return `FALLBACK_WINDOW` — too little to learn.
 *  2. Find the peak hour (most samples; ties broken by the earlier hour for determinism).
 *  3. Grow a contiguous window outward from the peak — at each step adding whichever adjacent hour
 *     (treating the 24h clock as a ring) holds more samples — until the window covers at least
 *     `WINDOW_COVERAGE` of all samples. This yields the smallest contiguous band around the peak
 *     that captures the crew's dominant eating hours, and naturally wraps across midnight.
 */
export function deriveActiveWindow(histogram: number[]): ActiveWindow {
  const total = histogram.reduce((a, b) => a + b, 0);
  if (total < MIN_SAMPLES_FOR_PROFILE) return { ...FALLBACK_WINDOW };

  // 2. Peak hour — earliest hour holding the max count (deterministic tie-break).
  let peak = 0;
  for (let h = 1; h < 24; h++) {
    if (histogram[h] > histogram[peak]) peak = h;
  }

  // 3. Grow outward from the peak around the 24h ring until coverage is met.
  let lo = peak; // left edge (may decrease, wrapping below 0)
  let hi = peak; // right edge (may increase, wrapping past 23)
  let covered = histogram[peak];
  const target = total * WINDOW_COVERAGE;
  // At most 23 expansions are possible (the band can grow to cover all 24 hours).
  for (let step = 0; step < 23 && covered < target; step++) {
    const nextLo = (lo - 1 + 24) % 24;
    const nextHi = (hi + 1) % 24;
    // Prefer the heavier neighbour; on a tie, expand left (earlier) for determinism.
    if (histogram[nextLo] >= histogram[nextHi]) {
      lo = nextLo;
      covered += histogram[nextLo];
    } else {
      hi = nextHi;
      covered += histogram[nextHi];
    }
  }

  return { startHour: lo, endHour: hi, source: "learned" };
}

/** Assemble a full profile (histogram + sample count + window) from raw posting timestamps. */
export function computeMealtimeProfile(timestampsMs: number[]): MealtimeProfile {
  const histogram = buildHourHistogram(timestampsMs);
  const sampleCount = histogram.reduce((a, b) => a + b, 0);
  return { histogram, sampleCount, window: deriveActiveWindow(histogram) };
}

/**
 * True iff `hour` (0–23 UTC) falls inside `window`, inclusive of both edges. Correctly handles a
 * window that wraps past midnight (e.g. 22..2 contains 23, 0, 1).
 */
export function isHourInWindow(hour: number, window: ActiveWindow): boolean {
  const { startHour, endHour } = window;
  if (startHour <= endHour) return hour >= startHour && hour <= endHour;
  // Wrapped window: [start..23] ∪ [0..end].
  return hour >= startHour || hour <= endHour;
}
