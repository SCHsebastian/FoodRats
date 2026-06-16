// Payload "key" values. Client PushPayloadMapper matches against these.
export const KEY_NEW_COMMENT = "new_comment";
export const KEY_NEW_MEAL_POST = "new_meal_post";
export const KEY_WEEKLY_DIGEST = "weekly_digest";
// Social-proof streak nudge (roadmap §1.1): "N of M crewmates already posted today,
// but you haven't." Sent server-side to non-posters with a live token. The client i18n
// task (`w1-streak-nudges-i18n`) adds the matching PushPayloadMapper branch + NotificationStringKey.
export const KEY_SOCIAL_NUDGE = "social_nudge";

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
  // posted = today's poster count in the crew, size = crew size. Matches the
  // client template "%1$d of %2$d posted" (roadmap §1.1).
  socialNudgeTitle: "Your crew is eating 👀",
  socialNudgeBody: (posted: number, size: number) =>
    `${posted} of ${size} crewmates already posted today — your turn`,
} as const;
