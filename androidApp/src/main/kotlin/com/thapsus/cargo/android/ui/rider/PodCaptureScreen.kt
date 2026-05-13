package com.thapsus.cargo.android.ui.rider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.OrangeButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.PodEventDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Proof-of-delivery capture for the rider. Mirrors iOS PodCaptureView.
 *
 * Flow:
 *   1. Tap "Take photo" → system camera (TakePicturePreview) returns a
 *      Bitmap. JPEG-encode in-memory.
 *   2. Tap "Add signature" → SignaturePadCompose modal sheet returns PNG.
 *   3. Capture GPS once (FINE if permitted, else COARSE) — best-effort.
 *   4. Fill Recipient name + 4-digit OTP + optional notes.
 *   5. Tap "Submit POD":
 *        a. POST /last-mile/pod/upload-url for "photo" → PUT JPEG bytes
 *           to signedUrl → keep `path`.
 *        b. Same dance for "signature" PNG.
 *        c. Build PodEventDto with photoPath + signaturePath + parcelIds
 *           + otp + recipient + lat/lng → vm.capturePod(event).
 *
 * Multi-parcel bundles use parcelIds[0] for the upload paths and for the
 * representative `parcel_id` field on PodEventDto; server fans out the
 * capture across every id in parcel_ids.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodCaptureScreen(
    runId: String,
    runZone: String,
    riderId: String,
    parcelIds: List<String>,
    defaultRecipient: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val vm = remember(riderId) { ThapsusSdk.riderRunViewModel(riderId) }
    val lastMile = remember { ThapsusSdk.lastMile() }
    val scope = rememberCoroutineScope()

    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var signatureBytes by remember { mutableStateOf<ByteArray?>(null) }
    var recipient by remember { mutableStateOf(defaultRecipient) }
    var otp by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }

    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var showSignature by remember { mutableStateOf(false) }
    val signatureSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            photoBitmap = bitmap
            photoBytes = bitmap.toJpegBytes(quality = 85)
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) cameraLauncher.launch(null) }

    val locationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) requestOneShotLocation(context) { loc ->
            lat = loc?.latitude
            lng = loc?.longitude
        }
    }

    // Auto-grab location once on entry (best-effort). The rider can re-request
    // via the location pill if it failed silently.
    LaunchedEffect(Unit) {
        if (hasAnyLocationPermission(context)) {
            requestOneShotLocation(context) { loc ->
                lat = loc?.latitude
                lng = loc?.longitude
            }
        }
    }

    val canSubmit = photoBytes != null && signatureBytes != null &&
        recipient.trim().isNotBlank() && otp.trim().length == 4 && !submitting

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
        EyebrowPill(label = "Last mile")
        EditorialHeader(
            title = "Proof of delivery",
            subtitle = "$runZone · ${parcelIds.size} parcel${if (parcelIds.size == 1) "" else "s"}"
        )

        // Photo card
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Photo of handover", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                photoBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "POD photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
                InkButton(
                    text = if (photoBytes == null) "Take photo" else "Retake photo",
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            cameraLauncher.launch(null)
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
            }
        }

        // Signature card
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recipient signature", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                if (signatureBytes != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(Modifier.width(6.dp))
                        Text("Signature captured", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                    }
                }
                InkButton(
                    text = if (signatureBytes == null) "Add signature" else "Re-sign",
                    onClick = { showSignature = true }
                )
            }
        }

        // Recipient + OTP + notes
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Handover details", color = Brand.ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Recipient name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = otp,
                    onValueChange = { v -> if (v.length <= 4) otp = v.filter { it.isDigit() } },
                    label = { Text("4-digit OTP") },
                    placeholder = { Text("1234") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )
            }
        }

        // Location pill — tap to refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brand.cream.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
                .border(1.dp, Brand.ink.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
                .clickable {
                    if (hasAnyLocationPermission(context)) {
                        requestOneShotLocation(context) { loc ->
                            lat = loc?.latitude
                            lng = loc?.longitude
                        }
                    } else {
                        locationPermission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = if (lat != null) Brand.Orange else Brand.ink.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            val text = if (lat != null && lng != null) {
                "%.4f, %.4f".format(lat!!, lng!!)
            } else {
                "Tap to capture location"
            }
            Text(
                text,
                color = if (lat != null) Brand.ink else Brand.ink.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }

        submitError?.let { msg ->
            CalloutBanner(title = "Couldn't submit", message = msg)
        }

        if (submitted) {
            CalloutBanner(
                title = "POD captured",
                message = "Queued to outbox. The run will refresh once it syncs."
            )
        }

        OrangeButton(
            text = if (submitting) "Submitting…" else "Submit POD",
            enabled = canSubmit,
            onClick = {
                submitting = true
                submitError = null
                scope.launch {
                    val pid = parcelIds.firstOrNull() ?: ""
                    val photo = photoBytes
                    val sig = signatureBytes
                    if (pid.isBlank() || photo == null || sig == null) {
                        submitError = "Photo, signature and a target parcel are required."
                        submitting = false
                        return@launch
                    }
                    try {
                        val photoPath = uploadAsset(lastMile, pid, kind = "photo",
                            bytes = photo, contentType = "image/jpeg")
                        val signaturePath = uploadAsset(lastMile, pid, kind = "signature",
                            bytes = sig, contentType = "image/png")
                        val event = PodEventDto(
                            id = UUID.randomUUID().toString(),
                            parcelId = pid,
                            parcelIds = parcelIds,
                            runId = runId,
                            capturedAt = Clock.System.now().toString(),
                            photoUrl = null,
                            signatureUrl = null,
                            photoPath = photoPath,
                            signaturePath = signaturePath,
                            otpUsed = otp.trim(),
                            recipientName = recipient.trim(),
                            recipientPhone = null,
                            result = "delivered",
                            riderId = riderId,
                            notes = notes.trim().takeIf { it.isNotBlank() },
                            latitude = lat,
                            longitude = lng,
                            capturedBy = riderId
                        )
                        vm.capturePod(event)
                        submitted = true
                    } catch (t: Throwable) {
                        submitError = t.message ?: "Upload failed."
                    } finally {
                        submitting = false
                    }
                }
            }
        )
        if (submitted) {
            InkButton(text = "Back to stops", onClick = onClose)
        }
        Spacer(Modifier.height(40.dp))
    }

    if (showSignature) {
        ModalBottomSheet(
            onDismissRequest = { showSignature = false },
            sheetState = signatureSheetState
        ) {
            SignaturePadCompose(
                onConfirm = { png ->
                    signatureBytes = png
                    showSignature = false
                },
                onCancel = { showSignature = false }
            )
        }
    }
}

/**
 * Uploads `bytes` to the private `pods` bucket via a server-issued signed
 * upload URL. Returns the in-bucket path that PodEventDto persists; admin
 * views later mint download URLs from this path via
 * `LastMileRepository.requestPodDocumentUrl`.
 */
private suspend fun uploadAsset(
    lastMile: com.thapsus.cargo.data.repository.LastMileRepository,
    parcelId: String,
    kind: String,
    bytes: ByteArray,
    contentType: String
): String? = withContext(Dispatchers.IO) {
    val resp = lastMile.requestPodUploadUrl(parcelId = parcelId, kind = kind)
    val signed = resp.signedUrl ?: error("Server did not return a signed upload URL for $kind")
    val path = resp.path

    // Plain HttpURLConnection PUT — no new Gradle dep, no ktor classpath
    // questions. The Supabase Storage signed upload endpoint accepts the
    // body directly with a matching Content-Type header.
    val conn = (URL(signed).openConnection() as HttpURLConnection).apply {
        requestMethod = "PUT"
        doOutput = true
        setRequestProperty("Content-Type", contentType)
        connectTimeout = 30_000
        readTimeout = 60_000
    }
    try {
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        if (code !in 200..299) error("Upload failed: HTTP $code")
    } finally {
        conn.disconnect()
    }
    path
}

private fun Bitmap.toJpegBytes(quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

private fun hasAnyLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

/**
 * Best-effort one-shot location read. Prefers FINE → falls back to COARSE →
 * passive last-known. Callback fires once, on the main looper. We don't
 * subscribe to continuous updates — the POD only needs the drop coordinate.
 */
@Suppress("MissingPermission") // caller verifies via [hasAnyLocationPermission]
private fun requestOneShotLocation(context: Context, onResult: (Location?) -> Unit) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (lm == null) {
        onResult(null)
        return
    }
    val provider = when {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }
    if (provider == null) {
        // Fall back to whatever last-known we have.
        val last = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        onResult(last)
        return
    }
    val listener = object : android.location.LocationListener {
        override fun onLocationChanged(location: Location) {
            onResult(location)
            lm.removeUpdates(this)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) { onResult(null) }
    }
    try {
        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
    } catch (t: Throwable) {
        onResult(lm.getLastKnownLocation(provider))
    }
}
