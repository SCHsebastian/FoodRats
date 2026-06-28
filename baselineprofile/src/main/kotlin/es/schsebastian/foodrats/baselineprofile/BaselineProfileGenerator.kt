package es.schsebastian.foodrats.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile for [APP_PACKAGE].
 *
 * Run on a rooted/userdebug device or a Gradle Managed Device (see the module build script):
 *
 *   ./gradlew :baselineprofile:generateBaselineProfile
 *
 * The output (`baseline-prof.txt`) is collected by the `androidx.baselineprofile` plugin and merged
 * into `:androidApp`'s release ART profile, which ships in the AAB (roadmap §5.3).
 *
 * Two journeys are recorded; the plugin merges them:
 *  - [generate] — the unauthenticated cold start (Splash → SignIn), valid on ANY build type.
 *  - [generateAuthenticated] — the authenticated landing (Splash → Main → Feed → scroll → composer),
 *    which only reaches the Feed on the **benchmark** build type, where androidApp/src/benchmark/
 *    installs the fake-session backdoor (no Google Sign-In, no network). On `release`/`debug` the
 *    app stops at SignIn and this journey records only the pre-auth path — still a valid profile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = APP_PACKAGE,
        // Compile the whole journey we exercise; default heuristics are fine for a startup profile.
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        waitForFirstScreen()
    }

    /**
     * Authenticated startup journey. On the benchmark build the fake session boots straight to a
     * populated Feed: we wait for the scrollable feed, scroll it a few times to warm the
     * LazyColumn item code, then open the composer route. The landing is part of the startup
     * profile because an already-signed-in user's real cold start lands on this very Feed.
     */
    @Test
    fun generateAuthenticated() = rule.collect(
        packageName = APP_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        if (waitForFeed()) {
            scrollFeed()
            openComposer()
        } else {
            // Not the benchmark build (or the fake session is unavailable): fall back to the
            // unauthenticated screen so the run still yields a usable profile.
            waitForFirstScreen()
        }
    }
}

internal const val APP_PACKAGE = "es.schsebastian.foodrats"

/**
 * Wait until the first interactive frame is on screen. The app launches to Splash and then to
 * SignIn within a couple of seconds; we wait on the window content settling rather than a specific
 * resource-id so the profile keeps working if the SignIn layout changes. If/when a stable test tag
 * exists on the first screen, prefer `device.wait(Until.hasObject(By.res(...)), timeout)`.
 */
internal fun MacrobenchmarkScope.waitForFirstScreen() {
    device.waitForIdle(FIRST_SCREEN_TIMEOUT_MS)
    // Best-effort settle: wait for any clickable element (the Google Sign-In button) to appear.
    device.wait(Until.hasObject(By.clickable(true)), FIRST_SCREEN_TIMEOUT_MS)
}

/**
 * Wait for the authenticated Feed to render, signalled by its scrollable container (the feed
 * LazyColumn). Returns `true` once a scrollable surface appears (the authed landing reached),
 * `false` on timeout (unauthenticated build → stuck at SignIn, which has no scrollable list).
 *
 * NOTE: a stable `testTag("feedRoot")` on the Feed root would be more robust than `By.scrollable`;
 * adding it lives in :feature:feed (outside this module's scope). Until then this content-shape
 * heuristic is the stable Feed signal we can assert here.
 */
internal fun MacrobenchmarkScope.waitForFeed(): Boolean {
    device.waitForIdle(FEED_TIMEOUT_MS)
    // Until.hasObject yields a Boolean (false on timeout); `== true` also null-guards the platform type.
    return device.wait(Until.hasObject(By.scrollable(true)), FEED_TIMEOUT_MS) == true
}

/** Scroll the feed a few times so the LazyColumn item/row code is exercised and profiled. */
internal fun MacrobenchmarkScope.scrollFeed() {
    val feed = device.findObject(By.scrollable(true)) ?: return
    feed.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION)
    repeat(FEED_SCROLL_PASSES) {
        feed.fling(Direction.DOWN)
        device.waitForIdle(SCROLL_SETTLE_MS)
    }
    feed.fling(Direction.UP)
    device.waitForIdle(SCROLL_SETTLE_MS)
}

/**
 * Open the composer route from the dock's capture button. Best-effort: targeted by the English
 * capture content-description (the benchmark device defaults to the `en` locale). If not found the
 * profile still captures the Feed + scroll path.
 */
internal fun MacrobenchmarkScope.openComposer() {
    val capture = device.findObject(By.desc(CAPTURE_CONTENT_DESC)) ?: return
    capture.click()
    device.waitForIdle(FEED_TIMEOUT_MS)
}

// English value of shared `nav_capture_cta` — the dock capture button's contentDescription.
private const val CAPTURE_CONTENT_DESC = "Add today's meal"
private const val FIRST_SCREEN_TIMEOUT_MS = 5_000L
private const val FEED_TIMEOUT_MS = 10_000L
private const val SCROLL_SETTLE_MS = 500L
private const val FEED_SCROLL_PASSES = 3
private const val GESTURE_MARGIN_FRACTION = 5
