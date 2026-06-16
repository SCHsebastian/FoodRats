import {
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv } from "./helpers";

let env: RulesTestEnvironment;

// The four public, immutable reference catalogs. Read is open (`if true`) so the
// vocabulary can be fetched before auth; clients never write (admin-SDK seeded only).
const CATALOGS = [
  { name: "ingredients", path: "ingredients/tomato", seed: { en: "Tomato", es: "Tomate" } },
  { name: "dishIngredientMap", path: "dishIngredientMap/pizza", seed: { ingredients: ["tomato", "cheese"] } },
  { name: "cuisines", path: "cuisines/italian", seed: { en: "Italian", es: "Italiana" } },
  { name: "dishCuisineMap", path: "dishCuisineMap/pizza", seed: { cuisine: "italian" } },
] as const;

beforeAll(async () => {
  env = await makeEnv();
});
afterAll(async () => {
  await env.cleanup();
});
beforeEach(async () => {
  await env.clearFirestore();
  // Seed each catalog (admin write bypasses rules) so reads have a doc to return.
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const { path, seed } of CATALOGS) {
      await setDoc(doc(db, path), seed);
    }
  });
});

describe("reference catalogs — public read", () => {
  for (const { name, path } of CATALOGS) {
    it(`an UNAUTHENTICATED user can read ${name}`, async () => {
      const db = env.unauthenticatedContext().firestore();
      await assertSucceeds(getDoc(doc(db, path)));
    });

    it(`an authenticated user can read ${name}`, async () => {
      const db = env.authenticatedContext("alice").firestore();
      await assertSucceeds(getDoc(doc(db, path)));
    });
  }
});

describe("reference catalogs — no client writes", () => {
  for (const { name, path } of CATALOGS) {
    it(`an UNAUTHENTICATED user CANNOT write ${name}`, async () => {
      const db = env.unauthenticatedContext().firestore();
      await assertFails(setDoc(doc(db, path), { en: "Forged" }));
    });

    it(`an authenticated user CANNOT write ${name}`, async () => {
      const db = env.authenticatedContext("alice").firestore();
      await assertFails(setDoc(doc(db, path), { en: "Forged" }));
    });
  }
});
