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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.thapsus.cargo.presentation.TicketDetailViewModel

/** Ticket detail — thread of messages + reply box. Mirrors iOS TicketDetailView. */
@Composable
fun TicketDetailScreen(ticketId: String, onClose: () -> Unit) {
    val vm = remember(ticketId) { ThapsusSdk.ticketDetailViewModel(ticketId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsStateWithLifecycle()
    var reply by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand.ink)
            }
        }

        when (val s = state) {
            TicketDetailViewModel.UiState.Loading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
            is TicketDetailViewModel.UiState.Error -> CalloutBanner(title = "Couldn't load ticket", message = s.message)
            is TicketDetailViewModel.UiState.Loaded -> {
                EditorialHeader(
                    eyebrow = "Ticket",
                    title = s.ticket?.subject ?: "Untitled ticket"
                )
                s.messages.forEach { m ->
                    val fromMe = m.role == null || m.role == "customer"
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (fromMe) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .background(
                                    if (fromMe) Brand.Orange.copy(alpha = 0.18f) else Brand.cream.copy(alpha = 0.78f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                if (!fromMe) {
                                    Text(
                                        m.name ?: "Support team",
                                        color = Brand.Orange,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(m.message, color = Brand.ink)
                                m.createdAt?.take(16)?.replace('T', ' ')?.let {
                                    Text(it, color = Brand.ink.copy(alpha = 0.55f), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                SoftCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Reply", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = reply,
                            onValueChange = { reply = it },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            placeholder = { Text("Type a reply…") }
                        )
                        OrangeButton(
                            text = "Send reply",
                            enabled = reply.trim().isNotEmpty(),
                            onClick = { vm.reply(reply.trim()); reply = "" }
                        )
                    }
                }
            }
            else -> {}
        }
        Spacer(Modifier.height(40.dp))
    }
}
