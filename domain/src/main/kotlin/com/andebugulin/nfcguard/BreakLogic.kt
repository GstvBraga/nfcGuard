package com.andebugulin.nfcguard

/**
 * In-app breaks: pause an active mode without a tag, drawn from a per-session
 * budget ([ModeOptions.breakBudgetMinutes]). A break is a timed unlock, so the
 * mode comes back through the normal reactivation path.
 */
object BreakLogic {

    /** Minutes still available, or null when the mode allows no breaks. */
    fun remainingMinutes(state: AppState, modeId: String): Long? {
        val budget = state.modes.find { it.id == modeId }?.options?.breakBudgetMinutes ?: return null
        return (budget - (state.breakUsedMinutes[modeId] ?: 0)).coerceAtLeast(0)
    }

    /** New state with the break applied, or null if the mode is off or the budget is short. */
    fun startBreak(
        state: AppState,
        modeId: String,
        minutes: Long,
        now: Long,
        currentDayOfWeek: Int,
        currentMinuteOfDay: Int
    ): AppState? {
        val remaining = remainingMinutes(state, modeId) ?: return null
        if (modeId !in state.activeModes || minutes <= 0 || minutes > remaining) return null

        val unlocked = NfcUnlockLogic.applyUnlock(
            state = state,
            pending = PendingUnlock(modeIds = setOf(modeId), schedulesToDeactivate = emptySet()),
            selectedModeIds = null,
            reactivateAtMillis = now + minutes * 60_000,
            now = now,
            currentDayOfWeek = currentDayOfWeek,
            currentMinuteOfDay = currentMinuteOfDay
        ).newState
        val used = (state.breakUsedMinutes[modeId] ?: 0) + minutes
        return unlocked.copy(breakUsedMinutes = unlocked.breakUsedMinutes + (modeId to used))
    }
}
