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
import com.thapsus.cargo.presentation.DsarViewModel

/**
 * GDPR data-subject-access-request surface. Customer chooses Export
 * or Erase, optional notes, and submits — server has 30 days to
 * fulfil. Mirrors iOS DsarView.
 */
@Composable
fun DsarScreen() {
    val vm = remember { ThapsusSdk.dsarViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.load() }
    val state by vm.state.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()

    var selectedType by remember { mutableStateOf("export") }
    var notes by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Privacy",
            title = "Your data",
            subtitle = "Request an export of your data or have it permanently erased."
        )

        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Request type", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                TypeOption("Export my data", "export", selectedType) { selectedType = it }
                TypeOption("Erase my account", "erase", selectedType) { selectedType = it }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth().height(110.dp)
                )
                OrangeButton(
                    text = "Submit request",
                    enabled = form !is DsarViewModel.FormState.Submitting,
                    onClick = { vm.submit(selectedType, notes.takeIf { it.isNotBlank() }) }
                )
            }
        }

        (form as? DsarViewModel.FormState.Submitted)?.let {
            CalloutBanner(title = "Submitted", message = it.message)
        }
        (form as? DsarViewModel.FormState.Error)?.let {
            CalloutBanner(title = "Couldn't submit", message = it.message)
        }

        when (val s = state) {
            is DsarViewModel.UiState.Loading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
            is DsarViewModel.UiState.Error -> CalloutBanner(title = "Couldn't load", message = s.message)
            is DsarViewModel.UiState.Loaded -> {
                if (s.requests.isNotEmpty()) {
                    Text("Past requests", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
                    s.requests.forEach { r ->
                        SoftCard {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text(r.type.uppercase(), color = Brand.Orange, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                                    Text(r.status.uppercase(), color = Brand.ink.copy(alpha = 0.65f), fontSize = 11.sp)
                                }
                                r.createdAt?.take(10)?.let {
                                    Text(it, color = Brand.ink.copy(alpha = 0.55f), fontSize = 12.sp)
                                }
                                r.notes?.let { Text(it, color = Brand.ink, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
            else -> {}
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TypeOption(label: String, value: String, selected: String, onSelect: (String) -> Unit) {
    val isSelected = selected == value
    val bg = if (isSelected) Brand.Orange.copy(alpha = 0.16f) else Color.Transparent
    val border = if (isSelected) Brand.Orange else Brand.ink.copy(alpha = 0.12f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(2.dp)
            .clickable { onSelect(value) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .background(if (isSelected) Brand.Orange else Color.Transparent, RoundedCornerShape(999.dp))
                .padding(8.dp)
        ) {}
        Text(label, color = Brand.ink, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
