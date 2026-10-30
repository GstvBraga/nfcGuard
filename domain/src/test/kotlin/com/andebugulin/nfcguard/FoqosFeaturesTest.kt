package com.andebugulin.nfcguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FoqosFeaturesTest {

    private fun mode(
        id: String,
        nfcTagIds: List<String> = listOf("tag-1"),
        blockMode: BlockMode = BlockMode.BLOCK_SELECTED,
        options: ModeOptions = ModeOptions(tagStartsMode = true)
    ) = Mode(id = id, name = id, blockedApps = listOf("app"), blockMode = blockMode, nfcTagIds = nfcTagIds, options = options)

    // ─── TagStartLogic ──────────────────────────────────────────────────────

    @Test
    fun tagStart_offModeWithOwnTag_activatesIt() {
        val state = AppState(modes = listOf(mode("m1")))
        val result = TagStartLogic.apply(state, "tag-1", now = 1_000)
        assertEquals(setOf("m1"), result.started)
        assertTrue("m1" in result.newState.activeModes)
        assertTrue("m1" in result.newState.manuallyActivatedModes)
        assertTrue(result.newState.timedModeDeactivations.isEmpty())
    }

    @Test
    fun tagStart_withSessionMinutes_schedulesDeactivation() {
        val state = AppState(modes = listOf(mode("m1", options = ModeOptions(tagStartsMode = true, tagSessionMinutes = 25))))
        val result = TagStartLogic.apply(state, "tag-1", now = 1_000)
        assertEquals(1_000 + 25 * 60_000L, result.newState.timedModeDeactivations["m1"])
    }

    @Test
    fun tagStart_optionOff_doesNothing() {
        val state = AppState(modes = listOf(mode("m1", options = ModeOptions())))
        val result = TagStartLogic.apply(state, "tag-1", now = 0)
        assertFalse(result.changed)
        assertEquals(state, result.newState)
    }

    @Test
    fun tagStart_anyWildcardNeverStarts() {
        val state = AppState(modes = listOf(mode("m1", nfcTagIds = listOf("ANY"))))
        assertFalse(TagStartLogic.apply(state, "tag-1", now = 0).changed)
    }

    @Test
    fun tagStart_otherTag_doesNothing() {
        val state = AppState(modes = listOf(mode("m1")))
        assertFalse(TagStartLogic.apply(state, "tag-2", now = 0).changed)
    }

    @Test
    fun tagStart_pausedMode_scanEndsSessionForGood() {
        val state = AppState(
            modes = listOf(mode("m1")),
            timedModeReactivations = mapOf("m1" to 99_999L)
        )
        val result = TagStartLogic.apply(state, "tag-1", now = 0)
        assertEquals(setOf("m1"), result.ended)
        assertTrue(result.newState.activeModes.isEmpty())
        assertTrue(result.newState.timedModeReactivations.isEmpty())
    }

    @Test
    fun tagStart_conflictingPolarity_isReportedNotApplied() {
        val state = AppState(
            modes = listOf(mode("allow", blockMode = BlockMode.ALLOW_SELECTED), mode("m1", nfcTagIds = listOf("tag-1"))),
            activeModes = setOf("allow")
        )
        val result = TagStartLogic.apply(state, "tag-1", now = 0)
        assertEquals(setOf("m1"), result.conflicts)
        assertFalse("m1" in result.newState.activeModes)
    }

    // ─── BreakLogic ─────────────────────────────────────────────────────────

    private val breakMode = mode("m1", options = ModeOptions(breakBudgetMinutes = 15))

    @Test
    fun break_withinBudget_pausesAndCounts() {
        val state = AppState(modes = listOf(breakMode), activeModes = setOf("m1"))
        val after = BreakLogic.startBreak(state, "m1", 5, now = 0, currentDayOfWeek = 1, currentMinuteOfDay = 600)!!
        assertFalse("m1" in after.activeModes)
        assertEquals(5 * 60_000L, after.timedModeReactivations["m1"])
        assertEquals(10L, BreakLogic.remainingMinutes(after, "m1"))
    }

    @Test
    fun break_overBudget_isRefused() {
        val state = AppState(modes = listOf(breakMode), activeModes = setOf("m1"), breakUsedMinutes = mapOf("m1" to 12L))
        assertNull(BreakLogic.startBreak(state, "m1", 5, 0, 1, 600))
    }

    @Test
    fun break_modeWithoutBudget_isRefused() {
        val state = AppState(modes = listOf(mode("m1")), activeModes = setOf("m1"))
        assertNull(BreakLogic.remainingMinutes(state, "m1"))
        assertNull(BreakLogic.startBreak(state, "m1", 1, 0, 1, 600))
    }

    @Test
    fun break_usageSurvivesReactivationButResetsOnFreshStart() {
        val state = AppState(modes = listOf(breakMode), activeModes = setOf("m1"))
        val onBreak = BreakLogic.startBreak(state, "m1", 5, 0, 1, 600)!!
        val back = NfcUnlockLogic.applyReactivation(onBreak, "m1", now = 5 * 60_000L).newState
        assertEquals(10L, BreakLogic.remainingMinutes(back, "m1"))

        val off = ModeActivationLogic.applyModeDeactivation(back, "m1").newState
        val fresh = ModeActivationLogic.applyModeActivation(off, "m1", null).newState
        assertEquals(15L, BreakLogic.remainingMinutes(fresh, "m1"))
    }

    // ─── ModeEffects ────────────────────────────────────────────────────────

    @Test
    fun effects_mergeStrictestAcrossActiveModes() {
        val state = AppState(
            modes = listOf(
                mode("a", options = ModeOptions(ringer = RingerSetting.VIBRATE, blockReels = true)),
                mode("b", options = ModeOptions(ringer = RingerSetting.SILENT, doNotDisturb = true)),
                mode("c", options = ModeOptions(grayscale = true, blockShorts = true))
            ),
            activeModes = setOf("a", "b")
        )
        val effects = ModeEffects.effectsFor(state)
        assertEquals(RingerSetting.SILENT, effects.ringer)
        assertTrue(effects.doNotDisturb)
        assertTrue(effects.blockReels)
        assertFalse(effects.grayscale)
        assertFalse(effects.blockShorts)
    }

    @Test
    fun effects_noActiveModes_isAllOff() {
        assertEquals(ModeEffects.Effects(), ModeEffects.effectsFor(AppState(modes = listOf(mode("a")))))
    }

    // ─── SessionStats ───────────────────────────────────────────────────────

    private val day = 86_400_000L
    private fun session(start: Long, end: Long, name: String = "m") = FocusSession(name, name, start, end)

    @Test
    fun stats_minutesBetween_clipsToWindow() {
        val sessions = listOf(session(0, 60 * 60_000L), session(2 * 60 * 60_000L, 3 * 60 * 60_000L))
        assertEquals(60L, SessionStats.minutesBetween(sessions, 30 * 60_000L, 150 * 60_000L))
    }

    @Test
    fun stats_minutesByMode_groupsByName() {
        val sessions = listOf(session(0, 60_000, "a"), session(0, 120_000, "a"), session(0, 60_000, "b"))
        assertEquals(mapOf("a" to 3L, "b" to 1L), SessionStats.minutesByMode(sessions))
    }

    @Test
    fun stats_streak_countsBackAndToleratesEmptyToday() {
        val today = LocalDate.of(2026, 1, 10)
        val epochToday = today.toEpochDay() * day
        val sessions = listOf(
            session(epochToday - day, epochToday - day + 1),
            session(epochToday - 2 * day, epochToday - 2 * day + 1),
            session(epochToday - 4 * day, epochToday - 4 * day + 1)
        )
        assertEquals(2, SessionStats.streakDays(sessions, today, ZoneOffset.UTC))
        val withToday = sessions + session(epochToday + 5, epochToday + 10)
        assertEquals(3, SessionStats.streakDays(withToday, today, ZoneOffset.UTC))
    }
}
