package com.example.metalrat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the UI needs from Android or the desktop. */
interface Platform {
    /** Shown while there are no bookmarks yet: how to save one on this platform. */
    val emptyHint: String
    fun open(url: String)
    fun copy(text: String)
    fun exportJson(json: String, fileName: String)
    /** Lets the user pick a file and hands its text to [onText]. */
    fun importJson(onText: (String) -> Unit)
}

private val Accent = Color(0xFFE08A3C)
private val Background = Color(0xFF121417)
private val CardColor = Color(0xFF1C1F24)
private val Muted = Color(0xFF9AA0A8)

@Composable
fun MetalRatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF1A1206),
            background = Background,
            surface = CardColor,
            surfaceVariant = Color(0xFF262A30),
            onSurfaceVariant = Muted,
        ),
    ) {
        Surface(color = Background, modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Composable
fun BookmarkScreen(
    store: BookmarkStore,
    enricher: Enricher,
    thumbs: ThumbnailCache,
    platform: Platform,
    header: @Composable () -> Unit = {},
) {
    val items by store.items.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var creating by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shown = remember(items, query) {
        val q = query.trim()
        if (q.isEmpty()) items
        else items.filter { b ->
            listOf(b.title, b.url, b.selectedText, b.note, b.author, b.source).any { it?.contains(q, ignoreCase = true) == true }
        }
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MetalRat", style = MaterialTheme.typography.titleLarge, color = Accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Suchen") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).widthIn(max = 480.dp),
                )
                TextButton(onClick = { creating = true }) { Text("+ Link") }
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Als JSON exportieren") }, onClick = {
                            menuOpen = false
                            val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                            platform.exportJson(store.exportJson(), "metalrat-$day.json")
                        })
                        DropdownMenuItem(text = { Text("JSON importieren") }, onClick = {
                            menuOpen = false
                            platform.importJson { text ->
                                val message = runCatching { store.importJson(text) }
                                    .fold({ "$it neue Einträge importiert" }, { "Datei konnte nicht gelesen werden" })
                                scope.launch { snackbar.showSnackbar(message) }
                            }
                        })
                    }
                }
            }
            header()
            if (shown.isEmpty()) {
                Text(
                    if (items.isEmpty()) platform.emptyHint else "Nichts gefunden.",
                    color = Muted,
                    modifier = Modifier.padding(24.dp).widthIn(max = 560.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.id }) { b ->
                        BookmarkCard(
                            b, thumbs,
                            onOpen = { Links.openUrl(b)?.let(platform::open) },
                            onEdit = { editing = b },
                            onCopy = { Links.openUrl(b)?.let(platform::copy) },
                            onDelete = {
                                val removed = store.delete(b.id)
                                scope.launch {
                                    val result = snackbar.showSnackbar("Gelöscht", actionLabel = "Rückgängig")
                                    if (result == SnackbarResult.ActionPerformed && removed != null) store.add(removed)
                                }
                            },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    editing?.let { b ->
        EditDialog(b, isNew = false, onDismiss = { editing = null }) { changed ->
            editing = null
            store.update(changed.id) { changed }
            enricher.enrich(changed.id)
        }
    }
    if (creating) {
        val blank = remember { Bookmark(id = newBookmarkId(), createdAt = System.currentTimeMillis(), source = "von Hand") }
        EditDialog(blank, isNew = true, onDismiss = { creating = false }) { created ->
            creating = false
            store.add(created)
            enricher.enrich(created.id)
        }
    }
}

private sealed interface Thumb {
    data object Loading : Thumb
    data object Missing : Thumb
    class Ready(val image: ImageBitmap) : Thumb
}

private val dateFormat = SimpleDateFormat("d. MMM yyyy, HH:mm", Locale.GERMAN)

@Composable
private fun BookmarkCard(
    b: Bookmark,
    thumbs: ThumbnailCache,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val link = Links.openUrl(b)
    val thumbUrl = b.thumbnailUrl
    val initial = when {
        thumbUrl == null || thumbs.hasFailed(thumbUrl) -> Thumb.Missing
        else -> thumbs.cached(thumbUrl)?.let { Thumb.Ready(it) } ?: Thumb.Loading
    }
    val thumb by produceState(initial, thumbUrl) {
        if (thumbUrl != null && value == Thumb.Loading) {
            value = thumbs.load(thumbUrl)?.let { Thumb.Ready(it) } ?: Thumb.Missing
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = CardColor),
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(enabled = link != null, onClick = onOpen),
    ) {
        when (val t = thumb) {
            is Thumb.Ready -> Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Image(t.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                b.positionSec?.let { TimeBadge(it, Modifier.align(Alignment.BottomEnd).padding(8.dp)) }
            }
            Thumb.Loading -> Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                b.positionSec?.let { TimeBadge(it, Modifier.align(Alignment.BottomEnd).padding(8.dp)) }
            }
            // No preview (or it's gone): the link itself is the headline.
            Thumb.Missing -> Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("↗", color = Accent, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Text(
                    b.url?.let(Links::displayLink) ?: "Noch kein Link – ⋮ › Bearbeiten",
                    color = if (b.url != null) Accent else Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                b.positionSec?.let { Spacer(Modifier.width(8.dp)); TimeBadge(it) }
            }
        }
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
            val title = b.title ?: b.url?.let(Links::displayLink)
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            b.selectedText?.let { text ->
                Row(Modifier.padding(top = 6.dp, end = 8.dp).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Accent))
                    Spacer(Modifier.width(8.dp))
                    Text(text, fontStyle = FontStyle.Italic, color = Muted, maxLines = 4, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            b.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, end = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val origin = listOfNotNull(
                    b.author,
                    b.source,
                    b.url?.let(Links::host)?.takeIf { thumb is Thumb.Ready },
                    dateFormat.format(Date(b.createdAt)),
                ).distinct().joinToString(" · ")
                Text(origin, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("⋮", color = Muted) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menuOpen = false; onEdit() })
                        if (link != null) DropdownMenuItem(text = { Text("Link kopieren") }, onClick = { menuOpen = false; onCopy() })
                        DropdownMenuItem(text = { Text("Löschen") }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeBadge(sec: Long, modifier: Modifier = Modifier) {
    Text(
        "▶ " + Links.formatClock(sec),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.background(Color(0xCC000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun EditDialog(b: Bookmark, isNew: Boolean, onDismiss: () -> Unit, onSave: (Bookmark) -> Unit) {
    var url by remember { mutableStateOf(b.url.orEmpty()) }
    var title by remember { mutableStateOf(b.title.orEmpty()) }
    var time by remember { mutableStateOf(b.positionSec?.let(Links::formatClock).orEmpty()) }
    var text by remember { mutableStateOf(b.selectedText.orEmpty()) }
    var note by remember { mutableStateOf(b.note.orEmpty()) }
    val timeValid = time.isBlank() || Links.parseTime(time) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Link merken" else "Bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("Link") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it }, label = { Text("Titel") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(time, { time = it }, label = { Text("Zeitpunkt (z. B. 12:34)") }, singleLine = true,
                    isError = !timeValid, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(text, { text = it }, label = { Text("Textstelle (wird beim Öffnen markiert)") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = timeValid && (url.isNotBlank() || title.isNotBlank() || text.isNotBlank()), onClick = {
                val newUrl = url.trim().takeIf { it.isNotEmpty() }?.let(Links::normalizeUrl)
                val urlChanged = newUrl != b.url
                onSave(
                    b.copy(
                        url = newUrl,
                        title = title.trim().ifEmpty { null },
                        positionSec = Links.parseTime(time)?.takeIf { it > 0 }
                            ?: if (time.isBlank() && urlChanged) newUrl?.let(Links::timeFromUrl) else null,
                        selectedText = text.trim().ifEmpty { null } ?: newUrl?.let(Links::selectionFromFragment),
                        note = note.trim().ifEmpty { null },
                        // New link → fetch a new preview.
                        thumbnailUrl = if (urlChanged) null else b.thumbnailUrl,
                    ),
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
