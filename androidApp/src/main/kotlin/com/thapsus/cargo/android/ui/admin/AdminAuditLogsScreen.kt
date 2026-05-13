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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.thapsus.cargo.data.dto.AdminLogRow

/**
 * Admin audit-log feed. Paginated via AdminRepository.adminLogs.
 * Records who did what (provision a user, force a password reset,
 * edit pricing). Mirrors iOS AdminAuditLogsView.
 */
@Composable
fun AdminAuditLogsScreen(onBack: () -> Unit) {
    val adminRepo = remember { ThapsusSdk.adminRepo() }
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<AdminLogRow>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { adminRepo.adminLogs(page = 1) }
            .onSuccess { resp ->
                logs = resp.logs
                val total = resp.pagination?.total ?: resp.logs.size
                hasMore = resp.logs.size < total
                loading = false
            }
            .onFailure { error = it.message ?: "Couldn't load logs"; loading = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand.ink)
            }
        }
        EyebrowPill(label = "Admin")
        EditorialHeader(
            title = "Audit logs",
            subtitle = "Who did what, in chronological order."
        )

        error?.let { CalloutBanner(title = "Couldn't load", message = it) }

        if (loading && logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
        }

        if (!loading && logs.isEmpty() && error == null) {
            SoftCard {
                Text("No admin actions recorded yet.", color = Brand.ink.copy(alpha = 0.7f))
            }
        }

        logs.forEach { row -> LogRow(row) }

        if (hasMore && logs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brand.cream.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !loading) {
                        val next = page + 1
                        loading = true
                        scope.launch {
                            runCatching { adminRepo.adminLogs(page = next) }
                                .onSuccess { resp ->
                                    logs = logs + resp.logs
                                    page = next
                                    val total = resp.pagination?.total ?: logs.size
                                    hasMore = logs.size < total
                                    loading = false
                                }
                                .onFailure { error = it.message ?: "Couldn't load more"; loading = false }
                        }
                    }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (loading) "Loading…" else "Load more",
                    color = Brand.Orange,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun LogRow(row: AdminLogRow) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.action,
                    color = Brand.Orange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f)
                )
                row.createdAt?.take(16)?.replace('T', ' ')?.let {
                    Text(it, color = Brand.ink.copy(alpha = 0.55f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            (row.adminName ?: row.adminEmail)?.let {
                Text(it, color = Brand.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            row.details?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Brand.ink.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}
