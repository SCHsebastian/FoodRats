package es.schsebastian.foodrats.core.domain.telemetry

import kotlin.concurrent.Volatile

/**
 * Production sink for [FrLog] warnings/errors. Implemented in the data layer and
 * wired at app boot (release builds only) so `:core:domain` stays free of vendor
 * SDKs — the concrete sink forwards to the per-platform [CrashReporter].
 */
interface FrLogSink {
    /**
     * Forwards a warning/error. [tag] is the `FrLog` tag, [message] the rendered
     * text, [throwable] the optional cause (recorded as a non-fatal by the sink).
     */
    fun warn(tag: String, message: String, throwable: Throwable?)
}

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
 * ## Production sink
 *
 * In debug builds [sink] is null and the only output is `println` (Logcat / Xcode
 * console). In release builds the app boot installs an [FrLogSink] backed by the
 * per-platform [CrashReporter] (Crashlytics breadcrumbs on Android/iOS) — see
 * `frLogSinkModule` / `installFrLogSink`. Every [w] call forwards to `sink.warn(...)`;
 * the carried throwable (if any) is recorded as a non-fatal. The debug `println`
 * path is unchanged. The fluent lambda form still skips message construction when a
 * tag is off, so leaving instrumentation in place stays cheap.
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
        const val Notifications = "Notifications"
        const val Lifecycle  = "Lifecycle"
        /** Prefix used by [es.schsebastian.foodrats.core.presentation.mvi.MviViewModel] — full tag is `MVI/<VmClassName>`. */
        const val Mvi        = "MVI"
    }

    /** Master switch — when false, nothing logs regardless of per-tag state. */
    @Volatile var enabled: Boolean = true

    /**
     * Production observability sink — null in debug (println-only). Installed at app
     * boot in release builds (see `installFrLogSink`). Public because the [w] inline
     * function references it; assign through [installFrLogSink], not directly, off
     * call sites. Receives only warnings/errors ([w]), never debug spam ([d]).
     */
    @Volatile var sink: FrLogSink? = null

    /** Tags explicitly turned off; unknown tags default to ENABLED (under the master switch). */
    @PublishedApi
    internal val disabledTags: MutableSet<String> = mutableSetOf()

    fun isEnabled(tag: String): Boolean = enabled && tag !in disabledTags
    fun enable(tag: String)  { disabledTags.remove(tag) }
    fun disable(tag: String) { disabledTags.add(tag) }

    /** Installs the production [sink]. Idempotent; call once at app boot. */
    fun installSink(sink: FrLogSink) { this.sink = sink }

    inline fun d(tag: String, message: () -> String) {
        if (isEnabled(tag)) println("FR/$tag ${message()}")
    }

    inline fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        // Warnings/errors always reach the production sink (independent of the per-tag
        // debug switch) so a muted tag never silences release observability.
        val rendered = message()
        sink?.warn(tag, rendered, throwable)
        if (isEnabled(tag)) {
            println("FR/$tag ⚠ $rendered")
            throwable?.printStackTrace()
        }
    }
}
