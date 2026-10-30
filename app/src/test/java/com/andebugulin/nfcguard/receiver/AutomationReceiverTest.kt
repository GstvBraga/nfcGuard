package com.andebugulin.nfcguard.receiver

import com.andebugulin.nfcguard.AppState
import com.andebugulin.nfcguard.data.AppStateRepository
import com.andebugulin.nfcguard.data.SessionLog
import com.andebugulin.nfcguard.testing.grantOverlayPermission
import com.andebugulin.nfcguard.testing.mode
import com.andebugulin.nfcguard.testing.resetAppStateRepository

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AutomationReceiverTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val state get() = AppStateRepository.getInstance(app).current

    @Before fun setUp() {
        resetAppStateRepository()
        grantOverlayPermission()
        app.getSharedPreferences("focus_sessions", Context.MODE_PRIVATE).edit().clear().commit()
        AutomationReceiver.setEnabled(app, true)
    }

    private fun seed(s: AppState) = runBlocking { AppStateRepository.getInstance(app).update { s } }

    private fun send(action: String, mode: String) {
        AutomationReceiver().onReceive(app, Intent(action).putExtra(AutomationReceiver.EXTRA_MODE, mode))
    }

    @Test fun `disabled automation is ignored`() {
        AutomationReceiver.setEnabled(app, false)
        seed(AppState(modes = listOf(mode(id = "m1", name = "Focus"))))
        send(AutomationReceiver.ACTION_ACTIVATE, "Focus")
        assertTrue(state.activeModes.isEmpty())
    }

    @Test fun `activate by name, case-insensitive`() {
        seed(AppState(modes = listOf(mode(id = "m1", name = "Focus"))))
        send(AutomationReceiver.ACTION_ACTIVATE, "focus")
        assertEquals(setOf("m1"), state.activeModes)
    }

    @Test fun `toggle switches an untagged mode off`() {
        seed(AppState(modes = listOf(mode(id = "m1")), activeModes = setOf("m1")))
        send(AutomationReceiver.ACTION_TOGGLE, "m1")
        assertTrue(state.activeModes.isEmpty())
    }

    @Test fun `a mode locked to a specific tag cannot be switched off`() {
        seed(AppState(modes = listOf(mode(id = "m1", tagIds = listOf("tag-1"))), activeModes = setOf("m1")))
        send(AutomationReceiver.ACTION_DEACTIVATE, "m1")
        assertEquals(setOf("m1"), state.activeModes)
    }

    @Test fun `transitions are broadcast and recorded as sessions`() {
        seed(AppState(modes = listOf(mode(id = "m1", name = "Focus"))))
        send(AutomationReceiver.ACTION_ACTIVATE, "m1")
        send(AutomationReceiver.ACTION_DEACTIVATE, "m1")

        val actions = shadowOf(app).broadcastIntents.map { it.action }
        assertTrue(AutomationReceiver.MODE_STARTED in actions)
        assertTrue(AutomationReceiver.MODE_STOPPED in actions)

        val sessions = SessionLog.sessions(app)
        assertEquals(1, sessions.size)
        assertEquals("Focus", sessions.single().modeName)
        assertFalse(sessions.single().end < sessions.single().start)
    }
}
