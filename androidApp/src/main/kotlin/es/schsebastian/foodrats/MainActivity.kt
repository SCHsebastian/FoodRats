package es.schsebastian.foodrats

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import es.schsebastian.foodrats.app.root.FoodRatsApp
import es.schsebastian.foodrats.feature.notifications.platform.PermissionLauncherHolder
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val launcherHolder: PermissionLauncherHolder by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Register the permission launcher BEFORE the lifecycle hits STARTED.
        if (Build.VERSION.SDK_INT >= 33) {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                launcherHolder.deliver(granted)
            }
            launcherHolder.register(launcher)
        }

        // Bind a () -> Activity? lambda into Koin so AndroidNotificationPermissionGateway can read
        // shouldShowRequestPermissionRationale().
        val activityRef: () -> android.app.Activity? = { this@MainActivity }
        getKoin().declare(activityRef, allowOverride = true)

        setContent { FoodRatsApp() }
    }

    override fun onDestroy() {
        launcherHolder.clear()
        super.onDestroy()
    }
}
