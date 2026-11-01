package com.example.metalrat

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

private val os = System.getProperty("os.name").lowercase()
private val isMac = "mac" in os

private fun dataDir(): File {
    val home = System.getProperty("user.home")
    return when {
        isMac -> File(home, "Library/Application Support/MetalRat")
        else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "metalrat")
    }
}

fun main() {
    val dir = dataDir()
    val store = BookmarkStore(File(dir, "bookmarks.json"))
    val enricher = Enricher(store, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    val server = CaptureServer(store, enricher).also { it.start() }
    val thumbs = ThumbnailCache(File(dir, "thumbs")) { Image.makeFromEncoded(it).toComposeImageBitmap() }
    val icon = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
        ?.use { BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()) }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MetalRat",
            icon = icon,
            state = rememberWindowState(width = 1100.dp, height = 800.dp),
        ) {
            val platform = remember { DesktopPlatform(window) }
            MetalRatTheme {
                BookmarkScreen(store, enricher, thumbs, platform, header = {
                    server.error?.let {
                        Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                })
            }
        }
    }
}

private class DesktopPlatform(private val window: Frame) : Platform {
    private val key = if (isMac) "⌥⇧B" else "Alt+Umschalt+B"

    override val emptyHint = """
        Noch nichts gemerkt.

        Im Browser: $key drücken oder auf das MetalRat-Symbol der Erweiterung klicken. Die Seite landet hier – mit dem Zeitpunkt des Videos und, falls du Text markiert hast, mit der Textstelle. Beim Öffnen springt das Video dorthin bzw. der Browser scrollt zur Stelle und markiert sie.

        Rechtsklick › „In MetalRat merken“ geht auch, ebenso oben „+ Link“.

        Die Erweiterung liegt im Projektordner unter browser-extension/ (Anleitung in der README).
    """.trimIndent()

    override fun open(url: String) {
        // `open` / `xdg-open` keep the #:~:text= fragment; Desktop.browse doesn't on every system.
        val command = when {
            isMac -> listOf("open", url)
            "linux" in os -> listOf("xdg-open", url)
            else -> null
        }
        if (command != null && runCatching { ProcessBuilder(command).start() }.isSuccess) return
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    override fun copy(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    override fun exportJson(json: String, fileName: String) {
        val dialog = FileDialog(window, "Exportieren", FileDialog.SAVE).apply { file = fileName; isVisible = true }
        val name = dialog.file ?: return
        File(dialog.directory, name).writeText(json)
    }

    override fun importJson(onText: (String) -> Unit) {
        val dialog = FileDialog(window, "JSON importieren", FileDialog.LOAD).apply { isVisible = true }
        val name = dialog.file ?: return
        onText(File(dialog.directory, name).readText())
    }

    override fun practice(url: String?, title: String, minutes: Int): String? {
        val started = Wassertiger.practice(minutes * 60, title)
        url?.let(::open)
        return if (started) null else "Wassertiger nicht gefunden (ab Version 1.5) – nur das Video geöffnet."
    }
}

/** Starts a practice timer in Wassertiger: the running app via its local port, otherwise launches it. */
private object Wassertiger {
    private const val PORT = 47124

    fun practice(seconds: Int, title: String): Boolean = viaPort(seconds, title) || launch(seconds, title)

    private fun viaPort(seconds: Int, title: String): Boolean = runCatching {
        val query = "seconds=$seconds&title=" + java.net.URLEncoder.encode(title, "UTF-8")
        val conn = URI("http://127.0.0.1:$PORT/practice?$query").toURL().openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 800
        conn.readTimeout = 1500
        conn.responseCode.also { conn.disconnect() } == 204
    }.getOrDefault(false)

    private fun launch(seconds: Int, title: String): Boolean {
        val args = listOf("--practice-seconds", "$seconds", "--practice-title", title)
        val command = if (isMac) {
            listOf("open", "-a", "Wassertiger", "--args") + args
        } else {
            val binary = listOf("/opt/wassertiger/bin/Wassertiger", "${System.getProperty("user.home")}/Wassertiger/bin/Wassertiger")
                .firstOrNull { File(it).canExecute() } ?: return false
            listOf(binary) + args
        }
        // `open` returns at once (exit code says whether the app exists); the Linux binary *is* the app: don't wait.
        return runCatching { ProcessBuilder(command).start().let { if (isMac) it.waitFor() == 0 else true } }.getOrDefault(false)
    }
}
