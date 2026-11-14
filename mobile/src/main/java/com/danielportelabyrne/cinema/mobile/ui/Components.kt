package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.ShelfLook
import com.danielportelabyrne.cinema.data.movies.ShelfLooks
import com.danielportelabyrne.cinema.data.movies.Title
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.Row as CatalogueRow
import com.danielportelabyrne.cinema.mobile.data.watchedFraction

/** A picture from Jellyfin (with the sign-in header) or anywhere else. */
@Composable
fun RemoteImage(model: Model, url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    if (url == null) { Box(modifier.background(Palette.placeholder)); return }
    val req = ImageRequest.Builder(ctx).data(url).crossfade(true).apply { model.imageHeaders(url).forEach { (k, v) -> addHeader(k, v) } }.build()
    AsyncImage(model = req, contentDescription = null, modifier = modifier.background(Palette.placeholder), contentScale = contentScale)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = RowTitle(text, modifier)

private val soft = Color(0x66000000)

/** A tall poster with its name beneath (the classic row). */
@Composable
fun PosterCard(model: Model, url: String?, name: String, sub: String? = null, width: Dp = 118.dp, rank: Int? = null, progress: Float? = null, onClick: () -> Unit) {
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        Box {
            RemoteImage(model, url, Modifier.fillMaxWidth().aspectRatio(2f / 3f).shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color.Black).clip(RoundedCornerShape(14.dp)))
            if (progress != null && progress > 0f) WaveBar(progress, Modifier.align(Alignment.BottomCenter).padding(horizontal = 4.dp))
        }
        Text(name, style = MaterialTheme.typography.labelMedium, color = Color(0xFFCDBFAD), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.dim, maxLines = 1)
    }
}

/** A wide still with the name over the picture (stills, small stills, the grid). */
@Composable
fun StillTile(model: Model, url: String?, name: String, sub: String? = null, width: Dp = 236.dp, progress: Float? = null, small: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.width(width).aspectRatio(16f / 9f).shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color.Black).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        RemoteImage(model, url, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xCC0B0807))))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = if (progress != null) 14.dp else 8.dp)) {
            Text(name, style = TextStyle(fontFamily = fraunces(500, 20f), fontSize = if (small) 13.sp else 16.sp, lineHeight = if (small) 15.sp else 18.sp), color = Palette.ivory, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null && !small) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted, maxLines = 1)
        }
        if (progress != null && progress > 0f) WaveBar(progress, Modifier.align(Alignment.BottomCenter).padding(horizontal = 6.dp, vertical = 0.dp))
    }
}

/** One large wide card with the overview, for the row right under the featured title. */
@Composable
fun SpotlightCard(model: Model, t: Title, onClick: () -> Unit) {
    Column(Modifier.width(312.dp).glass(18.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            RemoteImage(model, t.backdropUrl, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0x990B0807))))
        }
        Column(Modifier.padding(14.dp)) {
            Text(t.name, style = TextStyle(fontFamily = fraunces(500, 30f), fontSize = 21.sp, lineHeight = 23.sp, letterSpacing = (-0.4).sp), color = Palette.ivory, maxLines = 2)
            Text(t.metaLine(), style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
            t.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = Palette.reading, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

/** A Top 10 card: the rank as a big outlined saffron numeral behind-left of the poster. */
@Composable
fun RankedCard(model: Model, rank: Int, t: Title, onClick: () -> Unit) {
    Box(Modifier.width(168.dp).height(184.dp).clickable(onClick = onClick)) {
        Text("$rank", style = TextStyle(fontFamily = fraunces(600, 96f), fontSize = 104.sp, letterSpacing = (-6).sp, color = Palette.butter.copy(alpha = 0.6f), drawStyle = Stroke(width = 3f)),
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 2.dp), maxLines = 1, softWrap = false)
        RemoteImage(model, t.posterUrl, Modifier.align(Alignment.BottomEnd).width(112.dp).aspectRatio(2f / 3f).shadow(10.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)))
    }
}

/** A continue-watching or next-up card: the wide still with the wave scrubber under it. */
@Composable
fun StillCard(model: Model, item: JellyfinItem, onClick: () -> Unit) {
    Column(Modifier.width(250.dp).clickable(onClick = onClick)) {
        val f = watchedFraction(item)
        StillTile(model, if (item.isEpisode) model.stillOf(item) else item.takeIf { it.hasBackdrop }?.let { model.client.backdropUrl(it.id) } ?: model.posterOf(item),
            if (item.isEpisode) item.seriesName ?: item.name else item.name, null, 250.dp, progress = f.takeIf { it > 0f }) { onClick() }
        Text(if (item.isEpisode) "S${item.season ?: 0} E${item.episode ?: 0} · ${item.name}" else item.year?.toString().orEmpty(),
            style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun ItemRow(model: Model, heading: String, items: List<JellyfinItem>, wide: Boolean = false) {
    if (items.isEmpty()) return
    RowTitle(heading)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.id }) { item ->
            if (wide) StillCard(model, item) { model.openItem(item) }
            else PosterCard(model, model.posterOf(item), item.name, item.year?.toString(), progress = watchedFraction(item).takeIf { it > 0f }) { model.openItem(item) }
        }
    }
}

/**
 * A catalogue row in one of the TV's six looks: spotlight (one large card, then posters), stills, posters, a grid of small
 * stills two high, small stills, or a numbered Top 10. A look with too few wide pictures falls back to posters.
 */
@Composable
fun ShelfRow(model: Model, row: CatalogueRow, planned: ShelfLook) {
    val titles = row.titles
    if (titles != null && titles.isEmpty()) return
    RowTitle(row.spec.heading)
    if (titles == null) { LoadingLine("Loading"); return }
    val look = ShelfLooks.resolve(planned, titles)
    val pad = PaddingValues(horizontal = 16.dp)
    val gap = Arrangement.spacedBy(12.dp)
    when (look) {
        ShelfLook.RANKED -> LazyRow(contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(titles.take(10).size) { i -> RankedCard(model, i + 1, titles[i]) { model.open(titles[i]) } }
        }
        ShelfLook.POSTERS -> LazyRow(contentPadding = pad, horizontalArrangement = gap) {
            items(titles.size) { i -> val t = titles[i]; PosterCard(model, t.posterUrl, t.name, t.year?.toString()) { model.open(t) } }
        }
        ShelfLook.STILLS -> LazyRow(contentPadding = pad, horizontalArrangement = gap) {
            items(titles.size) { i -> val t = titles[i]; StillTile(model, t.backdropUrl ?: t.posterUrl, t.name, t.year?.toString()) { model.open(t) } }
        }
        ShelfLook.SMALL_STILLS -> LazyRow(contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(titles.size) { i -> val t = titles[i]; StillTile(model, t.backdropUrl ?: t.posterUrl, t.name, null, 170.dp, small = true) { model.open(t) } }
        }
        ShelfLook.GRID -> LazyHorizontalGrid(GridCells.Fixed(2), Modifier.height(202.dp), contentPadding = pad,
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(titles) { t -> StillTile(model, t.backdropUrl ?: t.posterUrl, t.name, null, 170.dp, small = true) { model.open(t) } }
        }
        ShelfLook.SPOTLIGHT -> {
            val lead = ShelfLooks.spotlightIndex(titles) ?: 0
            LazyRow(contentPadding = pad, horizontalArrangement = gap, verticalAlignment = Alignment.Top) {
                item { SpotlightCard(model, titles[lead]) { model.open(titles[lead]) } }
                items(titles.size) { i -> if (i != lead) { val t = titles[i]; PosterCard(model, t.posterUrl, t.name, t.year?.toString()) { model.open(t) } } }
            }
        }
    }
}

@Composable
fun LoadingRow() = LoadingLine("Loading")

@Composable
fun Empty(text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        StripeMark(Modifier.size(56.dp, 8.dp), radius = 4.dp)
        Spacer(Modifier.height(16.dp))
        Text(text, color = Palette.ivoryMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A small glass badge. */
@Composable
fun Pill(text: String, color: Color = Palette.glassStrong) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Palette.ivory,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** The certificate in a box, then the plain items dotted apart, then the IMDb tag. */
@Composable
fun MetaLine(certificate: String?, items: List<String>, imdb: Double?, modifier: Modifier = Modifier) {
    val plain = items.filter { it.isNotBlank() }
    val cert = certificate?.takeIf { it.isNotBlank() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        cert?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.ivory, modifier = Modifier.background(Palette.glassStrong, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
        }
        plain.forEachIndexed { i, text ->
            if (cert != null || i > 0) Dot()
            Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Palette.ivoryMuted, maxLines = 1)
        }
        imdb?.let {
            if (cert != null || plain.isNotEmpty()) Dot()
            Text("IMDb", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF141008), modifier = Modifier.background(Color(0xFFF5C518), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
            Text("%.1f".format(it), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Palette.ivory, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun Dot() = Box(Modifier.padding(horizontal = 8.dp).size(3.dp).background(Palette.dim, RoundedCornerShape(50)))

/** A "Streaming on …" glass pill with a small gradient disc. */
@Composable
fun ProviderPill(text: String, modifier: Modifier = Modifier) {
    Row(modifier.glass(50.dp).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).background(Brush.linearGradient(listOf(Palette.stripeSaffron, Palette.stripeTerra)), RoundedCornerShape(50)))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.butter, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The cream pill: the page's main action (resting buttons are glass; on a phone there is no focus, so this one is lit). */
@Composable
fun CreamButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Palette.ivory).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) androidx.compose.material3.Icon(icon, null, Modifier.padding(end = 8.dp).size(20.dp), tint = Palette.ink)
        Text(text, color = Palette.ink, style = MaterialTheme.typography.labelLarge)
    }
}

/** The terracotta gradient pill: a call to action that must read wherever it sits (Request, Queue download). */
@Composable
fun TerraButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(Color(0xFFEB8A56), Color(0xFFD4623A)))).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) androidx.compose.material3.Icon(icon, null, Modifier.padding(end = 8.dp).size(20.dp), tint = Color(0xFF1D0D05))
        Text(text, color = Color(0xFF1D0D05), style = MaterialTheme.typography.labelLarge)
    }
}

/** The glass pill: every other button. */
@Composable
fun GlassButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.glass(50.dp, Palette.glassStrong).clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) androidx.compose.material3.Icon(icon, null, Modifier.padding(end = 8.dp).size(20.dp), tint = Palette.ivory)
        Text(text, color = Palette.ivory, style = MaterialTheme.typography.labelLarge)
    }
}

/** A selectable pill for filters and seasons: cream when chosen, glass otherwise. */
@Composable
fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.ink else Palette.ivory,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Palette.ivory else Palette.glassStrong).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h).size(1.dp))

@Composable
fun HStack(content: @Composable () -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }

@Composable
fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).glass(20.dp).padding(18.dp)) { content() }
}
