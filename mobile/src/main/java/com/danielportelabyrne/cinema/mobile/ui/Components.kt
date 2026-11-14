package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.movies.Title
import com.danielportelabyrne.cinema.mobile.Model
import com.danielportelabyrne.cinema.mobile.Row as CatalogueRow
import com.danielportelabyrne.cinema.mobile.data.watchedFraction

/** A picture from Jellyfin (with the sign-in header) or anywhere else. */
@Composable
fun RemoteImage(model: Model, url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    if (url == null) { Box(modifier.background(Palette.raised)); return }
    val req = ImageRequest.Builder(ctx).data(url).crossfade(true).apply { model.imageHeaders(url).forEach { (k, v) -> addHeader(k, v) } }.build()
    AsyncImage(model = req, contentDescription = null, modifier = modifier.background(Palette.raised), contentScale = contentScale)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Palette.ivory, modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp))
}

/** A poster, its name under it; a rank for Top 10 rows. */
@Composable
fun PosterCard(model: Model, url: String?, name: String, sub: String? = null, width: Dp = 112.dp, rank: Int? = null, progress: Float? = null, onClick: () -> Unit) {
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        Box {
            RemoteImage(model, url, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)))
            if (rank != null) Text("$rank", style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp), color = Palette.ivory,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
            if (progress != null && progress > 0f) LinearProgressIndicator(progress = { progress }, color = Palette.butter, trackColor = Color(0x66000000),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp).height(3.dp))
        }
        Text(name, style = MaterialTheme.typography.labelMedium, color = Palette.ivory, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted, maxLines = 1)
    }
}

/** A wide still with progress: Continue watching and Next up. */
@Composable
fun StillCard(model: Model, item: JellyfinItem, onClick: () -> Unit) {
    Column(Modifier.width(240.dp).clickable(onClick = onClick)) {
        Box {
            RemoteImage(model, if (item.isEpisode) model.stillOf(item) else item.takeIf { it.hasBackdrop }?.let { model.client.backdropUrl(it.id) } ?: model.posterOf(item),
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
            val f = watchedFraction(item)
            if (f > 0f) LinearProgressIndicator(progress = { f }, color = Palette.butter, trackColor = Color(0x66000000),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp).height(3.dp))
        }
        Text(if (item.isEpisode) item.seriesName ?: item.name else item.name, style = MaterialTheme.typography.titleSmall, color = Palette.ivory, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (item.isEpisode) Text("S${item.season ?: 0} E${item.episode ?: 0} · ${item.name}", style = MaterialTheme.typography.bodySmall,
            color = Palette.ivoryMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ItemRow(model: Model, heading: String, items: List<JellyfinItem>, wide: Boolean = false) {
    if (items.isEmpty()) return
    SectionTitle(heading)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.id }) { item ->
            if (wide) StillCard(model, item) { model.openItem(item) }
            else PosterCard(model, model.posterOf(item), item.name, item.year?.toString(), progress = watchedFraction(item).takeIf { it > 0f }) { model.openItem(item) }
        }
    }
}

@Composable
fun TitleRow(model: Model, row: CatalogueRow) {
    val titles = row.titles
    if (titles != null && titles.isEmpty()) return
    SectionTitle(row.spec.heading)
    if (titles == null) { LoadingRow(); return }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(titles.size) { i ->
            val t: Title = titles[i]
            PosterCard(model, t.posterUrl, t.name, t.year?.toString(), rank = if (row.spec.ranked) i + 1 else null) { model.open(t) }
        }
    }
}

@Composable
fun LoadingRow() {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(6) { Box(Modifier.width(112.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Palette.raised)) }
    }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Palette.ivoryMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun Pill(text: String, color: Color = Palette.raised) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Palette.ivory,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** The fade under a backdrop, so text over it stays readable. */
val scrim = Brush.verticalGradient(listOf(Color.Transparent, Palette.espresso))

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h).size(1.dp))

@Composable
fun HStack(content: @Composable () -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
