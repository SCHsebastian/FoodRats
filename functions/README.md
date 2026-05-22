# foodrats-functions

Cloud Functions v2 (Node 20, TypeScript) for FoodRats push notifications.

## Build

```bash
pnpm install
pnpm run build
pnpm run lint
```

## Deploy

Requires Blaze plan. From repo root:

```bash
pnpm dlx firebase-tools deploy --only functions --project foodrats-de4ec
```

## Exports

- `onCommentCreated` — Firestore onCreate at `crews/{crewId}/meals/{mealId}/comments/{commentId}`
- `onMealCreated` — Firestore onCreate at `crews/{crewId}/meals/{mealId}`
- `weeklyDigest` — Pub/Sub Scheduler `0 9 * * 1` UTC

## FCM payload contract

All pushes ship `data.key` (matches `PushPayloadMapper` on the client) plus an English fallback in the `notification` block for OS lock-screen display. Client-side localization via `StringKey` is deferred.
