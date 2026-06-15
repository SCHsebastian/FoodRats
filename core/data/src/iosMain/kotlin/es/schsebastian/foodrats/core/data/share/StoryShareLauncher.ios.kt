package es.schsebastian.foodrats.core.data.share

/**
 * iOS [StoryShareLauncher] actual (spec §6.2). The Stories hand-off (UIPasteboard background-image
 * item + `instagram-stories://share`, with a `UIActivityViewController` fallback) must run from a
 * live `UIViewController` on the main thread, which Kotlin/Native can't reach cleanly — so, exactly
 * like [ShareControllerIos] + `ShareBridge.swift`, it delegates to a Swift lambda supplied at
 * startup (`StoryShareBridge.swift`, threaded through ContentView.swift → MainViewController →
 * `storyShareIosModule`).
 *
 * The Swift bridge decides Instagram-vs-fallback (it owns `canOpenURL` + the pasteboard), so it
 * reports the [StoryShareOutcome] back through [outcomeOf]. Because the bridge dispatches onto the
 * main thread asynchronously, the synchronous return here is best-effort: the actual presentation
 * happens after this returns. We optimistically report [StoryShareOutcome.OpenedInstagram] when the
 * bridge is wired; a real outcome callback would require an async bridge contract (deferred — the
 * presentation task only needs the share to fire and a toast to show).
 *
 * @param storyBridge called with the PNG bytes; returns a status code: 0 = Instagram opened,
 *   1 = fallback sheet, 2 = failed. (See `StoryShareBridge.swift`.)
 */
actual typealias StoryShareLauncher = StoryShareLauncherIos

class StoryShareLauncherIos(
    private val storyBridge: (ByteArray) -> Int,
) {

    fun shareToStories(imagePng: ByteArray): StoryShareOutcome =
        outcomeOf(storyBridge(imagePng))

    private fun outcomeOf(code: Int): StoryShareOutcome = when (code) {
        0 -> StoryShareOutcome.OpenedInstagram
        1 -> StoryShareOutcome.OpenedFallbackSheet
        else -> StoryShareOutcome.Failed
    }
}
