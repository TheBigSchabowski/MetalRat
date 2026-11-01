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

    val LADDER = listOf(45 * MIN, 2 * HOUR, DAY, 3 * DAY, 7 * DAY, 14 * DAY, 30 * DAY)

    /** The durations offered when starting a practice session, in minutes. */
    val DURATIONS = listOf(5, 10, 15, 30, 60, 120)

    fun practiced(previous: Practice?, minutes: Int, now: Long): Practice {
        val count = previous?.count ?: 0
        val end = now + minutes * MIN
        return Practice(count = count + 1, lastAt = now, lastMinutes = minutes, due = end + LADDER[minOf(count, LADDER.lastIndex)])
    }

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
