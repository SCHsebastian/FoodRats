# 011 · w1-streak-nudges-function

**Status:** done

**Summary (≤6 lines):**
- `streakNudge` scheduled Cloud Function (social-proof nudge per §1.1): nudges non-posters in crews with ≥1 poster today & ≥2 members; hourly `0 * * * *` UTC, europe-west3. Extracted shared `crewScan` (weeklyDigest re-points to it).
- Files: `functions/src/triggers/streakNudge.ts` (new), `functions/src/triggers/crewScan.ts` (new), `weeklyDigest.ts`, `fcm/push.ts` (+`SocialNudge`), `i18n/keys.ts` (+`KEY_SOCIAL_NUDGE`), `index.ts`, `__tests__/streakNudge.test.ts` (new 12), `firestore.rules` (+`nudges` server-only deny).
- Decisions: today=UTC day; per-uid daily dedupe at `accounts/{uid}/nudges/{dayKey}`; skip tokenless; re-check posters at send; message = notification+data hybrid, `key="social_nudge"`, `postedCount`/`crewSize`, NO deep link (opens Feed).
- Blockers: none. `streak_nudge_sent` server analytics not emitted (no server analytics sink — `logger.info` for now).

**Verify (quoted):**
```
pnpm --dir functions build → tsc (no diagnostics, exit 0)
pnpm --dir functions test → Test Files 7 passed (7) / Tests 61 passed (61) (streakNudge 12/12, weeklyDigest 7/7 post-refactor)
```

**i18n handoff:** client `PushPayloadMapper` branch on `key="social_nudge"` + `NotificationStringKey.SocialNudge*` en/es; decide whether to disable client `DailyInactivityWorker` (double-nudge risk). **MANUAL:** deploy functions + rules.

Report: `docs/session/reports/w1-streak-nudges-function.md` · Handoff: `docs/session/handoffs/w1-streak-nudges-function.md`
