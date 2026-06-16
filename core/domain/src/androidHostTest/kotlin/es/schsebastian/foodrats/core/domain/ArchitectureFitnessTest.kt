package es.schsebastian.foodrats.core.domain

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test
import kotlin.test.fail

/**
 * Whole-project architecture fitness functions (roadmap #4).
 *
 * These lock in load-bearing invariants documented in the root CLAUDE.md and the
 * design spec but previously only enforced by code review. Each rule is scoped by
 * PACKAGE (never class-name suffix — suffix-matching false-positives on legit
 * adapter files like `FirestoreAccountWriter`, `HasPostedTodayAdapter`, the
 * MediaPipe classifiers). Every rule passes against the current (fixed) tree.
 *
 * The narrower `:core:domain`-only import rule lives in [KonsistRulesTest]; this
 * class adds the project-wide rules via [Konsist.scopeFromProject].
 */
class ArchitectureFitnessTest {

    private val featureRoots = listOf(
        "auth", "crew", "feed", "ingredient", "meal", "mealai", "notifications", "stats",
    )

    // ---------------------------------------------------------------------------------------------
    // Rule 1 — Feature isolation.
    // No file in `...feature.<A>...` imports from `...feature.<B>...` (A != B). Cross-feature
    // collaboration goes only through ports declared in `core`. Note: `feature/meal-ai` uses the
    // package segment `mealai` (no hyphen), so the match is on the package segment, not the dir.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun feature_does_not_import_another_feature() {
        Konsist.scopeFromProject()
            .files
            .filter { file -> file.packagee?.name?.let(::featureSegmentOf) != null }
            .assertFalse { file ->
                val ownFeature = featureSegmentOf(file.packagee!!.name)
                file.imports.any { imp ->
                    val target = featureSegmentOf(imp.name)
                    target != null && target != ownFeature
                }
            }
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 2 — Dispatcher boundary (the unambiguous half).
    // ZERO `withContext(` in any file under a `...domain.usecase` package or a `...presentation`
    // package — i.e. use cases and ViewModels never switch dispatchers; that boundary lives in
    // repositories only. (We deliberately do NOT enforce "exactly one withContext per repo
    // method": that half must special-case Flow-returning methods and the legit nested
    // `IngredientRepository` cache write, which is too brittle to encode reliably — see report.)
    // Preview/sample composables are excluded.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun usecases_and_viewmodels_do_not_switch_dispatchers() {
        Konsist.scopeFromProject()
            .files
            .filter { file -> file.hasPackage("..domain.usecase..") || file.hasPackage("..presentation..") }
            .assertFalse { file ->
                file.functions(includeNested = true, includeLocal = true)
                    .filterNot(::isPreviewOrSample)
                    .any { fn -> WITH_CONTEXT_CALL.containsMatchIn(fn.text) }
            }
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 3 — designsystem purity.
    // No file in `core.designsystem` may import a domain type (`...core.domain...`) or Firebase.
    // The `...designsystem.preview` package (ThemeGallery + FrPreview helpers — a dev-only
    // design-review surface) is excluded from the check.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun designsystem_does_not_import_domain_or_firebase() {
        Konsist.scopeFromProject()
            .files
            .filter { file -> file.hasPackage("es.schsebastian.foodrats.core.designsystem..") }
            .filterNot { file -> file.hasPackage("..core.designsystem.preview..") }
            .assertFalse { file ->
                file.imports.any { imp ->
                    imp.name.startsWith("es.schsebastian.foodrats.core.domain") ||
                        imp.name.startsWith("com.google.firebase") ||
                        imp.name.startsWith("dev.gitlive.firebase")
                }
            }
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 4 — No hardcoded user-facing strings in feature production code.
    // A Compose `Text("literal")` / `FrText("literal")` outside `composeResources`/catalog/preview
    // is a hardcoded string — all user-visible text must flow through `resolve(StringKey)`.
    // Scoped to feature `...presentation..` packages (production UI). Preview/sample composables
    // are excluded; the catalog module and the designsystem preview package are out of scope by
    // construction (not feature presentation). The designsystem's own literals all live in
    // preview/sample functions, so they aren't user-facing app copy and are not in scope here.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun feature_ui_has_no_hardcoded_text_literals() {
        Konsist.scopeFromProject()
            .files
            .filter { file -> file.hasPackage("..feature..presentation..") }
            .assertFalse { file ->
                file.functions(includeNested = true, includeLocal = true)
                    .filterNot(::isPreviewOrSample)
                    .any { fn -> TEXT_STRING_LITERAL.containsMatchIn(fn.text) }
            }
    }

    // ---------------------------------------------------------------------------------------------
    // Rule 5 — Catalog coverage.
    // Every public `Fr*` composable in `:core:designsystem` (atoms/molecules/templates) has a
    // catalog entry. The catalog is the design-review surface; a public component absent from it
    // is invisible to designers. We assert each public `Fr*` composable NAME appears in one of the
    // four catalog story files (`stories/*Stories.kt`). Preview/sample helpers are excluded.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun every_public_designsystem_composable_has_catalog_entry() {
        val scope = Konsist.scopeFromProject()

        val publicComposables = scope
            .files
            .filter { file ->
                file.hasPackage("..core.designsystem.atoms..") ||
                    file.hasPackage("..core.designsystem.molecules..") ||
                    file.hasPackage("..core.designsystem.templates..")
            }
            .flatMap { it.functions(includeNested = false, includeLocal = false) }
            .filter { fn -> fn.name.startsWith("Fr") }
            .filter { fn -> fn.hasAnnotation { it.name == "Composable" } }
            .filter { fn -> fn.hasPublicOrDefaultModifier }
            .filterNot(::isPreviewOrSample)
            .map { it.name }
            .distinct()

        val catalogText = scope
            .files
            .filter { it.hasPackage("es.schsebastian.foodrats.catalog.stories..") }
            .joinToString("\n") { it.text }

        val uncatalogued = publicComposables.filter { name ->
            // Word-boundary match so `FrCard` doesn't accidentally satisfy `FrCardX`.
            !Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(catalogText)
        }

        if (uncatalogued.isNotEmpty()) {
            fail(
                "Public Fr* composables missing a :catalogApp story entry " +
                    "(stories/*Stories.kt): $uncatalogued",
            )
        }
        // Sanity: prove the scan actually found composables (guards against a silent empty scope).
        if (publicComposables.isEmpty()) {
            fail("Expected to find public Fr* composables in :core:designsystem but found none")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Returns the feature segment (e.g. "meal", "mealai") of a dotted name, or null. */
    private fun featureSegmentOf(dottedName: String): String? {
        val match = FEATURE_SEGMENT.find(dottedName) ?: return null
        return match.groupValues[1].takeIf { it in featureRoots }
    }

    private fun isPreviewOrSample(fn: KoFunctionDeclaration): Boolean =
        fn.name.endsWith("Preview") ||
            fn.name.endsWith("Sample") ||
            fn.hasAnnotation { ann -> ann.name.contains("Preview") }

    private companion object {
        private val FEATURE_SEGMENT =
            Regex("""es\.schsebastian\.foodrats\.feature\.([a-z0-9]+)""")

        /** `withContext(` — the dispatcher-switch call we forbid in use cases / ViewModels. */
        private val WITH_CONTEXT_CALL = Regex("""\bwithContext\s*\(""")

        /** `Text("…")` or `FrText("…")` with a non-empty string literal first argument. */
        private val TEXT_STRING_LITERAL = Regex("""\b(Fr)?Text\s*\(\s*"[^"]""")
    }
}
