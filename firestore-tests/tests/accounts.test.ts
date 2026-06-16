import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv } from "./helpers";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await makeEnv();
});
afterAll(async () => {
  await env.cleanup();
});
beforeEach(async () => {
  await env.clearFirestore();
  // Seed a public profile doc for alice (display fields only — never PII).
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "accounts/alice"), {
      id: "alice",
      handle: "alice",
      displayName: "Alice",
      avatarUrl: "https://x/a.jpg",
    });
    await setDoc(doc(db, "accounts/alice/private/contact"), { email: "alice@example.com" });
  });
});

describe("accounts/{uid} — public profile", () => {
  it("owner can read own profile", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(getDoc(doc(db, "accounts/alice")));
  });

  it("a crew-mate (any authed user) can read another's public profile (display identity)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertSucceeds(getDoc(doc(db, "accounts/alice")));
  });

  it("an UNAUTHENTICATED user cannot read any profile", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "accounts/alice")));
  });

  it("a non-owner cannot write another user's profile", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, "accounts/alice"), { displayName: "Hacked" }));
  });

  it("owner can write own profile", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, "accounts/alice"), { displayName: "Alice B." }));
  });
});

describe("accounts/{uid}/private — owner-only PII (the P0 fix)", () => {
  it("owner can read own private doc", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(getDoc(doc(db, "accounts/alice/private/contact")));
  });

  it("a different authed user CANNOT read another's private doc (no email leak)", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(getDoc(doc(db, "accounts/alice/private/contact")));
  });

  it("a different authed user cannot write another's private doc", async () => {
    const db = env.authenticatedContext("bob").firestore();
    await assertFails(setDoc(doc(db, "accounts/alice/private/contact"), { email: "evil@x" }));
  });
});
