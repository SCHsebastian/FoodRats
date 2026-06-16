# CHARTER — Orchestrated Autonomous Build

Durable contract for a long, multi-session, quota-survivable build. Written once; this is the
source of authority for HOW we work. `TASKS.md` is the source of truth for WHAT is left.

## Objective

Implement the features in `docs/roadmap/2026-06-14-feature-roadmap.md` following the designs in
`docs/specs/`. Go in wave order (0 → 5).

## Project rules (from CLAUDE.md — non-negotiable; every implementer obeys these)

1. **DDD ubiquitous language.** `Meal` (never Post/Entry), `Plate` (never Photo for the composed
   artifact), `Score` (never rating/stars), `Crew` (never group/team), `Member` (Crew membership,
   distinct from `Account`). Banned generic names when a domain word exists: `Entity`, `Manager`,
   `Helper`, `Service` (unless a real stateless application service), `Util`, `Handler` (unless HTTP).
2. **Ports for cross-context reads.** Features NEVER depend on other features. Cross-context
   communication goes through ports declared in `:core:domain` (e.g. `MealReadPort`,
   `ActiveCrewProvider`, `AccountReadPort`).
3. **Typed `Result<T, E>` with sealed-interface errors.** Custom `Result<T,E>` in `:core:domain`,
   NOT stdlib `Result<T>` (it carries `Throwable`). Errors are `sealed interface` with nested
   `sealed interface` groups and `data object` leaves (e.g. `MealError.Publish.AlreadyPostedToday`).
   No enums. No `Unknown` cases unless genuinely justified. Each feature has a matching
   `*ErrorToStringKeyTest` locking exhaustiveness.
4. **One I/O boundary per public data-layer method.** Exactly one `withContext(dispatchers.io)`
   per public repository method. ZERO `withContext` in use cases or ViewModels.
5. **Vendor SDKs only in adapter layers.** Firebase/GitLive types never appear in `domain/`. DTOs +
   error mapping live in each feature's `data/firebase/`. Konsist enforces this for `:core:domain`.
6. **All user-visible text via i18n** — `resolve(StringKey)`. Each feature defines its own
   `<Feature>StringKey` enum. Includes punctuation + glyph separators (`★`, `•`, `(N)`). No
   hardcoded strings outside `composeResources/`. Populate BOTH `values/strings.xml` and
   `values-es/strings.xml`.
7. **A catalog entry per public `Fr*`.** Every public `Fr*` composable (and foundation token group)
   ships with a `:catalogApp` entry — UNLESS it lives in a feature module and would force
   feature/Firebase deps into the catalog APK (the catalog depends ONLY on `:core:designsystem`).
   Domain-aware components live in the owning feature's `presentation/components/`.
8. **MVI single source of truth.** State lives only in `MviViewModel`'s `State`. No parallel
   `MutableStateFlow`. Feed flows into use cases via `state.map { it.x }.filterNotNull()
   .distinctUntilChanged()`. `FeedViewModel` is the reference pattern.
9. **Analytics:** new event = a new `AnalyticsEvent` sealed leaf (snake_case, NO PII) + a call site
   in the ViewModel/coordinator AFTER the use case returns `Ok` (never in a use case). Consent gate
   lives ONLY in `ConsentGatedAnalytics`. Inject `analytics: AnalyticsPort = NoopAnalyticsTracker`.
10. **Brainstorm before non-trivial work.** Never implement on a 1-sentence brief.
11. **Verify before claiming done.** A change is not done until a verify command has RUN and its
    output is QUOTED. "Should work" / "looks right" is not allowed. If verify fails, FIX it.

## Build conventions (from CLAUDE.md)

- Typesafe project accessors: `projects.core.domain`, never `project(":core:domain")`. New modules
  must be registered in `settings.gradle.kts`.
- `@JvmInline` required on every commonMain `value class` (`import kotlin.jvm.JvmInline`).
- Firebase-touching modules target `JvmTarget.JVM_17`; add the Firebase BOM
  (`platform("com.google.firebase:firebase-bom:33.5.1")`) to `androidMain.dependencies`.
- Material Icons: `material-icons-core` only on iOS; vendor SVG paths into `FrIcons.kt` when needed.
- Pre-launch: NO backwards compatibility, NO migrations, schema changes are free.
- Use `pnpm` / `pnpm dlx`, never npm/npx. Cloud Functions live in `functions/` (vitest).

## Orchestration loop (the orchestrator runs this; keeps its OWN context lean)

1. Read `TASKS.md`. Pick the first `todo` whose deps are all `done`. Mark it `doing`; rewrite TASKS.md.
2. Spawn ONE implementation subagent (general-purpose, same Opus model) with the BRIEF TEMPLATE.
   Pass ONLY: task id/title, the ONE spec file + section, the target files, the verify command.
   The orchestrator does NOT read specs or source itself.
3. Agent implements, verifies, writes its FULL report to `docs/session/reports/<id>.md`, returns a
   ≤6-line summary only.
4. On return: write `docs/session/journal/<NNN>-<id>.md` (6-line summary + quoted verify output +
   task id). Set the task `done` or `blocked` in TASKS.md. **If the task surfaced any MANUAL
   user step (deploy, cloud IAM, store/privacy declaration, index, on-device smoke), append it to
   `docs/session/human.md`** (deduplicated). CHECKPOINT ALL THIS BEFORE STARTING THE NEXT TASK —
   a cut-off must never lose a completed unit.
5. Give the user a 2–3 line status. Continue to the next task.

Keep orchestrator context minimal: never read source or specs; never accept file dumps from agents;
if the running summary grows or gets summarized, re-derive truth from TASKS.md + latest journal
entries. ONE task in flight at a time.

## Brief template (give verbatim to each implementation subagent)

> You implement ONE task in the FoodRats KMP repo. Obey `docs/session/CHARTER.md` and `CLAUDE.md`.
> Task: `<id>` — `<title>`.
> Read ONLY: `<spec file>` §`<section>` plus the files you must change. Do not sweep the repo.
> FIRST check whether prior (interrupted) work for this task already exists on disk; if so,
>   verify/finish it rather than starting over.
> Implement it. Then VERIFY: run `<verify-cmd>`; if it fails, fix and re-run until green or a genuine
>   blocker. Quote the output.
> Write your FULL report to `docs/session/reports/<id>.md`. If the next task needs context from you,
>   add a short `docs/session/handoffs/<id>.md`.
> Return to me ONLY (≤6 lines): Status · Files changed (paths) · Verify (cmd + last 3 lines) ·
>   Decisions · Blockers · Suggested next. No file contents.

## Decisions / blockers

If a task needs a product decision the spec doesn't answer, mark it `blocked` in TASKS.md with the
question, skip to the next independent task, and surface the question to the user. NEVER guess on
anything irreversible (deletes, schema you'd have to migrate, shared/prod systems).

## Post-build review pack (user directive, 2026-06-15)

**Before the review pack (user directive 2026-06-15):** once Wave 5 is finished, FIRST save a session
memory (write to the memory dir + MEMORY.md index pointer) capturing the orchestrated build state,
THEN `/compact`, THEN continue with the review pack below.

After ALL roadmap tasks are `done`/`blocked` (whole build finished), run a multi-agent REVIEW PACK
to find and fix bugs/issues, and **iterate it twice**. Use the `Workflow` tool (ultracode is on):
- Phase 1 — REVIEW: fan out reviewers across dimensions (correctness/bugs, architecture-rule
  violations per CLAUDE.md, MVI/ports/Result-error conventions, i18n completeness, security rules,
  test gaps, Firestore/functions correctness) over the diff produced by this build. Adversarially
  verify each finding (independent skeptics; drop unconfirmed) so only real issues remain.
- Phase 2 — FIX: one fix agent per confirmed issue (worktree isolation if they touch shared files),
  each re-running the relevant module verify-cmd until green. Quote outputs.
- Then REPEAT the whole review→fix cycle once more (2 iterations total). Stop early only if a full
  review iteration surfaces zero confirmed issues.
- Record results in `docs/session/reports/review-pack-iter{1,2}.md`; append any new MANUAL steps to
  `human.md`; journal each iteration. Never claim a fix done without a quoted green verify.

## Stop conditions

STOP when a wave finishes, all tasks are done/blocked, or a hard blocker hits. Otherwise keep going
autonomously without asking permission per step.

## Resume (paste in a fresh session after a quota cut-off)

> Resume the orchestrated build. Read `docs/session/CHARTER.md`, `docs/session/TASKS.md`, and the 2
> most recent files in `docs/session/journal/`. Reconcile: any task still `doing` was cut off
> mid-flight — re-dispatch it fresh (the agent will detect partial work on disk); trust `done` rows.
> Then continue the ORCHESTRATION LOOP at the first non-`done` task. Do NOT read specs yourself —
> delegate. Confirm in 3 lines where you're resuming, then go.
