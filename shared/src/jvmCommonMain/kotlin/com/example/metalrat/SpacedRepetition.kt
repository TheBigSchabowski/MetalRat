package com.example.metalrat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Practice intervals, counted from the *end* of a practice session: 5 min practice at 14:00 → due
 * again 14:50; 2 h practice → due 2 h 45 min later. Each practice moves one step up the ladder.
 */
object SpacedRepetition {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    private const val WEEK = 7 * DAY
    private const val MONTH = 30 * DAY

    val LADDER = listOf(45 * MIN, 2 * HOUR, DAY, 3 * DAY, WEEK, 2 * WEEK, MONTH)

    /** The durations offered when starting a practice session, in minutes. */
    val DURATIONS = listOf(5, 10, 15, 30, 60, 120)

    fun practiced(previous: Practice?, minutes: Int, now: Long, ladder: List<Long> = LADDER): Practice {
        val steps = ladder.ifEmpty { LADDER }
        val count = previous?.count ?: 0
        val end = now + minutes * MIN
        return Practice(count = count + 1, lastAt = now, lastMinutes = minutes, due = end + steps[minOf(count, steps.lastIndex)])
    }

    /** "45m", "2h", "1h30m", "1d", "1w", "1mo" (30 days), a bare number = minutes. Null if unreadable. */
    fun parseInterval(text: String): Long? {
        val s = text.trim().lowercase().replace(" ", "")
        if (s.isEmpty()) return null
        s.toLongOrNull()?.let { return (it * MIN).takeIf { ms -> ms > 0 } }
        val part = Regex("(\\d+)(mo|min|w|d|h|m)")
        if (part.replace(s, "").isNotEmpty()) return null
        val ms = part.findAll(s).sumOf { m ->
            val n = m.groupValues[1].toLong()
            n * when (m.groupValues[2]) {
                "mo" -> MONTH
                "w" -> WEEK
                "d" -> DAY
                "h" -> HOUR
                else -> MIN
            }
        }
        return ms.takeIf { it > 0 }
    }

    /** Inverse of [parseInterval]: 2_700_000 → "45m", 90 min → "1h30m", 30 days → "1mo". */
    fun formatInterval(ms: Long): String {
        if (ms % MONTH == 0L) return "${ms / MONTH}mo"
        if (ms % WEEK == 0L) return "${ms / WEEK}w"
        var rest = ms / MIN
        return buildString {
            val d = rest / (24 * 60); rest %= 24 * 60
            val h = rest / 60; val m = rest % 60
            if (d > 0) append("${d}d")
            if (h > 0) append("${h}h")
            if (m > 0 || isEmpty()) append("${m}m")
        }
    }

    /** A whole ladder: "45m, 2h, 1d" → list; null if any entry is unreadable. */
    fun parseLadder(text: String): List<Long>? =
        text.split(',', ';', '→', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            .map { parseInterval(it) ?: return null }.takeIf { it.isNotEmpty() }

    fun formatLadder(ladder: List<Long>): String = ladder.joinToString(", ") { formatInterval(it) }

    /** Moves the next repetition to [due] (custom "Nächste Wiederholung"). */
    fun dueAt(p: Practice, due: Long): Practice = p.copy(due = due)

    /** Back to the start of the ladder (e.g. after being too generous with yourself): due right now. */
    fun reset(p: Practice, now: Long): Practice = p.copy(count = 0, due = now)

    /** The duration chosen last time, preselected in the dialog. */
    fun lastMinutes(items: List<Bookmark>): Int =
        items.mapNotNull { it.practice }.maxByOrNull { it.lastAt }?.lastMinutes ?: DURATIONS.first()

    fun isDue(p: Practice, now: Long) = p.due <= now

    /** "fällig", "in 45 min", "in 2 h 5 min", "morgen 14:30", "in 3 Tagen", "am 12. Dez.". */
    fun dueLabel(p: Practice, now: Long): String {
        val left = p.due - now
        if (left <= 0) return "fällig"
        val minutes = (left + MIN - 1) / MIN
        return when {
            minutes < 60 -> "in $minutes min"
            left < 12 * HOUR -> "in ${minutes / 60} h" + (minutes % 60).let { if (it > 0) " $it min" else "" }
            left < 2 * DAY && sameDayAs(p.due, now + DAY) -> "morgen " + SimpleDateFormat("HH:mm", Locale.GERMAN).format(Date(p.due))
            left < 2 * DAY -> "heute " + SimpleDateFormat("HH:mm", Locale.GERMAN).format(Date(p.due))
            left < 14 * DAY -> "in ${(left + DAY / 2) / DAY} Tagen"
            else -> "am " + SimpleDateFormat("d. MMM", Locale.GERMAN).format(Date(p.due))
        }
    }

    private fun sameDayAs(a: Long, b: Long): Boolean {
        val f = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
        return f.format(Date(a)) == f.format(Date(b))
    }

    /** Title for the Wassertiger timer. */
    fun timerTitle(b: Bookmark): String =
        "Üben: " + (b.title ?: b.url?.let(Links::displayLink) ?: "Video").let { if (it.length > 40) it.take(39) + "…" else it }
}
