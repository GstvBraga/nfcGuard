package com.andebugulin.nfcguard

/**
 * What the active modes ask of the device besides blocking apps, merged into
 * one set of [Effects]. The strictest request wins: any mode asking for Do Not
 * Disturb turns it on, SILENT beats VIBRATE beats UNCHANGED.
 */
object ModeEffects {

    data class Effects(
        val doNotDisturb: Boolean = false,
        val ringer: RingerSetting = RingerSetting.UNCHANGED,
        val grayscale: Boolean = false,
        val blockReels: Boolean = false,
        val blockShorts: Boolean = false
    )

    fun effectsFor(state: AppState): Effects {
        val options = state.modes.filter { it.id in state.activeModes }.map { it.options }
        return Effects(
            doNotDisturb = options.any { it.doNotDisturb },
            ringer = when {
                options.any { it.ringer == RingerSetting.SILENT } -> RingerSetting.SILENT
                options.any { it.ringer == RingerSetting.VIBRATE } -> RingerSetting.VIBRATE
                else -> RingerSetting.UNCHANGED
            },
            grayscale = options.any { it.grayscale },
            blockReels = options.any { it.blockReels },
            blockShorts = options.any { it.blockShorts }
        )
    }
}
