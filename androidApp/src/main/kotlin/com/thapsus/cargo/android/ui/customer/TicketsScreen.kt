package com.thapsus.cargo.android.ui.customer

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.presentation.TicketsListViewModel

/**
 * Customer support tickets list. Mirrors iOS TicketsView.
 * Tap a ticket → detail; FAB-style "New ticket" opens a bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(userId: String, onOpenTicket: (String) -> Unit) {
    val vm = remember { ThapsusSdk.ticketsListViewModel(userId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Help",
            title = "Support tickets",
            subtitle = "Open a thread and our team will reply within one working day."
        )
        OrangeButton(text = "New ticket", onClick = { showCreate = true })

        when (val s = state) {
            TicketsListViewModel.UiState.Loading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
            is TicketsListViewModel.UiState.Error -> CalloutBanner(title = "Couldn't load", message = s.message)
            is TicketsListViewModel.UiState.Loaded -> {
                if (s.items.isEmpty()) {
                    SoftCard {
                        Text("No tickets yet. If something feels off, tell us above.", color = Brand.ink.copy(alpha = 0.7f))
                    }
                } else {
                    s.items.forEach { t ->
                        SoftCard(modifier = Modifier.clickable { onOpenTicket(t.id) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.subject, color = Brand.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    StatusChip(t.status)
                                }
                                t.createdAt?.take(10)?.let { Text(it, color = Brand.ink.copy(alpha = 0.55f), fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
            else -> {}
        }
        Spacer(Modifier.height(40.dp))
    }

    if (showCreate) {
        ModalBottomSheet(onDismissRequest = { showCreate = false }, sheetState = sheetState) {
            CreateTicketSheet(
                onSubmit = { subj, desc -> vm.create(subj, desc); showCreate = false },
                onCancel = { showCreate = false }
            )
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = when (status) {
        "open" -> Color(0xFF1976D2)
        "pending_user" -> Color(0xFFE08B00)
        "resolved", "closed" -> Color(0xFF2E7D32)
        else -> Color(0xFF707070)
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(status.replace('_', ' ').uppercase(), color = color, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun CreateTicketSheet(onSubmit: (String, String) -> Unit, onCancel: () -> Unit) {
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val canSubmit = subject.trim().isNotBlank() && description.trim().length >= 10

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorialHeader(eyebrow = "Help", title = "New ticket", subtitle = "Tell us what's going on.")
        OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().height(160.dp))
        OrangeButton(text = "Send", enabled = canSubmit, onClick = { onSubmit(subject.trim(), description.trim()) })
        androidx.compose.material3.TextButton(onClick = onCancel) { Text("Cancel") }
        Spacer(Modifier.height(20.dp))
    }
}
