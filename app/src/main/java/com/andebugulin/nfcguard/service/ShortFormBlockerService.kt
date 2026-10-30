package com.andebugulin.nfcguard.service

import com.andebugulin.nfcguard.ModeEffects
import com.andebugulin.nfcguard.data.AppLogger
import com.andebugulin.nfcguard.data.AppStateRepository

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Backs out of Instagram Reels and YouTube Shorts while a mode with
 * [com.andebugulin.nfcguard.ModeOptions.blockReels] / `blockShorts` is on,
 * leaving the rest of both apps usable.
 *
 * Separate from [ForegroundDetectorService] on purpose: spotting the player
 * needs window content, which that service promises never to read. This one
 * is scoped to the two packages in its XML config, and the user enables it
 * only if they want the feature.
 *
 * Detection matches view ids from the apps' current layouts. The apps rename
 * them now and then, so a release of theirs can break this until the ids
 * below are updated.
 */
class ShortFormBlockerService : AccessibilityService() {

    private var lastBackAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val ids = when (pkg) {
            INSTAGRAM -> REELS_VIEW_IDS
            YOUTUBE -> SHORTS_VIEW_IDS
            else -> return
        }
        val now = System.currentTimeMillis()
        if (now - lastBackAt < BACK_COOLDOWN_MS) return

        val effects = ModeEffects.effectsFor(AppStateRepository.getInstance(this).current)
        val wanted = if (pkg == INSTAGRAM) effects.blockReels else effects.blockShorts
        if (!wanted) return

        val root = rootInActiveWindow ?: return
        val onPlayer = ids.any { id ->
            root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").any { it.isVisibleToUser }
        }
        if (onPlayer) {
            lastBackAt = now
            performGlobalAction(GLOBAL_ACTION_BACK)
            AppLogger.log("SHORTFORM", "Backed out of short-form player in $pkg")
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val INSTAGRAM = "com.instagram.android"
        private const val YOUTUBE = "com.google.android.youtube"
        private const val BACK_COOLDOWN_MS = 700L

        private val REELS_VIEW_IDS = listOf("clips_viewer_view_pager", "clips_viewer_pager", "root_clips_layout")
        private val SHORTS_VIEW_IDS = listOf("reel_recycler", "reel_player_page_container", "reel_watch_player")

        fun isEnabled(context: Context): Boolean {
            val name = "${context.packageName}/${ShortFormBlockerService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(name, ignoreCase = true) }
        }
    }
}
