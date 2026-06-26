package es.schsebastian.foodrats.benchmark

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.koin.core.context.loadKoinModules

/**
 * BENCHMARK-ONLY backdoor installer (STARTUP-4).
 *
 * Why a ContentProvider, not an Application subclass: `FoodRatsApplication` is `final` and lives in
 * src/main (out of this track's scope), so it can't be subclassed/edited. Instead this provider —
 * declared ONLY in androidApp/src/benchmark/AndroidManifest.xml — is merged into the `benchmark`
 * variant alone and auto-instantiated at process start (the same manifest-merged auto-install seam
 * AndroidX App Startup / LeakCanary use). It keeps the real [FoodRatsApplication] and only LAYERS
 * the fake-session Koin override on top.
 *
 * Timing: `ContentProvider.onCreate` runs during `handleBindApplication`, BEFORE
 * `Application.onCreate` (where `startKoin` runs). So we don't load the override here — Koin isn't
 * started yet. Instead we register an `ActivityLifecycleCallbacks` and load it in
 * [onActivityPreCreated] of the first Activity (API 29+, fine at minSdk 30): by then
 * `Application.onCreate` (hence `startKoin`) has completed, and it runs BEFORE the launcher
 * Activity's `onCreate`/composition — so every later resolution (RootNavViewModel, FeedViewModel)
 * already sees the fakes. Koin allows overrides by default, so each re-bound `single` replaces the
 * real port.
 *
 * SECURITY: this class and [benchmarkSessionOverrideModule] exist only under src/benchmark/; the
 * release and debug variants never compile or merge them, so the fake signed-in session is
 * unreachable from any shipped or local app.
 */
class BenchmarkBackdoorInstaller : ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                // startKoin has run (Application.onCreate completed during bindApplication); install
                // the override once, before any Activity/composition resolves the real ports.
                app.unregisterActivityLifecycleCallbacks(this)
                loadKoinModules(benchmarkSessionOverrideModule)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        return true
    }

    // No data surface — this provider only exists to run [onCreate] at process start.
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
