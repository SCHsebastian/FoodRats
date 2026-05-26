package es.schsebastian.foodrats

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Cold start from an App Link / custom-scheme URL. RootNavViewModel parses + routes it.
        publishDeepLink(intent)

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

    // Warm start: the activity is `singleTop`, so a new App Link reuses this instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishDeepLink(intent)
    }

    /** Forward only VIEW-action URLs (real deep links) — the LAUNCHER intent carries no data. */
    private fun publishDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.dataString?.let(deepLinkBus::publish)
    }

    override fun onDestroy() {
        launcherHolder.clear()
        locationLauncherHolder.clear()
        super.onDestroy()
    }
}
