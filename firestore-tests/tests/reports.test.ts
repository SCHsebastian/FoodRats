import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, deleteDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv, mealId } from "./helpers";

// Emulator rule tests for the `reports` moderation queue (UGC compliance §4, security review F3–F6).
// The doc id is `${reporterUid}|${targetKey}`; the create rule whitelists the field set, pins each
// present id back into `targetKey`, gates on crew membership + target existence, and blocks account
// self-reports. read/update/delete are server-only (Admin SDK) — always denied to clients.

let env: RulesTestEnvironment;

const CREW = "c1";
const DAY = "2026-06-13";
const MEAL = mealId(CREW, "alice", DAY, "lunch"); // c1_alice_2026-06-13_lunch — embeds `_`
const COMMENT = "cmt-1"; // embeds `-`
const MEAL_KEY = `meal|${CREW}|${MEAL}`;
const COMMENT_KEY = `comment|${CREW}|${MEAL}|${COMMENT}`;
const ACCOUNT_KEY = `account|alice`;

const docId = (reporter: string, targetKey: string) => `${reporter}|${targetKey}`;

/** A well-formed meal report exactly as the client (`ReportDto`) writes it. bob reports alice's meal. */
const mealReport = (reporter: string, overrides: Record<string, unknown> = {}) => ({
  reporterId: reporter,
  targetType: "meal",
  crewId: CREW,
  mealId: MEAL,
  commentId: null,
  accountId: null,
  targetKey: MEAL_KEY,
  reason: "spam",
  status: "open",
  createdAtEpochMs: Date.now(),
  ...overrides,
});

const commentReport = (reporter: string, overrides: Record<string, unknown> = {}) => ({
  reporterId: reporter,
  targetType: "comment",
  crewId: CREW,
  mealId: MEAL,
  commentId: COMMENT,
  accountId: null,
  targetKey: COMMENT_KEY,
  reason: "harassment",
  status: "open",
  createdAtEpochMs: Date.now(),
  ...overrides,
});

const accountReport = (reporter: string, overrides: Record<string, unknown> = {}) => ({
  reporterId: reporter,
  targetType: "account",
  crewId: null,
  mealId: null,
  commentId: null,
  accountId: "alice",
  targetKey: ACCOUNT_KEY,
  reason: "hate",
  status: "open",
  createdAtEpochMs: Date.now(),
  ...overrides,
});

beforeAll(async () => {
  env = await makeEnv();
});
afterAll(async () => {
  await env.cleanup();
});
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    // Crew c1: alice (owner) + bob are members; charlie is NOT a member.
    await setDoc(doc(db, "crews/c1"), {
      ownerId: "alice",
      name: "Crew One",
      memberIds: ["alice", "bob"],
      members: { alice: {}, bob: {} },
    });
    await setDoc(doc(db, `crews/${CREW}/meals/${MEAL}`), {
      id: MEAL,
      authorId: "alice",
      crewId: CREW,
      dayKey: DAY,
      slot: "lunch",
    });
    await setDoc(doc(db, `crews/${CREW}/meals/${MEAL}/comments/${COMMENT}`), {
      authorId: "alice",
      text: "hi",
      createdAtEpochMs: Date.now(),
    });
    await setDoc(doc(db, "accounts/alice"), { displayName: "Alice" });
  });
});

describe("reports create — well-formed member reports are ALLOWED", () => {
  it("a crew member can report a meal in their crew", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(setDoc(doc(db, `reports/${docId("bob", MEAL_KEY)}`), mealReport("bob")));
  });

  it("a crew member can report a comment in their crew", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      setDoc(doc(db, `reports/${docId("bob", COMMENT_KEY)}`), commentReport("bob")),
    );
  });

  it("any authed user can report another account (no crew scope)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(
      setDoc(doc(db, `reports/${docId("bob", ACCOUNT_KEY)}`), accountReport("bob")),
    );
  });
});

describe("reports create — F5 membership gate", () => {
  it("a NON-member cannot report a meal in a crew they're not in", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(
      setDoc(doc(db, `reports/${docId("charlie", MEAL_KEY)}`), mealReport("charlie")),
    );
  });

  it("a NON-member cannot report a comment in a crew they're not in", async () => {
    const db = env.authenticatedContext("charlie").firestore();
    await assertFails(
      setDoc(doc(db, `reports/${docId("charlie", COMMENT_KEY)}`), commentReport("charlie")),
    );
  });
});

describe("reports create — F4 field whitelist + target pinning", () => {
  it("DENIES a forged extra field", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(
        doc(db, `reports/${docId("bob", MEAL_KEY)}`),
        mealReport("bob", { evil: "smuggled" }),
      ),
    );
  });

  it("DENIES a status other than 'open'", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(db, `reports/${docId("bob", MEAL_KEY)}`), mealReport("bob", { status: "actioned" })),
    );
  });

  it("DENIES a crewId/mealId that does not reconstruct targetKey (aim at a different victim)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    // targetKey still points at the real meal, but mealId field is swapped to another id.
    await assertFails(
      setDoc(
        doc(db, `reports/${docId("bob", MEAL_KEY)}`),
        mealReport("bob", { mealId: "c1_bob_2026-06-13_dinner" }),
      ),
    );
  });

  it("DENIES an unknown reason", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(db, `reports/${docId("bob", MEAL_KEY)}`), mealReport("bob", { reason: "bogus" })),
    );
  });
});

describe("reports create — F3 doc-id binding", () => {
  it("DENIES a doc id that is not reporterUid|targetKey", async () => {
    const db = env.authenticatedContext("bob").firestore();
    // Right payload, wrong doc id (uses ':' instead of '|').
    await assertFails(setDoc(doc(db, `reports/bob:${MEAL_KEY}`), mealReport("bob")));
  });

  it("DENIES a doc id whose reporter prefix is not the caller", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, `reports/${docId("charlie", MEAL_KEY)}`), mealReport("bob")));
  });
});

describe("reports create — target existence", () => {
  it("DENIES a report against a meal that does not exist", async () => {
    const db = env.authenticatedContext("bob").firestore();
    const ghost = mealId(CREW, "alice", DAY, "dinner");
    const ghostKey = `meal|${CREW}|${ghost}`;
    await assertFails(
      setDoc(
        doc(db, `reports/${docId("bob", ghostKey)}`),
        mealReport("bob", { mealId: ghost, targetKey: ghostKey }),
      ),
    );
  });
});

describe("reports create — F6 account self-report", () => {
  it("DENIES reporting your own account", async () => {
    // alice reports alice
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(db, `reports/${docId("alice", ACCOUNT_KEY)}`), accountReport("alice")),
    );
  });
});

describe("reports read/update/delete — server-only", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `reports/${docId("bob", MEAL_KEY)}`), mealReport("bob"));
    });
  });

  it("a client cannot READ a report", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(getDoc(doc(db, `reports/${docId("bob", MEAL_KEY)}`)));
  });

  it("a client cannot UPDATE a report", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(db, `reports/${docId("bob", MEAL_KEY)}`), { status: "actioned" }));
  });

  it("a client cannot DELETE a report", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(deleteDoc(doc(db, `reports/${docId("bob", MEAL_KEY)}`)));
  });
});

// ---------------------------------------------------------------------------
// New: target existence gates — comment and account branches
// ---------------------------------------------------------------------------

describe("reports create — target existence (comment + account)", () => {
  it("DENIES a report against a comment that does not exist", async () => {
    const db = env.authenticatedContext("bob").firestore();
    const ghostComment = "cmt-ghost";
    const ghostKey = `comment|${CREW}|${MEAL}|${ghostComment}`;
    await assertFails(
      setDoc(
        doc(db, `reports/${docId("bob", ghostKey)}`),
        commentReport("bob", { commentId: ghostComment, targetKey: ghostKey }),
      ),
    );
  });

  it("DENIES a report against an account that does not exist", async () => {
    const db = env.authenticatedContext("bob").firestore();
    const ghostAccount = "ghost-user";
    const ghostKey = `account|${ghostAccount}`;
    await assertFails(
      setDoc(
        doc(db, `reports/${docId("bob", ghostKey)}`),
        accountReport("bob", { accountId: ghostAccount, targetKey: ghostKey }),
      ),
    );
  });
});

// ---------------------------------------------------------------------------
// New: timestamp window — stale and future createdAtEpochMs
// ---------------------------------------------------------------------------

describe("reports create — timestamp window", () => {
  it("DENIES a report with a stale createdAtEpochMs (> 60s in the past)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    const stale = Date.now() - 90_000; // 90s ago
    await assertFails(
      setDoc(
        doc(db, `reports/${docId("bob", MEAL_KEY)}`),
        mealReport("bob", { createdAtEpochMs: stale }),
      ),
    );
  });

  it("DENIES a report with a future createdAtEpochMs (> 60s ahead)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    const future = Date.now() + 120_000; // 2 min in the future
    await assertFails(
      setDoc(
        doc(db, `reports/${docId("bob", MEAL_KEY)}`),
        mealReport("bob", { createdAtEpochMs: future }),
      ),
    );
  });
});

// ---------------------------------------------------------------------------
// New: moderationActions — client read + write denied
// ---------------------------------------------------------------------------

describe("moderationActions — server-only (client access denied)", () => {
  const ACTION_ID = "action_meal|c1|c1_alice_2026-06-13_lunch";

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `moderationActions/${ACTION_ID}`), {
        targetKey: MEAL_KEY,
        targetType: "meal",
        action: "removed_meal",
        reporters: ["bob"],
        distinctCount: 1,
        threshold: 3,
        reasonHistogram: { spam: 1 },
        crewId: CREW,
        authorId: "alice",
        createdAtEpochMs: Date.now(),
      });
    });
  });

  it("authenticated client CANNOT read a moderationActions doc", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(getDoc(doc(db, `moderationActions/${ACTION_ID}`)));
  });

  it("authenticated client CANNOT write a moderationActions doc", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(db, `moderationActions/action_meal|c1|forged`), {
        targetKey: "meal|c1|forged",
        targetType: "meal",
        action: "removed_meal",
        reporters: ["bob"],
        distinctCount: 1,
        threshold: 1,
        reasonHistogram: {},
        crewId: "c1",
        authorId: "alice",
        createdAtEpochMs: Date.now(),
      }),
    );
  });

  it("unauthenticated client CANNOT read a moderationActions doc", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, `moderationActions/${ACTION_ID}`)));
  });
});
