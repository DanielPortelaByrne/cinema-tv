package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.movies.TitleLogos
import kotlinx.coroutines.withContext

/**
 * A title heading that shows the title's own logo in place of its name in type, when one arrives and is fit to show
 * (the same rule as the TV: [TitleLogos]). The name in [style] stays until then, and for good when there is no IMDb id,
 * no logo, or one that would be drawn too small to read, so a title is never blank and never shown twice. The logo is
 * fitted (not cropped or stretched) inside [boxW] x [boxH] and left-aligned like the type; it goes through the app's one
 * image source and cache. The name stays as the picture's description.
 */
@Composable
fun TitleLogo(
    name: String,
    imdbId: String?,
    style: TextStyle,
    boxW: Int,
    boxH: Int,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    minHeight: Int = 56,
) {
    val source = LocalImages.current
    val url = TitleLogos.url(imdbId)
    val logo by produceState<ImageBitmap?>(url?.let { source.cached(it) }, url, source) {
        value = url?.let { source.cached(it) } ?: url?.let { withContext(imageDispatcher) { source.load(it) } }
    }
    val img = logo
    if (img != null && TitleLogos.usable(img.width, img.height, boxW, boxH, minHeight)) {
        val scale = minOf(boxW.toDouble() / img.width, boxH.toDouble() / img.height)
        Image(
            img, contentDescription = name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
            modifier = modifier.size((img.width * scale).dp, (img.height * scale).dp),
        )
    } else T(name, style, maxLines = maxLines, modifier = modifier)
}
