package es.schsebastian.foodrats.core.domain.telemetry

/**
 * Lightweight, channel-scoped logger for debugging KMP code paths.
 *
 * Writes to `println` so output appears in Android Logcat (tagged `I/System.out`)
 * and the iOS Xcode console. No platform deps — safe to call from `:core:domain`.
 *
 * Each channel can be toggled independently, plus a master switch. The fluent
 * lambda form (`FrLog.d(Channel.SignOut) { "..." }`) skips message construction
 * entirely when the channel is off, so leaving instrumentation in place is cheap.
 *
 * Configure at app boot or from a debug menu, e.g.:
 *
 * ```
 * FrLog.disable(FrLog.Channel.Prefs)        // shut up DataStore noise
 * FrLog.setAll(false)                        // total silence
 * FrLog.enabled = false                      // master kill switch (preserves channel config)
 * ```
 */
object FrLog {
    enum class Channel {
        /** Sign-out flow across UI → ViewModel → Port → Repository → DataSource. */
        SignOut,
        /** `SessionProvider.current` emissions + Firebase auth-state ticks. */
        Session,
        /** Sign-in paths (Google/Email) + Firebase Auth events. */
        Auth,
        /** `ActiveCrewProvider.current` emissions + writes/clears. */
        ActiveCrew,
        /** `RootNavViewModel` stage transitions + emitted nav effects. */
        RootNav,
        /** DataStore reads/writes/clears via `AppPreferences`. */
        Prefs,
        /** `EventsEffect` lifecycle gate — drops/passes. */
        Lifecycle,
    }

    /** Master switch — when false, nothing logs regardless of per-channel state. */
    var enabled: Boolean = true

    private val channelState: MutableMap<Channel, Boolean> =
        Channel.entries.associateWithTo(mutableMapOf()) { true }

    fun isEnabled(channel: Channel): Boolean = enabled && channelState[channel] == true
    fun enable(channel: Channel) { channelState[channel] = true }
    fun disable(channel: Channel) { channelState[channel] = false }
    fun setAll(value: Boolean) { for (c in Channel.entries) channelState[c] = value }

    inline fun d(channel: Channel, message: () -> String) {
        if (isEnabled(channel)) println("FR/${channel.name} ${message()}")
    }

    inline fun w(channel: Channel, throwable: Throwable? = null, message: () -> String) {
        if (isEnabled(channel)) {
            println("FR/${channel.name} ⚠ ${message()}")
            throwable?.printStackTrace()
        }
    }
}
