package com.andebugulin.nfcguard.receiver

import com.andebugulin.nfcguard.AppState
import com.andebugulin.nfcguard.Mode
import com.andebugulin.nfcguard.ModeActivationLogic
import com.andebugulin.nfcguard.data.AppLogger
import com.andebugulin.nfcguard.data.AppStateRepository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking

/**
 * Lets Tasker, MacroDroid, adb and the like drive modes:
 *
 *   am broadcast -p com.andebugulin.nfcguard -a com.andebugulin.nfcguard.ACTIVATE --es mode "Focus"
 *
 * `mode` is a mode name (case-insensitive) or id. Off unless the user enables
 * automation in settings. Automation may switch any mode on, but only switches
 * off modes that do not require a specific tag — otherwise any app on the
 * phone could undo a tag lock.
 *
 * In the other direction, [MODE_STARTED] / [MODE_STOPPED] are broadcast on
 * every transition (see StateSyncer) so automation apps can react.
 */
class AutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!isEnabled(context)) {
            AppLogger.log("AUTOMATION", "Ignored ${intent.action}: automation disabled")
            return
        }
        val key = intent.getStringExtra(EXTRA_MODE) ?: return
        runBlocking {
            AppStateRepository.getInstance(context).update { state ->
                val mode = state.modes.find { it.id == key || it.name.equals(key, ignoreCase = true) }
                    ?: return@update state.also { AppLogger.log("AUTOMATION", "No mode '$key'") }
                val on = mode.id in state.activeModes
                when (intent.action) {
                    ACTION_ACTIVATE -> activate(state, mode)
                    ACTION_DEACTIVATE -> deactivate(state, mode)
                    ACTION_TOGGLE -> if (on) deactivate(state, mode) else activate(state, mode)
                    else -> state
                }
            }
        }
    }

    private fun activate(state: AppState, mode: Mode): AppState {
        if (mode.id in state.activeModes) return state
        val result = ModeActivationLogic.applyModeActivation(state, mode.id, timedUntilMillis = null)
        AppLogger.log("AUTOMATION", "Activate '${mode.name}': ${result::class.simpleName}")
        return result.newState
    }

    private fun deactivate(state: AppState, mode: Mode): AppState {
        if (mode.id !in state.activeModes) return state
        if (requiresSpecificTag(mode)) {
            AppLogger.log("AUTOMATION", "Refused to deactivate '${mode.name}': it requires a specific tag")
            return state
        }
        AppLogger.log("AUTOMATION", "Deactivate '${mode.name}'")
        return ModeActivationLogic.applyModeDeactivation(state, mode.id).newState
    }

    companion object {
        const val ACTION_ACTIVATE = "com.andebugulin.nfcguard.ACTIVATE"
        const val ACTION_DEACTIVATE = "com.andebugulin.nfcguard.DEACTIVATE"
        const val ACTION_TOGGLE = "com.andebugulin.nfcguard.TOGGLE"
        const val EXTRA_MODE = "mode"

        const val MODE_STARTED = "com.andebugulin.nfcguard.MODE_STARTED"
        const val MODE_STOPPED = "com.andebugulin.nfcguard.MODE_STOPPED"
        const val EXTRA_MODE_ID = "mode_id"
        const val EXTRA_MODE_NAME = "mode_name"

        private const val PREFS = "guardian_prefs"
        private const val KEY_ENABLED = "automation_enabled"

        fun requiresSpecificTag(mode: Mode): Boolean = mode.nfcTagIds.any { it != "ANY" }

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
    }
}
