package com.thapsus.cargo.android.ui.operator

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.hardware.SkuScannerView
import com.thapsus.cargo.android.ui.primitives.InkCard
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.OpsScannedParcelDto
import com.thapsus.cargo.presentation.SkuScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkuScannerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val vm = remember { ThapsusSdk.skuScannerViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsStateWithLifecycle()

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    LaunchedEffect(Unit) {
        if (!hasCamera) launcher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Scan SKU", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (hasCamera) {
                when (val s = state) {
                    is SkuScannerViewModel.State.Idle,
                    is SkuScannerViewModel.State.Looking,
                    is SkuScannerViewModel.State.NotFound,
                    is SkuScannerViewModel.State.Failed -> {
                        SkuScannerView { vm.onScanned(it) }
                        StatusOverlay(state = s, onTryAgain = { vm.reset() })
                    }
                    is SkuScannerViewModel.State.Found -> {
                        FoundParcelView(parcel = s.parcel, onScanAnother = { vm.reset() })
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Camera permission needed", color = Color.White)
                        Button(
                            onClick = { launcher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = Brand.Orange)
                        ) { Text("Grant access") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusOverlay(state: SkuScannerViewModel.State, onTryAgain: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier = Modifier
                .padding(20.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            when (state) {
                is SkuScannerViewModel.State.Looking -> Text(
                    "Looking up ${state.barcode}…",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                is SkuScannerViewModel.State.NotFound -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "No parcel for ${state.barcode}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "TRY AGAIN",
                        color = Brand.Orange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Brand.Orange.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    LaunchedEffect(state) { onTryAgain() } // auto-reset after a moment
                }
                is SkuScannerViewModel.State.Failed -> Text(
                    state.message,
                    color = Color(0xFFEF5350),
                    fontWeight = FontWeight.SemiBold
                )
                else -> Text("Point at a STK label", color = Color.White)
            }
        }
    }
}

@Composable
private fun FoundParcelView(parcel: OpsScannedParcelDto, onScanAnother: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF100C0C))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InkCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "FOUND",
                    color = Brand.cream.copy(alpha = 0.6f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
                Text(
                    parcel.barcode ?: parcel.packageId.take(8),
                    color = Brand.Orange,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(parcel.description ?: parcel.retailer ?: "Parcel", color = Brand.cream)
            }
        }
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Status", color = Brand.ink.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                    Text(
                        parcel.packageStatus?.replace('_', ' ') ?: "—",
                        color = Brand.ink,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                parcel.customer?.fullName?.let {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Customer", color = Brand.ink.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                        Text(it, color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    }
                }
                parcel.retailer?.let {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Retailer", color = Brand.ink.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                        Text(it, color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    }
                }
                parcel.chargeableKg?.let {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Chargeable", color = Brand.ink.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                        Text("%.2f kg".format(it), color = Brand.ink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Button(
            onClick = onScanAnother,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand.Orange, contentColor = Color.White)
        ) {
            Text("Scan another", fontWeight = FontWeight.SemiBold)
        }
    }
}
