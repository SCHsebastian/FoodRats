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
 *  - NAMESPACE-CONSTRAINED (security): every doc-provided path (`platePath` and each `plates[].path`)
 *    must live under THIS meal's own `crews/{crewId}/meals/` prefix — derived from `mealDocPath`,
 *    which every caller passes from a TRUSTED source (a Firestore document's own path — never from
 *    doc-provided data). `plates[]` is otherwise client-controlled and unvalidated by
 *    `firestore.rules` (up to 10 arbitrary strings per meal); every caller here acts with the Admin
 *    SDK, which bypasses `storage.rules` entirely. Without this constraint, a meal's author could
 *    stash another crew's (or an account's) real object path in their own meal's `plates[]` and have
 *    it deleted (`onMealDeleted`, `deleteAccount`) or signed into a download URL (`exportMyData`)
 *    just by acting on their own meal. An out-of-namespace `platePath` is treated the same as
 *    missing/empty (falls back to the deterministic primary); an out-of-namespace `plates[].path`
 *    entry is dropped rather than unioned in.
 */
export function mealPhotoPaths(
  mealDocPath: string,
  doc: { platePath?: unknown; plates?: unknown } | undefined,
): string[] {
  const match = /^(crews\/[^/]+\/meals)\/([^/]+)$/.exec(mealDocPath);
  if (match === null) return [];
  const [, mealsPrefix, mealId] = match;
  const ownPrefix = `${mealsPrefix}/`;
  const isOwnPath = (path: string): boolean => path.startsWith(ownPrefix);

  const seen = new Set<string>();
  const out: string[] = [];
  const add = (path: string): void => {
    if (path === "" || seen.has(path)) return;
    seen.add(path);
    out.push(path);
  };

  // `||` (not `??`): a doc that persisted platePath: "" (or a non-string) must fall back too —
  // targeting "" would silently skip the real primary photo. Mirrors `mealStoragePaths`. A
  // foreign-namespace platePath is treated identically to missing/empty rather than honored.
  const rawPlatePath = typeof doc?.platePath === "string" ? doc.platePath : "";
  const platePath = isOwnPath(rawPlatePath) ? rawPlatePath : "";
  add(platePath || `${mealsPrefix}/${mealId}.jpg`);

  const plates = doc?.plates;
  if (Array.isArray(plates)) {
    for (const entry of plates) {
      const path = (entry as { path?: unknown } | null)?.path;
      if (typeof path === "string" && isOwnPath(path)) add(path);
    }
  }

  return out;
}
