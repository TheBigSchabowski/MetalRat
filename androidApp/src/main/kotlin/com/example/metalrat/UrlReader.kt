package com.example.metalrat

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

/** The app in front and, if it is a browser, the address it shows. */
data class Foreground(val pkg: String, val url: String?, val screen: ScreenInfo? = null)

/** What an app without media session or address bar shows about the current post (Instagram). */
data class ScreenInfo(val author: String, val caption: String?, val url: String)

/**
 * Accessibility service with one job: read the address bar of the browser that is open, so a
 * bookmark gets the page link. Assigned to the accessibility shortcut (e.g. "hold both volume keys"),
 * it also saves directly — the hardware key combo.
 */
class UrlReader : AccessibilityService() {
    @Volatile private var lastPkg: String? = null
    private val lastUrl = HashMap<String, String>()
    private var lastRead = 0L

    override fun onServiceConnected() {
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            accessibilityButtonController.registerAccessibilityButtonCallback(
                object : AccessibilityButtonController.AccessibilityButtonCallback() {
                    override fun onClicked(controller: AccessibilityButtonController) {
                        Toast.makeText(this@UrlReader, Capturer.captureCurrent(this@UrlReader), Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in IGNORED) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) lastPkg = pkg
        // Remember the last address per browser: when the quick settings panel covers the screen,
        // the browser window may not be readable any more.
        if (pkg in BROWSERS) {
            val now = SystemClock.uptimeMillis()
            if (now - lastRead < 700) return
            lastRead = now
            val root = event.source?.let(::rootOf) ?: return
            readUrl(root, pkg)?.let { synchronized(lastUrl) { lastUrl[pkg] = it } }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun foreground(): Foreground? {
        val root = try {
            windows.asSequence()
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root }
                .firstOrNull { it.packageName?.toString().let { p -> p != null && p != packageName && p !in IGNORED } }
        } catch (e: Exception) {
            null
        }
        val pkg = root?.packageName?.toString() ?: lastPkg ?: return null
        val url = root?.let { readUrl(it, pkg) } ?: synchronized(lastUrl) { lastUrl[pkg] }
        val screen = if (pkg == INSTAGRAM && root != null) instagram(root) else null
        return Foreground(pkg, url, screen)
    }

    /** Diagnosis: logs what the app in front shows (texts, descriptions, view ids) under the tag "MetalRat". */
    fun logForeground() {
        val root = try {
            windows.asSequence().filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.mapNotNull { it.root }
                .firstOrNull { it.packageName?.toString().let { p -> p != null && p != packageName && p !in IGNORED } }
        } catch (e: Exception) { null } ?: return android.util.Log.i("MetalRat", "dump: kein App-Fenster").let { }
        android.util.Log.i("MetalRat", "dump: ${root.packageName}")
        var count = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (count++ > 600) return
            val text = n.text?.toString()?.take(120)
            val desc = n.contentDescription?.toString()?.take(120)
            val id = n.viewIdResourceName?.substringAfter(":id/")
            if (text != null || desc != null || id != null) {
                android.util.Log.i("MetalRat", "dump: ${" ".repeat(depth)}${id ?: "-"} | ${text ?: ""} | ${desc ?: ""}" +
                    if (n.isClickable) " [klickbar]" else "")
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
    }

    fun dismissShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    }

    /**
     * Instagram shows no link and has no media session. The open reel/post still names its author
     * and caption, so the bookmark gets those plus the author's reels page as link.
     */
    private fun instagram(root: AccessibilityNodeInfo): ScreenInfo? {
        fun first(id: String) = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM:id/$id").firstOrNull()
        fun captionUnder(node: AccessibilityNodeInfo?): String? {
            node ?: return null
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
            for (i in 0 until node.childCount) captionUnder(node.getChild(i))?.let { return it }
            return null
        }
        // Reels viewer first: it lies on top of the feed, which stays in the tree below it.
        val reelAuthor = first("clips_author_username")?.text?.toString()?.trim()
        if (!reelAuthor.isNullOrEmpty()) {
            val caption = captionUnder(first("clips_caption_component"))
            return ScreenInfo(reelAuthor, cleanCaption(caption), "https://www.instagram.com/$reelAuthor/reels/")
        }
        val postAuthor = first("row_feed_photo_profile_name")?.text?.toString()?.trim()
        if (!postAuthor.isNullOrEmpty()) {
            val caption = first("row_feed_comment_textview_layout")?.let(::captionUnder)
            return ScreenInfo(postAuthor, cleanCaption(caption), "https://www.instagram.com/$postAuthor/")
        }
        return null
    }

    private fun cleanCaption(caption: String?): String? =
        caption?.replace(Regex("\\s+"), " ")?.trim()?.removeSuffix("…")?.removeSuffix("more")?.trim()?.ifEmpty { null }

    private fun rootOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var n = node
        while (true) n = n.parent ?: return n
    }

    private fun readUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        for (id in URL_BAR_IDS) {
            val text = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
                .firstNotNullOfOrNull { node -> node.text?.toString()?.takeIf { !node.isFocused } }
            toUrl(text)?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile var instance: UrlReader? = null
            private set

        private val IGNORED = setOf("com.android.systemui")
        const val INSTAGRAM = "com.instagram.android"

        private val URL_BAR_IDS = listOf(
            "url_bar",                          // Chrome, Brave, Edge, Vivaldi, Kiwi, Cromite
            "mozac_browser_toolbar_url_view",   // Firefox, Fennec, IronFox, Mull
            "location_bar_edit_text",           // Samsung Internet
            "url_field",                        // Opera
            "omnibarTextInput",                 // DuckDuckGo
            "url",
        )

        val BROWSERS = setOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
            "com.brave.browser", "com.microsoft.emmx", "com.vivaldi.browser", "com.kiwibrowser.browser",
            "org.cromite.cromite", "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
            "org.mozilla.focus", "org.ironfoxoss.ironfox", "us.spotco.fennec_dos", "com.sec.android.app.sbrowser",
            "com.opera.browser", "com.duckduckgo.mobile.android",
        )

        private fun toUrl(text: String?): String? {
            val s = text?.trim()?.takeIf { it.isNotEmpty() && ' ' !in it } ?: return null
            return Links.normalizeUrl(s).takeIf { it.startsWith("http") }
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, UrlReader::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
