/**
 * Badge milestone logic — assigns accounts/{uid}.badgeId to the highest earned
 * tier from a lifetime authored-meal count.
 *
 * Tiers (lowest to highest priority):
 *   "first"   – 1 canonical publish
 *   "ten"     – 10 canonical publishes
 *   "fifty"   – 50 canonical publishes
 *   "hundred" – 100 canonical publishes
 *
 * A single publish fans out to multiple per-crew meal docs (multi-crew feature).
 * The canonical publish key is `{uid}_{dayKey}_{slot}` (mealId without the leading
 * crewId_ prefix), so each canonical publish is counted exactly once regardless of
 * how many crew copies were written.
 *
 * The counter and badgeId are always written by the Admin SDK (bypasses rules).
 * Client writes to badgeId are denied by the Firestore security rules.
 */

export interface BadgeDeps {
  /**
   * Atomically consume one canonical publish: check the dedup marker at
   * accounts/{uid}/publishedMealKeys/{canonicalKey} and — only when absent — write the
   * marker AND increment the lifetime count in the SAME Firestore transaction.
   *
   * Returns null when the key was already counted; otherwise the count before/after.
   *
   * MUST be one atomic unit. Marker and count may never diverge: a crash aborts the
   * whole transaction (the re-delivery re-runs it from scratch), and a committed marker
   * implies its increment committed with it. Two separate writes are NOT an
   * implementation option — marker-first lost the count on a crash in between (the
   * retry short-circuited on the marker), increment-first double-counts on retry.
   */
  countCanonicalPublish: (
    uid: string,
    canonicalKey: string,
  ) => Promise<{ prevCount: number; newCount: number } | null>;
  /**
   * Write the badgeId (and only the badgeId) to the account doc.
   * Called only when the badge changes — no-op when already at the right tier.
   */
  writeBadge: (uid: string, badgeId: string) => Promise<void>;
}

/** Ordered tiers: highest threshold first so we can take the first match. */
export const BADGE_TIERS: Array<{ id: string; threshold: number }> = [
  { id: "hundred", threshold: 100 },
  { id: "fifty",   threshold: 50  },
  { id: "ten",     threshold: 10  },
  { id: "first",   threshold: 1   },
];

/**
 * Given a new meal count, return the badge id for the highest earned tier,
 * or null if the count is below all thresholds.
 *
 * "Highest earned" = the tier whose threshold is ≤ count (highest threshold wins).
 * No downgrade: the caller is responsible for only writing when the badge improves.
 */
export function badgeIdForCount(count: number): string | null {
  for (const tier of BADGE_TIERS) {
    if (count >= tier.threshold) return tier.id;
  }
  return null;
}

/**
 * Derive the badge id that the previous count earned (before this publish),
 * so we know whether the new count crosses a new threshold.
 */
function badgeIdForPrevCount(count: number): string | null {
  return badgeIdForCount(count);
}

/**
 * Process one `onMealCreated` event for the badge pipeline.
 *
 * @param uid            Author UID from the meal doc.
 * @param crewId         The crew this meal doc lives under (first path segment).
 * @param mealId         Full Firestore meal doc id: `{crewId}_{uid}_{dayKey}_{slot}`.
 * @param deps           Injectable deps (real vs test doubles).
 * @returns              The badge id assigned (or null if no change / below threshold).
 */
export async function processBadgeMilestone(
  uid: string,
  crewId: string,
  mealId: string,
  deps: BadgeDeps,
): Promise<string | null> {
  // Strip the crewId prefix to obtain the canonical publish key shared across
  // all per-crew copies of the same publish.  crewId is always a Firestore
  // auto-id (20-char alphanumeric) followed by a single "_".
  const canonicalKey = mealId.startsWith(crewId + "_")
    ? mealId.slice(crewId.length + 1)
    : mealId; // fallback: keep full id (should not happen with well-formed docs)

  // Dedup + count in ONE transaction: only the first crew copy of a canonical publish
  // gets a non-null result, and the marker can never exist without its increment.
  const counted = await deps.countCanonicalPublish(uid, canonicalKey);
  if (counted === null) return null;

  const prevBadge = badgeIdForPrevCount(counted.prevCount);
  const newBadge = badgeIdForCount(counted.newCount);

  // No change or still below the first threshold.
  if (newBadge === null || newBadge === prevBadge) return null;

  await deps.writeBadge(uid, newBadge);
  return newBadge;
}
