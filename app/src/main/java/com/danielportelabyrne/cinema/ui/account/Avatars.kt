package com.danielportelabyrne.cinema.ui.account

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.danielportelabyrne.cinema.R

/**
 * What a person can be on Cinema, drawn in a disc: one of the characters (pictures in res/drawable-nodpi/av_*.png,
 * cut from art/characters-sheet.png by scripts/cut_characters.py), a character from a favourite live-action film
 * or series (the actor's TMDB photo, "cast:<photo path>|<character>"), a favourite animation's own artwork
 * ("art:<image address>|<title>"), or, with nothing chosen, the person's initial.
 */
object Avatars {
    class Character(val id: String, val name: String, val drawable: Int)

    val ALL = listOf(
        Character("popcorn", "Popcorn", R.drawable.av_popcorn),
        Character("reel", "Film reel", R.drawable.av_reel),
        Character("clapper", "Clapperboard", R.drawable.av_clapper),
        Character("ghost", "Ghost", R.drawable.av_ghost),
        Character("alien", "Alien", R.drawable.av_alien),
        Character("robot", "Robot", R.drawable.av_robot),
        Character("vampire", "Vampire", R.drawable.av_vampire),
        Character("cowboy", "Cowboy", R.drawable.av_cowboy),
        Character("astronaut", "Astronaut", R.drawable.av_astronaut),
        Character("cat", "Cat", R.drawable.av_cat),
        Character("monster", "Monster", R.drawable.av_monster),
        Character("director", "Director", R.drawable.av_director),
    )

    fun byId(id: String?): Character? = ALL.firstOrNull { it.id == id }

    // --- Pictures from films and series -------------------------------------------------------------------------

    fun castId(profilePath: String, character: String) = "cast:$profilePath|$character"
    fun artId(url: String, title: String) = "art:$url|$title"
    /** A picture fetched from the web (a cast photo or a title's artwork), not a drawn character. */
    fun isCast(id: String?) = id?.startsWith("cast:") == true || id?.startsWith("art:") == true
    /** The character's (or the title's) name a picture stands for. */
    fun castName(id: String?): String? = id?.takeIf { isCast(it) }?.substringAfterLast('|')
    private fun pictureUrl(id: String) = if (id.startsWith("art:")) id.removePrefix("art:").substringBeforeLast('|')
        else "https://image.tmdb.org/t/p/w185" + id.removePrefix("cast:").substringBefore('|')

    private const val CREAM = 0xFFF6ECDB.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()
    private val photos = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val waiting = java.util.concurrent.ConcurrentHashMap<String, MutableList<java.lang.ref.WeakReference<View>>>()

    /** Fetches a picture once (kept while Cinema runs) and redraws [view] when it has come. */
    private fun ensure(url: String, view: View?) {
        if (photos.containsKey(url)) return
        val list = waiting.getOrPut(url) { java.util.Collections.synchronizedList(ArrayList()) }
        view?.let { list += java.lang.ref.WeakReference(it) }
        if (list.size > 1) return   // already on its way
        Thread {
            val b = try { java.net.URL(url).openStream().use { android.graphics.BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
            if (b != null) photos[url] = b
            waiting.remove(url)?.forEach { ref -> ref.get()?.let { v -> v.post { v.invalidate() } } }
        }.start()
    }

    /** One person's picture in a disc of radius [r] about ([cx], [cy]). [view] is what to redraw when a picture arrives. */
    fun draw(c: Canvas, id: String?, cx: Float, cy: Float, r: Float, initial: String, ground: Int = 0xFF3A2519.toInt(), serif: Typeface? = null, view: View? = null) {
        box.set(cx - r, cy - r, cx + r, cy + r)
        val drawable = byId(id)?.let { ch -> view?.context?.getDrawable(ch.drawable) }
        when {
            drawable != null -> {
                // The character pictures are square cut-outs of round artwork: shown in the disc only.
                val save = c.save()
                path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW); c.clipPath(path)
                drawable.setBounds(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt()); drawable.draw(c)
                c.restoreToCount(save)
            }
            id != null && isCast(id) -> drawPicture(c, id, cx, cy, r, view, serif)
            else -> letter(c, initial, cx, cy, r, ground, serif)
        }
    }

    /** A letter (the initial, or "+") on a disc, centred on its ink rather than its baseline. */
    fun letter(c: Canvas, text: String, cx: Float, cy: Float, r: Float, ground: Int, serif: Typeface?, ink: Int = CREAM, size: Float = 1.0f) {
        p.style = Paint.Style.FILL; p.shader = null
        if (ground != 0) { p.color = ground; c.drawCircle(cx, cy, r, p) }
        p.color = ink; p.textAlign = Paint.Align.LEFT; p.textSize = r * size; p.typeface = serif
        val b = Rect(); p.getTextBounds(text, 0, text.length, b)
        c.drawText(text, cx - (b.left + b.right) / 2f, cy - (b.top + b.bottom) / 2f, p)
        p.typeface = null
    }

    /**
     * A cast photo cropped to the face (TMDB's portraits run from the top of the head: a square from just below the
     * top, a little narrower than the photo); a title's artwork from its upper middle, where its characters stand.
     */
    private fun drawPicture(c: Canvas, id: String, cx: Float, cy: Float, r: Float, view: View?, serif: Typeface?) {
        val url = pictureUrl(id)
        val bitmap = photos[url]
        if (bitmap == null) {
            ensure(url, view)
            letter(c, castName(id)?.firstOrNull { it.isLetter() }?.uppercase() ?: "?", cx, cy, r, 0xFF3A2519.toInt(), serif)
            return
        }
        val w = bitmap.width; val h = bitmap.height
        val save = c.save()
        path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW); c.clipPath(path)
        // A soft ground behind, for drawings with see-through edges and photos narrower than the disc.
        p.style = Paint.Style.FILL; p.shader = null; p.color = if (id.startsWith("art:")) 0xFFF6ECDB.toInt() else 0xFF2A1D16.toInt()
        c.drawCircle(cx, cy, r, p)
        p.isFilterBitmap = true
        if (id.startsWith("art:")) {
            // A character's drawing (often the whole figure): wide enough to fill the disc, from just above its head, so
            // the head and shoulders show rather than a small full-length figure.
            val scale = maxOf(box.width() / w * 1.25f, minOf(box.width() / w, box.height() / h))
            val dw = w * scale; val dh = h * scale
            val top = box.top + r * 0.08f
            c.drawBitmap(bitmap, Rect(0, 0, w, h), RectF(cx - dw / 2, top, cx + dw / 2, top + dh), p)
        } else {
            // A cast photo zoomed out: head and shoulders, from the top of the portrait, its full width across the disc.
            val tall = minOf(h, (w * 1.12f).toInt())
            val dh = box.height(); val dw = dh * w / tall
            c.drawBitmap(bitmap, Rect(0, 0, w, tall), RectF(cx - dw / 2, box.top, cx + dw / 2, box.bottom), p)
        }
        c.restoreToCount(save)
    }
}
