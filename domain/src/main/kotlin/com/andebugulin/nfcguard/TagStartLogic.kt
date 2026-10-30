package com.andebugulin.nfcguard

/**
 * The "same tag turns it on" half of the NFC toggle. The "turns it off" half
 * is the existing unlock flow ([NfcUnlockLogic]); the caller only gets here
 * when that flow found nothing to unlock.
 *
 * A tag starts a mode when the mode opted in ([ModeOptions.tagStartsMode]),
 * lists the tag explicitly ("ANY" never starts anything) and is off. If the
 * mode is merely paused — a timed unlock or a break waiting to reactivate —
 * the scan ends the session for good instead.
 */
object TagStartLogic {

    data class Result(
        val newState: AppState,
        val started: Set<String>,
        val ended: Set<String>,
        val conflicts: Set<String>
    ) {
        val changed: Boolean get() = started.isNotEmpty() || ended.isNotEmpty()
    }

    fun apply(state: AppState, tagId: String, now: Long): Result {
        val candidates = state.modes.filter {
            it.options.tagStartsMode && tagId in it.nfcTagIds && it.id !in state.activeModes
        }
        var current = state
        val started = mutableSetOf<String>()
        val ended = mutableSetOf<String>()
        val conflicts = mutableSetOf<String>()

        candidates.forEach { mode ->
            if (mode.id in current.timedModeReactivations) {
                current = ModeActivationLogic.applyModeDeactivation(current, mode.id).newState
                ended += mode.id
                return@forEach
            }
            val until = mode.options.tagSessionMinutes?.let { now + it * 60_000 }
            when (val result = ModeActivationLogic.applyModeActivation(current, mode.id, until)) {
                is ModeActivationLogic.ActivateModeResult.Activated -> {
                    current = result.newState
                    started += mode.id
                }
                is ModeActivationLogic.ActivateModeResult.Conflict -> conflicts += mode.id
                is ModeActivationLogic.ActivateModeResult.ModeNotFound -> Unit
            }
        }
        return Result(current, started, ended, conflicts)
    }
}
