package com.andebugulin.nfcguard.ui.modes

import com.andebugulin.nfcguard.ModeOptions
import com.andebugulin.nfcguard.R
import com.andebugulin.nfcguard.RingerSetting
import com.andebugulin.nfcguard.service.DeviceEffects
import com.andebugulin.nfcguard.service.ShortFormBlockerService
import com.andebugulin.nfcguard.ui.GuardianTheme
import com.andebugulin.nfcguard.ui.components.GuardianDialog
import com.andebugulin.nfcguard.ui.components.GuardianType

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SESSION_CHOICES = listOf<Long?>(null, 15, 25, 45, 60, 90)
private val BREAK_CHOICES = listOf<Long?>(null, 5, 10, 15, 30)

/** Everything a mode can do besides blocking apps. Edits a copy; nothing is saved until the editor saves. */
@Composable
fun ModeOptionsDialog(
    initial: ModeOptions,
    installedApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (ModeOptions) -> Unit
) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(initial) }
    var pickingApp by remember { mutableStateOf(false) }

    GuardianDialog(
        title = stringResource(R.string.options_title),
        confirmLabel = stringResource(R.string.options_done),
        onConfirm = { onConfirm(options) },
        dismissLabel = stringResource(R.string.options_cancel),
        onDismiss = onDismiss
    ) {
        Column(
            Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel(stringResource(R.string.options_section_tag))
            OptionSwitch(
                label = stringResource(R.string.options_tag_starts_mode),
                hint = stringResource(R.string.options_tag_starts_mode_hint),
                checked = options.tagStartsMode,
                onChange = { options = options.copy(tagStartsMode = it) }
            )
            if (options.tagStartsMode) {
                Text(stringResource(R.string.options_session_length), style = GuardianType.Meta, color = GuardianTheme.TextSecondary)
                Choices(SESSION_CHOICES, options.tagSessionMinutes, { minutesLabel(it, R.string.options_until_unlocked) }) {
                    options = options.copy(tagSessionMinutes = it)
                }
            }

            SectionLabel(stringResource(R.string.options_section_breaks))
            Choices(BREAK_CHOICES, options.breakBudgetMinutes, { minutesLabel(it, R.string.options_no_breaks) }) {
                options = options.copy(breakBudgetMinutes = it)
            }

            SectionLabel(stringResource(R.string.options_section_on_start))
            OptionSwitch(
                label = stringResource(R.string.options_dnd),
                checked = options.doNotDisturb,
                onChange = { options = options.copy(doNotDisturb = it) }
            )
            val needsDndAccess = (options.doNotDisturb || options.ringer == RingerSetting.SILENT) &&
                !DeviceEffects.hasDndAccess(context)
            if (needsDndAccess) {
                GrantButton(stringResource(R.string.options_grant_dnd)) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
            Text(stringResource(R.string.options_ringer), style = GuardianType.Meta, color = GuardianTheme.TextSecondary)
            Choices(RingerSetting.entries, options.ringer, {
                when (it) {
                    RingerSetting.UNCHANGED -> stringResource(R.string.options_ringer_unchanged)
                    RingerSetting.VIBRATE -> stringResource(R.string.options_ringer_vibrate)
                    RingerSetting.SILENT -> stringResource(R.string.options_ringer_silent)
                }
            }) { options = options.copy(ringer = it) }

            OptionSwitch(
                label = stringResource(R.string.options_grayscale),
                checked = options.grayscale,
                onChange = { options = options.copy(grayscale = it) }
            )
            if (options.grayscale && !DeviceEffects.hasGrayscaleAccess(context)) {
                Text(stringResource(R.string.options_grayscale_adb), style = GuardianType.Meta, color = GuardianTheme.Warning)
                Text(
                    "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = GuardianTheme.TextSecondary
                )
            }

            val launchName = options.launchApp?.let { pkg -> installedApps.find { it.packageName == pkg }?.appName ?: pkg }
            OptionRow(
                label = stringResource(R.string.options_launch_app),
                value = launchName ?: stringResource(R.string.options_launch_none),
                onClick = { pickingApp = true }
            )

            SectionLabel(stringResource(R.string.options_section_short_form))
            OptionSwitch(
                label = stringResource(R.string.options_block_reels),
                checked = options.blockReels,
                onChange = { options = options.copy(blockReels = it) }
            )
            OptionSwitch(
                label = stringResource(R.string.options_block_shorts),
                checked = options.blockShorts,
                onChange = { options = options.copy(blockShorts = it) }
            )
            if ((options.blockReels || options.blockShorts) && !ShortFormBlockerService.isEnabled(context)) {
                Text(stringResource(R.string.options_short_form_service_hint), style = GuardianType.Meta, color = GuardianTheme.Warning)
                GrantButton(stringResource(R.string.options_open_accessibility)) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
    }

    if (pickingApp) {
        LaunchAppPicker(
            apps = installedApps,
            onDismiss = { pickingApp = false },
            onPick = {
                options = options.copy(launchApp = it)
                pickingApp = false
            }
        )
    }
}

@Composable
private fun LaunchAppPicker(apps: List<AppInfo>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) { apps.filter { it.appName.contains(query, ignoreCase = true) } }
    GuardianDialog(
        title = stringResource(R.string.options_launch_app),
        confirmLabel = stringResource(R.string.options_launch_none),
        onConfirm = { onPick(null) },
        dismissLabel = stringResource(R.string.options_cancel),
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.modes_search_apps), fontSize = 12.sp) },
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(shown, key = { it.packageName }) { app ->
                TextButton(onClick = { onPick(app.packageName) }, modifier = Modifier.fillMaxWidth()) {
                    Text(app.appName, color = GuardianTheme.TextPrimary, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun minutesLabel(minutes: Long?, noneLabel: Int): String =
    if (minutes == null) stringResource(noneLabel) else stringResource(R.string.options_minutes, minutes)

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = GuardianType.Label, color = GuardianTheme.TextTertiary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = GuardianTheme.TextPrimary)
            if (hint != null) Text(hint, style = GuardianType.Meta, color = GuardianTheme.TextTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = GuardianTheme.TextTertiary,
                uncheckedTrackColor = GuardianTheme.BackgroundSurface
            )
        )
    }
}

@Composable
private fun OptionRow(label: String, value: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = GuardianTheme.TextPrimary, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(value.uppercase(), style = GuardianType.Label, color = GuardianTheme.TextPrimary) }
    }
}

@Composable
private fun GrantButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(0.dp)) {
        Text(label, style = GuardianType.Label, color = GuardianTheme.TextPrimary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(choices: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { choice ->
            val isSelected = choice == selected
            Surface(
                onClick = { onSelect(choice) },
                shape = RoundedCornerShape(0.dp),
                color = if (isSelected) Color.White else GuardianTheme.BackgroundSurface
            ) {
                Text(
                    label(choice),
                    fontSize = 11.sp,
                    color = if (isSelected) Color.Black else Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
