package com.danielportelabyrne.cinema

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import com.danielportelabyrne.cinema.ui.account.Headshots
import java.io.File

/**
 * Debug builds only: checks the profile-picture headshots on real photos. Reads every .jpg in this app's external
 * files folder (start it once first, so the app makes the folder itself), finds the face as the picker does (Headshots), and writes hs-sheet.png beside them: each photo with
 * the frame it would use, and the disc the picker would show (or "rejected" when it would not be offered).
 *
 *     adb push photos/. /sdcard/Android/data/<package>/files/
 *     adb shell am start -n <package>/com.danielportelabyrne.cinema.HeadshotCheckActivity
 */
class HeadshotCheckActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread {
            val dir = getExternalFilesDir(null) ?: return@Thread
            val files = dir.listFiles { f -> f.name.endsWith(".jpg") }?.sortedBy { it.name }.orEmpty()
            if (files.isEmpty()) { Log.i("HeadshotCheck", "done: no photos in $dir"); runOnUiThread { finish() }; return@Thread }
            val cell = 300; val cols = 6
            val rows = (files.size + cols - 1) / cols
            val sheet = Bitmap.createBitmap(cols * cell, rows * cell, Bitmap.Config.ARGB_8888)
            val c = Canvas(sheet); c.drawColor(0xFF0B0807.toInt())
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f }
            var kept = 0
            files.forEachIndexed { i, f ->
                val photo = BitmapFactory.decodeFile(f.path) ?: return@forEachIndexed
                val x0 = (i % cols) * cell.toFloat(); val y0 = (i / cols) * cell.toFloat()
                val face = Headshots.find(photo)
                // Left: the photo, small, with the frame drawn on it. Right: the disc.
                val thumbH = cell - 40f; val thumbW = thumbH * photo.width / photo.height
                val thumb = RectF(x0 + 6, y0 + 30, x0 + 6 + thumbW, y0 + 30 + thumbH)
                c.drawBitmap(photo, null, thumb, p)
                val disc = RectF(x0 + 6 + thumbW + 8, y0 + 60, x0 + cell - 8f, y0 + 60 + (cell - 14 - thumbW - 8))
                val r = disc.width() / 2f; val cx = disc.centerX(); val cy = disc.top + r
                if (face != null) {
                    kept++
                    val fr = Headshots.frame(face, photo.width, photo.height)
                    val k = thumbH / photo.height
                    p.style = Paint.Style.STROKE; p.color = 0xFFF6BF33.toInt(); p.strokeWidth = 2f
                    c.drawRect(thumb.left + fr.left * k, thumb.top + fr.top * k, thumb.left + fr.right * k, thumb.top + fr.bottom * k, p)
                    p.style = Paint.Style.FILL
                    c.save(); val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }; c.clipPath(clip)
                    c.drawBitmap(photo, Rect(fr.left.toInt(), fr.top.toInt(), fr.right.toInt(), fr.bottom.toInt()), RectF(cx - r, cy - r, cx + r, cy + r), p)
                    c.restore()
                    c.drawText("${f.nameWithoutExtension} %.2f".format(Headshots.score(face, photo.width)), x0 + 8, y0 + 22, text)
                } else {
                    c.drawText("${f.nameWithoutExtension} rejected", x0 + 8, y0 + 22, text)
                }
            }
            File(dir, "hs-sheet.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Log.i("HeadshotCheck", "done: ${files.size} photos, $kept headshots")
            runOnUiThread { finish() }
        }.start()
    }
}
