package es.schsebastian.foodrats.feature.notifications.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.domain.repository.NotificationPermissionGateway

class AndroidNotificationPermissionGateway(
    private val appContext: Context,
    private val activityProvider: () -> Activity?,        // resolved at call-time; MainActivity wires it
    private val launcherHolder: PermissionLauncherHolder,
) : NotificationPermissionGateway {

    override suspend fun current(): NotificationPermission {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return NotificationPermission.Granted   // implicit pre-Tiramisu
        val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return if (granted) NotificationPermission.Granted else NotificationPermission.NotYetRequested
    }

    override suspend fun request(): NotificationPermission {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return NotificationPermission.Granted
        val activity = activityProvider() ?: return NotificationPermission.Denied
        val granted = launcherHolder.requestAsync(Manifest.permission.POST_NOTIFICATIONS)
        if (granted) return NotificationPermission.Granted
        // If we can't show rationale anymore, system marks it "Don't ask again" → DeniedForever.
        val canShowRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        return if (canShowRationale) NotificationPermission.Denied else NotificationPermission.DeniedForever
    }

    override fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", appContext.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(intent)
    }
}
