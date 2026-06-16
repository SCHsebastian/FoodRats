package es.schsebastian.foodrats.core.data.share

/**
 * The result of a share-to-Stories attempt (spec §4.3). Not a `Result<T, E>` — there is no
 * recoverable domain error to `when`-exhaust; these three states are a UI affordance (they pick the
 * toast, §10), and the launcher is fire-and-present (it just starts an Activity / presents a
 * controller). Never thrown back as a failure taxonomy.
 */
enum class StoryShareOutcome {
    /** Instagram Stories opened with the card as the full-screen background. */
    OpenedInstagram,

    /** Instagram was unavailable; the system share sheet opened with the PNG attached. */
    OpenedFallbackSheet,

    /** Even the fallback sheet could not be presented (no Activity / no view controller). */
    Failed,
}

/**
 * Hands a rendered card PNG to Instagram Stories, with a graceful fallback to the system share sheet
 * (spec §4.3 / §6). Never throws — it always returns a [StoryShareOutcome].
 *
 * actuals:
 *  - androidMain — `com.instagram.share.ADD_TO_STORY` with a `FileProvider` content:// URI +
 *    `FLAG_GRANT_READ_URI_PERMISSION`; falls back to `ACTION_SEND` (image/png) in a chooser.
 *  - iosMain — bridges to Swift (`StoryShareBridge.swift`): `UIPasteboard` background-image item +
 *    `instagram-stories://share`; falls back to `UIActivityViewController`. The Swift glue mirrors
 *    `ShareBridge.swift` and is supplied as a lambda at startup.
 */
expect class StoryShareLauncher {
    /**
     * Attempts to open Instagram Stories with [imagePng] as the full-screen background. Falls back to
     * the system share sheet when Instagram is not installed or its Stories intent is unavailable.
     */
    fun shareToStories(imagePng: ByteArray): StoryShareOutcome
}
