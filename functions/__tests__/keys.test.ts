import { describe, expect, it } from "vitest";
import {
  KEY_NEW_COMMENT,
  KEY_NEW_MEAL_POST,
  KEY_SOCIAL_NUDGE,
  KEY_WEEKLY_DIGEST,
  localizeNotification,
  normalizeLang,
} from "../src/i18n/keys";

describe("normalizeLang", () => {
  it("maps Spanish variants to es", () => {
    expect(normalizeLang("es")).toBe("es");
    expect(normalizeLang("es-ES")).toBe("es");
    expect(normalizeLang("ES")).toBe("es");
  });

  it("maps English, unknown, and absent tags to en", () => {
    expect(normalizeLang("en")).toBe("en");
    expect(normalizeLang("en-US")).toBe("en");
    expect(normalizeLang("fr")).toBe("en");
    expect(normalizeLang(undefined)).toBe("en");
    expect(normalizeLang(null)).toBe("en");
    expect(normalizeLang("")).toBe("en");
  });
});

describe("localizeNotification — matches the client Compose-resource strings", () => {
  it("localizes new_comment (en + es)", () => {
    const data = { commenterName: "Ana", dishName: "paella" };
    expect(localizeNotification("en", KEY_NEW_COMMENT, data)).toEqual({
      title: "Ana commented on your paella",
      body: "Tap to read",
    });
    expect(localizeNotification("es", KEY_NEW_COMMENT, data)).toEqual({
      title: "Ana comentó tu paella",
      body: "Pulsa para leer",
    });
  });

  it("localizes new_meal_post (en + es)", () => {
    const data = { authorName: "Beto", dishName: "tacos" };
    expect(localizeNotification("en", KEY_NEW_MEAL_POST, data)).toEqual({
      title: "Beto posted a meal",
      body: "tacos — tap to view",
    });
    expect(localizeNotification("es", KEY_NEW_MEAL_POST, data)).toEqual({
      title: "Beto publicó una comida",
      body: "tacos — pulsa para ver",
    });
  });

  it("localizes weekly_digest to the static client body (en + es)", () => {
    expect(localizeNotification("en", KEY_WEEKLY_DIGEST, {})).toEqual({
      title: "Your week in food",
      body: "Tap to see the recap",
    });
    expect(localizeNotification("es", KEY_WEEKLY_DIGEST, {})).toEqual({
      title: "Tu semana en comida",
      body: "Pulsa para ver el resumen",
    });
  });

  it("localizes social_nudge with the count params (en + es)", () => {
    const data = { postedCount: "3", crewSize: "5" };
    expect(localizeNotification("en", KEY_SOCIAL_NUDGE, data)).toEqual({
      title: "Your crew is eating 👀",
      body: "3 of 5 crewmates already posted today — your turn",
    });
    expect(localizeNotification("es", KEY_SOCIAL_NUDGE, data)).toEqual({
      title: "Tu crew está comiendo 👀",
      body: "3 de 5 compañeros ya han publicado hoy — te toca",
    });
  });

  it("returns null for an unknown key (caller falls back to English default)", () => {
    expect(localizeNotification("es", "totally_unknown", {})).toBeNull();
  });
});
