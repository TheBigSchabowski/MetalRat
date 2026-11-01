package com.example.metalrat

import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var pendingExport: String? = null
    private var importCallback: ((String) -> Unit)? = null
    private var resumes by mutableIntStateOf(0)

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val data = pendingExport
        pendingExport = null
        if (uri != null && data != null) {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(data.toByteArray()) }
            toast("Exportiert")
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = importCallback
        importCallback = null
        if (uri != null && callback != null) {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }?.let(callback)
        }
    }

    private val platform = object : Platform {
        override val emptyHint = """
            Noch nichts gemerkt.

            Video (YouTube-App oder Browser): Schnelleinstellungen runterziehen › „Merken“ antippen. Gespeichert wird mit dem aktuellen Zeitpunkt; beim Öffnen geht's genau dort weiter.

            Textstelle (z. B. Wikipedia): Text markieren › „Merken“ im Menü. Beim Öffnen scrollt der Browser hin und markiert sie.

            Aus jeder App: Teilen › „Merken“.
        """.trimIndent()

        override fun open(url: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                toast("Keine App zum Öffnen gefunden")
            }
        }

        override fun copy(text: String) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Link", text))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast("Link kopiert")
        }

        override fun exportJson(json: String, fileName: String) {
            pendingExport = json
            exportLauncher.launch(fileName)
        }

        override fun importJson(onText: (String) -> Unit) {
            importCallback = onText
            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = MetalRatApp.instance
        setContent {
            MetalRatTheme {
                BookmarkScreen(app.store, app.enricher, app.thumbs, platform, header = { SetupCard(resumes) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumes++ // re-check permissions after coming back from the settings
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openNotificationAccess() {
        val component = ComponentName(this, MediaListener::class.java)
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else null
        try {
            startActivity(detail ?: Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun requestTile(onDone: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, MerkenTile::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                Prefs.setTileAdded(this)
                onDone()
            }
        }
    }

    @Composable
    private fun SetupCard(refresh: Int) {
        val media = remember(refresh) { MediaSessions.hasAccess(this) }
        val reader = remember(refresh) { UrlReader.isEnabled(this) }
        var tile by remember(refresh) { mutableStateOf(Prefs.tileAdded(this)) }
        var hidden by remember { mutableStateOf(false) }
        if (hidden || (media && reader && tile)) return

        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Einrichten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { hidden = true }) { Text("Später") }
                }
                SetupStep(
                    done = tile,
                    title = "Kachel „Merken“",
                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "In die Schnelleinstellungen legen (runterziehen, da wo WLAN und Bildschirmaufnahme sind)."
                    } else {
                        "Schnelleinstellungen runterziehen › Stift ✎ › „Merken“ nach oben ziehen."
                    },
                    action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Hinzufügen" else "Erledigt",
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestTile { tile = true }
                    else { Prefs.setTileAdded(this@MainActivity); tile = true }
                }
                SetupStep(
                    done = media,
                    title = "Video-Zeitpunkt lesen",
                    text = "Benachrichtigungszugriff für MetalRat erlauben. Damit liest MetalRat Titel und Position " +
                        "des laufenden Videos (YouTube, Browser, andere Player) – Benachrichtigungen selbst nicht.",
                    action = "Erlauben",
                ) { openNotificationAccess() }
                SetupStep(
                    done = reader,
                    title = "Link aus dem Browser lesen",
                    text = "Bedienungshilfe „MetalRat“ einschalten. Sie liest beim Merken die Adresse der offenen Seite. " +
                        "Tipp: Den Kurzbefehl dieser Bedienungshilfe auf „Lautstärketasten gedrückt halten“ stellen – " +
                        "dann merkt die Tastenkombi direkt.",
                    action = "Einschalten",
                ) { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                if ((!media || !reader) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Ausgegraut / „Eingeschränkte Einstellung“? App-Info › ⋮ › „Eingeschränkte Einstellungen zulassen“, dann nochmal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                        }) { Text("App-Info") }
                    }
                }
            }
        }
    }

    @Composable
    private fun SetupStep(done: Boolean, title: String, text: String, action: String, onAction: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (done) "✓" else "○", color = if (done) Color(0xFF81C784) else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (!done) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!done) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}
