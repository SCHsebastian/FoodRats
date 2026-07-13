/**
 * The Storage object paths a meal doc's PHOTOS occupy: every well-formed `plates[].path` entry (the
 * client's ordered multi-photo list — up to `MealPublishPolicy.MAX_PHOTOS_PER_MEAL`, index 0 always
 * the primary) UNIONED with the legacy single-photo fallback (`platePath`, or the deterministic
 * `crews/{crewId}/meals/{mealId}.jpg` upload path for docs that predate `platePath` itself).
 *
 * Shared by every place that must touch every photo a meal owns: reclaiming Storage objects when the
 * meal doc is deleted (`onMealDeleted`), cascading a deleted account's blobs (`deleteAccount`), and
 * signing every photo URL into a GDPR export (`exportMyData`). A legacy doc (no `plates` field — every
 * meal published before the multi-photo feature) resolves to exactly one path, the primary, so the
 * single-photo behavior those callers already had is unchanged.
 *
 * Contract:
 *  - De-duplicated: a multi-photo doc's `plates[0].path` is normally identical to `platePath` (both
 *    describe the same primary object); duplicates collapse to one entry.
 *  - `mealDocPath` must be a well-formed `crews/{crewId}/meals/{mealId}` Firestore doc path; anything
 *    else returns `[]` rather than guessing a path (mirrors the callers' pre-existing "never
 *    delete/sign a guessed path" posture for a malformed doc path).
 *  - A `plates` entry with a missing/non-string `path` — or a non-array `plates` field entirely — is
 *    skipped defensively. Firestore data isn't statically typed, so a malformed doc must not throw.
 */
export function mealPhotoPaths(
  mealDocPath: string,
  doc: { platePath?: unknown; plates?: unknown } | undefined,
): string[] {
  const match = /^(crews\/[^/]+\/meals)\/([^/]+)$/.exec(mealDocPath);
  if (match === null) return [];
  const [, mealsPrefix, mealId] = match;

  const seen = new Set<string>();
  const out: string[] = [];
  const add = (path: string): void => {
    if (path === "" || seen.has(path)) return;
    seen.add(path);
    out.push(path);
  };

  // `||` (not `??`): a doc that persisted platePath: "" (or a non-string) must fall back too —
  // targeting "" would silently skip the real primary photo. Mirrors `mealStoragePaths`.
  const platePath = typeof doc?.platePath === "string" ? doc.platePath : "";
  add(platePath || `${mealsPrefix}/${mealId}.jpg`);

  const plates = doc?.plates;
  if (Array.isArray(plates)) {
    for (const entry of plates) {
      const path = (entry as { path?: unknown } | null)?.path;
      if (typeof path === "string") add(path);
    }
  }

  return out;
}
