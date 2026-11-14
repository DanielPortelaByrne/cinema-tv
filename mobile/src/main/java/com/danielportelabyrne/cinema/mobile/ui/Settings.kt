package com.danielportelabyrne.cinema.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.settings.Arr
import com.danielportelabyrne.cinema.data.settings.ManualArr
import com.danielportelabyrne.cinema.mobile.Model

@Composable
fun SettingsScreen(model: Model) {
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader(model, "Settings")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Card {
                Label("Jellyfin")
                var name by remember { mutableStateOf<String?>(null) }
                androidx.compose.runtime.LaunchedEffect(Unit) { model.bg({ model.client.userName() }) { name = it } }
                Text(model.client.server.ifBlank { "Not set up" }, color = Palette.ivory)
                name?.let { Text("Signed in as $it", color = Palette.ivoryMuted, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { model.client.forget(); model.settings.setupDone = false; model.stack.clear(); model.needsSetup = true }) {
                    Text("Sign out and change server", color = Palette.terracotta)
                }
            }
            Card { Label("Seerr"); SeerrForm(model) }
            Card {
                Label("Country")
                var region by rememberSaveable { mutableStateOf(model.settings.region) }
                OutlinedTextField(region, { region = it.take(2).uppercase() }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Two letters, for what is streaming where") })
                Save { model.settings.region = region; Catalog.country = region; model.rows.clear(); model.toast("Country set to $region") }
            }
            Card {
                Label("Helper services")
                var helper by rememberSaveable { mutableStateOf(model.settings.requestHelperUrl.orEmpty()) }
                var stream by rememberSaveable { mutableStateOf(model.settings.streamServiceUrl.orEmpty()) }
                Field("Request helper address", helper) { helper = it }
                Field("Stream service address", stream) { stream = it }
                Save { model.settings.requestHelperUrl = helper.ifBlank { null }; model.settings.streamServiceUrl = stream.ifBlank { null }; model.toast("Saved") }
            }
            Arr.entries.forEach { arr ->
                Card {
                    Label("${arr.label} (only if Seerr doesn't share it)")
                    val saved = model.settings.manualArr(arr)
                    var url by rememberSaveable(arr) { mutableStateOf(saved?.baseUrl.orEmpty()) }
                    var key by remember(arr) { mutableStateOf(saved?.apiKey.orEmpty()) }
                    Field("Address (port ${arr.defaultPort})", url) { url = it }
                    OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Save { model.settings.setManualArr(arr, if (url.isBlank() || key.isBlank()) null else ManualArr(url.trim(), key.trim())); model.toast("Saved ${arr.label}") }
                }
            }
        }
    }
}

@Composable
private fun Label(s: String) = Text(s, style = MaterialTheme.typography.titleMedium, color = Palette.butter, modifier = Modifier.padding(bottom = 8.dp))

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
}

@Composable
private fun Save(onClick: () -> Unit) { Gap(8.dp); PrimaryButton("Save", Icons.Filled.Check, onClick = onClick) }
