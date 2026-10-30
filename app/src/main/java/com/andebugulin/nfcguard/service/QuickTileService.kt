package com.andebugulin.nfcguard.service

import com.andebugulin.nfcguard.AppState
import com.andebugulin.nfcguard.ModeActivationLogic
import com.andebugulin.nfcguard.R
import com.andebugulin.nfcguard.data.AppStateRepository
import com.andebugulin.nfcguard.ui.MainActivity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Quick Settings tile. With nothing on, a tap starts the mode picked in
 * settings (or the only mode, if there is just one). With a mode on, a tap
 * opens the app — switching off still goes through the tag.
 */
class QuickTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val repo = AppStateRepository.getInstance(this)
        val modeId = quickModeId(this, repo.current)
        if (repo.current.activeModes.isNotEmpty() || modeId == null) {
            openApp()
            return
        }
        CoroutineScope(Dispatchers.Default).launch {
            repo.update { ModeActivationLogic.applyModeActivation(it, modeId, timedUntilMillis = null).newState }
            refresh()
        }
    }

    // The Intent overload only runs below API 34, where it is still supported.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val state = AppStateRepository.getInstance(this).current
        val active = state.modes.filter { it.id in state.activeModes }
        tile.state = if (active.isEmpty()) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = active.joinToString { it.name }.ifEmpty {
                quickModeId(this, state)?.let { id -> state.modes.find { it.id == id }?.name }
            }
        }
        tile.updateTile()
    }

    companion object {
        private const val PREFS = "guardian_prefs"
        private const val KEY_MODE = "quick_tile_mode_id"

        fun quickModeId(context: Context, state: AppState): String? {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)
            return saved?.takeIf { id -> state.modes.any { it.id == id } } ?: state.modes.singleOrNull()?.id
        }

        fun setQuickModeId(context: Context, modeId: String?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, modeId).apply()
        }

        fun requestRefresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, QuickTileService::class.java)) }
        }
    }
}
