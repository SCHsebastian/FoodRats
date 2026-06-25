# Child Safety (CSAE/CSAM) runbook

Internal procedure for handling child sexual abuse & exploitation (CSAE) reports and child sexual abuse
material (CSAM) in FoodRats. Backs the public standards at
**https://foodrats-de4ec.web.app/child-safety** and the Google Play Child Safety Standards declaration.
Sibling of `docs/moderation/RUNBOOK.md` (general moderation).

> Everything below must stay consistent with the public standards page. If you change one, change both.

## Designated point of contact

- **Name:** Sebastián Cardona
- **Email:** hello@chsumiapps.com
- This is the contact published on the standards page and entered in the Play Console CSAE declaration.
  The person reachable here must be able to discuss FoodRats' child-safety compliance.

## How a child-safety report reaches us

1. A user selects **Report → Child safety** in the app (the `child_safety` reason).
2. The client writes `reports/{reporterId|targetKey}` with `reason: "child_safety"`.
   - Allowed by `firestore.rules` (the `child_safety` value is whitelisted on report-create).
3. The `onReportCreated` Cloud Function (`functions/src/triggers/onReportCreated.ts`) **escalates on the first
   report** (no multi-reporter threshold): it runs the existing takedown path immediately and writes a
   high-priority audit doc to `moderationActions` tagged `escalated: true, priority: "high", reason: "child_safety"`.
   - Result: the meal/comment is hidden (or the account flagged) the instant one child-safety report lands.
4. A direct email to **hello@chsumiapps.com** is the out-of-app channel (also on the standards page).

## When CSAE/CSAM is confirmed — do this

Act promptly. Order matters: do **not** download, copy, or forward suspected CSAM beyond what is strictly
necessary to remove it and to report it to the authorities.

1. **Remove** the content from the app (already auto-hidden by the escalation; confirm it is gone from all crews).
2. **Disable** the responsible account and revoke its access to the service.
3. **Preserve** evidence as permitted by law: the Firestore report doc, the `moderationActions` audit entry, the
   storage object reference/URL, the account id, and timestamps. Do not delete these until the report to
   authorities is filed and any retention obligation has lapsed.
4. **Report** apparent CSAM to the **NCMEC CyberTipline** (https://report.cybertip.org) and/or the appropriate
   authority in the relevant jurisdiction. Anyone may file a CyberTipline report; ESP registration is optional
   and US-specific and is not required to file.
5. **Record** the action: confirm the `moderationActions` entry, and note in it the outcome (account disabled,
   report filed + reference number).
6. **Cooperate** with any follow-up from law enforcement or NCMEC as required by law.

## Triage queue

- Operator surface: the `moderationActions` collection. Filter `priority == "high"` / `reason == "child_safety"`
  for the child-safety queue. (No console UI exists yet — query Firestore directly.)
- Target SLA: review high-priority child-safety entries **within 24 hours** (matches the 24h response time the
  EULA promises for objectionable-content reports).

## Preventive controls already in place

- On-device text filter (`WordlistTextModeration`, all languages) hard-blocks objectionable dish titles,
  descriptions, and comments before they post.
- Per-user **block**, and reporter-threshold auto-hide for non-child-safety reasons.
- Closed-group model: content is visible only to a user's chosen crews (3–8 people), not the public.

## Legal posture

- FoodRats is **not directed to children under 13** (privacy page "Children" section; set the same in Play
  Console → App content → Target audience & content).
- We comply with applicable child-safety laws, including CSAM reporting obligations.

## Related

- Public standards: `website/child-safety/index.html` → https://foodrats-de4ec.web.app/child-safety
- Plan + draft: `docs/session/2026-06-25-google-play-child-safety/`
- Report pipeline: `functions/src/triggers/onReportCreated.ts`, `firestore.rules` (reports block)
- General moderation: `docs/moderation/RUNBOOK.md`
