package es.schsebastian.foodrats.core.data.share

import android.content.Context
import android.content.Intent

class ShareControllerAndroid(private val applicationContext: Context) : ShareController {
    override fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(chooser)
    }
}
