import { onCall, HttpsError, type CallableRequest } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { getAuth } from "firebase-admin/auth";
import { logger } from "firebase-functions/v2";

/**
 * Permanent, server-cascaded account deletion (Apple 5.1.1(v) / GDPR Art. 17).
 *
 * A client-callable that, for the authenticated caller `uid`, erases EVERY trace of the account
 * with the Admin SDK (which bypasses Firestore/Storage rules — the client itself cannot reach
 * other members' comments/ratings or the Auth user record, which is why this runs server-side):
 *   1+2  every meal `uid` authored across every crew + its plate blob (recursiveDelete sweeps
 *        each meal's `comments` + deprecated `ratings` subcollections),
 *   3    `uid`'s comments on OTHER users' meals,
 *   4    `uid`'s ratings/votes on OTHER users' meals (key delete + aggregate recompute in a txn),
 *   5+9  crew memberships — owned crews reassigned-or-deleted per the §6 policy (deleting orphaned
 *        `crewCodes` for any crew removed), non-owned crews drop `uid` from `memberIds`+`members`,
 *   6+7+8 the `accounts/{uid}` doc (+ `private`/`devices` subtrees), the avatar blob, top-level
 *        `devices/{uid}`,
 *   10   the Firebase Auth user record — LAST, only after all data is gone, so a mid-cascade
 *        failure leaves a still-valid session the user can retry from.
 *
 * The handler is a thin wrapper over [deleteAccountCore], which is dependency-injected and
 * unit-tested without the Admin SDK (mirrors `mintPlateUrls`'s `buildSignedUrls` /
 * `weeklyDigest`'s `paginateCrews`). The owned-crew policy is extracted as the pure
 * [planCrewReassignment] so it can be asserted directly.
 *
 * See `docs/specs/2026-06-14-account-deletion-design.md` §5, §6, §11, §14.1.
 */

export interface DeleteAccountRequest {
  confirmation: string;
}

export interface DeleteAccountResponse {
  deleted: true;
}

/** A meal `uid` authored: its doc path + the plate blob path to reclaim (`null` if unresolvable). */
export interface MealRef {
  path: string;
  platePath: string | null;
}

/** A single document path (e.g. a comment on another user's meal). */
export interface DocRef {
  path: string;
}

/** Minimal crew projection the cascade + reassignment policy needs. */
export interface CrewSnap {
  crewId: string;
  ownerId: string;
  memberIds: string[];
  /** accountId → membership; `joinedAt` is epoch-ms, used to pick the longest-tenured new owner. */
  members: Record<string, { joinedAt: number }>;
  /** The invite code doc id under `crewCodes/{code}`, or `null` if the crew has none. */
  code: string | null;
}

/**
 * The owned-crew plan (§6), as a pure function of the crew snapshot and the leaving uid:
 *  - `uid` is NOT the owner → `{ kind: "drop" }`: just remove `uid` from the crew.
 *  - `uid` is the owner and the SOLE member → `{ kind: "delete" }`: hard-delete the crew + its code.
 *  - `uid` is the owner with other members → `{ kind: "reassign", newOwnerId }`: hand ownership to
 *    the earliest-`joinedAt` remaining member, ties broken by `accountId` ascending (deterministic).
 *
 * Throws when ownership must transfer but no deterministic next owner can be resolved (malformed
 * `members` map) — the caller turns that into an `aborted` HttpsError so the account is preserved.
 */
export type CrewPlan =
  | { kind: "drop" }
  | { kind: "delete" }
  | { kind: "reassign"; newOwnerId: string };

export function planCrewReassignment(crew: CrewSnap, uid: string): CrewPlan {
  if (crew.ownerId !== uid) return { kind: "drop" };

  const others = crew.memberIds.filter((m) => m !== uid);
  if (others.length === 0) return { kind: "delete" };

  // Longest-tenured remaining member becomes owner; tie-break on accountId ascending so the
  // pick is deterministic across re-runs. A member missing from the `members` map sorts last
  // (treated as +Infinity joinedAt) but is still eligible if it's the only candidate.
  const ranked = [...others].sort((a, b) => {
    const ja = crew.members[a]?.joinedAt ?? Number.POSITIVE_INFINITY;
    const jb = crew.members[b]?.joinedAt ?? Number.POSITIVE_INFINITY;
    if (ja !== jb) return ja - jb;
    return a < b ? -1 : a > b ? 1 : 0;
  });

  const newOwnerId = ranked[0];
  if (newOwnerId === undefined) {
    throw new Error(`planCrewReassignment: no resolvable next owner for crew ${crew.crewId}`);
  }
  return { kind: "reassign", newOwnerId };
}

/**
 * Injected so the cascade core is testable with fakes (no Admin SDK), like `buildSignedUrls` /
 * `paginateCrews`. Each delete tolerates not-found / double-fire so a re-run over a half-deleted
 * account converges (idempotent — §16).
 */
export interface DeletionDeps {
  /** `"DELETE <displayName>"` derived from `accounts/{uid}` (server-side phrase re-validation). */
  expectedPhrase: (uid: string) => Promise<string>;
  /** collectionGroup meals where authorId == uid. */
  authoredMeals: (uid: string) => Promise<MealRef[]>;
  /** collectionGroup comments where authorId == uid (includes comments on OTHER users' meals). */
  authoredComments: (uid: string) => Promise<DocRef[]>;
  /** Meals NOT authored by uid that carry ratings[uid] (iterated over the crews uid belongs to). */
  votedMeals: (uid: string) => Promise<MealRef[]>;
  /** Crews where uid in memberIds. */
  memberCrews: (uid: string) => Promise<CrewSnap[]>;
  recursiveDelete: (path: string) => Promise<void>;
  /** Storage blob delete; ignoreNotFound. */
  deleteBlob: (path: string) => Promise<void>;
  /** Txn: delete ratings[uid] on `mealPath` + recompute ratingSum/voterCount. */
  removeRating: (mealPath: string, uid: string) => Promise<void>;
  /** Applies the §6 plan (delete crew + code, or reassign owner + drop uid). */
  reassignOrDeleteCrew: (crew: CrewSnap, uid: string) => Promise<void>;
  /** getAuth().deleteUser(uid). */
  deleteAuthUser: (uid: string) => Promise<void>;
}

export async function deleteAccountCore(
  deps: DeletionDeps,
  uid: string | undefined,
  req: DeleteAccountRequest,
): Promise<DeleteAccountResponse> {
  if (!uid) throw new HttpsError("unauthenticated", "Sign-in required.");

  // Re-validate the phrase server-side (defense in depth — the client also gates it).
  const expected = await deps.expectedPhrase(uid);
  if ((req.confirmation ?? "").trim() !== expected) {
    throw new HttpsError("failed-precondition", "Confirmation phrase did not match.");
  }

  // 1+2: authored meals (sweeps comments/ratings) + their plate blobs.
  for (const m of await deps.authoredMeals(uid)) {
    await deps.recursiveDelete(m.path);
    if (m.platePath !== null) await deps.deleteBlob(m.platePath);
  }
  // 3: comments on OTHER users' meals.
  for (const c of await deps.authoredComments(uid)) {
    await deps.recursiveDelete(c.path);
  }
  // 4: ratings on OTHER users' meals (decrement aggregates in a txn).
  for (const m of await deps.votedMeals(uid)) {
    await deps.removeRating(m.path, uid);
  }
  // 5+9: crew memberships — reassign-or-delete per §6 (deletes crewCodes for deleted crews).
  for (const crew of await deps.memberCrews(uid)) {
    try {
      await deps.reassignOrDeleteCrew(crew, uid);
    } catch (e) {
      logger.error(`deleteAccount: crew ${crew.crewId} reassign/delete failed`, e);
      throw new HttpsError("aborted", "Could not reassign crew ownership.");
    }
  }
  // 6+7+8: identity doc (+ private + devices), avatar blob, top-level devices.
  await deps.recursiveDelete(`accounts/${uid}`);
  await deps.deleteBlob(`avatars/${uid}.jpg`);
  await deps.recursiveDelete(`devices/${uid}`);

  // 10: Auth user LAST — only after all data is gone, so a mid-failure leaves a retryable session.
  await deps.deleteAuthUser(uid);

  return { deleted: true };
}

export const deleteAccount = onCall(
  { region: "europe-west3" },
  async (request: CallableRequest<DeleteAccountRequest>): Promise<DeleteAccountResponse> => {
    const db = getFirestore();

    const platePathOf = (path: string, platePath: unknown): string | null => {
      if (typeof platePath === "string" && platePath !== "") return platePath;
      // Fall back to the deterministic `crews/{crewId}/meals/{mealId}.jpg` upload path; if the meal
      // path can't be parsed we return null so no blob is deleted (matches exportMyData — never
      // attempt a delete at a malformed/guessed path).
      const m = /^(crews\/[^/]+)\/meals\/([^/]+)$/.exec(path);
      return m ? `${m[1]}/meals/${m[2]}.jpg` : null;
    };

    const deps: DeletionDeps = {
      expectedPhrase: async (uid) => {
        const name = (
          (await db.doc(`accounts/${uid}`).get()).data()?.displayName as string | undefined
        )?.trim() ?? "";
        return name === "" ? "DELETE" : `DELETE ${name}`;
      },

      authoredMeals: async (uid) =>
        (await db.collectionGroup("meals").where("authorId", "==", uid).get()).docs.map((d) => ({
          path: d.ref.path,
          platePath: platePathOf(d.ref.path, d.data().platePath),
        })),

      authoredComments: async (uid) =>
        (await db.collectionGroup("comments").where("authorId", "==", uid).get()).docs.map((d) => ({
          path: d.ref.path,
        })),

      votedMeals: async (uid) => {
        // ratings[uid] map-key existence isn't directly queryable; iterate the crews uid belongs
        // to and collect meals NOT authored by uid that carry a ratings[uid] entry. Crews are
        // tiny (≤ 8 members) so this bounded per-crew meal scan is fine (§17 risk note).
        const crews = await db.collection("crews").where("memberIds", "array-contains", uid).get();
        const out: MealRef[] = [];
        for (const crew of crews.docs) {
          const meals = await db.collection(`crews/${crew.id}/meals`).get();
          for (const meal of meals.docs) {
            const data = meal.data();
            if ((data.authorId as string | undefined) === uid) continue;
            const ratings = data.ratings as Record<string, unknown> | undefined;
            if (ratings && Object.prototype.hasOwnProperty.call(ratings, uid)) {
              out.push({ path: meal.ref.path, platePath: platePathOf(meal.ref.path, data.platePath) });
            }
          }
        }
        return out;
      },

      memberCrews: async (uid) =>
        (await db.collection("crews").where("memberIds", "array-contains", uid).get()).docs.map(
          (d) => ({
            crewId: d.id,
            ownerId: d.data().ownerId as string,
            memberIds: (d.data().memberIds as string[] | undefined) ?? [],
            members:
              (d.data().members as Record<string, { joinedAt: number }> | undefined) ?? {},
            code: (d.data().code as string | undefined) ?? null,
          }),
        ),

      recursiveDelete: (p) => db.recursiveDelete(db.doc(p)),

      deleteBlob: async (p) => {
        await getStorage().bucket().file(p).delete({ ignoreNotFound: true });
      },

      removeRating: async (mealPath, uid) => {
        const ref = db.doc(mealPath);
        await db.runTransaction(async (txn) => {
          const snap = await txn.get(ref);
          if (!snap.exists) return;
          const data = snap.data() ?? {};
          const ratings = (data.ratings as Record<string, { score?: number }> | undefined) ?? {};
          const mine = ratings[uid];
          if (mine === undefined) return; // already gone — idempotent re-run.
          const score = typeof mine.score === "number" ? mine.score : 0;
          txn.update(ref, {
            [`ratings.${uid}`]: FieldValue.delete(),
            ratingSum: FieldValue.increment(-score),
            voterCount: FieldValue.increment(-1),
          });
        });
      },

      reassignOrDeleteCrew: async (crew, uid) => {
        const plan = planCrewReassignment(crew, uid);
        const crewRef = db.doc(`crews/${crew.crewId}`);
        if (plan.kind === "delete") {
          // Sole-member owner: hard-delete the crew (its meals are reclaimed by #1/#2 since uid
          // authored them) + its invite code. Mirrors CrewRepository.leave's last-member path.
          if (crew.code) await db.doc(`crewCodes/${crew.code}`).delete().catch(() => undefined);
          await db.recursiveDelete(crewRef);
          return;
        }
        if (plan.kind === "reassign") {
          await crewRef.update({
            ownerId: plan.newOwnerId,
            memberIds: FieldValue.arrayRemove(uid),
            [`members.${uid}`]: FieldValue.delete(),
          });
          return;
        }
        // drop: non-owned membership.
        await crewRef.update({
          memberIds: FieldValue.arrayRemove(uid),
          [`members.${uid}`]: FieldValue.delete(),
        });
      },

      deleteAuthUser: async (uid) => {
        // Tolerate an already-deleted user so an idempotent re-run (§14.1) doesn't fail at the
        // very last step — getAuth().deleteUser throws on a missing uid rather than no-op'ing.
        try {
          await getAuth().deleteUser(uid);
        } catch (e) {
          if ((e as { code?: string }).code !== "auth/user-not-found") throw e;
        }
      },
    };

    try {
      return await deleteAccountCore(deps, request.auth?.uid, request.data);
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      logger.error("deleteAccount failed", e);
      throw new HttpsError("internal", "Could not delete account.");
    }
  },
);
