package es.schsebastian.foodrats

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import es.schsebastian.foodrats.app.navigation.DeepLinkBus
import es.schsebastian.foodrats.app.root.FoodRatsApp
import es.schsebastian.foodrats.core.data.location.LocationPermissionLauncherHolder
import es.schsebastian.foodrats.feature.notifications.platform.PermissionLauncherHolder
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val launcherHolder: PermissionLauncherHolder by inject()
    private val locationLauncherHolder: LocationPermissionLauncherHolder by inject()
    private val deepLinkBus: DeepLinkBus by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the branded SplashScreen (Theme.FoodRats.Splash) before super.onCreate; it
        // hands off to postSplashScreenTheme once the first frame is ready.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Cold start from an App Link / custom-scheme URL. RootNavViewModel parses + routes it.
        // Only on a FRESH launch (savedInstanceState == null): a recreation (rotation, dark-mode
        // flip, locale change, process-death restore from recents) re-runs onCreate with the SAME
        // intent — republishing would yank the user back to the linked screen on every config
        // change after a notification tap. A genuinely new tap on a live activity arrives via
        // onNewIntent below, so nothing is lost by skipping recreations.
        if (savedInstanceState == null) publishDeepLink(intent)

        // Register both permission launchers BEFORE the lifecycle hits STARTED.
        // Each runtime permission gets its own launcher so unrelated permissions never share state.
        if (Build.VERSION.SDK_INT >= 33) {
            val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                launcherHolder.deliver(granted)
            }
            launcherHolder.register(notificationLauncher)
        }
        val locationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            locationLauncherHolder.deliver(granted)
        }
        locationLauncherHolder.register(locationLauncher)

        // Bind a () -> Activity? lambda into Koin so AndroidNotificationPermissionGateway can read
        // shouldShowRequestPermissionRationale().
        val activityRef: () -> android.app.Activity? = { this@MainActivity }
        getKoin().declare(activityRef, allowOverride = true)

        setContent { FoodRatsApp() }
    }

    // Warm start: the activity is `singleTop`, so a new App Link OR notification tap reuses this instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishDeepLink(intent)
    }

    /**
     * Turn an incoming intent into a [DeepLinkBus] publish from either source:
     *  - App Link / custom-scheme tap → the URL is the intent `data` on a VIEW action.
     *  - FCM notification tap → a backgrounded notification-message launches us with the push
     *    `data` payload as intent extras; the server puts the canonical deep link under `link`.
     *
     * A reminder/streak push carries no `link`, so nothing is published and the app simply opens
     * to Feed (the authenticated landing) — exactly the desired "just open it" behaviour.
     */
    private fun publishDeepLink(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_VIEW) intent.dataString?.let(deepLinkBus::publish)
        intent.getStringExtra(FCM_LINK_EXTRA)?.let(deepLinkBus::publish)
    }

    override fun onDestroy() {
        launcherHolder.clear()
        locationLauncherHolder.clear()
        super.onDestroy()
    }

    private companion object {
        // FCM data key carrying the canonical deep link (set server-side in functions/.../push.ts).
        const val FCM_LINK_EXTRA = "link"
    }
}
