package com.danielportelabyrne.cinema.mobile.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Dimension
import coil.size.Size
import com.danielportelabyrne.cinema.data.movies.TitleLogos

/**
 * A title heading that shows the title's own logo in place of its name in type, when one arrives and is fit to show
 * (the same rule as the TV and desktop: [TitleLogos]). The name in [style] stays until then, and for good when there
 * is no IMDb id, no logo, or one that would be drawn too small to read, so a title is never blank and never shown twice.
 * The logo is fitted (not cropped or stretched) inside [boxW] x [boxH] dp and left-aligned like the type; it goes
 * through the app's one image loader and cache, asked for at the box's width only. The name stays as the picture's
 * description for screen readers.
 */
@Composable
fun TitleLogo(
    name: String,
    imdbId: String?,
    style: TextStyle,
    boxW: Int,
    boxH: Int,
    modifier: Modifier = Modifier,
    maxLines: Int = 3,
    minHeight: Int = 28,
    color: Color = Palette.ivory,
) {
    val ctx = LocalContext.current
    val url = TitleLogos.url(imdbId)
    val logo by produceState<ImageBitmap?>(null, url) {
        value = null
        if (url == null) return@produceState
        val px = (boxW * ctx.resources.displayMetrics.density).toInt()
        val req = ImageRequest.Builder(ctx).data(url).size(Size(Dimension.Pixels(px), Dimension.Undefined)).build()
        value = ((ctx.imageLoader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
    }
    val img = logo
    // Only the picture's shape matters: it is fitted to the box by aspect, whatever size it was decoded at.
    val shown = img?.takeIf { it.width > 0 && it.height > 0 }?.let { fit(it.width, it.height, boxW, boxH, minHeight) }
    if (img != null && shown != null) {
        Image(
            img, contentDescription = name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
            modifier = modifier.size(shown.first.dp, shown.second.dp),
        )
    } else Text(name, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** The size in dp a [w] x [h] logo is drawn at inside the box, or null when that is too short to read. */
internal fun fit(w: Int, h: Int, boxW: Int, boxH: Int, minHeight: Int): Pair<Float, Float>? {
    val aspect = w.toDouble() / h
    if (!TitleLogos.usable((aspect * 1000).toInt(), 1000, boxW, boxH, minHeight)) return null
    val drawnW = minOf(boxW.toDouble(), boxH * aspect)
    return drawnW.toFloat() to (drawnW / aspect).toFloat()
}
