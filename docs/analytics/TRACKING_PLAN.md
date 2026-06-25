# FoodRats Tracking Plan

> Human-readable mirror of the executable taxonomy in
> `core/domain/src/commonMain/kotlin/.../analytics/AnalyticsEvent.kt`. **That sealed interface is the
> source of truth** — this table is regenerated from it, never hand-diverged. Design rationale:
> `docs/specs/2026-06-14-analytics-base-definition-design.md`.
>
> Status legend: **live** = wired to a real trigger and shipping; **proposed** = defined in the
> taxonomy, not yet wired to a call site (wire next; the leaf already exists so adding the call site is
> a one-liner). All events are consent-gated (no-op until opt-in) and PII-free.

## Conventions

- Event names: `snake_case`, past tense, ≤40 chars, letter-first. GA4 predefined names reused where
  they fit (`login`, `sign_up`, `join_group`, `share`, `select_content`, `post_score`, `screen_view`).
- Properties: `snake_case`; booleans `has_`/`is_`; counts `_count`; durations `_ms`; ids `_id`.
- Track from the **ViewModel/coordinator after a use case returns `Ok`** (never from a use case).
- GA4 caps enforced in the adapter: ≤25 params/event, name ≤40, string value ≤100. See spec §9.

## Events

| Event (`name`) | Status | Trigger | Properties | Code |
|---|---|---|---|---|
| `login` | live | Sign-in `Result` Ok (existing account / Google) | `method` | `SignInViewModel.emitFromResult` |
| `sign_up` | live | First-time account creation Ok | `method` | `SignInViewModel.emitFromResult` |
| `sign_in_failed` | live | Sign-in/up `Result` Err | `method`, `error_leaf` | `SignInViewModel.emitFromResult` |
| `crew_created` | live | Create-crew Ok | `crew_id` | `CrewPickerViewModel.doCreate` |
| `join_group` | live | Join-by-code Ok | `group_id`, `join_method` | `CrewPickerViewModel.doJoin` |
| `crew_left` | live | Leave-crew Ok | `crew_id` | `CrewSettingsViewModel.doLeave` |
| `crew_renamed` | live | Rename-crew Ok (owner) | `crew_id` | `CrewSettingsViewModel.doSaveCrewName` |
| `crew_deleted` | live | Delete-crew Ok (owner) | `crew_id` | `CrewSettingsViewModel.doDelete` |
| `crew_switched` | live | Active crew switched | `crew_id` | `CrewPickerViewModel.PickCrew` |
| `share` | live | Invite link shared | `content_type=crew_invite`, `item_id` | `CrewSettingsViewModel.ShareLinkTapped` |
| `meal_capture_started` | live | Capture-screen draft started (`startDraft` Ok) | `capture_source=unknown` | `CaptureMealViewModel.Start` |
| `plate_classified` | live | On-device classification Ok (classifier ran) | `detected_count`, `classify_latency_ms`, `classifier_version` | `ComposePlateViewModel.onPhotoCaptured` |
| `ingredients_confirmed` | live | Ingredient picker confirmed | `detected_count`, `confirmed_count` | `SelectIngredientsViewModel.ConfirmAndExit` |
| `meal_composer_opened` | live | Composer screen opened | — | `ComposePlateViewModel.init` |
| `meal_published` | live | Publish upload `Result` Ok (true outcome) | `meal_slot`, `ingredient_count`, `has_description`, `audience_crew_count`, `publish_source` | `BackgroundMealUploadCoordinator.doUpload` |
| `meal_publish_failed` | live | Publish upload `Result` Err | `error_leaf` | `BackgroundMealUploadCoordinator.doUpload` |
| `meal_deleted` | live | Delete-meal Ok | `by_author` | `MealDetailViewModel.deleteMealAction` |
| `select_content` | live | Meal detail opened | `content_type=meal`, `item_id` | `MealDetailViewModel.init` |
| `post_score` | live | Rate-meal Ok (feed or detail) | `score`, `meal_id` | `FeedViewModel.rate` / `MealDetailViewModel.rate` |
| `comment_posted` | live | Post-comment Ok | `meal_id` | `MealDetailViewModel.postComment` |
| `feed_day_viewed` | live | A feed day loads | `meal_count`, `day_offset` | `FeedViewModel` (once per distinct loaded day; deduped via `lastTrackedFeedDay`) |
| `streak_viewed` | live | Stats opened (default Week / streak surface) — once per VM lifetime | — | `StatsViewModel.init` |
| `leaderboard_viewed` | live | First open of a leaderboard tab (Month/Historic) per VM lifetime | — | `StatsViewModel.handle(SelectTab)` |
| `achievement_unlocked` | live | An achievement's unlock timestamp persisted (Ok) | `achievement_id` | `AchievementsViewModel.persistAndCelebrate` |
| `digest_story_opened` | live | Weekly-recap story first ready | `digest_source`, `scene_count` | `WeeklyStoryViewModel` (shared) |
| `digest_story_scene_viewed` | live | A recap scene becomes visible | `scene_kind`, `scene_index` | `WeeklyStoryViewModel` (shared) |
| `digest_story_completed` | live | Advanced past the last recap scene | `scene_count` | `WeeklyStoryViewModel` (shared) |
| `share` | live | Recap scene shared as a story card (OpenedInstagram/FallbackSheet, not Failed) | `content_type=recap`, `item_id`=scene-kind wire slug | `WeeklyStoryViewModel.shareScene` (shared) |
| `notif_permission_prompted` | live | OS permission dialog requested | `prompt_count` | `NotificationPermissionViewModel` |
| `notif_permission_granted` | live | OS permission granted | — | `NotificationPermissionViewModel` |
| `notif_permission_denied` | live | OS permission denied | — | `NotificationPermissionViewModel` |
| `screen_view` | live | Every nav destination change (root + tab NavHosts) | `screen_name` | `NavGraph.TrackScreenViews` |
| `setting_changed` | partial | A user-changeable persisted preference changed | `setting`, `enabled` (boolean toggles only) | `CrewSettingsViewModel.doToggleBlindVoting` (blind_voting); other settings wire next |
| `consent_granted` | proposed | After analytics consent recorded | `consent_version` | consent UI (wire next) |

## User properties

| Property (`key`) | Source | Status |
|---|---|---|
| `active_crew_size` | members of the active crew | proposed |
| `crews_count` | number of crews the account is in | proposed |
| `app_locale` | resolved app language | proposed |
| `notif_permission_state` | OS notification permission state | proposed |
| `data_consent_version` | `Account.dataConsentVersion` | proposed |

Identity (`setUserId` = account UID) is wired live via `AnalyticsIdentityBinder` (consent-gated).

## North Star & funnels

- **North Star:** Weekly Engaged Crew-Days (≥2 members each publish + react in a crew-day).
- **Activation (72h):** `login` → `crew_created`/`join_group` → `meal_composer_opened` → `meal_published`
  → (`select_content`/`post_score`/`comment_posted`).
- See spec §5 for the full metrics tree and guardrails.

## Governance

- A taxonomy change is a PR editing `AnalyticsEvent.kt`; evolve additively, rename via expand-contract.
- CI guardrails: `AnalyticsTaxonomyTest` (name/PII/limits), `ConsentGatedAnalyticsTest` (gate),
  the `:core:domain` Konsist no-Firebase rule, and each feature's `*ModuleVerifyTest` (binding present).
- **Manual pre-release QA:** walk sign-in → crew → publish → feed → stats → notification with Firebase
  DebugView open on Android (`adb shell setprop debug.firebase.analytics.app es.schsebastian.foodrats`)
  and iOS (`-FIRDebugEnabled`), asserting each event fires once with the right typed params.
