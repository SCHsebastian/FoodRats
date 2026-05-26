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
  meal/Ingredient.kt              IngredientSlug (@JvmInline value class), Ingredient
  meal/IngredientCategory.kt      sealed interface { data object Vegetable : … }
  meal/IngredientReadPort.kt      observeCatalog(), findBySlugs(set), suggestForDish(slug)
  meal/DishLabel.kt               data class (dishSlug: String, confidence: Float)
  meal/MealClassifierPort.kt      classify(jpeg): Result<List<DishLabel>, ClassifierError>
  meal/ClassifierError.kt         sealed interface — port and error live together in :core:domain

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
  presentation/IngredientCategoryToStringKey.kt
  di/IngredientModule.kt

feature/meal-ai/                  new module — pure adapter for MealClassifierPort
  domain/
    usecase/ClassifyPlateUseCase.kt
  data/
    MediaPipeMealClassifier.kt    expect class in commonMain, implements MealClassifierPort
    androidMain: actual using com.google.mediapipe:tasks-vision
    iosMain:     actual using MediaPipeTasksVision cocoapod (cinterop)
    composeResources/files/food101.tflite   (loaded from inside data layer only)
  presentation/MealAiErrorToStringKey.kt
  i18n/MealAiStringKey.kt
  di/MealAiModule.kt
```

`DishLabel` and `ClassifierError` live in `:core:domain` (not `:feature:meal-ai/domain/`) because the port that produces them lives there — otherwise `:core:domain` would need to import `:feature:meal-ai`, which the Konsist rule forbids. `:feature:meal-ai/domain/` keeps only `ClassifyPlateUseCase` (orchestration). The asset-loading code (`composeResources`) lives in `:feature:meal-ai/data/`, never in the use case or domain — that preserves the "no Compose Resources in domain" reading of the project rule.

`:feature:meal` consumes `MealClassifierPort` and `IngredientReadPort` from `:core:domain`. No direct dependency on `:feature:ingredient` or `:feature:meal-ai`. This honors the cross-feature ban — each feature only knows the ports.

**JVM target:** both new feature modules go on **JVM 17** because both transitively touch Firebase (`:feature:ingredient` directly; `:feature:meal-ai` because Koin pulls in shared modules — confirm during scaffold and bump if needed).

**iOS cocoapod scope.** `:feature:meal-ai` declares `cocoapods { pod("MediaPipeTasksVision") }` in its own `build.gradle.kts`. The pod ships its own static framework; it does **not** need to be re-exported through the `:shared` umbrella because the only consumer of `MediaPipeMealClassifier` is `:feature:meal-ai` itself (the port lives in `:core:domain`, the implementation never crosses the framework boundary from Swift). Verify during scaffold: the umbrella `FoodRatsShared.framework` must still build without manual `framework { export(projects.featureMealAi) }`. If iOS linking complains about `MediaPipeTasksVision` not found, add `linkerOpts("-framework", "MediaPipeTasksVision")` to the iOS targets — the same pattern `:feature:meal` uses today for `CoreLocation`.

## 4. Domain model

### 4.1 `:core:domain` additions

```kotlin
import es.schsebastian.foodrats.core.domain.result.Result   // project Result<T, E>, NOT kotlin.Result
import kotlinx.coroutines.flow.Flow

@JvmInline
value class IngredientSlug(val value: String) {
    init { require(value.isNotBlank() && value.length <= 64) }   // 64 = arbitrary safety floor << Firestore doc-id limit
}

data class Ingredient(
    val slug: IngredientSlug,
    val displayName: String,
    val category: IngredientCategory,
    val iconKey: String? = null,
)

sealed interface IngredientCategory {
    data object Vegetable : IngredientCategory
    data object Fruit     : IngredientCategory
    data object Meat      : IngredientCategory
    data object Fish      : IngredientCategory
    data object Dairy     : IngredientCategory
    data object Grain     : IngredientCategory
    data object Legume    : IngredientCategory
    data object Sauce     : IngredientCategory
    data object Spice     : IngredientCategory
    data object Sweet     : IngredientCategory
    data object Beverage  : IngredientCategory
    data object Other     : IngredientCategory
}

data class DishLabel(val dishSlug: String, val confidence: Float)

interface IngredientReadPort {
    fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>>
    suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient>
    suspend fun suggestForDish(dishSlug: String): List<IngredientSlug>
}

interface MealClassifierPort {
    suspend fun classify(jpeg: ByteArray): Result<List<DishLabel>, ClassifierError>
}
```

`IngredientCategory` is a `sealed interface` with `data object` leaves — same shape as every other domain type post-`fbf5e40`, even though it carries no payload today. Keeps the door open to attaching metadata (e.g. an `iconHint`) without breaking the type.

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

The triple `(ingredients, detectedIngredients, classifierVersion)` is the dataset-quality artifact: ground truth + model prediction + model identity.

**Hard cap of 30 slugs in either list.** The UI is the cap contract: `SelectIngredientsScreen` (§7.2) disables further selection at 30; `SetDetected` is bounded by Food-101's dish→ingredients table (no entry exceeds ~10). The repository **does not** silently truncate — if it ever receives >30, that is a programmer error and `FirebaseMealRepository.publish` returns `MealError.Validation.TooManyIngredients` (new leaf). A matching `MealErrorToStringKey` entry + test row is added.

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

New `UpdateMealDraftCommand` variants and corresponding branches in `UpdateMealDraftUseCase` (`when` is exhaustive — these must be added together):

```kotlin
sealed interface UpdateMealDraftCommand {
    // … existing variants …
    data class SetDetected(val detected: List<IngredientSlug>, val version: String) : UpdateMealDraftCommand
    data class SetIngredients(val ingredients: List<IngredientSlug>) : UpdateMealDraftCommand
}

// In UpdateMealDraftUseCase.invoke:
is SetDetected -> draft.copy(
    detectedIngredients = command.detected,
    ingredients = command.detected,        // initial mirror — replaces, not merges
    classifierVersion = command.version,
)
is SetIngredients -> draft.copy(ingredients = command.ingredients)
```

`SetDetected` writes **both** `detectedIngredients` and `ingredients`. This is the rule that makes "user does nothing → publish with detected" work. `SetIngredients` only touches the user-confirmed list, leaving the raw prediction frozen — that's what gives us the human-correction signal in the dataset.

### 4.4 `MealDraftJson` schema extension (`:feature:meal/data/local/`)

`MealDraftLocalStore` serializes to a hand-rolled `MealDraftJson` (see existing `MealDraftLocalStore.kt:27-39`). Adding fields to `MealDraft` does **not** auto-extend the persisted JSON — schema must be updated explicitly or every process-restart loses the classification fields. Required edits:

```kotlin
@Serializable
private data class MealDraftJson(
    // … existing fields …
    val ingredients: List<String> = emptyList(),
    val detectedIngredients: List<String> = emptyList(),
    val classifierVersion: String? = null,
)
```

Plus `MealDraftJson.from(draft)` writes the three new fields (`.map { it.value }`), and `MealDraftJson.toDomain()` rehydrates them with `IngredientSlug(...)` after filtering blanks. Covered by a new `MealDraftLocalStoreTest` (§11.1).

### 4.5 `Account` reserved fields (`:feature:auth/domain/`)

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
- Subscribes to a Firestore **snapshot listener** on `ingredients` collection at first access (not a one-shot read). The listener fires on any admin edit — picker stays current within the session without a reload.
- Persists every snapshot to DataStore (`IngredientCatalogCache`) as the JSON-serialized map. On cold start the StateFlow emits the cached value immediately, then is reconciled by the first snapshot.
- Exposes `StateFlow<Map<IngredientSlug, Ingredient>>`.
- The listener is detached when the last subscriber goes away (`shareIn(SharingStarted.WhileSubscribed(5_000))`) so we're not paying for a permanent connection.

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
- If `confidence < 0.30` → `ClassifierError.Run.LowConfidence`. Banner "Couldn't detect ingredients" — user proceeds normally.
- If `confidence ≥ 0.30` but `dishSlug` is not in `dishIngredientMap` (model knows the class, we haven't seeded a mapping yet) → `ClassifierError.Run.DishUnmapped`. Same UX banner. Distinct error leaf so Crashlytics tells us "model coverage is fine, our seed lags" vs "model itself fails".

### 6.4 Errors

```kotlin
sealed interface ClassifierError {
    sealed interface Load : ClassifierError {
        data object ModelMissing  : Load
        data object ModelCorrupt  : Load
    }
    sealed interface Run : ClassifierError {
        data object DecodeFailed    : Run
        data object InferenceFailed : Run
        data object LowConfidence   : Run    // top-1 < 0.30
        data object DishUnmapped    : Run    // top-1 above threshold but no dishIngredientMap entry
    }
}
```

- `Load.*` is reported to Crashlytics as non-fatal (build/assets bug, not expected at runtime).
- `Run.*` is counted in custom Crashlytics keys (`classifier_run_failures`, dimensioned by leaf name) but **not** reported as non-fatal. `LowConfidence` and `DishUnmapped` will dominate; that's data, not a bug.

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
- Selection cap: once `draftIngredients.size == 30`, further unselected rows render disabled with a tooltip `IngredientStringKey.SelectionFull` ("Máximo 30 ingredientes"). Already-selected rows always toggle off. The UI is the cap contract; the repository treats >30 as a programmer error (`MealError.Validation.TooManyIngredients`, §4.2).
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

**Re-capture behavior.** If the user goes back, retakes the photo, and returns to compose with a new `photoBytes`, the `LaunchedEffect(photoBytes)` fires again. The new `SetDetected` **replaces** both `detectedIngredients` and `ingredients` — manual edits from the previous photo are discarded. Rationale: the previous photo's ingredients are about a different plate; preserving them would silently associate corrections with the wrong image. This is intentional, not an open question.

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

Four exhaustive mappers, each with a `commonTest` lock:

- `:feature:meal-ai/presentation/MealAiErrorToStringKey.kt` — covers all `ClassifierError` leaves (`Load.ModelMissing`, `Load.ModelCorrupt`, `Run.DecodeFailed`, `Run.InferenceFailed`, `Run.LowConfidence`, `Run.DishUnmapped`).
- `:feature:ingredient/presentation/IngredientErrorToStringKey.kt` — covers `IngredientError` leaves.
- `:feature:ingredient/presentation/IngredientCategoryToStringKey.kt` — exhaustive `when` over the 12 category leaves; not an error, but the same exhaustiveness lock.
- `:feature:meal/presentation/MealErrorToStringKey.kt` — extended with one new leaf: `MealError.Validation.TooManyIngredients` (§4.2).

**`MealAiStringKey`** (en/es):
- `Classifying` — "Analizando ingredientes…" / "Analyzing ingredients…"
- `ClassifierBannerNoDetection` — covers both `LowConfidence` and `DishUnmapped` (same UX)
- `ClassifierBannerLoadFailed` — covers `Load.*`

**`IngredientStringKey`** (en/es):
- `SelectIngredientsTitle`, `SelectIngredientsSearchHint`, `SelectionFull`, `IngredientsRowAdd`, `IngredientsRowSummary` (parameterized `"%1$d ingredientes"`).
- `DetectedSectionTitle` — "Detectados" / "Detected".
- `CatalogLoadFailed`, `CatalogEmpty`, `RetryAction`.
- All 12 category keys: `CategoryVegetable`, `CategoryFruit`, `CategoryMeat`, `CategoryFish`, `CategoryDairy`, `CategoryGrain`, `CategoryLegume`, `CategorySauce`, `CategorySpice`, `CategorySweet`, `CategoryBeverage`, `CategoryOther`.
- "Unknown ingredient" fallback string for slugs not in the current catalog (§8): `UnknownIngredient`.

All en/es bilingual in each module's `composeResources/values{,-es}/strings.xml`.

The ingredient *names themselves* live in the Firestore catalog (`names.en`, `names.es`), resolved at the repository — a deliberate departure from the `StringKey`-only convention for data that changes server-side without app releases. Acknowledged precedent for the project.

## 11. Testing

### 11.1 Unit tests (`commonTest`)

- `ClassifyPlateUseCaseTest` — fake `MealClassifierPort` returns canned `DishLabel`; fake `IngredientReadPort` returns canned `defaultIngredients`. Verifies use case wires them and returns `Result.Ok(slugs)`. Failure modes covered: `LowConfidence`, `DishUnmapped`, `InferenceFailed`.
- `ComposePlateViewModelTest` (extend existing):
  - On `photoBytes` set, `state.detectedIngredients` populates from classifier.
  - `state.draftIngredients` mirrors `detectedIngredients` initially (`SetDetected` writes both — §4.3).
  - Classifier failure → `state.classifierError` set, `canContinue` stays `true`.
  - **Re-capturing the photo overwrites both `detectedIngredients` and `ingredients`** (§7.3 re-capture semantics) — user edits to the previous photo are discarded.
- `SelectIngredientsViewModelTest` — search by alias, multi-select toggle, category collapse/expand, 30-cap UI gating, empty-state.
- `IngredientRepositoryTest` — observes the `StateFlow` against a fake datasource emitting snapshot updates; verifies DataStore cache rehydration on cold start before the listener fires; verifies live snapshot updates propagate.
- `MealDraftLocalStoreTest` — write a draft with `ingredients`, `detectedIngredients`, `classifierVersion` populated; reload from DataStore; round-trip preserves all three (covers C2 from review).
- `MealDtoMapperTest` — domain Meal with 30 ingredients → DTO → domain round-trip preserves all three new fields; >30 truncated input rejected at repository (`MealError.Validation.TooManyIngredients`); unknown slugs preserved on DTO→domain (§8).
- `UpdateMealDraftUseCaseTest` (extend existing) — `SetDetected` writes both lists + version; `SetIngredients` writes only `ingredients`.
- `MealAiErrorToStringKeyTest`, `IngredientErrorToStringKeyTest`, `IngredientCategoryToStringKeyTest`, `MealErrorToStringKeyTest` — exhaustive `when` matchers (the `MealError` test gains the `TooManyIngredients` row).

### 11.2 Konsist tests (`androidHostTest`)

- `:core:domain` — all new files (`Ingredient.kt`, `IngredientCategory.kt`, `IngredientReadPort.kt`, `DishLabel.kt`, `MealClassifierPort.kt`, `ClassifierError.kt`) import only stdlib + `kotlinx-datetime` + `kotlinx-coroutines-core`. The existing `KonsistRulesTest` already enforces this for the whole module — verify the new files pass without exemption.
- `:feature:meal-ai` — does not import any other `:feature:*`. `domain/` does not import `org.jetbrains.compose.resources` (the asset is loaded from `data/` only).
- `:feature:ingredient` — does not import any other `:feature:*`. `domain/` does not import Compose or Firebase.

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

- **License-clean model.** Retrain on Open Images food subset or owned data before public launch. **Blocker for public launch.**
- **Consent UI** that bumps `Account.dataConsentVersion`. **Blocker for any non-internal release** (including friends-and-family alpha). Without it, every published Meal accumulates as an unconsented training datum (decision #7 from brainstorm + decision #8 reserved fields). The current `:feature:meal-ai` design will silently grow a backlog of `(ingredients, detectedIngredients)` pairs that cannot be used externally until consent has been retroactively obtained — which in practice means deletion. Ship consent before any external user posts.
- **Cloud LLM fallback.** If on-device accuracy is poor, add a `MealClassifierPort` actual that calls Gemini Flash via a Cloud Function for photos where on-device confidence < threshold.
- **Ingredient chips on feed cards.** A glanceable summary on `FrFeedMealCard`.
- **`IngredientFrequency` leaderboard** in `:feature:stats`.
- **Cloud Function `exportAnonymizedMeals`** for B2B dataset partnerships. Requires consent UI shipped first.
- **Object-detection variant** that gives bounding boxes (for an AR-ish "tap the rice" UX).
- **Per-meal nutrition estimation** from `ingredients` + serving heuristics.

## 14. Open questions

- **Model evaluation pipeline.** We have no internal test set of plate photos yet. Build a small (200-image) eval set during implementation so we can measure regression if/when we swap the model.
- **`iconKey` source.** Initial drawables can be Material symbol mapped from category, or hand-curated SVG icons. Lean toward category-based until volume justifies bespoke art. Confirm that `FrIcon` and `FrText` exist in `:core:designsystem` today (referenced by `FrIngredientRow` in §7.4); if missing, scaffold them as part of this release.
- **Cocoapod link verification.** Confirm during scaffold that `MediaPipeTasksVision` links cleanly without re-export through the `FoodRatsShared` umbrella; if iOS link fails, add `linkerOpts("-framework", "MediaPipeTasksVision")` per the existing `CoreLocation` pattern.
