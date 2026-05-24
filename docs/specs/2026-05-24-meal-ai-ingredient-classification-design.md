# Meal AI Ingredient Classification — Design

**Status:** Approved for plan
**Date:** 2026-05-24
**Owner:** Sebas
**Supersedes:** —
**Related:** `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` (Meal exemplar §10), `docs/specs/2026-05-21-meal-description-replaces-tags-design.md` (Description VO)

## 1. Goal

When a user publishes a Meal, an on-device food image classifier predicts the dish, maps that dish to a default set of ingredient slugs from a server-side catalog, and stores the ingredients as metadata on the Meal. The user can edit the detected list (add, remove, or browse the full catalog) from a dedicated picker screen before publishing. If the user does nothing, the Meal publishes with whatever the model detected. Adding ingredients is never required.

A secondary goal is dataset hygiene: every Meal records both the human-confirmed `ingredients` and the raw `detectedIngredients`, plus the `classifierVersion` of the model that produced them. This preserves the option to use the corpus for training, retraining, or eventual data partnerships — but does not introduce any user-facing consent flow today.

## 2. Non-goals

- No object-detection bounding boxes — labels only.
- No cloud LLM vision call. The decision was on-device. Revisit later if accuracy is insufficient.
- No real-time/preview-mode classification. Inference fires once per captured photo.
- No nutrition data, no calorie estimation, no recipe suggestions. Catalog is just `(slug, names, category, icon)`.
- No legacy migration. The app is pre-launch; new schema is the only schema.
- No data-consent UI in this release. Two reserved fields land on `Account` so the option exists later.

## 3. Architecture

Two new bounded contexts. Both are bounded contexts in their own right — not auxiliary code of `:feature:meal`.

```
core/domain/
  meal/Ingredient.kt              IngredientSlug (@JvmInline value class), Ingredient, IngredientCategory
  meal/IngredientReadPort.kt      observeCatalog(), findBySlugs(set), suggestForDish(slug)
  meal/MealClassifierPort.kt      classify(jpeg: ByteArray): Result<List<DishLabel>, ClassifierError>

feature/ingredient/               new module
  domain/
    IngredientError.kt
    usecase/ObserveCatalogUseCase.kt
    usecase/SearchIngredientsUseCase.kt
  data/firebase/
    IngredientDto.kt
    IngredientFirestoreDataSource.kt
    IngredientRepository.kt       implements IngredientReadPort
  data/local/
    IngredientCatalogCache.kt     DataStore-backed disk cache
  presentation/
    select/SelectIngredientsScreen.kt
    select/SelectIngredientsViewModel.kt
    select/SelectIngredientsContract.kt
    components/FrIngredientRow.kt
  i18n/IngredientStringKey.kt
  presentation/IngredientErrorToStringKey.kt
  di/IngredientModule.kt

feature/meal-ai/                  new module
  domain/
    ClassifierError.kt
    DishLabel.kt                  (dishSlug: String, confidence: Float)
    usecase/ClassifyPlateUseCase.kt
  data/
    MediaPipeMealClassifier.kt    expect class in commonMain
    androidMain: actual using com.google.mediapipe:tasks-vision
    iosMain:     actual using MediaPipeTasksVision cocoapod (cinterop)
    assets/food101.tflite         packaged via composeResources
  presentation/MealAiErrorToStringKey.kt
  i18n/MealAiStringKey.kt
  di/MealAiModule.kt
```

`:feature:meal` consumes `MealClassifierPort` and `IngredientReadPort` from `:core:domain`. No direct dependency on `:feature:ingredient` or `:feature:meal-ai`. This honors the cross-feature ban — each feature only knows the ports.

**JVM target:** both new feature modules go on **JVM 17** because both transitively touch Firebase (`:feature:ingredient` directly; `:feature:meal-ai` because Koin pulls in shared modules — confirm during scaffold and bump if needed).

**iOS:** `:feature:meal-ai` requires the `MediaPipeTasksVision` cocoapod. Add to `cocoapods { pod("MediaPipeTasksVision") }` in that module's `build.gradle.kts`.

## 4. Domain model

### 4.1 `:core:domain` additions

```kotlin
@JvmInline
value class IngredientSlug(val value: String) {
    init { require(value.isNotBlank() && value.length <= 64) }
}

data class Ingredient(
    val slug: IngredientSlug,
    val displayName: String,
    val category: IngredientCategory,
    val iconKey: String? = null,
)

enum class IngredientCategory {
    Vegetable, Fruit, Meat, Fish, Dairy, Grain, Legume, Sauce, Spice, Sweet, Beverage, Other
}

interface IngredientReadPort {
    fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>>
    suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient>
    suspend fun suggestForDish(dishSlug: String): List<IngredientSlug>
}

interface MealClassifierPort {
    suspend fun classify(jpeg: ByteArray): Result<List<DishLabel>, ClassifierError>
}
```

`IngredientCategory` is an enum (closed taxonomy of presentation), not a sealed interface — no error semantics, no future payloads.

### 4.2 `Meal` aggregate changes (`:core:domain/meal/Meal.kt`)

```kotlin
data class Meal(
    val id: MealId,
    val author: MealAuthor,
    val crewId: CrewId,
    val day: MealDay,
    val slot: MealSlot,
    val photoUrl: String,
    val dish: DishName,
    val description: Description,
    val publishedAt: Instant,
    val coordinates: Coordinates? = null,
    val ingredients: List<IngredientSlug> = emptyList(),
    val detectedIngredients: List<IngredientSlug> = emptyList(),
    val classifierVersion: String? = null,
)
```

- `ingredients`: human-confirmed list (may equal `detectedIngredients` if the user didn't touch them, or empty if classifier failed and they didn't add manually).
- `detectedIngredients`: raw classifier output mapped to slugs. Frozen at publish time.
- `classifierVersion`: e.g. `"food101-v1"`. `null` if classifier didn't run (failure, or future manual path).

The triple `(ingredients, detectedIngredients, classifierVersion)` is the dataset-quality artifact: ground truth + model prediction + model identity. Hard cap of **30 slugs** in either list; truncated silently at the repository boundary with a non-fatal Crashlytics report. No `MealError` leaf for this (defensive only, never expected in happy path).

### 4.3 `MealDraft` changes (`:feature:meal/domain/`)

```kotlin
data class MealDraft(
    val crewId: CrewId,
    val authorId: AccountId,
    val day: MealDay,
    val plate: Plate?,
    val dish: DishName?,
    val description: Description,
    val slot: MealSlot? = null,
    val coordinates: Coordinates? = null,
    val ingredients: List<IngredientSlug> = emptyList(),
    val detectedIngredients: List<IngredientSlug> = emptyList(),
    val classifierVersion: String? = null,
)
```

New `UpdateMealDraftCommand` variants:
- `SetDetected(detected: List<IngredientSlug>, version: String)` — called after classification succeeds; also seeds `ingredients` to mirror `detected` initially.
- `SetIngredients(ingredients: List<IngredientSlug>)` — called when user returns from the picker.

### 4.4 `Account` reserved fields (`:feature:auth/domain/`)

```kotlin
data class Account(
    // … existing fields …
    val dataConsentVersion: Int = 0,
    val dataConsentGrantedAt: Instant? = null,
)
```

`0` = never granted. No UI yet. Bumped to `1` when (and if) a future feature introduces an opt-in dialog. Persisted in `AccountDto` with defaults.

## 5. Server-side catalog

### 5.1 Firestore schema

`ingredients/{slug}`:

```json
{
  "slug": "tomato",
  "names": { "en": "Tomato", "es": "Tomate" },
  "category": "Vegetable",
  "iconKey": "tomato",
  "aliases": ["cherry tomato", "tomate cherry", "tomate pera"],
  "createdAt": <Timestamp>,
  "updatedAt": <Timestamp>
}
```

`dishIngredientMap/{dishSlug}`:

```json
{
  "dishSlug": "lasagna",
  "modelLabel": "lasagna",
  "defaultIngredients": ["pasta", "tomato", "cheese", "beef", "onion"]
}
```

Two collections because the dish→ingredients mapping is a side-table of the classifier model identity (Food-101 today, possibly something else later); the ingredient catalog itself is permanent domain data. Rewriting the dish-map when the model changes does not touch the catalog.

### 5.2 Seed

- `functions/seed/ingredients.json` — ~200 entries at launch (Vegetable 40, Fruit 30, Meat 15, Fish 15, Dairy 15, Grain 25, Legume 10, Sauce 30, Spice 20, Sweet 5, Beverage 5).
- `functions/seed/dish-ingredient-map.json` — 101 entries (one per Food-101 class).
- `functions/scripts/seed-catalog.ts` — Node script with batched writes, run via `pnpm seed:catalog`.
- Validation test `functions/__tests__/seed.test.ts` — no duplicate slugs, all dish-map referenced slugs exist, all `names` have `en` and `es`.

### 5.3 Security rules

```
match /ingredients/{slug}    { allow read: if true; allow write: if false; }
match /dishIngredientMap/{d} { allow read: if true; allow write: if false; }
```

Writes only via admin SDK (seed script with service account). No client writes ever.

### 5.4 Client cache

`IngredientRepository`:
- On first access reads full `ingredients` collection (one network read, ~200 docs).
- Persists the result in DataStore (`IngredientCatalogCache`) keyed by collection hash.
- Exposes `StateFlow<Map<IngredientSlug, Ingredient>>` — instant on subsequent app launches.
- Refresh on app start (fire-and-forget); UI uses the StateFlow without blocking.

Pagination skipped — catalog stays under 2k entries for the foreseeable future. Revisit threshold if it grows past that.

### 5.5 i18n

- Ingredient names live in the catalog data (`names.en` / `names.es`), resolved at the repository: `names[currentLang] ?: names["en"]`.
- `IngredientStringKey` covers only UI chrome ("Search ingredients", "Category: Vegetables", error banners). Names are data, not resources.

## 6. Classifier runtime

### 6.1 Model

- **Food-101 dish classifier**, MobileNetV3-Small backbone, int8 quantized, ~6–10 MB `.tflite`. Bundled as `composeResources/files/food101.tflite` in `:feature:meal-ai`.
- `classifierVersion = "food101-v1"` recorded on every successful classification.
- License: Food-101 dataset images carry a non-commercial caveat. **Accepted risk for pre-launch / closed beta.** Before public launch we either (a) retrain on clean images, or (b) negotiate license, or (c) swap to MediaPipe Model Maker fine-tune on owned/Open-Images data. Tracked as tech debt in this spec.

### 6.2 Runtime — MediaPipe Tasks `ImageClassifier`

Both platforms consume the same `.tflite` via Google AI Edge MediaPipe Tasks:

- **Android:** `com.google.mediapipe:tasks-vision` artifact, `ImageClassifier.createFromOptions(...)`.
- **iOS:** `MediaPipeTasksVision` cocoapod, `MPPImageClassifier` via cinterop.

Implementation shape:

```kotlin
// commonMain
internal expect class MediaPipeMealClassifier(
    dispatchers: DispatcherProvider,
    crashReporter: CrashReporter,
) : MealClassifierPort
```

The same class name on both platforms; each `actual` wires its native runtime. The Koin module in each module's `actual` binds `MealClassifierPort` → `MediaPipeMealClassifier`. Single lazy-initialized instance per process. The first call pays the model-load cost (~50–150 ms on A14, ~80–300 ms on mid-Android); subsequent calls are pure inference (~10–40 ms).

I/O boundary lives **inside `MediaPipeMealClassifier.classify`** (`withContext(dispatchers.io) { ... }`), not in the use case. `ClassifyPlateUseCase` stays pure — it just delegates to the port and maps the result.

### 6.3 Thresholding

- Take top-1 dish label.
- If confidence < `0.30`, return `ClassifierError.Run.NoLabelsAbove` and the UI shows "Couldn't detect ingredients" — user proceeds normally.
- If confidence ≥ `0.30`, look up `suggestForDish(dishSlug)`; if the dish slug isn't in the dish-map (e.g. model says `caprese_salad` but we haven't seeded that slug yet), again `NoLabelsAbove` semantics.

### 6.4 Errors

```kotlin
sealed interface ClassifierError {
    sealed interface Load : ClassifierError {
        data object ModelMissing : Load
        data object ModelCorrupt : Load
    }
    sealed interface Run : ClassifierError {
        data object DecodeFailed : Run
        data object InferenceFailed : Run
        data object NoLabelsAbove : Run
    }
}
```

- `Load.*` is reported to Crashlytics as non-fatal (build/assets bug, not expected at runtime).
- `Run.*` is counted in custom Crashlytics keys (`classifier_run_failures`) but **not** reported as non-fatal. Some plate photos legitimately confuse the model; the floor will be non-zero.

## 7. UX flow

### 7.1 ComposePlateScreen changes

A new `FrIngredientsRow` molecule appears between the description field and the location picker. State:
- `classifying = true` → "Analizando ingredientes…" with shimmer (uses `Motion.medium`).
- `classifying = false && draftIngredients.isEmpty()` → "Añadir ingredientes" CTA row.
- `draftIngredients.isNotEmpty()` → "N ingredientes" with comma-joined summary of the first 3 plus `+N` overflow.

Tap → `navigate(Route.SelectIngredients)`. The destination reads/writes the current draft through `ObserveMealDraftUseCase` / `UpdateMealDraftUseCase` — no `SavedStateHandle` round-trip needed because the draft is the single source of truth.

### 7.2 SelectIngredientsScreen

- `FrTopBar` with title "Ingredientes" and a back arrow.
- `FrSearchField` at the top, debounced 200ms, matches against `Ingredient.displayName` and `aliases`.
- Section "Detectados" with the `detectedIngredients` rows pre-marked.
- Categories below as collapsible sections (default: Vegetable expanded, others collapsed).
- Each row: `FrIngredientRow(icon, name, selected, onToggle)`. Tappable surface = whole row.
- Selection cap: once `draftIngredients.size == 30`, further unselected rows render disabled with a tooltip `IngredientStringKey.SelectionFull` ("Máximo 30 ingredientes"). Already-selected rows always toggle off. This matches the 30-cap enforced at the repository boundary (§4.2) — the UI prevents reaching the truncation path.
- Bottom CTA `FrButton("Listo", Primary)` — pop nav, the updates already flushed through the draft repository.
- Empty state if catalog is offline + no cache: `FrErrorBanner` with retry.

### 7.3 Detected ingredients flow

```
CaptureMealScreen returns photoBytes
  → ComposePlateScreen mounts
  → LaunchedEffect(photoBytes): vm.onPhotoCaptured(bytes)
    → classify(bytes) → DishLabel
    → suggestForDish(label.dishSlug) → List<IngredientSlug>
    → updateDraft(SetDetected(slugs, "food101-v1"))
  → draft.ingredients == draft.detectedIngredients
  → user optionally goes to picker, edits, returns
  → draft.ingredients now reflects user edits
  → draft.detectedIngredients remains the raw prediction
  → user taps Continue → existing publish flow runs
  → PublishMealUseCase carries ingredients + detectedIngredients + classifierVersion onto Meal
```

### 7.4 Ingredient row component

`FrIngredientRow` lives in `:feature:ingredient/presentation/components/` (domain-aware composable; not in `:core:designsystem`). Primitive of the design system it consumes: `FrCheckbox`, `FrIcon`, `FrText`. Icon resolved by `iconKey` → drawable in `:core:designsystem/ingredients/`. No raw `Color(0x…)`.

## 8. Data flow at publish

`PublishMealUseCase` extends to copy `MealDraft.ingredients`, `MealDraft.detectedIngredients`, and `MealDraft.classifierVersion` onto the published `Meal`. `MealDto` adds three fields:

```kotlin
@Serializable
data class MealDto(
    // … existing …
    val ingredients: List<String> = emptyList(),
    val detectedIngredients: List<String> = emptyList(),
    val classifierVersion: String? = null,
)
```

`MealMapper`:
- Domain → DTO: `.map { it.value }`.
- DTO → Domain: filter to non-blank, take ≤30, wrap as `IngredientSlug`. Unknown slugs (not in current catalog) are still preserved — they may exist because the catalog was edited after the meal was published; the UI shows them as "Unknown ingredient".

No changes to `BackgroundMealUploadCoordinator`, `FirebaseMealRepository.publish`, security rules for meals, or any feed/stats consumer.

## 9. Cross-context reads

- **`:feature:feed`** — does not change in this release. Future enhancement: surface ingredient chips on `FrFeedMealCard`. Out of scope here.
- **`:feature:stats`** — same. A `IngredientFrequency` leaderboard becomes possible once data accumulates. Out of scope.
- **`:feature:meal`** — depends on `MealClassifierPort` + `IngredientReadPort` from `:core:domain`. No direct module dep on `:feature:meal-ai` or `:feature:ingredient`. Wired via Koin in `shared/`.

## 10. Errors and StringKeys

Three new exhaustive mappers, each with a `commonTest` lock:

- `:feature:meal-ai/presentation/MealAiErrorToStringKey.kt`
- `:feature:ingredient/presentation/IngredientErrorToStringKey.kt`
- `:feature:meal/presentation/MealErrorToStringKey.kt` — **unchanged** (no new MealError leaves).

`MealAiStringKey`: `ClassifierBannerNoDetection`, `ClassifierBannerOffline`, `Classifying`.
`IngredientStringKey`: `SelectIngredientsTitle`, `SelectIngredientsSearchHint`, `CategoryVegetable`, …, `CatalogLoadFailed`, `CatalogEmpty`, `IngredientsRowAdd`, `IngredientsRowSummary` (parameterized "%1$d ingredientes").

All en/es bilingual in each module's `composeResources/values{,-es}/strings.xml`.

## 11. Testing

### 11.1 Unit tests (`commonTest`)

- `ClassifyPlateUseCaseTest` — fake `MealClassifierPort` returns canned `DishLabel`; fake `IngredientReadPort` returns canned `defaultIngredients`. Verifies use case wires them and returns `Result.Success(slugs)`. Failure modes: `NoLabelsAbove`, `InferenceFailed`.
- `ComposePlateViewModelTest` (extend existing):
  - On `photoBytes` set, `state.detectedIngredients` populates from classifier.
  - `state.draftIngredients` mirrors `detectedIngredients` initially.
  - Classifier failure → `state.classifierError` set, `canContinue` stays `true`.
  - Manual picker edits don't get clobbered if the user re-captures the same photo.
- `SelectIngredientsViewModelTest` — search by alias, multi-select toggle, category filter, empty-state.
- `IngredientRepositoryTest` — observes the `StateFlow` against a fake datasource; verifies DataStore cache rehydration.
- `MealAiErrorToStringKeyTest`, `IngredientErrorToStringKeyTest` — exhaustive `when` matchers.

### 11.2 Konsist tests (`androidHostTest`)

- `:core:domain` — `Ingredient.kt`, `IngredientReadPort.kt`, `MealClassifierPort.kt` import only stdlib + `kotlinx-datetime` + `kotlinx-coroutines-core`.
- `:feature:meal-ai` — does not import any other `:feature:*`.
- `:feature:ingredient` — same.

### 11.3 Compose UI tests (`androidHostTest`)

- `FrIngredientRowTest` — render, toggle, accessibility label.
- `SelectIngredientsScreenTest` — search, multi-select, category collapse/expand, "Listo" CTA. Use `createComposeRule()` v2 + Robolectric SDK 33 with display qualifiers.

### 11.4 Seed validation (`functions/__tests__/`)

- `seed.test.ts` — parses both JSON files, asserts no duplicate slugs, all `dishIngredientMap[*].defaultIngredients` resolve to entries in `ingredients`, every `names` has both `en` and `es`.

### 11.5 Manual device smoke (pre-release checklist)

- Capture pizza → classifier returns `pizza` → ingredients pre-mark `cheese, tomato, dough` → publish → feed shows meal → reopen → ingredients persisted.
- Capture jamón sandwich → low confidence → banner "Couldn't detect" → user adds manually via picker → publish OK.
- Force-stop after `SetDetected` and before publish → reopen → draft restored from `MealDraftLocalStore` → detected ingredients preserved.

## 12. Performance budget

- Inference latency target: < 200 ms on iPhone 12 / Pixel 6. First call (model load) may take ~300 ms; subsequent ~20–40 ms.
- APK size delta: ~6–10 MB for the `.tflite` + ~2 MB for MediaPipe runtime. Total ≤ 12 MB increase.
- iOS framework: similar.
- Catalog cold load: one Firestore read of ~200 docs ≈ 30–50 KB → < 500 ms on 4G, instant on warm DataStore.

## 13. Future / out-of-scope

These are deliberately not in this release:

- **License-clean model.** Retrain on Open Images food subset or owned data before public launch.
- **Cloud LLM fallback.** If detection accuracy is poor, add a `MealClassifierPort` actual that calls Gemini Flash via a Cloud Function on photos where on-device confidence is below threshold.
- **Ingredient chips on feed cards.** A glanceable summary on `FrFeedMealCard`.
- **`IngredientFrequency` leaderboard** in `:feature:stats`.
- **Consent UI** that bumps `Account.dataConsentVersion`. Required before any data export to third parties.
- **Cloud Function `exportAnonymizedMeals`** for B2B dataset partnerships. Requires consent UI first.
- **Object-detection variant** that gives bounding boxes (for an AR-ish "tap the rice" UX).
- **Per-meal nutrition estimation** from `ingredients` + serving heuristics.

## 14. Open questions

- **Model evaluation pipeline.** We have no internal test set of plate photos yet. Build a small (200-image) eval set during implementation so we can measure regression if/when we swap the model.
- **`iconKey` source.** Initial drawables can be Material symbol mapped from category, or hand-curated SVG icons. Lean toward category-based until volume justifies bespoke art.
- **Re-classification on edit.** If the user re-captures the photo on the same draft, the current design overwrites `detectedIngredients` and re-mirrors `ingredients` to it — including discarding their manual edits. Acceptable; document it; revisit if it surprises testers.
