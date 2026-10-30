package com.andebugulin.nfcguard.service

import com.andebugulin.nfcguard.ModeEffects
import com.andebugulin.nfcguard.RingerSetting
import com.andebugulin.nfcguard.data.AppLogger

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings

/**
 * Applies Do Not Disturb, ringer and grayscale for the active modes, and puts
 * back whatever the user had before once no mode asks for them any more.
 *
 * What was applied and the user's original values live in prefs, so a
 * process death between "mode on" and "mode off" still restores correctly.
 * A setting whose permission is missing is left out of "applied", so it is
 * retried on the next sync after the user grants it.
 */
object DeviceEffects {

    private const val PREFS = "device_effects"
    private const val KEY_DND = "applied_dnd"
    private const val KEY_RINGER = "applied_ringer"
    private const val KEY_GRAY = "applied_gray"
    private const val KEY_ORIG_FILTER = "orig_filter"
    private const val KEY_ORIG_RINGER = "orig_ringer"

    private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val DALTONIZER = "accessibility_display_daltonizer"
    private const val DALTONIZER_MONOCHROME = 0

    fun hasDndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    /** WRITE_SECURE_SETTINGS can only be granted over adb: `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`. */
    fun hasGrayscaleAccess(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun apply(context: Context, effects: ModeEffects.Effects) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        val notifications = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)

        val appliedDnd = prefs.getBoolean(KEY_DND, false)
        if (effects.doNotDisturb != appliedDnd && hasDndAccess(context)) {
            runCatching {
                if (effects.doNotDisturb) {
                    edit.putInt(KEY_ORIG_FILTER, notifications.currentInterruptionFilter)
                    notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                } else {
                    notifications.setInterruptionFilter(
                        prefs.getInt(KEY_ORIG_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
                    )
                }
                edit.putBoolean(KEY_DND, effects.doNotDisturb)
            }.onFailure { AppLogger.log("EFFECTS", "DND failed: ${it.message}") }
        }

        val appliedRinger = RingerSetting.valueOf(prefs.getString(KEY_RINGER, RingerSetting.UNCHANGED.name)!!)
        if (effects.ringer != appliedRinger) {
            runCatching {
                if (appliedRinger == RingerSetting.UNCHANGED) {
                    edit.putInt(KEY_ORIG_RINGER, audio.ringerMode)
                }
                audio.ringerMode = when (effects.ringer) {
                    RingerSetting.SILENT -> AudioManager.RINGER_MODE_SILENT
                    RingerSetting.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                    RingerSetting.UNCHANGED -> prefs.getInt(KEY_ORIG_RINGER, AudioManager.RINGER_MODE_NORMAL)
                }
                edit.putString(KEY_RINGER, effects.ringer.name)
            }.onFailure { AppLogger.log("EFFECTS", "Ringer failed: ${it.message}") }
        }

        val appliedGray = prefs.getBoolean(KEY_GRAY, false)
        if (effects.grayscale != appliedGray && hasGrayscaleAccess(context)) {
            runCatching {
                val resolver = context.contentResolver
                Settings.Secure.putInt(resolver, DALTONIZER_ENABLED, if (effects.grayscale) 1 else 0)
                if (effects.grayscale) Settings.Secure.putInt(resolver, DALTONIZER, DALTONIZER_MONOCHROME)
                edit.putBoolean(KEY_GRAY, effects.grayscale)
            }.onFailure { AppLogger.log("EFFECTS", "Grayscale failed: ${it.message}") }
        }

        edit.apply()
    }
}
