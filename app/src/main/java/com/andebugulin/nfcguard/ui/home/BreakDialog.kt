package com.andebugulin.nfcguard.ui.home

import com.andebugulin.nfcguard.R
import com.andebugulin.nfcguard.ui.GuardianTheme
import com.andebugulin.nfcguard.ui.components.GuardianDialog
import com.andebugulin.nfcguard.ui.components.SelectableOption
import com.andebugulin.nfcguard.ui.formatDuration

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource

private val BREAK_LENGTHS = listOf(1L, 5L, 10L, 15L, 30L)

/** Pick how long a break lasts, capped by what is left of the session's budget. */
@Composable
fun BreakDialog(remainingMinutes: Long, onDismiss: () -> Unit, onStart: (Long) -> Unit) {
    val choices = (BREAK_LENGTHS.filter { it < remainingMinutes } + remainingMinutes).distinct()
    var selected by remember { mutableStateOf(choices.first()) }
    GuardianDialog(
        title = stringResource(R.string.break_title),
        message = stringResource(R.string.break_message, formatDuration(remainingMinutes)),
        confirmLabel = stringResource(R.string.break_start),
        onConfirm = { onStart(selected) },
        dismissLabel = stringResource(R.string.options_cancel),
        onDismiss = onDismiss,
        confirmColor = GuardianTheme.Warning
    ) {
        choices.forEach { minutes ->
            SelectableOption(
                label = formatDuration(minutes),
                selected = minutes == selected,
                onSelect = { selected = minutes }
            )
        }
    }
}
