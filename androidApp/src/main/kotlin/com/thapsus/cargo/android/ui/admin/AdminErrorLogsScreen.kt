package com.thapsus.cargo.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.ErrorLogRow
import com.thapsus.cargo.presentation.AdminErrorLogsViewModel

/**
 * Server error-log feed. Mirrors iOS AdminErrorLogsView.
 * Stats header (24h / 7d / fatal-24h / total) + level filter chips +
 * free-text search box. Read-only list; admin can wipe the buffer
 * (a separate "Clear all" action is not surfaced here yet — that
 * lives behind a confirmation flow on iOS too).
 */
@Composable
fun AdminErrorLogsScreen(onBack: () -> Unit) {
    val vm = remember { ThapsusSdk.adminErrorLogsViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.load() }
    val state by vm.state.collectAsStateWithLifecycle()

    var search by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand.ink)
            }
        }
        EyebrowPill(label = "Admin")
        EditorialHeader(
            title = "Error logs",
            subtitle = "Server-side warnings + exceptions."
        )

        when (val s = state) {
            AdminErrorLogsViewModel.UiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Brand.ink) }
            is AdminErrorLogsViewModel.UiState.Error -> CalloutBanner(title = "Couldn't load", message = s.message)
            is AdminErrorLogsViewModel.UiState.Loaded -> {
                InkCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatBlock("24h", s.stats.stats.last24h.toString(), Modifier.weight(1f))
                        StatBlock("7d", s.stats.stats.last7d.toString(), Modifier.weight(1f))
                        StatBlock("Fatal 24h", s.stats.stats.fatal24h.toString(), Modifier.weight(1f), accent = true)
                        StatBlock("Total", s.stats.stats.total.toString(), Modifier.weight(1f))
                    }
                }

                LevelChipsRow(current = levelFilter, onChange = { lvl ->
                    levelFilter = lvl
                    vm.load(level = lvl, search = search.trim().takeIf { it.isNotBlank() })
                })

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search message") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        androidx.compose.material3.TextButton(onClick = {
                            vm.load(level = levelFilter, search = search.trim().takeIf { it.isNotBlank() })
                        }) { Text("Apply") }
                    }
                )

                if (s.logs.isEmpty()) {
                    SoftCard {
                        Text(
                            "No errors match the filter. ${"👏"}",
                            color = Brand.ink.copy(alpha = 0.75f)
                        )
                    }
                } else {
                    s.logs.forEach { row -> LogRow(row) }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            label.uppercase(),
            color = Brand.cream.copy(alpha = 0.55f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            value,
            color = if (accent) Color(0xFFFFB199) else Brand.cream,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp
        )
    }
}

@Composable
private fun LevelChipsRow(current: String?, onChange: (String?) -> Unit) {
    val options = listOf(
        null to "All",
        "error" to "Error",
        "warn" to "Warn",
        "fatal" to "Fatal",
        "info" to "Info"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val selected = current == value
            Box(
                modifier = Modifier
                    .background(
                        if (selected) Brand.ink else Brand.cream.copy(alpha = 0.78f),
                        RoundedCornerShape(999.dp)
                    )
                    .clickable { onChange(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label, color = if (selected) Brand.cream else Brand.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LogRow(row: ErrorLogRow) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.level.uppercase(),
                    color = levelColor(row.level),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier
                        .background(levelColor(row.level).copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Spacer(Modifier.weight(1f))
                row.createdAt?.take(16)?.replace('T', ' ')?.let {
                    Text(it, color = Brand.ink.copy(alpha = 0.55f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
            Text(row.message, color = Brand.ink, fontWeight = FontWeight.SemiBold)
            row.source?.let { Text(it, color = Brand.ink.copy(alpha = 0.6f), fontSize = 11.sp) }
            row.path?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.method?.let { m ->
                        Text(m, color = Brand.Orange, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                    }
                    Text(it, color = Brand.ink.copy(alpha = 0.55f), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
                    row.statusCode?.let { code ->
                        Text(code.toString(), color = Brand.ink.copy(alpha = 0.55f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun levelColor(level: String): Color = when (level.lowercase()) {
    "fatal", "error" -> Color(0xFFB3261E)
    "warn", "warning" -> Color(0xFFE08B00)
    "info" -> Color(0xFF1976D2)
    else -> Color(0xFF707070)
}
