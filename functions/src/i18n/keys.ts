// Payload "key" values. Client PushPayloadMapper matches against these.
export const KEY_NEW_COMMENT = "new_comment";
export const KEY_NEW_MEAL_POST = "new_meal_post";
export const KEY_WEEKLY_DIGEST = "weekly_digest";
// Social-proof streak nudge (roadmap §1.1): "N of M crewmates already posted today,
// but you haven't." Sent server-side to non-posters with a live token. The client i18n
// task (`w1-streak-nudges-i18n`) adds the matching PushPayloadMapper branch + NotificationStringKey.
export const KEY_SOCIAL_NUDGE = "social_nudge";
// Comment @-mention push (roadmap: comment @-mentions). Sent to mentioned crew members on
// comment CREATE only (never on edit — see PLAN.md). Client PushPayloadMapper matches this key.
export const KEY_COMMENT_MENTION = "comment_mention";

/**
 * Per-language notification text. THE source of truth for the OS-rendered `notification` block —
 * a backgrounded push is shown by the OS directly from this block, which the client never gets to
 * localize. Each string mirrors the client Compose-resource copy
 * (`feature/notifications/.../values{,-es}/strings.xml`) EXACTLY so foreground (client-localized)
 * and background (server-localized) notifications read identically. Keep the two in sync.
 */
interface NotificationStrings {
  newCommentTitle: (commenter: string, dish: string) => string;
  newCommentBody: string;
  newMealPostTitle: (author: string) => string;
  newMealPostBody: (dish: string) => string;
  weeklyDigestTitle: string;
  weeklyDigestBody: string;
  socialNudgeTitle: string;
  socialNudgeBody: (posted: number, size: number) => string;
  commentMentionTitle: (mentioner: string, dish: string) => string;
  commentMentionBody: string;
  /**
   * Localized interpolant fallbacks for missing/unresolvable data params. Applied HERE (inside
   * `localizeNotification`, i.e. per language group) — never in the triggers — so an English
   * fallback word can't be baked into `data` before language grouping and leak into another
   * language's OS-rendered text (the "Someone comentó tu …" bug, 2026-07-15).
   */
  fallbackPerson: string; // new_comment / comment_mention commenter
  fallbackCrewmate: string; // new_meal_post author
  fallbackYourDish: string; // completes "your ___" / "tu ___" (new_comment title)
  fallbackADish: string; // stands alone: "a meal" / "una comida" (meal post, mention)
}

const EN: NotificationStrings = {
  newCommentTitle: (commenter, dish) => `${commenter} commented on your ${dish}`,
  newCommentBody: "Tap to read",
  newMealPostTitle: (author) => `${author} posted a meal`,
  newMealPostBody: (dish) => `${dish} — tap to view`,
  weeklyDigestTitle: "Your week in food",
  weeklyDigestBody: "Tap to see the recap",
  socialNudgeTitle: "Your crew is eating 👀",
  socialNudgeBody: (posted, size) =>
    `${posted} of ${size} crewmates already posted today — your turn`,
  commentMentionTitle: (mentioner, dish) => `${mentioner} mentioned you on ${dish}`,
  commentMentionBody: "Tap to read",
  fallbackPerson: "Someone",
  fallbackCrewmate: "A crewmate",
  fallbackYourDish: "meal",
  fallbackADish: "a meal",
};

const ES: NotificationStrings = {
  newCommentTitle: (commenter, dish) => `${commenter} comentó tu ${dish}`,
  newCommentBody: "Pulsa para leer",
  newMealPostTitle: (author) => `${author} publicó una comida`,
  newMealPostBody: (dish) => `${dish} — pulsa para ver`,
  weeklyDigestTitle: "Tu semana en comida",
  weeklyDigestBody: "Pulsa para ver el resumen",
  socialNudgeTitle: "Tu crew está comiendo 👀",
  socialNudgeBody: (posted, size) =>
    `${posted} de ${size} compañeros ya han publicado hoy — te toca`,
  commentMentionTitle: (mentioner, dish) => `${mentioner} te mencionó en ${dish}`,
  commentMentionBody: "Pulsa para leer",
  fallbackPerson: "Alguien",
  fallbackCrewmate: "Un compañero",
  fallbackYourDish: "comida",
  fallbackADish: "una comida",
};

const TABLES: Record<"en" | "es", NotificationStrings> = { en: EN, es: ES };

/** Normalize a stored device language tag ("es", "es-ES", "EN", undefined) to a supported table. */
export function normalizeLang(tag: string | undefined | null): "en" | "es" {
  return (tag ?? "").toLowerCase().startsWith("es") ? "es" : "en";
}

/**
 * Build the localized `{title, body}` for a push from its `key` + `data` params, in [lang].
 * Returns null for an unknown key so the caller can fall back to the payload's default English text.
 * The `data` shape mirrors what the triggers attach (and what the client PushPayloadMapper reads).
 */
export function localizeNotification(
  lang: "en" | "es",
  key: string,
  data: Record<string, string>,
): { title: string; body: string } | null {
  const s = TABLES[lang];
  switch (key) {
    case KEY_NEW_COMMENT:
      return {
        title: s.newCommentTitle(
          data.commenterName || s.fallbackPerson,
          data.dishName || s.fallbackYourDish,
        ),
        body: s.newCommentBody,
      };
    case KEY_NEW_MEAL_POST:
      return {
        title: s.newMealPostTitle(data.authorName || s.fallbackCrewmate),
        body: s.newMealPostBody(data.dishName || s.fallbackADish),
      };
    case KEY_WEEKLY_DIGEST:
      // The OS body localizes to the static client copy (the rich award parts stay English-only and
      // are reachable in-app via the deep link) — consistent with the in-app banner's body.
      return { title: s.weeklyDigestTitle, body: s.weeklyDigestBody };
    case KEY_SOCIAL_NUDGE: {
      const posted = Number(data.postedCount ?? "0");
      const size = Number(data.crewSize ?? "0");
      return { title: s.socialNudgeTitle, body: s.socialNudgeBody(posted, size) };
    }
    case KEY_COMMENT_MENTION:
      return {
        title: s.commentMentionTitle(
          data.commenterName || s.fallbackPerson,
          data.dishName || s.fallbackADish,
        ),
        body: s.commentMentionBody,
      };
    default:
      return null;
  }
}

// Server-side English fallback for OS lock-screen text, used by triggers to set the payload's
// DEFAULT notificationTitle/Body. `sendToTokens` overrides these per-recipient via
// `localizeNotification` when the device has a stored languageTag; this stays the English baseline.
export const FALLBACK = {
  newCommentTitle: EN.newCommentTitle,
  newCommentBody: EN.newCommentBody,
  newMealPostTitle: EN.newMealPostTitle,
  newMealPostBody: EN.newMealPostBody,
  weeklyDigestTitle: EN.weeklyDigestTitle,
  weeklyDigestBody: (parts: string[]) => parts.join(" · "),
  socialNudgeTitle: EN.socialNudgeTitle,
  socialNudgeBody: EN.socialNudgeBody,
  commentMentionTitle: EN.commentMentionTitle,
  commentMentionBody: EN.commentMentionBody,
} as const;
