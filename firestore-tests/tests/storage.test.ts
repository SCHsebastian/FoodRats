import {
  assertFails,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { getBytes, ref, uploadBytes } from "firebase/storage";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

const here = dirname(fileURLToPath(import.meta.url));
/** The actual production rules under test — repo-root `storage.rules`. */
const STORAGE_RULES = readFileSync(resolve(here, "..", "..", "storage.rules"), "utf8");

const PLATE = "crews/c1/meals/c1_alice_2026-06-14_lunch.jpg";
const AVATAR = "avatars/alice.jpg";
const BANNER = "crew_banners/c1/banner.jpg";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-foodrats",
    storage: { rules: STORAGE_RULES },
  });
});

afterAll(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearStorage();
});

/** Plant an object behind disabled rules so the assertions below test READS, not the seed. */
async function seed(path: string) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await uploadBytes(ref(ctx.storage(), path), new Uint8Array([1, 2, 3]), {
      contentType: "image/jpeg",
    });
  });
}

describe("storage.rules — direct reads are denied (#15)", () => {
  it("denies an authenticated member reading a plate object directly", async () => {
    await seed(PLATE);
    const storage = env.authenticatedContext("alice").storage();
    await assertFails(getBytes(ref(storage, PLATE)));
  });

  it("denies an authenticated user reading an avatar object directly", async () => {
    await seed(AVATAR);
    const storage = env.authenticatedContext("alice").storage();
    await assertFails(getBytes(ref(storage, AVATAR)));
  });

  it("denies an unauthenticated read (old token-URL holder)", async () => {
    await seed(PLATE);
    const storage = env.unauthenticatedContext().storage();
    await assertFails(getBytes(ref(storage, PLATE)));
  });

  it("denies an authenticated member reading a crew banner object directly (C9)", async () => {
    await seed(BANNER);
    const storage = env.authenticatedContext("alice").storage();
    await assertFails(getBytes(ref(storage, BANNER)));
  });
});
