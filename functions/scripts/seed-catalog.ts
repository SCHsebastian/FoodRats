/**
 * Seeds the Firestore `ingredients` and `dishIngredientMap` collections from the
 * static JSON in `functions/seed/`. Admin-only tool — run locally with
 * GOOGLE_APPLICATION_CREDENTIALS pointing at a service-account key for the
 * target project.
 *
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/sa.json pnpm seed:catalog
 *
 * Idempotent: docs are keyed by slug, so re-running overwrites in place.
 */
import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import ingredients from "../seed/ingredients.json";
import dishMap from "../seed/dish-ingredient-map.json";

const PROJECT_ID = "foodrats-de4ec";

type Ingredient = {
  slug: string;
  names: Record<string, string>;
  category: string;
  iconKey: string | null;
  aliases: string[];
};

type DishMap = {
  dishSlug: string;
  modelLabel: string;
  defaultIngredients: string[];
};

/** Fail fast before touching Firestore if the seed data is internally inconsistent. */
function assertIntegrity(catalog: Ingredient[], dishes: DishMap[]): void {
  const slugSet = new Set(catalog.map((i) => i.slug));
  if (slugSet.size !== catalog.length) {
    throw new Error("ingredients.json contains duplicate slugs");
  }
  const missing: string[] = [];
  for (const d of dishes) {
    for (const ing of d.defaultIngredients) {
      if (!slugSet.has(ing)) missing.push(`${d.dishSlug} -> ${ing}`);
    }
  }
  if (missing.length > 0) {
    throw new Error(`dish map references unknown ingredient slugs:\n${missing.join("\n")}`);
  }
}

async function main(): Promise<void> {
  const catalog = ingredients as Ingredient[];
  const dishes = dishMap as DishMap[];
  assertIntegrity(catalog, dishes);

  initializeApp({ credential: applicationDefault(), projectId: PROJECT_ID });
  const db = getFirestore();
  const now = new Date();

  const ingredientBatch = db.batch();
  for (const ing of catalog) {
    ingredientBatch.set(
      db.collection("ingredients").doc(ing.slug),
      { ...ing, updatedAt: now },
      { merge: true },
    );
  }
  await ingredientBatch.commit();

  const dishBatch = db.batch();
  for (const d of dishes) {
    dishBatch.set(
      db.collection("dishIngredientMap").doc(d.dishSlug),
      { ...d, updatedAt: now },
      { merge: true },
    );
  }
  await dishBatch.commit();

  console.log(`Wrote ${catalog.length} ingredients + ${dishes.length} dish maps to ${PROJECT_ID}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
