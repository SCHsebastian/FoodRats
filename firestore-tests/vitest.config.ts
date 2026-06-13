import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // The tests share one emulator + one project id; clearing Firestore between
    // tests means files must not run concurrently against the same datastore.
    fileParallelism: false,
    testTimeout: 20_000,
    hookTimeout: 30_000,
  },
});
