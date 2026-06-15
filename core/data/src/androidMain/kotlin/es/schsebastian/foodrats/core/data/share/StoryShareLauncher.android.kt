package es.schsebastian.foodrats.core.data.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android [StoryShareLauncher] actual (spec §6.1). Writes the PNG to the app cache, exposes it via a
 * [FileProvider] content:// URI, and tries the Instagram-Stories explicit intent first; on failure it
 * falls back to a system `ACTION_SEND` chooser. Never throws — every path resolves to a
 * [StoryShareOutcome].
 *
 * The exported image is a flat PNG: no signed URL is embedded; the pixels are the payload (spec §9).
 * Read permission is granted per-share via `FLAG_GRANT_READ_URI_PERMISSION`.
 *
 * Requires a `<provider>` for `androidx.core.content.FileProvider` with authority
 * `${applicationId}.fileprovider` and an `@xml/file_paths` exposing the cache dir — see the manifest
 * + `res/xml/file_paths.xml` (spec §6.1 / §15 step 8).
 */
actual typealias StoryShareLauncher = StoryShareLauncherAndroid

class StoryShareLauncherAndroid(
    private val context: Context,
) {

    fun shareToStories(imagePng: ByteArray): StoryShareOutcome {
        val uri = runCatching { writePngAndGetUri(imagePng) }.getOrNull()
            ?: return StoryShareOutcome.Failed

        return when {
            tryInstagramStories(uri) -> StoryShareOutcome.OpenedInstagram
            tryFallbackChooser(uri) -> StoryShareOutcome.OpenedFallbackSheet
            else -> StoryShareOutcome.Failed
        }
    }

    private fun writePngAndGetUri(imagePng: ByteArray): Uri {
        val dir = File(context.cacheDir, ShareCardFiles.CACHE_SUBDIR).apply { mkdirs() }
        val file = File(dir, ShareCardFiles.fileName())
        file.writeBytes(imagePng)
        return FileProvider.getUriForFile(
            context,
            ShareCardFiles.fileProviderAuthority(context.packageName),
            file,
        )
    }

    private fun tryInstagramStories(uri: Uri): Boolean {
        val intent = Intent(INSTAGRAM_ADD_TO_STORY).apply {
            setDataAndType(uri, ShareCardFiles.PNG_MIME)
            putExtra("source_application", context.packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // resolveActivity returns null when Instagram is absent / the intent is unhandled.
        if (intent.resolveActivity(context.packageManager) == null) return false
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    private fun tryFallbackChooser(uri: Uri): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = ShareCardFiles.PNG_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { context.startActivity(chooser); true }.getOrDefault(false)
    }

    private companion object {
        const val INSTAGRAM_ADD_TO_STORY = "com.instagram.share.ADD_TO_STORY"
    }
}
