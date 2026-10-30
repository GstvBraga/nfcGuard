package com.andebugulin.nfcguard.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Activity-owned tag actions, exposed to screens without threading them
 * through every composable. A scanned code arrives through the same
 * `scannedNfcTagId` channel as an NFC tap, so registration and unlocking
 * handle both without knowing which it was.
 */
class TagActions(
    val scanCode: () -> Unit,
    val writeTag: () -> Unit
)

val LocalTagActions = staticCompositionLocalOf { TagActions(scanCode = {}, writeTag = {}) }
