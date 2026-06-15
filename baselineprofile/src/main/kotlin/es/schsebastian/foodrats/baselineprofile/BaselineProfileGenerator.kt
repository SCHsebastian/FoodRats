package es.schsebastian.foodrats.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
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
 * The output (`baseline-prof.txt`) is collected by the `androidx.baselineprofile` plugin and
 * merged into `:androidApp`'s release ART profile, which ships in the AAB (roadmap §5.3).
 *
 * Journey: a cold start that lands on the unauthenticated first screen (Splash → SignIn). The app
 * gates everything behind Google Sign-In, so a fuller authenticated journey (feed scroll, composer)
 * can't be driven headlessly without test credentials — see [waitForFirstScreen] and the module
 * report for how to extend this once a test account / sign-in bypass exists.
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
}

internal const val APP_PACKAGE = "es.schsebastian.foodrats"

/**
 * Wait until the first interactive frame is on screen. The app launches to Splash and then to
 * SignIn within a couple of seconds; we wait on the window content settling rather than a specific
 * resource-id so the profile keeps working if the SignIn layout changes. If/when a stable test tag
 * exists on the first screen, prefer `device.wait(Until.hasObject(By.res(...)), timeout)`.
 */
internal fun androidx.benchmark.macro.MacrobenchmarkScope.waitForFirstScreen() {
    device.waitForIdle(FIRST_SCREEN_TIMEOUT_MS)
    // Best-effort settle: wait for any clickable element (the Google Sign-In button) to appear.
    device.wait(Until.hasObject(By.clickable(true)), FIRST_SCREEN_TIMEOUT_MS)
}

private const val FIRST_SCREEN_TIMEOUT_MS = 5_000L
