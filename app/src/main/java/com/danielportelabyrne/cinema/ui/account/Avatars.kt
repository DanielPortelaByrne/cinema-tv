package com.danielportelabyrne.cinema.ui.account

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * The little characters a person can be on Cinema: cinema things and film-night monsters, drawn in code (so they are
 * sharp at any size) inside a coloured disc. [draw] paints one into a circle of radius r around (cx, cy); with no
 * character chosen it paints the person's initial instead.
 */
object Avatars {
    class Character(val id: String, val name: String, val ground: Int)

    val ALL = listOf(
        Character("popcorn", "Popcorn", 0xFF7A2E22.toInt()),
        Character("reel", "Film reel", 0xFF2F6E66.toInt()),
        Character("clapper", "Clapperboard", 0xFF4D5B2C.toInt()),
        Character("ghost", "Ghost", 0xFF3B3355.toInt()),
        Character("alien", "Alien", 0xFF1F3B4D.toInt()),
        Character("robot", "Robot", 0xFF8A4B1E.toInt()),
        Character("vampire", "Vampire", 0xFF5A1620.toInt()),
        Character("cowboy", "Cowboy", 0xFF9A6A2E.toInt()),
        Character("astronaut", "Astronaut", 0xFF1B2440.toInt()),
        Character("cat", "Cat", 0xFF2F4F3A.toInt()),
        Character("monster", "Monster", 0xFF3E2A4F.toInt()),
        Character("director", "Director", 0xFFB0452B.toInt()),
    )

    fun byId(id: String?): Character? = ALL.firstOrNull { it.id == id }

    // --- Characters from films and series: the actor's TMDB photo, kept as "cast:<photo path>|<character>" ------

    fun castId(profilePath: String, character: String) = "cast:$profilePath|$character"
    fun isCast(id: String?) = id?.startsWith("cast:") == true
    fun castName(id: String?): String? = id?.takeIf { isCast(it) }?.substringAfter('|')
    private fun castUrl(id: String) = "https://image.tmdb.org/t/p/w185" + id.removePrefix("cast:").substringBefore('|')

    private val photos = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()
    private val waiting = java.util.concurrent.ConcurrentHashMap<String, MutableList<java.lang.ref.WeakReference<android.view.View>>>()

    /** Fetches the photo for [id] once (cached for the app's life) and redraws [view] when it has come. */
    private fun ensure(id: String, view: android.view.View?) {
        val url = castUrl(id)
        if (photos.containsKey(url)) return
        val list = waiting.getOrPut(url) { java.util.Collections.synchronizedList(ArrayList()) }
        view?.let { list += java.lang.ref.WeakReference(it) }
        if (list.size > 1 && view != null) return   // already on its way
        Thread {
            val b = try { java.net.URL(url).openStream().use { android.graphics.BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
            if (b != null) photos[url] = b
            waiting.remove(url)?.forEach { ref -> ref.get()?.let { v -> v.post { v.invalidate() } } }
        }.start()
    }

    /** The photo in the disc, cropped to the face (the top of a portrait), or the character's initial while it loads. */
    private fun drawCast(c: Canvas, id: String, cx: Float, cy: Float, r: Float, view: android.view.View?, serif: Typeface?) {
        val bitmap = photos[castUrl(id)]
        if (bitmap == null) {
            ensure(id, view)
            p.style = Paint.Style.FILL; p.color = 0xFF3A2519.toInt(); c.drawCircle(cx, cy, r, p)
            val letter = castName(id)?.firstOrNull { it.isLetter() }?.uppercase() ?: "?"
            p.color = CREAM; p.textAlign = Paint.Align.CENTER; p.textSize = r * 1.05f; p.typeface = serif
            val b = Rect(); p.getTextBounds(letter, 0, letter.length, b); c.drawText(letter, cx, cy + b.height() / 2f, p); p.typeface = null
            return
        }
        val side = minOf(bitmap.width, bitmap.height)
        val src = Rect((bitmap.width - side) / 2, 0, (bitmap.width + side) / 2, side)
        val save = c.save()
        path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW); c.clipPath(path)
        box.set(cx - r, cy - r, cx + r, cy + r)
        p.isFilterBitmap = true
        c.drawBitmap(bitmap, src, box, p)
        c.restoreToCount(save)
    }

    private const val CREAM = 0xFFF6ECDB.toInt()
    private const val INK = 0xFF1B120C.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()

    /** One character (or [initial] on [ground] when [id] is none) in a disc of radius [r]. */
    fun draw(c: Canvas, id: String?, cx: Float, cy: Float, r: Float, initial: String, ground: Int = 0xFF3A2519.toInt(), serif: Typeface? = null, view: android.view.View? = null) {
        if (id != null && isCast(id)) { drawCast(c, id, cx, cy, r, view, serif); return }
        val ch = byId(id)
        p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.color = ch?.ground ?: ground
        c.drawCircle(cx, cy, r, p)
        if (ch == null) {
            p.color = CREAM; p.textAlign = Paint.Align.CENTER; p.textSize = r * 1.05f; p.typeface = serif
            val b = Rect(); p.getTextBounds(initial, 0, initial.length, b)
            c.drawText(initial, cx, cy + b.height() / 2f, p)
            p.typeface = null
            return
        }
        // Everything is drawn in a unit square (-1..1) about the centre, clipped to the disc.
        val save = c.save()
        path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW); c.clipPath(path)
        c.translate(cx, cy); c.scale(r, r)
        when (ch.id) {
            "popcorn" -> popcorn(c); "reel" -> reel(c); "clapper" -> clapper(c); "ghost" -> ghost(c)
            "alien" -> alien(c); "robot" -> robot(c); "vampire" -> vampire(c); "cowboy" -> cowboy(c)
            "astronaut" -> astronaut(c); "cat" -> cat(c); "monster" -> monster(c); "director" -> director(c)
        }
        c.restoreToCount(save)
    }

    // --- Pieces ------------------------------------------------------------------------------------------------

    private fun fill(color: Int) { p.style = Paint.Style.FILL; p.color = color }
    private fun line(color: Int, w: Float) { p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = w }
    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float) { box.set(l, t, r, b); c.drawOval(box, p) }
    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, round: Float = 0f) { box.set(l, t, r, b); c.drawRoundRect(box, round, round, p) }

    /** Two round eyes with a glint, and a smile. */
    private fun face(c: Canvas, y: Float, gap: Float = 0.22f, eye: Float = 0.09f, smile: Boolean = true, eyeColor: Int = INK) {
        fill(eyeColor); c.drawCircle(-gap, y, eye, p); c.drawCircle(gap, y, eye, p)
        fill(CREAM); c.drawCircle(-gap + eye * 0.35f, y - eye * 0.35f, eye * 0.32f, p); c.drawCircle(gap + eye * 0.35f, y - eye * 0.35f, eye * 0.32f, p)
        if (smile) { line(eyeColor, 0.06f); box.set(-0.16f, y + 0.02f, 0.16f, y + 0.26f); c.drawArc(box, 20f, 140f, false, p) }
    }

    private fun popcorn(c: Canvas) {
        // A striped bucket, popcorn heaped on top.
        path.reset(); path.moveTo(-0.5f, -0.1f); path.lineTo(0.5f, -0.1f); path.lineTo(0.36f, 0.95f); path.lineTo(-0.36f, 0.95f); path.close()
        fill(CREAM); c.drawPath(path, p)
        fill(0xFFD8402E.toInt())
        listOf(-0.3f, 0.06f).forEach { x ->
            path.reset(); path.moveTo(x - 0.12f, -0.1f); path.lineTo(x + 0.12f, -0.1f); path.lineTo(x + 0.08f, 0.95f); path.lineTo(x - 0.1f, 0.95f); path.close(); c.drawPath(path, p)
        }
        path.reset(); path.moveTo(0.38f, -0.1f); path.lineTo(0.5f, -0.1f); path.lineTo(0.36f, 0.95f); path.lineTo(0.3f, 0.95f); path.close(); c.drawPath(path, p)
        fill(0xFFFFF4D6.toInt())
        listOf(-0.42f to -0.18f, -0.18f to -0.32f, 0.08f to -0.36f, 0.32f to -0.24f, 0.46f to -0.12f, -0.02f to -0.16f, -0.3f to -0.1f, 0.22f to -0.1f)
            .forEach { (x, y) -> c.drawCircle(x, y, 0.16f, p) }
        fill(0xFFEDBE56.toInt()); c.drawCircle(-0.12f, -0.28f, 0.05f, p); c.drawCircle(0.3f, -0.3f, 0.04f, p)
        face(c, 0.3f, gap = 0.17f, eye = 0.08f)
    }

    private fun reel(c: Canvas) {
        fill(0xFF1A1714.toInt()); c.drawCircle(0f, 0f, 0.78f, p)
        line(0xFF9A8F80.toInt(), 0.05f); c.drawCircle(0f, 0f, 0.76f, p)
        fill(0xFF2F6E66.toInt())
        for (i in 0 until 6) { val a = Math.toRadians(i * 60.0 - 90); c.drawCircle((0.48 * Math.cos(a)).toFloat(), (0.48 * Math.sin(a)).toFloat(), 0.15f, p) }
        fill(0xFFEDBE56.toInt()); c.drawCircle(0f, 0f, 0.3f, p)
        face(c, -0.04f, gap = 0.11f, eye = 0.06f)
    }

    private fun clapper(c: Canvas) {
        // The board, its striped arm raised, a face on the slate.
        fill(0xFF1A1714.toInt()); rect(c, -0.62f, -0.18f, 0.62f, 0.7f, 0.08f)
        c.save(); c.rotate(-16f, -0.62f, -0.2f)
        rect(c, -0.62f, -0.44f, 0.62f, -0.22f, 0.04f)
        fill(CREAM); for (i in 0 until 4) { path.reset(); val x = -0.52f + i * 0.32f; path.moveTo(x, -0.44f); path.lineTo(x + 0.14f, -0.44f); path.lineTo(x + 0.04f, -0.22f); path.lineTo(x - 0.1f, -0.22f); path.close(); c.drawPath(path, p) }
        c.restore()
        fill(CREAM); for (i in 0 until 4) { path.reset(); val x = -0.52f + i * 0.32f; path.moveTo(x, -0.18f); path.lineTo(x + 0.14f, -0.18f); path.lineTo(x + 0.04f, -0.02f); path.lineTo(x - 0.1f, -0.02f); path.close(); c.drawPath(path, p) }
        face(c, 0.24f, eyeColor = CREAM)
    }

    private fun ghost(c: Canvas) {
        path.reset(); path.moveTo(-0.55f, 1.1f); path.lineTo(-0.55f, -0.1f)
        box.set(-0.55f, -0.7f, 0.55f, 0.5f); path.arcTo(box, 180f, 180f)
        path.lineTo(0.55f, 1.1f); path.close()
        fill(0xFFF3EEF7.toInt()); c.drawPath(path, p)
        fill(INK); oval(c, -0.3f, -0.26f, -0.08f, 0.04f); oval(c, 0.08f, -0.26f, 0.3f, 0.04f)
        oval(c, -0.1f, 0.12f, 0.1f, 0.36f)
        fill(0xFFE9A0B0.toInt()); c.drawCircle(-0.36f, 0.12f, 0.07f, p); c.drawCircle(0.36f, 0.12f, 0.07f, p)
    }

    private fun alien(c: Canvas) {
        line(0xFF8FD16A.toInt(), 0.06f); c.drawLine(-0.22f, -0.5f, -0.38f, -0.84f, p); c.drawLine(0.22f, -0.5f, 0.38f, -0.84f, p)
        fill(0xFFEDBE56.toInt()); c.drawCircle(-0.38f, -0.84f, 0.08f, p); c.drawCircle(0.38f, -0.84f, 0.08f, p)
        fill(0xFF8FD16A.toInt()); path.reset(); path.moveTo(0f, 0.86f)
        path.cubicTo(-0.75f, 0.5f, -0.7f, -0.62f, 0f, -0.6f); path.cubicTo(0.7f, -0.62f, 0.75f, 0.5f, 0f, 0.86f); c.drawPath(path, p)
        fill(INK); c.save(); c.rotate(20f, -0.26f, 0f); oval(c, -0.44f, -0.14f, -0.1f, 0.12f); c.restore()
        c.save(); c.rotate(-20f, 0.26f, 0f); oval(c, 0.1f, -0.14f, 0.44f, 0.12f); c.restore()
        fill(CREAM); c.drawCircle(-0.22f, -0.04f, 0.04f, p); c.drawCircle(0.32f, -0.04f, 0.04f, p)
        line(INK, 0.05f); c.drawLine(-0.08f, 0.5f, 0.08f, 0.5f, p)
    }

    private fun robot(c: Canvas) {
        line(0xFFB8B2A8.toInt(), 0.06f); c.drawLine(0f, -0.52f, 0f, -0.78f, p)
        fill(0xFFD8402E.toInt()); c.drawCircle(0f, -0.82f, 0.09f, p)
        fill(0xFFCFC9BE.toInt()); rect(c, -0.6f, -0.52f, 0.6f, 0.62f, 0.16f)
        fill(0xFF8E887E.toInt()); rect(c, -0.74f, -0.14f, -0.6f, 0.22f, 0.04f); rect(c, 0.6f, -0.14f, 0.74f, 0.22f, 0.04f)
        fill(0xFF1A1714.toInt()); rect(c, -0.42f, -0.3f, 0.42f, 0.06f, 0.1f)
        fill(0xFF6FE0E8.toInt()); c.drawCircle(-0.2f, -0.12f, 0.09f, p); c.drawCircle(0.2f, -0.12f, 0.09f, p)
        fill(0xFF1A1714.toInt()); for (i in 0 until 4) rect(c, -0.3f + i * 0.16f, 0.24f, -0.22f + i * 0.16f, 0.44f, 0.02f)
    }

    private fun vampire(c: Canvas) {
        fill(0xFF1A1714.toInt()); path.reset(); path.moveTo(-1f, 1f); path.lineTo(-0.7f, 0.2f); path.lineTo(0.7f, 0.2f); path.lineTo(1f, 1f); path.close(); c.drawPath(path, p)
        fill(0xFFB0202E.toInt()); path.reset(); path.moveTo(-0.7f, 0.2f); path.lineTo(-0.95f, 1f); path.lineTo(-0.55f, 1f); path.close(); c.drawPath(path, p)
        path.reset(); path.moveTo(0.7f, 0.2f); path.lineTo(0.95f, 1f); path.lineTo(0.55f, 1f); path.close(); c.drawPath(path, p)
        fill(0xFFE8E1DA.toInt()); oval(c, -0.46f, -0.56f, 0.46f, 0.62f)
        fill(0xFF1A1714.toInt()); path.reset(); path.moveTo(-0.5f, -0.2f); path.cubicTo(-0.5f, -0.78f, 0.5f, -0.78f, 0.5f, -0.2f)
        path.lineTo(0.3f, -0.36f); path.lineTo(0f, -0.14f); path.lineTo(-0.3f, -0.36f); path.close(); c.drawPath(path, p)
        face(c, 0.04f, gap = 0.18f, eye = 0.07f, smile = false, eyeColor = 0xFFB0202E.toInt())
        line(INK, 0.05f); c.drawLine(-0.16f, 0.3f, 0.16f, 0.3f, p)
        fill(CREAM); path.reset(); path.moveTo(-0.12f, 0.3f); path.lineTo(-0.06f, 0.3f); path.lineTo(-0.09f, 0.42f); path.close(); c.drawPath(path, p)
        path.reset(); path.moveTo(0.06f, 0.3f); path.lineTo(0.12f, 0.3f); path.lineTo(0.09f, 0.42f); path.close(); c.drawPath(path, p)
    }

    private fun cowboy(c: Canvas) {
        fill(0xFFE2A774.toInt()); oval(c, -0.44f, -0.2f, 0.44f, 0.8f)
        fill(0xFF6B3E1E.toInt()); oval(c, -0.9f, -0.32f, 0.9f, -0.08f)
        path.reset(); path.moveTo(-0.44f, -0.22f); path.lineTo(-0.38f, -0.72f); path.quadTo(0f, -0.9f, 0.38f, -0.72f); path.lineTo(0.44f, -0.22f); path.close(); c.drawPath(path, p)
        fill(0xFFEDBE56.toInt()); rect(c, -0.42f, -0.36f, 0.42f, -0.26f)
        face(c, 0.2f, gap = 0.17f, eye = 0.07f)
        fill(0xFF5A3418.toInt()); path.reset(); path.moveTo(-0.24f, 0.5f); path.quadTo(0f, 0.38f, 0.24f, 0.5f); path.quadTo(0f, 0.46f, -0.24f, 0.5f); c.drawPath(path, p)
    }

    private fun astronaut(c: Canvas) {
        fill(0xFFF3F0EA.toInt()); c.drawCircle(0f, 0.04f, 0.72f, p)
        fill(0xFF1A1F33.toInt()); oval(c, -0.5f, -0.36f, 0.5f, 0.36f)
        fill(0xFF6FA8E8.toInt()); path.reset(); path.addOval(RectF(-0.4f, -0.3f, -0.06f, -0.08f), Path.Direction.CW); c.drawPath(path, p)
        fill(CREAM); c.drawCircle(0.28f, 0.18f, 0.04f, p); c.drawCircle(0.14f, 0.24f, 0.025f, p); c.drawCircle(-0.2f, 0.2f, 0.03f, p)
        fill(0xFFD8402E.toInt()); rect(c, -0.1f, 0.48f, 0.1f, 0.6f, 0.03f)
        fill(0xFFC8C2B8.toInt()); c.drawCircle(-0.74f, 0.04f, 0.1f, p); c.drawCircle(0.74f, 0.04f, 0.1f, p)
    }

    private fun cat(c: Canvas) {
        fill(0xFFE08A3C.toInt())
        path.reset(); path.moveTo(-0.62f, -0.1f); path.lineTo(-0.5f, -0.72f); path.lineTo(-0.16f, -0.42f); path.close(); c.drawPath(path, p)
        path.reset(); path.moveTo(0.62f, -0.1f); path.lineTo(0.5f, -0.72f); path.lineTo(0.16f, -0.42f); path.close(); c.drawPath(path, p)
        oval(c, -0.64f, -0.5f, 0.64f, 0.66f)
        fill(0xFFF5D6B4.toInt()); oval(c, -0.3f, 0.08f, 0.3f, 0.56f)
        fill(0xFFE9A0B0.toInt()); path.reset(); path.moveTo(-0.07f, 0.16f); path.lineTo(0.07f, 0.16f); path.lineTo(0f, 0.24f); path.close(); c.drawPath(path, p)
        fill(0xFF2F6E2E.toInt()); oval(c, -0.36f, -0.2f, -0.14f, 0.04f); oval(c, 0.14f, -0.2f, 0.36f, 0.04f)
        fill(INK); oval(c, -0.27f, -0.18f, -0.23f, 0.02f); oval(c, 0.23f, -0.18f, 0.27f, 0.02f)
        line(CREAM, 0.03f); c.drawLine(-0.3f, 0.28f, -0.66f, 0.2f, p); c.drawLine(-0.3f, 0.34f, -0.66f, 0.38f, p)
        c.drawLine(0.3f, 0.28f, 0.66f, 0.2f, p); c.drawLine(0.3f, 0.34f, 0.66f, 0.38f, p)
    }

    private fun monster(c: Canvas) {
        fill(0xFF8FB86A.toInt()); rect(c, -0.5f, -0.5f, 0.5f, 0.72f, 0.12f)
        fill(0xFF1A1714.toInt()); path.reset(); path.moveTo(-0.52f, -0.16f); path.lineTo(-0.52f, -0.56f); path.lineTo(0.52f, -0.56f); path.lineTo(0.52f, -0.16f)
        for (i in 0..5) { val x = 0.52f - i * 0.2f; path.lineTo(x - 0.1f, -0.3f); path.lineTo(x - 0.2f, -0.16f) }
        path.close(); c.drawPath(path, p)
        fill(0xFF9A8F80.toInt()); rect(c, -0.68f, 0.18f, -0.5f, 0.3f, 0.03f); rect(c, 0.5f, 0.18f, 0.68f, 0.3f, 0.03f)
        face(c, 0.06f, gap = 0.2f, eye = 0.08f, smile = false)
        line(INK, 0.05f); c.drawLine(-0.2f, 0.42f, 0.2f, 0.42f, p)
        line(0xFF3F5A2E.toInt(), 0.03f); c.drawLine(0.12f, -0.1f, 0.34f, -0.1f, p)
        for (i in 0..3) c.drawLine(0.14f + i * 0.06f, -0.14f, 0.14f + i * 0.06f, -0.06f, p)
    }

    private fun director(c: Canvas) {
        fill(0xFFE2A774.toInt()); oval(c, -0.46f, -0.36f, 0.46f, 0.74f)
        fill(0xFF1A1714.toInt()); oval(c, -0.58f, -0.62f, 0.5f, -0.22f)
        c.drawCircle(0.08f, -0.64f, 0.06f, p)
        // Sunglasses and a moustache: very much the director.
        rect(c, -0.4f, 0.0f, -0.06f, 0.18f, 0.06f); rect(c, 0.06f, 0.0f, 0.4f, 0.18f, 0.06f)
        line(0xFF1A1714.toInt(), 0.04f); c.drawLine(-0.06f, 0.06f, 0.06f, 0.06f, p)
        fill(0xFF5A3418.toInt()); path.reset(); path.moveTo(-0.22f, 0.4f); path.quadTo(0f, 0.28f, 0.22f, 0.4f); path.quadTo(0f, 0.36f, -0.22f, 0.4f); c.drawPath(path, p)
        line(0xFF6B3E1E.toInt(), 0.05f); box.set(-0.12f, 0.42f, 0.12f, 0.56f); c.drawArc(box, 20f, 140f, false, p)
    }
}
