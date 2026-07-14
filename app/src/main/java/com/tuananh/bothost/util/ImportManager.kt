package com.tuananh.bothost.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object ImportManager {
    fun copyZipToSharedDownloads(context: Context, source: Uri, id: String): String {
        val fileName = "bothost_$id.zip"
        val input = context.contentResolver.openInputStream(source)
            ?: error("Không mở được file ZIP đã chọn.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BotHost")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Không tạo được file trong Download/BotHost.")
            context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                input.use { it.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return "/sdcard/Download/BotHost/$fileName"
        }

        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BotHost")
        dir.mkdirs()
        val target = File(dir, fileName)
        input.use { sourceStream -> FileOutputStream(target).use { sourceStream.copyTo(it) } }
        return target.absolutePath
    }
}
