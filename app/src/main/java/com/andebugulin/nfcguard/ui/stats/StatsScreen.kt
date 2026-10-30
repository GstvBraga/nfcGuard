package com.andebugulin.nfcguard.ui.stats

import com.andebugulin.nfcguard.R
import com.andebugulin.nfcguard.SessionStats
import com.andebugulin.nfcguard.data.SessionLog
import com.andebugulin.nfcguard.ui.GuardianTheme
import com.andebugulin.nfcguard.ui.components.GuardianType
import com.andebugulin.nfcguard.ui.components.ScreenHeader
import com.andebugulin.nfcguard.ui.formatDuration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sessions = remember { SessionLog.sessions(context) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val startOfWeek = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val byMode = remember(sessions) { SessionStats.minutesByMode(sessions).entries.sortedByDescending { it.value } }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Column(
        Modifier
            .fillMaxSize()
            .background(GuardianTheme.BackgroundPrimary)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        ScreenHeader(title = stringResource(R.string.stats_title), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.stats_today), formatDuration(SessionStats.minutesBetween(sessions, startOfToday, now)), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_last_7_days), formatDuration(SessionStats.minutesBetween(sessions, startOfWeek, now)), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_streak), stringResource(R.string.stats_days, SessionStats.streakDays(sessions, today, zone)), Modifier.weight(1f))
                }
            }
            if (sessions.isEmpty()) {
                item { Text(stringResource(R.string.stats_empty), color = GuardianTheme.TextTertiary, style = GuardianType.Meta) }
            }
            if (byMode.isNotEmpty()) {
                item { Text(stringResource(R.string.stats_by_mode), style = GuardianType.Label, color = GuardianTheme.TextSecondary) }
                items(byMode) { (name, minutes) -> Line(name, formatDuration(minutes)) }
            }
            if (sessions.isNotEmpty()) {
                item { Text(stringResource(R.string.stats_recent), style = GuardianType.Label, color = GuardianTheme.TextSecondary) }
                items(sessions.takeLast(30).reversed()) { session ->
                    Line(
                        "${session.modeName} · ${dateFormat.format(Date(session.start))}",
                        formatDuration((session.end - session.start) / 60_000)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(0.dp), color = GuardianTheme.BackgroundSurface) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = GuardianType.Meta, color = GuardianTheme.TextTertiary)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GuardianTheme.TextPrimary)
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = GuardianTheme.TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = GuardianTheme.TextSecondary)
    }
}
