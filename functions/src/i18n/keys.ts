// Payload "key" values. Client PushPayloadMapper matches against these.
export const KEY_NEW_COMMENT = "new_comment";
export const KEY_NEW_MEAL_POST = "new_meal_post";
export const KEY_WEEKLY_DIGEST = "weekly_digest";

// Server-side English fallback for OS lock-screen text.
// The client resolves localized strings via data.key + interpolation params.
export const FALLBACK = {
  newCommentTitle: (commenter: string, dish: string) =>
    `${commenter} commented on your ${dish}`,
  newCommentBody: "Tap to read",
  newMealPostTitle: (author: string) => `${author} posted a meal`,
  newMealPostBody: (dish: string) => `${dish} — tap to view`,
  weeklyDigestTitle: "Your week in food",
  weeklyDigestBody: (parts: string[]) => parts.join(" · "),
} as const;
