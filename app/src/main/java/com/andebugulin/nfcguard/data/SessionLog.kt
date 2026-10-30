package com.andebugulin.nfcguard.data

import com.andebugulin.nfcguard.AppState
import com.andebugulin.nfcguard.FocusSession

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Focus-session history, kept apart from AppState so config export/import
 * never carries or wipes it.
 *
 * [track] is idempotent: it compares the active modes with the sessions it
 * has open, so calling it on every state sync (including boot and watchdog
 * syncs) only records real transitions.
 */
object SessionLog {

    data class Changes(val started: Set<String>, val ended: Set<String>)

    private const val PREFS = "focus_sessions"
    private const val KEY_OPEN = "open"
    private const val KEY_CLOSED = "closed"
    private const val MAX_SESSIONS = 5000

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun track(context: Context, state: AppState, now: Long): Changes {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val open = decode<Map<String, FocusSession>>(prefs.getString(KEY_OPEN, null)) ?: emptyMap()

        val started = state.activeModes - open.keys
        val ended = open.keys - state.activeModes
        if (started.isEmpty() && ended.isEmpty()) return Changes(started, ended)

        val names = state.modes.associate { it.id to it.name }
        val newOpen = (open - ended) + started.associateWith {
            FocusSession(it, names[it] ?: it, start = now, end = now)
        }
        val closed = sessions(context) + ended.map { open.getValue(it).copy(end = now) }

        prefs.edit()
            .putString(KEY_OPEN, json.encodeToString(newOpen))
            .putString(KEY_CLOSED, json.encodeToString(closed.takeLast(MAX_SESSIONS)))
            .apply()
        return Changes(started, ended)
    }

    fun sessions(context: Context): List<FocusSession> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CLOSED, null))
            ?: emptyList()

    private inline fun <reified T> decode(raw: String?): T? =
        raw?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
}
