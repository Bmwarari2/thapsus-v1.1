package com.thapsus.cargo.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.presentation.AdminPaymentsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPaymentsScreen(onBack: () -> Unit) {
    val vm = remember { ThapsusSdk.adminPaymentsViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.load() }

    val state by vm.state.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Pending payments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            (action as? AdminPaymentsViewModel.ActionState.Done)?.let {
                CalloutBanner(title = "Action complete", message = it.message)
            }
            (action as? AdminPaymentsViewModel.ActionState.Error)?.let {
                CalloutBanner(title = "Action failed", message = it.message)
            }

            when (val s = state) {
                is AdminPaymentsViewModel.UiState.Loading -> CircularProgressIndicator(color = Brand.ink)
                is AdminPaymentsViewModel.UiState.Error -> CalloutBanner(
                    title = "Couldn't load",
                    message = s.message
                )
                is AdminPaymentsViewModel.UiState.Loaded -> {
                    if (s.transactions.isEmpty()) {
                        SoftCard { Text("No pending transactions.", color = Brand.ink.copy(alpha = 0.7f)) }
                    } else {
                        s.transactions.forEach { tx ->
                            SoftCard {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                tx.customerName ?: tx.payerName ?: "—",
                                                color = Brand.ink,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            tx.customerEmail?.let {
                                                Text(it, color = Brand.ink.copy(alpha = 0.6f), fontSize = 12.sp)
                                            }
                                        }
                                        Text(
                                            "+%.2f %s".format(tx.amount, tx.currency),
                                            color = Brand.Orange,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    tx.paymentReference?.let {
                                        Text(
                                            "Ref $it",
                                            color = Brand.ink.copy(alpha = 0.6f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                    tx.mpesaMessage?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it.take(120),
                                            color = Brand.ink.copy(alpha = 0.55f),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { vm.approve(tx.id, null) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                        ) { Text("Approve", fontWeight = FontWeight.SemiBold) }
                                        Button(
                                            onClick = { vm.reject(tx.id, null) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                                        ) { Text("Reject", fontWeight = FontWeight.SemiBold) }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
