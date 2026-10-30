package com.andebugulin.nfcguard

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One stretch of time a mode was on. A break ends one session and its reactivation starts the next. */
@Serializable
data class FocusSession(
    val modeId: String,
    val modeName: String,
    val start: Long,
    val end: Long
)

object SessionStats {

    /** Focus minutes inside [from, to). Modes on at the same time are summed, not merged. */
    fun minutesBetween(sessions: List<FocusSession>, from: Long, to: Long): Long =
        sessions.sumOf { (minOf(it.end, to) - maxOf(it.start, from)).coerceAtLeast(0) } / 60_000

    fun minutesByMode(sessions: List<FocusSession>): Map<String, Long> =
        sessions.groupBy { it.modeName }
            .mapValues { (_, list) -> list.sumOf { it.end - it.start } / 60_000 }

    /**
     * Consecutive days with at least one session, counting back from [today].
     * A day with no session yet does not break the streak until it is over,
     * so the count starts from yesterday when today is still empty.
     */
    fun streakDays(sessions: List<FocusSession>, today: LocalDate, zone: ZoneId): Int {
        val days = sessions.flatMap { session ->
            val first = Instant.ofEpochMilli(session.start).atZone(zone).toLocalDate()
            val last = Instant.ofEpochMilli(session.end).atZone(zone).toLocalDate()
            generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        }.toSet()

        var day = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (day in days) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
