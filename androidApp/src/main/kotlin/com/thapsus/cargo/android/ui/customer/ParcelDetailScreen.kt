package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.PackageDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcelDetailScreen(parcelId: String, userId: String, onClose: () -> Unit) {
    val packagesRepo = remember { ThapsusSdk.packages() }
    var parcel by remember { mutableStateOf<PackageDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(parcelId, userId) {
        runCatching { packagesRepo.refreshForUser(userId) }
            .onFailure { error = "Couldn't refresh parcel data" }
        packagesRepo.observeOne(parcelId).collect { parcel = it }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Parcel") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val p = parcel
            if (p == null && error != null) {
                CalloutBanner(title = "Couldn't load parcel", message = error!!)
            } else if (p == null) {
                Text("Loading…", color = Brand.ink.copy(alpha = 0.7f))
            } else {
                InkCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "TRACKING",
                            color = Brand.cream.copy(alpha = 0.6f),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp
                        )
                        Text(
                            p.trackingNumber ?: p.id.take(8),
                            color = Brand.Orange,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                        Text(p.description ?: p.retailer ?: "Parcel", color = Brand.cream)
                    }
                }
                SoftCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRow("Status", statusLabel(p.status))
                        p.retailer?.let { DetailRow("Retailer", it) }
                        p.description?.let { DetailRow("Description", it) }
                        p.chargeableKg?.let { DetailRow("Chargeable weight", "%.2f kg".format(it)) }
                        p.actualKg?.let { DetailRow("Actual weight", "%.2f kg".format(it)) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Brand.ink.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Text(value, color = Brand.ink, fontWeight = FontWeight.SemiBold)
    }
}
