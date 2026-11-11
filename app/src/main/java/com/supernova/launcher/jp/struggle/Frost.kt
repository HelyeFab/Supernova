package com.supernova.launcher.jp.struggle

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import kotlin.random.Random

// JP fork: the Struggle widget's frosted glass (Glass.kt in anki-struggle-widget),
// but fed by the view's real position on screen, so it never needs calibrating:
// the wallpaper patch behind the card, blurred, washed milky, grained, rimmed.
// Reading the wallpaper image needs MANAGE_EXTERNAL_STORAGE on Android 13+
// (appops); without it this returns null and the card uses a plain tint.
object Frost {
    private var wallpaper: Bitmap? = null
    private var wallpaperId = -1
    private var cacheKey: String? = null
    private var cacheBmp: Bitmap? = null

    @Synchronized
    fun render(ctx: Context, onScreen: Rect, screenW: Int, screenH: Int, radiusPx: Float, night: Boolean): Bitmap? {
        if (onScreen.width() < 20 || onScreen.height() < 20 || screenW <= 0 || screenH <= 0) return null
        val wp = loadWallpaper(ctx) ?: return null
        val key = "${onScreen.flattenToString()}|$screenW|$screenH|$wallpaperId|$night|$radiusPx"
        if (key == cacheKey) cacheBmp?.let { return it }

        // The launcher shows a static wallpaper centre-cropped to the screen.
        val scale = maxOf(screenW.toFloat() / wp.width, screenH.toFloat() / wp.height)
        val offX = (wp.width * scale - screenW) / 2f
        val offY = (wp.height * scale - screenH) / 2f
        val src = Rect(
            ((onScreen.left + offX) / scale).toInt(), ((onScreen.top + offY) / scale).toInt(),
            ((onScreen.right + offX) / scale).toInt(), ((onScreen.bottom + offY) / scale).toInt()
        )

        val outW = onScreen.width() / 2
        val outH = onScreen.height() / 2
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val radius = radiusPx / 2f
        val shape = RectF(0f, 0f, outW.toFloat(), outH.toFloat())

        c.save()
        c.clipPath(Path().apply { addRoundRect(shape, radius, radius, Path.Direction.CW) })
        val small = Bitmap.createBitmap(maxOf(4, outW / 14), maxOf(4, outH / 14), Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(wp, src, Rect(0, 0, small.width, small.height), Paint(Paint.FILTER_BITMAP_FLAG))
        boxBlur(small, 2); boxBlur(small, 2)
        c.drawBitmap(small, null, shape, Paint(Paint.FILTER_BITMAP_FLAG))

        val tintTop = if (night) Color.argb(110, 30, 40, 38) else Color.argb(150, 255, 255, 255)
        val tintBottom = if (night) Color.argb(150, 14, 20, 19) else Color.argb(95, 240, 248, 246)
        c.drawRect(shape, Paint().apply {
            shader = LinearGradient(0f, 0f, outW * 0.6f, outH.toFloat(), tintTop, tintBottom, Shader.TileMode.CLAMP)
        })
        val rnd = Random(42)
        val grain = Paint()
        for (i in 0 until outW * outH / 6) {
            val a = rnd.nextInt(6, 22)
            grain.color = if (rnd.nextBoolean()) Color.argb(a, 255, 255, 255) else Color.argb(a, 0, 0, 0)
            c.drawPoint(rnd.nextInt(outW).toFloat(), rnd.nextInt(outH).toFloat(), grain)
        }
        c.restore()
        c.drawRoundRect(RectF(0.5f, 0.5f, outW - 0.5f, outH - 0.5f), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.2f
            shader = LinearGradient(0f, 0f, 0f, outH.toFloat(),
                Color.argb(if (night) 90 else 200, 255, 255, 255),
                Color.argb(if (night) 30 else 70, 255, 255, 255), Shader.TileMode.CLAMP)
        })
        cacheKey = key
        cacheBmp = out
        return out
    }

    private fun loadWallpaper(ctx: Context): Bitmap? = try {
        val wm = WallpaperManager.getInstance(ctx)
        val wid = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        if (wallpaper == null || wid != wallpaperId) {
            @Suppress("DEPRECATION")
            wallpaper = (wm.drawable as? BitmapDrawable)?.bitmap
            wallpaperId = wid
        }
        wallpaper
    } catch (_: Exception) {
        null
    }

    private fun boxBlur(b: Bitmap, r: Int) {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
            val outer = if (horizontal) h else w
            val inner = if (horizontal) w else h
            for (o in 0 until outer) for (i in 0 until inner) {
                var a = 0; var rr = 0; var g = 0; var bb = 0; var n = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, inner - 1)
                    val p = if (horizontal) src[o * w + j] else src[j * w + o]
                    a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; bb += p and 255; n++
                }
                val idx = if (horizontal) o * w + i else i * w + o
                dst[idx] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (bb / n)
            }
        }
        pass(px, tmp, true); pass(tmp, px, false)
        b.setPixels(px, 0, w, 0, 0, w, h)
    }
}
