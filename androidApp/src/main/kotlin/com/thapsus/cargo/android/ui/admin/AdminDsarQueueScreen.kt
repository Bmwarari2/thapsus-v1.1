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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.DsarRequestDto
import kotlinx.coroutines.launch

/**
 * Admin queue of GDPR data-subject-access requests. Reads directly
 * from DsarRepository.queue(). Mirrors iOS AdminDsarQueueView.
 * Status filter chips + per-row "Mark fulfilled" action wired via
 * DsarRepository.updateStatus.
 */
@Composable
fun AdminDsarQueueScreen(onBack: () -> Unit) {
    val repo = remember { ThapsusSdk.dsar() }
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<DsarRequestDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<String?>("open") }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { repo.queue() }
            .onSuccess { rows = it; loading = false }
            .onFailure { error = it.message ?: "Couldn't load DSAR queue"; loading = false }
    }

    val visible = remember(rows, statusFilter) {
        if (statusFilter == null) rows else rows.filter { it.status == statusFilter }
    }

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
        EyebrowPill(label = "Privacy")
        EditorialHeader(
            title = "DSAR queue",
            subtitle = "30-day GDPR clock on every request."
        )

        FilterChipsRow(current = statusFilter, onChange = { statusFilter = it })

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
        }
        error?.let { CalloutBanner(title = "Couldn't load", message = it) }

        if (!loading && visible.isEmpty() && error == null) {
            SoftCard {
                Text("Queue clear — nothing to action.", color = Brand.ink.copy(alpha = 0.7f))
            }
        }

        visible.forEach { req ->
            RequestCard(
                req = req,
                onFulfill = {
                    scope.launch {
                        runCatching { repo.updateStatus(req.id, status = "fulfilled") }
                            .onSuccess { rows = rows.map { r -> if (r.id == req.id) r.copy(status = "fulfilled") else r } }
                            .onFailure { error = it.message ?: "Couldn't update" }
                    }
                }
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FilterChipsRow(current: String?, onChange: (String?) -> Unit) {
    val options = listOf(
        null to "All",
        "open" to "Open",
        "in_progress" to "In progress",
        "fulfilled" to "Fulfilled"
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
private fun RequestCard(req: DsarRequestDto, onFulfill: () -> Unit) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    req.type.uppercase(),
                    color = Brand.Orange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(req.status)
            }
            (req.userName ?: req.userEmail)?.let {
                Text(it, color = Brand.ink, fontWeight = FontWeight.SemiBold)
            }
            req.dueAt?.take(10)?.let {
                Text(
                    "Due $it",
                    color = Brand.ink.copy(alpha = 0.65f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
            req.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Brand.ink.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            if (req.status != "fulfilled") {
                Button(
                    onClick = onFulfill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Brand.Orange,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text("Mark fulfilled", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status) {
        "fulfilled" -> Color(0xFF2E7D32)
        "in_progress" -> Color(0xFF1976D2)
        "open" -> Color(0xFFE08B00)
        else -> Color(0xFF707070)
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            status.replace('_', ' ').uppercase(),
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 2.sp
        )
    }
}
