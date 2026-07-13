import { describe, expect, it } from "vitest";
import {
  FALLBACK,
  KEY_NEW_COMMENT,
  KEY_NEW_MEAL_POST,
  KEY_SOCIAL_NUDGE,
  KEY_WEEKLY_DIGEST,
  localizeNotification,
  normalizeLang,
} from "../src/i18n/keys";

describe("payload key constants — client PushPayloadMapper contract", () => {
  it("pins the exact key strings the client matches against", () => {
    expect(KEY_NEW_COMMENT).toBe("new_comment");
    expect(KEY_NEW_MEAL_POST).toBe("new_meal_post");
    expect(KEY_WEEKLY_DIGEST).toBe("weekly_digest");
    expect(KEY_SOCIAL_NUDGE).toBe("social_nudge");
  });
});

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
    expect(localizeNotification("en", "", {})).toBeNull();
  });

  it("tolerates missing data params on new_comment (empty-string fallback, no crash)", () => {
    expect(localizeNotification("en", KEY_NEW_COMMENT, {})).toEqual({
      title: " commented on your ",
      body: "Tap to read",
    });
  });

  it("tolerates missing data params on new_meal_post (empty-string fallback)", () => {
    expect(localizeNotification("es", KEY_NEW_MEAL_POST, {})).toEqual({
      title: " publicó una comida",
      body: " — pulsa para ver",
    });
  });

  it("tolerates missing count params on social_nudge (falls back to 0 of 0)", () => {
    expect(localizeNotification("en", KEY_SOCIAL_NUDGE, {})).toEqual({
      title: "Your crew is eating 👀",
      body: "0 of 0 crewmates already posted today — your turn",
    });
  });
});

describe("FALLBACK — English baseline for the OS lock-screen text", () => {
  it("mirrors the EN localization for comment + meal-post + nudge templates", () => {
    expect(FALLBACK.newCommentTitle("Ana", "paella")).toBe(
      localizeNotification("en", KEY_NEW_COMMENT, { commenterName: "Ana", dishName: "paella" })!
        .title,
    );
    expect(FALLBACK.newCommentBody).toBe("Tap to read");
    expect(FALLBACK.newMealPostTitle("Beto")).toBe("Beto posted a meal");
    expect(FALLBACK.newMealPostBody("tacos")).toBe("tacos — tap to view");
    expect(FALLBACK.weeklyDigestTitle).toBe("Your week in food");
    expect(FALLBACK.socialNudgeTitle).toBe("Your crew is eating 👀");
    expect(FALLBACK.socialNudgeBody(3, 5)).toBe(
      localizeNotification("en", KEY_SOCIAL_NUDGE, { postedCount: "3", crewSize: "5" })!.body,
    );
  });

  it("joins weekly-digest award parts with the ' · ' separator", () => {
    expect(FALLBACK.weeklyDigestBody(["Best meal: X (4.5★)", "Best cook: Ana"])).toBe(
      "Best meal: X (4.5★) · Best cook: Ana",
    );
  });

  it("produces an empty digest body for zero award parts", () => {
    expect(FALLBACK.weeklyDigestBody([])).toBe("");
  });
});
