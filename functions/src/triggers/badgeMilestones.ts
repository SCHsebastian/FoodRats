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
   * Read the current lifetime canonical-meal count for the account.
   * Returns 0 when the field does not exist yet.
   */
  readMealCount: (uid: string) => Promise<number>;
  /**
   * Check whether this canonical publish key has already been counted.
   * Uses a dedup marker at accounts/{uid}/publishedMealKeys/{canonicalKey}.
   */
  isAlreadyCounted: (uid: string, canonicalKey: string) => Promise<boolean>;
  /** Mark this canonical key as counted (write the dedup marker). */
  markCounted: (uid: string, canonicalKey: string) => Promise<void>;
  /** Atomically increment the meal count by 1. Returns the NEW count. */
  incrementMealCount: (uid: string) => Promise<number>;
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

  // Dedup: only count the first crew copy of a given canonical publish.
  if (await deps.isAlreadyCounted(uid, canonicalKey)) return null;
  await deps.markCounted(uid, canonicalKey);

  const prevCount = await deps.readMealCount(uid);
  const newCount = await deps.incrementMealCount(uid);

  const prevBadge = badgeIdForPrevCount(prevCount);
  const newBadge = badgeIdForCount(newCount);

  // No change or still below the first threshold.
  if (newBadge === null || newBadge === prevBadge) return null;

  await deps.writeBadge(uid, newBadge);
  return newBadge;
}
