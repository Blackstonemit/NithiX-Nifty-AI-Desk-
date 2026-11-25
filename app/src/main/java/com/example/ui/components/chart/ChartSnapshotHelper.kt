package com.example.ui.components.chart

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.example.R
import java.io.File
import java.io.FileOutputStream

/**
 * Exports a rendered chart Compose Picture into a crisp temporary PNG inside cacheDir
 * and launches the zero-permission Android Share Sheet via FileProvider.
 */
object ChartSnapshotHelper {
    fun shareChartPicture(
        context: Context,
        picture: Picture,
        symbol: String,
        timeframeLabel: String
    ): Result<String> = runCatching {
        val width = picture.width.coerceAtLeast(1)
        val height = picture.height.coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawPicture(picture)

        val dir = File(context.cacheDir, "chart_snapshots").apply { mkdirs() }
        val cleanSym = symbol.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "chart_${cleanSym}_${timeframeLabel}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.snapshot_subject_format, symbol, timeframeLabel))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.snapshot_chooser_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        context.getString(R.string.snapshot_ready_format, file.name)
    }
}
