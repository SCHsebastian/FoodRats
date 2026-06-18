package es.schsebastian.foodrats.core.data.share

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Process-wide holder for the currently-resumed [Activity], so a window-hosted off-screen capture
 * (see [StoryCardRendererAndroid]) can find a real window to attach its [androidx.compose.ui.platform.ComposeView]
 * to. A Compose tree only composes once attached to a window — there is no headless render path on
 * Android — so the renderer needs the foreground Activity.
 *
 * Must be installed from `Application.onCreate` ([install]), **before** the first Activity resumes —
 * `ActivityLifecycleCallbacks` only delivers future transitions, so registering lazily (e.g. on first
 * share) would miss the already-resumed Activity and leave [current] null.
 *
 * The reference is weak and cleared on pause, so this never keeps an Activity alive.
 */
object ForegroundActivityHolder {

    @Volatile
    private var activityRef: WeakReference<Activity> = WeakReference(null)
    @Volatile private var installed = false

    /** Registers lifecycle tracking. Idempotent. Call once from `Application.onCreate`. */
    fun install(application: Application) {
        if (installed) return
        installed = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                activityRef = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (activityRef.get() === activity) activityRef = WeakReference(null)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** The currently-resumed Activity, or null if none is in the foreground. */
    fun current(): Activity? = activityRef.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
}
