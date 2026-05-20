package es.schsebastian.foodrats.core.domain.telemetry

/**
 * Lightweight, tag-scoped logger for debugging KMP code paths.
 *
 * Writes to `println` so output appears in Android Logcat (tagged `I/System.out`)
 * and the iOS Xcode console. No platform deps — safe to call from `:core:domain`.
 *
 * Tags are free-form strings. The [Tags] constants exist for discoverability of
 * the conventional ones, but any tag is valid; unknown tags default to enabled
 * (under the master switch). The fluent lambda form skips message construction
 * entirely when a tag is off, so leaving instrumentation in place is cheap.
 *
 * Configure at app boot or from a debug menu, e.g.:
 *
 * ```
 * FrLog.disable(FrLog.Tags.Prefs)           // shut up DataStore noise
 * FrLog.disable("MVI/FeedViewModel")        // mute one chatty ViewModel
 * FrLog.enabled = false                     // master kill switch
 * ```
 *
 * `MviViewModel` auto-emits under `"MVI/<VmClassName>"` (intents, state Δ, effects).
 *
 * TODO(scope = "observability"): research how comparable apps wire production
 * metrics/telemetry (Crashlytics breadcrumbs, OpenTelemetry, custom analytics
 * pipelines) and decide whether FrLog should sprout a sink abstraction for
 * release builds or live as a debug-only println. For now it's debug-only.
 */
object FrLog {
    /**
     * Conventional tag names — call sites are free to use any string. These
     * constants exist for discoverability and to keep typos out of frequently-
     * used tags.
     */
    object Tags {
        const val SignOut    = "SignOut"
        const val Session    = "Session"
        const val Auth       = "Auth"
        const val ActiveCrew = "ActiveCrew"
        const val RootNav    = "RootNav"
        const val Prefs      = "Prefs"
        const val Lifecycle  = "Lifecycle"
        /** Prefix used by [es.schsebastian.foodrats.core.presentation.mvi.MviViewModel] — full tag is `MVI/<VmClassName>`. */
        const val Mvi        = "MVI"
    }

    /** Master switch — when false, nothing logs regardless of per-tag state. */
    var enabled: Boolean = true

    /** Tags explicitly turned off; unknown tags default to ENABLED (under the master switch). */
    @PublishedApi
    internal val disabledTags: MutableSet<String> = mutableSetOf()

    fun isEnabled(tag: String): Boolean = enabled && tag !in disabledTags
    fun enable(tag: String)  { disabledTags.remove(tag) }
    fun disable(tag: String) { disabledTags.add(tag) }

    inline fun d(tag: String, message: () -> String) {
        if (isEnabled(tag)) println("FR/$tag ${message()}")
    }

    inline fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isEnabled(tag)) {
            println("FR/$tag ⚠ ${message()}")
            throwable?.printStackTrace()
        }
    }
}
