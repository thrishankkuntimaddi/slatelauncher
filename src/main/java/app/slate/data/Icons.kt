package app.slate.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Renders every app icon into the same rounded-square shape.
 *  - Colour: the app's real artwork.
 *  - Mono:   a dark glass tile with the logo in soft white (themed icon layer when the app has one).
 */
class IconCache(context: Context, private val repo: AppRepo) {
    private val cache = LruCache<String, ImageBitmap>(500)

    fun peek(app: App, px: Int, style: IconStyle): ImageBitmap? = cache.get(id(app, px, style))

    suspend fun load(app: App, px: Int, style: IconStyle): ImageBitmap? {
        val id = id(app, px, style)
        cache.get(id)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { render(app, px, style) }.getOrNull()?.asImageBitmap()?.also { cache.put(id, it) }
        }
    }

    fun clear() = cache.evictAll()

    private fun id(app: App, px: Int, style: IconStyle) = "${app.key}@$px/${style.name}"

    private fun render(app: App, size: Int, style: IconStyle): Bitmap? {
        val icon = repo.activityInfo(app)?.getIcon(0) ?: return null
        return when (style) {
            IconStyle.Colour -> colour(icon, size)
            IconStyle.Mono -> mono(icon, size)
        }
    }

    /** Superellipse ("squircle"): |x|^5 + |y|^5 = 1 — smooth, continuous corners. */
    private fun shape(size: Int): Path = Path().apply {
        val r = size / 2f
        val n = 5.0
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = kotlin.math.cos(t); val s = kotlin.math.sin(t)
            val x = r + r * Math.signum(c) * Math.pow(kotlin.math.abs(c), 2 / n)
            val y = r + r * Math.signum(s) * Math.pow(kotlin.math.abs(s), 2 / n)
            if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
        }
        close()
    }

    private fun colour(icon: Drawable, size: Int): Bitmap {
        val art = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(art)
        if (icon is AdaptiveIconDrawable) {
            val pad = size / 4
            icon.background?.let { it.setBounds(-pad, -pad, size + pad, size + pad); it.draw(c) }
            icon.foreground?.let { it.setBounds(-pad, -pad, size + pad, size + pad); it.draw(c) }
        } else {
            // Legacy icon: sit it on a white tile so every icon shares one shape.
            c.drawColor(Color.WHITE)
            val inset = (size * 0.14f).toInt()
            icon.setBounds(inset, inset, size - inset, size - inset)
            icon.draw(c)
        }
        return mask(art, size)
    }

    private fun mono(icon: Drawable, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawPath(shape(size), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 38, 38, 44) })

        val glyphSize = (size * 0.56f).toInt()
        val glyph = glyph(icon, glyphSize * 2)?.let { normalize(it, glyphSize) } ?: return out
        val o = (size - glyphSize) / 2f
        c.drawBitmap(glyph, o, o, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun mask(art: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawPath(shape(size), p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(art, 0f, 0f, p)
        return out
    }

    /** White logo on transparency. */
    private fun glyph(icon: Drawable, size: Int): Bitmap? {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (icon is AdaptiveIconDrawable) {
            val pad = size / 4
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                icon.monochrome?.mutate()?.let { m ->
                    m.setTint(Color.WHITE)
                    m.setBounds(-pad, -pad, size + pad, size + pad)
                    m.draw(c)
                    return bmp
                }
            }
            val fg = icon.foreground ?: return null
            fg.setBounds(-pad, -pad, size + pad, size + pad)
            fg.draw(c)
        } else {
            icon.setBounds(0, 0, size, size)
            icon.draw(c)
        }
        toWhiteTones(bmp)
        return bmp
    }

    private fun normalize(src: Bitmap, size: Int): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[x + y * w] ushr 24) > 40) {
                if (x < l) l = x; if (x > r) r = x
                if (y < t) t = y; if (y > b) b = y
            }
        }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        if (r < l || b < t) return out
        val cw = (r - l + 1).toFloat(); val ch = (b - t + 1).toFloat()
        val scale = size / maxOf(cw, ch)
        val dw = cw * scale; val dh = ch * scale
        Canvas(out).drawBitmap(
            src, Rect(l, t, r + 1, b + 1),
            RectF((size - dw) / 2f, (size - dh) / 2f, (size + dw) / 2f, (size + dh) / 2f),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return out
    }

    /** Brightness → white opacity. Logos printed on a solid tile keep only what differs from the tile. */
    private fun toWhiteTones(bmp: Bitmap) {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val lum = FloatArray(px.size)
        var sum = 0.0; var n = 0
        for (i in px.indices) {
            val c = px[i]
            if ((c ushr 24) < 24) continue
            val v = (0.299f * ((c shr 16) and 0xff) + 0.587f * ((c shr 8) and 0xff) + 0.114f * (c and 0xff)) / 255f
            lum[i] = v; sum += v; n++
        }
        if (n == 0) return
        val mean = (sum / n).toFloat()
        val tile = n > px.size * 0.55f
        val bg = if (tile) {
            val hist = IntArray(32)
            fun add(i: Int) { if ((px[i] ushr 24) >= 24) hist[(lum[i] * 31).toInt()]++ }
            for (x in 0 until w) { add(x + (h / 6) * w); add(x + (h - 1 - h / 6) * w) }
            for (y in 0 until h) { add(w / 6 + y * w); add(w - 1 - w / 6 + y * w) }
            (hist.indices.maxBy { hist[it] } + 0.5f) / 32f
        } else 0f
        val invert = !tile && mean < 0.35f
        for (i in px.indices) {
            val a = px[i] ushr 24
            if (a == 0) continue
            val s = if (tile) ((abs(lum[i] - bg) - 0.08f) * 3f).coerceIn(0f, 1f)
            else 0.35f + 0.65f * (if (invert) 1f - lum[i] else lum[i])
            px[i] = ((a * s).toInt().coerceIn(0, 255) shl 24) or 0xF2F2F2
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }
}
