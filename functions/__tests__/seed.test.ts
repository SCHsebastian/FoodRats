import { describe, expect, it } from "vitest";
import ingredients from "../seed/ingredients.json";
import dishMap from "../seed/dish-ingredient-map.json";

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

const ALLOWED_CATEGORIES = new Set([
  "Vegetable", "Fruit", "Meat", "Fish", "Dairy", "Grain",
  "Legume", "Sauce", "Spice", "Sweet", "Beverage", "Other",
]);

// The 101 Food-101 class names — the dish map must cover exactly these.
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

const catalog = ingredients as Ingredient[];
const dishes = dishMap as DishMap[];
const slugs = catalog.map((i) => i.slug);
const slugSet = new Set(slugs);

describe("ingredient catalog seed", () => {
  it("has no duplicate slugs", () => {
    expect(slugSet.size).toBe(slugs.length);
  });

  it("every slug is lowercase snake_case ASCII <= 64 chars", () => {
    for (const i of catalog) {
      expect(i.slug, `slug "${i.slug}"`).toMatch(/^[a-z0-9_]{1,64}$/);
    }
  });

  it("every ingredient has en + es names", () => {
    for (const i of catalog) {
      expect(i.names.en, `${i.slug}.names.en`).toBeTruthy();
      expect(i.names.es, `${i.slug}.names.es`).toBeTruthy();
    }
  });

  it("every category is one of the 12 allowed values", () => {
    for (const i of catalog) {
      expect(ALLOWED_CATEGORIES.has(i.category), `${i.slug} category "${i.category}"`).toBe(true);
    }
  });

  it("every iconKey equals its slug", () => {
    for (const i of catalog) {
      expect(i.iconKey, `${i.slug}.iconKey`).toBe(i.slug);
    }
  });
});

describe("dish-ingredient map seed", () => {
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

  it("every dish has between 3 and 8 default ingredients", () => {
    for (const d of dishes) {
      expect(d.defaultIngredients.length, `${d.dishSlug} ingredient count`).toBeGreaterThanOrEqual(3);
      expect(d.defaultIngredients.length, `${d.dishSlug} ingredient count`).toBeLessThanOrEqual(8);
    }
  });

  it("every default ingredient resolves to a catalog slug", () => {
    for (const d of dishes) {
      for (const ing of d.defaultIngredients) {
        expect(slugSet.has(ing), `${d.dishSlug} references missing slug "${ing}"`).toBe(true);
      }
    }
  });
});
