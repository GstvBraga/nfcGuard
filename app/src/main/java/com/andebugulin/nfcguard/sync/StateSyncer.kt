package com.andebugulin.nfcguard.sync

import com.andebugulin.nfcguard.AppState
import com.andebugulin.nfcguard.BlockDecider
import com.andebugulin.nfcguard.BlockMode
import com.andebugulin.nfcguard.ModeEffects
import com.andebugulin.nfcguard.data.AppLogger
import com.andebugulin.nfcguard.data.AppStateRepository
import com.andebugulin.nfcguard.data.SessionLog
import com.andebugulin.nfcguard.receiver.AutomationReceiver
import com.andebugulin.nfcguard.receiver.ScheduleAlarmReceiver
import com.andebugulin.nfcguard.service.BlockerService
import com.andebugulin.nfcguard.service.DeviceEffects
import com.andebugulin.nfcguard.service.QuickTileService
import com.andebugulin.nfcguard.widget.GuardianWidget

import android.content.Context
import android.content.Intent

/**
 * Single dispatch point for state → platform side effects.
 *
 * Four concerns, all idempotent:
 *   1. (Re)start [BlockerService] computed from `state.activeModes`,
 *      `blockMode`, blocked-apps union, and active-mode metadata; or
 *      stop the service when there's truly nothing left to do.
 *   2. Reschedule all schedule start/end alarms via
 *      [ScheduleAlarmReceiver.scheduleAllUpcomingAlarms].
 *   3. Diff per-mode `timedModeDeactivations` and `timedModeReactivations`
 *      against the previous state and schedule/cancel alarms via
 *      [TimedAlarms] for each map entry that changed.
 *   4. Refresh widget(s) via [GuardianWidget.notifyAllWidgets].
 *   5. Mode side effects: device settings ([DeviceEffects]), session
 *      history ([SessionLog]) and, for modes that just turned on or off,
 *      the launch-app option and the automation broadcasts.
 *
 * Wired into [AppStateRepository.update] / [updateWith] so every
 * successful write fires the side effects exactly once. Callers that
 * need to force the same side effects without mutating state — boot,
 * service watchdog, service-restart receiver — call the single-arg
 * [sync] overload, which treats the prior state as empty and so
 * re-schedules every per-mode alarm in the current state (correct on
 * boot, when the OS has dropped all our pending alarms).
 */
object StateSyncer {

    /**
     * Apply side effects when no prior state is meaningful (boot,
     * watchdog, service restart). Re-schedules every per-mode alarm
     * present in [state].
     */
    fun sync(context: Context, state: AppState) {
        sync(context, oldState = AppState(), newState = state)
    }

    /**
     * Apply side effects implied by the transition from [oldState] to
     * [newState]. Only per-mode alarm entries that changed (added,
     * removed, or rescheduled to a new time) result in AlarmManager
     * calls; unchanged entries are no-ops.
     */
    fun sync(context: Context, oldState: AppState, newState: AppState) {
        applyBlockerServiceFor(context, newState)
        ScheduleAlarmReceiver.scheduleAllUpcomingAlarms(context)
        syncTimedAlarms(context, oldState, newState)
        GuardianWidget.notifyAllWidgets(context)
        QuickTileService.requestRefresh(context)
        applyModeSideEffects(context, newState)
    }

    private fun applyModeSideEffects(context: Context, state: AppState) {
        DeviceEffects.apply(context, ModeEffects.effectsFor(state))

        val changes = SessionLog.track(context, state, System.currentTimeMillis())
        val modes = state.modes.associateBy { it.id }
        changes.started.forEach { modeId ->
            modes[modeId]?.options?.launchApp?.let { pkg ->
                runCatching {
                    context.packageManager.getLaunchIntentForPackage(pkg)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let(context::startActivity)
                }.onFailure { AppLogger.log("SYNC", "Launch $pkg failed: ${it.message}") }
            }
            broadcast(context, AutomationReceiver.MODE_STARTED, modeId, modes[modeId]?.name)
        }
        changes.ended.forEach { modeId ->
            broadcast(context, AutomationReceiver.MODE_STOPPED, modeId, modes[modeId]?.name)
        }
    }

    private fun broadcast(context: Context, action: String, modeId: String, modeName: String?) {
        context.sendBroadcast(
            Intent(action)
                .putExtra(AutomationReceiver.EXTRA_MODE_ID, modeId)
                .putExtra(AutomationReceiver.EXTRA_MODE_NAME, modeName ?: modeId)
        )
    }

    private fun syncTimedAlarms(context: Context, old: AppState, new: AppState) {
        // Deactivations: schedule changed/new, cancel removed.
        new.timedModeDeactivations.forEach { (modeId, deadline) ->
            if (old.timedModeDeactivations[modeId] != deadline) {
                TimedAlarms.scheduleDeactivation(context, modeId, deadline)
            }
        }
        old.timedModeDeactivations.keys.forEach { modeId ->
            if (modeId !in new.timedModeDeactivations) {
                TimedAlarms.cancelDeactivation(context, modeId)
            }
        }
        // Reactivations: same pattern.
        new.timedModeReactivations.forEach { (modeId, deadline) ->
            if (old.timedModeReactivations[modeId] != deadline) {
                TimedAlarms.scheduleReactivation(context, modeId, deadline)
            }
        }
        old.timedModeReactivations.keys.forEach { modeId ->
            if (modeId !in new.timedModeReactivations) {
                TimedAlarms.cancelReactivation(context, modeId)
            }
        }
    }

    private fun applyBlockerServiceFor(context: Context, state: AppState) {
        val activeModes = state.modes.filter { state.activeModes.contains(it.id) }
        val modeNames = state.modes.associate { it.id to it.name }

        if (activeModes.isEmpty()) {
            // Keep the service alive while there's still something to wait
            // for (a schedule that might fire, or a paused mode that should
            // reactivate). Stop entirely when there is truly nothing left.
            val keepAlive = state.schedules.isNotEmpty() ||
                state.timedModeReactivations.isNotEmpty()
            if (keepAlive) {
                AppLogger.log("SYNC", "No active modes - keeping service alive for ${state.schedules.size} schedules / ${state.timedModeReactivations.size} pending reactivations")
                BlockerService.start(
                    context = context,
                    blockedApps = emptySet(),
                    blockMode = BlockMode.BLOCK_SELECTED,
                    activeModeIds = emptySet(),
                    timedModeReactivations = state.timedModeReactivations
                )
            } else {
                AppLogger.log("SYNC", "No active modes, schedules, or pending reactivations - stopping service")
                BlockerService.stop(context)
                ScheduleAlarmReceiver.cancelWatchdog(context)
            }
            return
        }

        val (blockMode, apps) = BlockDecider.ruleFor(activeModes)

        AppLogger.log("SYNC", "Starting service: mode=$blockMode, ${apps.size} apps, activeModes=${state.activeModes}")
        BlockerService.start(
            context = context,
            blockedApps = apps,
            blockMode = blockMode,
            activeModeIds = activeModes.map { it.id }.toSet(),
            manuallyActivatedModeIds = state.manuallyActivatedModes,
            timedModeDeactivations = state.timedModeDeactivations,
            modeNames = modeNames,
            timedModeReactivations = state.timedModeReactivations
        )
    }
}
