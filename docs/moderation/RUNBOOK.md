# FoodRats Moderation Runbook

**Owner:** the FoodRats developer (solo/small team). **SLA: every report is reviewed within 24 hours.**
**Scope:** Apple App Store Guideline 1.2 (Safety / User-Generated Content). Companion to the design spec `docs/specs/2026-06-19-ugc-compliance-design.md`.

FoodRats is a closed-group meal-sharing app. User-generated content = profiles (display name, avatar), meals (plate photo, dish name, free-text description), comments, and reactions. There is no public discovery — content is only visible inside a user's crews (3–8 members).

---

## 1. The four safety mechanisms (how each works)

| Mechanism | What it does | Where |
|---|---|---|
| **Filter** | On-device en/es wordlist screens text. Comments with objectionable text are **hard-blocked** before posting; meal descriptions get an **advisory** warning. | `WordlistTextModeration` (`:core:domain`) |
| **Report** | Any member can report a meal, comment, or user. Reports go to a server-only `reports` queue. | `FrReportSheet` → `ReportPort` → `reports/{id}` |
| **Block** | Any member can block another user; the blocked user's content disappears from the blocker's feed, comments, and stats. Manage at Profile → Blocked users. | `BlockedAccountsPort` |
| **EULA / Guidelines** | Full EULA + Community Guidelines are embedded in-app (en/es), shown at login (required acceptance, versioned) and in Profile. Apple's standard EULA is referenced in App Store Connect. | `shared/app/legal/`, `EulaPort` |

---

## 2. Auto-hide threshold (the first line of timely action)

A Cloud Function (`onReportCreated`, region `europe-west3`) fires on every new `reports/{id}` doc and counts **distinct reporters** for the reported target.

- **Threshold = 3 distinct reporters.**
- At or above threshold the function **auto-removes** the content immediately:
  - **Meal** → deletes `crews/{crewId}/meals/{mealId}`, which triggers the existing `onMealDeleted` cascade (removes comments/ratings subcollections + the plate image + thumbnail from Storage).
  - **Comment** → deletes `crews/{crewId}/meals/{mealId}/comments/{commentId}` via the Admin SDK.
  - **Account** → **never auto-deleted.** Account-level takedown is always a human decision (a dogpile may be a false positive). The function flags it for manual review only.
- Below threshold the function logs the report for the daily manual review and takes no action.

Because crews are 3–8 people, 3 distinct reporters is usually reached within minutes — so genuinely objectionable content is typically gone well inside the 24 h SLA without any human in the loop.

---

## 3. Manual review process (≤ 24 h SLA)

Run this **at least once every 24 hours** (set a daily reminder/cron):

1. **Open the queue.** In the Firebase console (project `foodrats-de4ec`) → Firestore → `reports` collection. Sort by `createdAtEpochMs` descending. The collection is server-only (no client can read it).
2. **Triage each `status == "open"` report.** Look at `targetType`, `reason`, `reporterId`, and `crewId`/`mealId`/`commentId`/`accountId` (these are pinned into `targetKey` and membership/existence-validated by the rules, so they reliably point at the reported content).
3. **Inspect the reported content** by navigating to the referenced doc (`crews/{crewId}/meals/{mealId}` etc.). For account reports, inspect `accounts/{accountId}`.
4. **Decide:**
   - **Remove content** (meal/comment) → see §4 (manual takedown).
   - **Account action** (warn / disable) → §5.
   - **Dismiss** (not a violation) → set the report doc `status = "dismissed"` (Admin SDK or console edit) and move on.
5. **Close the report.** After acting, set the target's report docs `status = "actioned"` (the function already does this when it auto-hides; do it by hand for manual removals).
6. **Respond if contacted.** If a reporter or affected user emailed the support contact (§7), reply within the SLA.

### Audit log
`onReportCreated` writes a structured `logger.info` line on every fire. In **Cloud Logging** (Firebase console → Functions → Logs, or `gcloud logging`), grep for `report_processed` to see: `{ targetKey, targetType, distinctReporters, threshold, thresholdReached, action: "below_threshold" | "removed_meal" | "removed_comment" | "flagged_account", reporterId, crewId, mealId, commentId, accountId }`. This is the moderation audit trail.

---

## 4. Manual takedown (crew-owner / author delete RBAC)

The app already has moderation delete built in — no special admin tool needed for in-crew removal:

- **A meal's author** can delete their own meal (from every crew it was shared to).
- **A crew owner** can delete any meal **or comment** in their crew (moderation).

So for in-crew abuse, the fastest manual path is to ask the crew owner to delete, or — for developer-side removal — delete the doc directly in the Firestore console / Admin SDK:
```
# meal (fires onMealDeleted cascade automatically)
firebase firestore:delete "crews/<crewId>/meals/<mealId>" --project foodrats-de4ec
# comment
firebase firestore:delete "crews/<crewId>/meals/<mealId>/comments/<commentId>" --project foodrats-de4ec
```
Deleting a meal doc fires `onMealDeleted`, which reclaims the plate image + thumbnail + subcollections. Deleting a comment is a single-doc delete (no Storage/subcollections).

---

## 5. Account-level action

Account takedown is always manual:
- **Disable a user:** Firebase console → Authentication → find the user → Disable account. They can no longer sign in. (Their existing content remains until removed per §4.)
- **Severe/illegal content:** disable the account, remove their content (§4), and preserve the report docs + the offending content references for any legal/compliance follow-up before deletion.
- Record the decision by setting the related `reports` docs `status = "actioned"`.

---

## 6. Filter tuning

The wordlist (`Wordlists.kt`, `:core:domain`) is conservative (precision over recall) to avoid blocking legitimate food talk (the "Scunthorpe problem"). If a false positive or a missed term is reported:
- Add/remove a term in the appropriate set (`EN`, `ES`, or `NEUTRAL`) with its `ModerationCategory`.
- Add a test case to `WordlistTextModerationTest`.
- The filter is a **deterrent**, not a guarantee — report + block are the safety net.

---

## 7. Published support contact (Guideline 1.2 + 1.5)

- **Support URL / domain:** `https://foodrats-de4ec.web.app` (the live Firebase Hosting domain — this is the canonical published domain; `foodrats.app` is **not** ours).
- **Support email (placeholder):** `hello@chsumiapps.com` — or a contact form hosted at `https://foodrats-de4ec.web.app/support`.
- These must be set in **App Store Connect** (app's Support URL + the standard EULA reference) and stated in the in-app Community Guidelines so users always have a way to reach the developer about objectionable content. Update this section if the domain/contact changes.

---

## 8. Reference

- Design spec: `docs/specs/2026-06-19-ugc-compliance-design.md`
- Function: `functions/src/triggers/onReportCreated.ts` (threshold constant lives here)
- Rules: `firestore.rules` (`reports/{reportId}` create-only/server-read; `accounts/{uid}/blocks/{blockedUid}` owner-only)
- Deploy after changes: `pnpm dlx firebase-tools deploy --only firestore:rules,functions --project foodrats-de4ec`
