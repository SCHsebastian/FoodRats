package es.schsebastian.foodrats.core.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp

private var androidContext: Context? = null

fun installAndroidFirebaseContext(context: Context) {
    androidContext = context.applicationContext
}

actual object FirebaseInitializer {
    actual fun init() {
        val ctx = androidContext
            ?: error("Call installAndroidFirebaseContext(this) in Application.onCreate before FirebaseInitializer.init()")
        if (FirebaseApp.getApps(ctx).isEmpty()) FirebaseApp.initializeApp(ctx)
    }
}
