package com.andebugulin.nfcguard.ui.home

import com.andebugulin.nfcguard.AppState
import com.andebugulin.nfcguard.R
import com.andebugulin.nfcguard.receiver.AutomationReceiver
import com.andebugulin.nfcguard.service.QuickTileService
import com.andebugulin.nfcguard.ui.GuardianTheme
import com.andebugulin.nfcguard.ui.components.GuardianType
import com.andebugulin.nfcguard.ui.components.SelectableOption

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Settings section for the automation receiver and the Quick Settings tile. */
@Composable
fun AutomationSettings(appState: AppState) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AutomationReceiver.isEnabled(context)) }
    var tileModeId by remember { mutableStateOf(QuickTileService.quickModeId(context, appState)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.automation_title),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = GuardianTheme.TextSecondary,
            letterSpacing = 2.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.automation_allow), fontSize = 13.sp, color = GuardianTheme.TextPrimary)
                Text(stringResource(R.string.automation_allow_hint), style = GuardianType.Meta, color = GuardianTheme.TextTertiary)
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    AutomationReceiver.setEnabled(context, it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Color.White
                )
            )
        }
        if (enabled) {
            Text(
                "am broadcast -p ${context.packageName} -a ${AutomationReceiver.ACTION_TOGGLE} --es mode \"<name>\"",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = GuardianTheme.TextSecondary
            )
            Text(stringResource(R.string.automation_events_hint), style = GuardianType.Meta, color = GuardianTheme.TextTertiary)
        }

        if (appState.modes.isNotEmpty()) {
            Text(stringResource(R.string.automation_tile_mode), fontSize = 13.sp, color = GuardianTheme.TextPrimary)
            appState.modes.forEach { mode ->
                SelectableOption(
                    label = mode.name,
                    selected = mode.id == tileModeId,
                    onSelect = {
                        tileModeId = mode.id
                        QuickTileService.setQuickModeId(context, mode.id)
                        QuickTileService.requestRefresh(context)
                    }
                )
            }
        }
    }
}
