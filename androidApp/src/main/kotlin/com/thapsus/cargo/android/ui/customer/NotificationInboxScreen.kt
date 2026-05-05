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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.NotificationDto
import com.thapsus.cargo.presentation.NotificationInboxViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxScreen(userId: String, onClose: () -> Unit) {
    val vm = remember(userId) { ThapsusSdk.notificationInboxViewModel(userId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.refresh() }

    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.markAllRead() }) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, tint = Brand.Orange)
                        Spacer(Modifier.size(6.dp))
                        Text("Mark all read", color = Brand.Orange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val s = state) {
                is NotificationInboxViewModel.UiState.Loading -> Row {
                    CircularProgressIndicator(color = Brand.ink)
                    Spacer(Modifier.size(12.dp))
                    Text("Loading…", color = Brand.ink)
                }
                is NotificationInboxViewModel.UiState.Error -> CalloutBanner(
                    title = "Couldn't load notifications",
                    message = s.message
                )
                is NotificationInboxViewModel.UiState.Loaded -> {
                    if (s.items.isEmpty()) {
                        SoftCard {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Inbox is empty", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Order updates, hold notices and admin messages will land here.",
                                    color = Brand.ink.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        s.items.forEach { item ->
                            NotificationRow(item = item, onTap = { if (!item.isRead) vm.markRead(item.id) })
                        }
                    }
                }
                else -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationDto, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.cream.copy(alpha = if (item.isRead) 0.6f else 0.85f), RoundedCornerShape(22.dp))
            .clickable(onClick = onTap)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (item.isRead) Color.Transparent else Brand.Orange, CircleShape)
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.message,
                color = Brand.ink,
                fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold
            )
            item.createdAt?.let {
                Text(it.take(19).replace('T', ' '), color = Brand.ink.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}
