# firestore-tests

Security-rules unit tests for the repo-root `firestore.rules`, run against the
Firebase **Firestore emulator** via [`@firebase/rules-unit-testing`]. These are
the regression lock the AAA+ architecture review (§5.13) asked for — there were
previously zero rules tests, which is why the inverted `accounts` read predicate
and the unbounded meal-`create` aggregates shipped.

## Run

```bash
cd firestore-tests
pnpm install
pnpm test          # boots the firestore emulator, runs vitest, tears it down
```

Requires a JDK on `PATH` (the emulator is a Java process) and `firebase-tools`
(pulled in as a devDependency). The tests load the **actual** `../firestore.rules`,
so a rule regression fails the suite.

## Coverage

- `accounts.test.ts` — public profile readable by authed users; **private PII
  subcollection owner-only** (the P0 fix); unauth denied; no cross-user writes.
- `meals.test.ts` — author + crew-membership gating; **create rejects non-zero
  rating aggregates / pre-populated `ratings` / far-future `publishedAt`** (the
  P1 self-stuffing fix); member-only reads; rating-add allowed; self-vote denied.
- `crews.test.ts` — create-as-sole-member; soft-token read posture; **membership
  cap (max 8) enforced on join**; owner-only rename.

[`@firebase/rules-unit-testing`]: https://firebase.google.com/docs/rules/unit-tests
