import { getFirestore, DocumentSnapshot } from "firebase-admin/firestore";

/**
 * Bounded, paginated scan over the `crews` collection — shared by every scheduled function that
 * must touch every crew exactly once (`weeklyDigest`, `streakNudge`, …).
 *
 * Page size for the bounded crews scan (#19, P2). A single `crews.get()` would load EVERY crew doc
 * into memory at once — un-boundable as the crew count grows. Paging keeps peak memory to one page
 * of crew ids plus one crew's per-crew working set, independent of total crew count. 200 keeps each
 * round-trip small while keeping the number of round-trips low.
 */
export const CREWS_PAGE_SIZE = 200;

/** A single bounded page of crew ids plus the cursor needed to fetch the next one. */
export interface CrewPage {
  ids: string[];
  /** The last doc in this page; pass back as `cursor` to fetch the next one. `null` when exhausted. */
  cursor: DocumentSnapshot | null;
}

/** Fetches one bounded page of crew ids starting after `cursor` (or from the start when null). */
export type ListCrewPage = (cursor: DocumentSnapshot | null) => Promise<CrewPage>;

/** Per-crew work invoked once for every crew. */
export type ProcessCrew = (crewId: string) => Promise<void>;

/**
 * Walks the crews collection one page at a time and invokes `processCrew` for every crew exactly
 * once. Memory stays bounded to a single page regardless of total crew count. Returns the number of
 * crews processed. Fully injectable (`listCrewPage` / `processCrew`) so the loop is unit-testable
 * without mocking the firebase-admin SDK.
 */
export async function paginateCrews(
  listCrewPage: ListCrewPage,
  processCrew: ProcessCrew,
): Promise<number> {
  let cursor: DocumentSnapshot | null = null;
  let processed = 0;
  // eslint-disable-next-line no-constant-condition
  while (true) {
    const page = await listCrewPage(cursor);
    for (const crewId of page.ids) {
      await processCrew(crewId);
      processed += 1;
    }
    // No cursor (or an empty short page) means the collection is exhausted.
    if (!page.cursor || page.ids.length === 0) break;
    cursor = page.cursor;
  }
  return processed;
}

/** Firestore-backed crew pager: orders by document id and pages with a cursor. */
export function firestoreCrewPager(): ListCrewPage {
  return async (cursor) => {
    let query = getFirestore()
      .collection("crews")
      .orderBy("__name__")
      .limit(CREWS_PAGE_SIZE);
    if (cursor) query = query.startAfter(cursor);
    const snap = await query.get();
    return {
      ids: snap.docs.map((d) => d.id),
      cursor: snap.docs.length < CREWS_PAGE_SIZE ? null : snap.docs[snap.docs.length - 1],
    };
  };
}
