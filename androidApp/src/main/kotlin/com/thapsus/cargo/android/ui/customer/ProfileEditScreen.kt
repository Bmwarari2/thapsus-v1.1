package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.repository.AuthSession
import com.thapsus.cargo.presentation.ProfileEditViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(onClose: () -> Unit) {
    val vm = remember { ThapsusSdk.profileEditViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val form by vm.form.collectAsStateWithLifecycle()

    val authSession by ThapsusSdk.auth().state.collectAsStateWithLifecycle(initialValue = AuthSession.Initializing)
    val profile = (authSession as? AuthSession.Authenticated)?.profile

    var name by remember(profile?.id) { mutableStateOf(profile?.fullName ?: "") }
    var phone by remember(profile?.id) { mutableStateOf(profile?.phone ?: "") }
    var deliveryAddress by remember(profile?.id) { mutableStateOf(profile?.deliveryAddress ?: "") }

    val isSubmitting = form is ProfileEditViewModel.FormState.Submitting

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
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
            EditorialHeader(
                eyebrow = "Profile",
                title = "Edit profile",
                subtitle = "Update your name, phone number, and Kenya delivery address. Email changes need a separate request via Support."
            )
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = deliveryAddress,
                        onValueChange = { deliveryAddress = it },
                        label = { Text("Kenya delivery address") },
                        placeholder = { Text("e.g. Westlands, Nairobi — Apartment 4B") },
                        minLines = 2,
                        maxLines = 4,
                        supportingText = {
                            Text("Riders use this to find you in Nairobi. Leave blank if you collect from the warehouse.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Language preference is synced from your iOS / web app for now — Android language picker arrives with the localization pass.",
                        color = Brand.ink.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            when (val f = form) {
                is ProfileEditViewModel.FormState.Saved -> CalloutBanner(
                    title = "Saved",
                    message = f.message
                )
                is ProfileEditViewModel.FormState.Error -> CalloutBanner(
                    title = "Couldn't save",
                    message = f.message
                )
                else -> Unit
            }

            InkButton(
                text = if (isSubmitting) "Saving…" else "Save",
                enabled = !isSubmitting && name.isNotBlank(),
                onClick = {
                    // Pass the address through (including the empty string) so
                    // the server treats it as a deliberate clear when the user
                    // wipes the field — matches the iOS ProfileEditView path.
                    vm.save(
                        name = name,
                        phone = phone,
                        languagePref = null,
                        deliveryAddress = deliveryAddress
                    )
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
