package com.themis.mobile.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ShareUtils {
    private const val INSTAGRAM = "com.instagram.android"
    private const val INSTAGRAM_STORY = "com.instagram.share.ADD_TO_STORY"

    /**
     * Writes the PNG into cacheDir/shared_images and returns a content:// URI (never file://).
     * PNG encoding runs on Dispatchers.IO, and older share images are deleted first to save storage.
     */
    suspend fun saveToCache(ctx: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "shared_images").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "workout_share_${System.currentTimeMillis()}.png")   // new name each time: no stale caching
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    }

    /**
     * Opens Instagram's Story editor with the image as the full-screen background.
     * If Instagram isn't installed (or refuses), falls back to the system share sheet.
     */
    fun shareToInstagramStory(ctx: Context, uri: Uri) {
        val story = Intent(INSTAGRAM_STORY).apply {
            setDataAndType(uri, "image/png")
            setPackage(INSTAGRAM)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val installed = ctx.packageManager.queryIntentActivities(story, 0).isNotEmpty()
        if (installed) {
            ctx.grantUriPermission(INSTAGRAM, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                ctx.startActivity(story)
                return
            } catch (_: ActivityNotFoundException) { /* fall through to the share sheet */ }
        }
        shareGeneric(ctx, uri)
    }

    /** System share sheet: WhatsApp, X, Messages, "Save to Photos" and so on. */
    fun shareGeneric(ctx: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)     // lets the chooser pass the read grant on to the picked app
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share your workout"))
    }
}
