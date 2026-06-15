import { describe, expect, it } from "vitest";
import cuisines from "../seed/cuisines.json";
import dishCuisineMap from "../seed/dish-cuisine-map.json";

type Cuisine = {
  slug: string;
  names: Record<string, string>;
  iconKey: string | null;
};

type DishCuisine = {
  dishSlug: string;
  modelLabel: string;
  cuisine: string;
};

// The 101 Food-101 class names — the dish-cuisine map must cover exactly these.
const FOOD101 = [
  "apple_pie", "baby_back_ribs", "baklava", "beef_carpaccio", "beef_tartare",
  "beet_salad", "beignets", "bibimbap", "bread_pudding", "breakfast_burrito",
  "bruschetta", "caesar_salad", "cannoli", "caprese_salad", "carrot_cake",
  "ceviche", "cheesecake", "cheese_plate", "chicken_curry", "chicken_quesadilla",
  "chicken_wings", "chocolate_cake", "chocolate_mousse", "churros", "clam_chowder",
  "club_sandwich", "crab_cakes", "creme_brulee", "croque_madame", "cup_cakes",
  "deviled_eggs", "donuts", "dumplings", "edamame", "eggs_benedict",
  "escargots", "falafel", "filet_mignon", "fish_and_chips", "foie_gras",
  "french_fries", "french_onion_soup", "french_toast", "fried_calamari", "fried_rice",
  "frozen_yogurt", "garlic_bread", "gnocchi", "greek_salad", "grilled_cheese_sandwich",
  "grilled_salmon", "guacamole", "gyoza", "hamburger", "hot_and_sour_soup",
  "hot_dog", "huevos_rancheros", "hummus", "ice_cream", "lasagna",
  "lobster_bisque", "lobster_roll_sandwich", "macaroni_and_cheese", "macarons", "miso_soup",
  "mussels", "nachos", "omelette", "onion_rings", "oysters",
  "pad_thai", "paella", "pancakes", "panna_cotta", "peking_duck",
  "pho", "pizza", "pork_chop", "poutine", "prime_rib",
  "pulled_pork_sandwich", "ramen", "ravioli", "red_velvet_cake", "risotto",
  "samosa", "sashimi", "scallops", "seaweed_salad", "shrimp_and_grits",
  "spaghetti_bolognese", "spaghetti_carbonara", "spring_rolls", "steak", "strawberry_shortcake",
  "sushi", "tacos", "takoyaki", "tiramisu", "tuna_tartare", "waffles",
];

const catalog = cuisines as Cuisine[];
const dishes = dishCuisineMap as DishCuisine[];
const slugs = catalog.map((c) => c.slug);
const slugSet = new Set(slugs);

describe("cuisine catalog seed", () => {
  it("has no duplicate slugs", () => {
    expect(slugSet.size).toBe(slugs.length);
  });

  it("every slug is lowercase snake_case ASCII <= 64 chars", () => {
    for (const c of catalog) {
      expect(c.slug, `slug "${c.slug}"`).toMatch(/^[a-z0-9_]{1,64}$/);
    }
  });

  it("every cuisine has en + es names", () => {
    for (const c of catalog) {
      expect(c.names.en, `${c.slug}.names.en`).toBeTruthy();
      expect(c.names.es, `${c.slug}.names.es`).toBeTruthy();
    }
  });

  it("every iconKey equals its slug", () => {
    for (const c of catalog) {
      expect(c.iconKey, `${c.slug}.iconKey`).toBe(c.slug);
    }
  });
});

describe("dish-cuisine map seed", () => {
  it("covers exactly the 101 Food-101 classes", () => {
    const dishSlugs = dishes.map((d) => d.dishSlug);
    expect(new Set(dishSlugs).size).toBe(dishSlugs.length); // no duplicates
    expect(new Set(dishSlugs)).toEqual(new Set(FOOD101));
    expect(dishSlugs.length).toBe(101);
  });

  it("every dishSlug equals its modelLabel", () => {
    for (const d of dishes) {
      expect(d.modelLabel, `${d.dishSlug}.modelLabel`).toBe(d.dishSlug);
    }
  });

  it("every dish maps to exactly one cuisine present in the catalog", () => {
    for (const d of dishes) {
      expect(d.cuisine, `${d.dishSlug}.cuisine missing`).toBeTruthy();
      expect(slugSet.has(d.cuisine), `${d.dishSlug} references missing cuisine "${d.cuisine}"`).toBe(true);
    }
  });

  it("has no orphan cuisines — every catalog cuisine is used by some dish", () => {
    const used = new Set(dishes.map((d) => d.cuisine));
    const orphans = slugs.filter((s) => !used.has(s));
    expect(orphans, `unused cuisines: ${orphans.join(", ")}`).toEqual([]);
  });
});
