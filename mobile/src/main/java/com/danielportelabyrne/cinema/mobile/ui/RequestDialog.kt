package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.mobile.Model

private fun episodes(n: Int) = "$n ${if (n == 1) "episode" else "episodes"}"

/**
 * Asked when Request is pressed: whether to save it to the phone too, once the server has it. A series already in the
 * library also picks which seasons' missing episodes to ask for.
 */
@Composable
fun RequestDialog(model: Model) {
    val page = model.asking ?: return
    val series = page.title.kind == Kind.SHOW
    val picking = series && page.library != null
    val missing = page.missing.orEmpty()
    var chosen by remember(page, page.missing) { mutableStateOf(missing.map { it.number }.toSet()) }
    AlertDialog(
        onDismissRequest = { model.asking = null },
        containerColor = Color(0xFF241A13),
        title = { Text("Request ${page.title.name}?", color = Palette.ivory) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (picking) missing.forEach { s ->
                    val on = s.number in chosen
                    fun flip() { chosen = if (on) chosen - s.number else chosen + s.number }
                    Row(Modifier.fillMaxWidth().clickable { flip() }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, { flip() }, colors = CheckboxDefaults.colors(checkedColor = Palette.butter, checkmarkColor = Color(0xFF241A13), uncheckedColor = Palette.ivoryMuted))
                        Column {
                            Text(s.name, style = MaterialTheme.typography.titleSmall, color = Palette.ivory)
                            Text(episodes(s.episodes.size) + " missing" + if (s.asked) " · already asked for" else "", style = MaterialTheme.typography.bodySmall, color = Palette.ivoryMuted)
                        }
                    }
                }
                val count = missing.filter { !picking || it.number in chosen }.sumOf { it.episodes.size }
                val note = "Your server fetches it first. Cinema can then save it to this phone over Wi-Fi by itself." +
                    if (series && count > 0) " That is ${episodes(count)} so far, each possibly several GB. Ones that air later are not saved." else ""
                Text(note, style = MaterialTheme.typography.bodyMedium, color = Palette.ivoryMuted, modifier = Modifier.padding(top = 8.dp))
                if (!picking || chosen.isNotEmpty()) {
                    CreamButton("Request and save to phone", Icons.Filled.Download, Modifier.fillMaxWidth().padding(top = 16.dp)) { model.request(page, chosen, toPhone = true) }
                    GlassButton("Just request", Icons.Filled.AddCircleOutline, Modifier.fillMaxWidth().padding(top = 10.dp)) { model.request(page, chosen, toPhone = false) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { model.asking = null }) { Text("Cancel", color = Palette.ivoryMuted) } },
    )
}
