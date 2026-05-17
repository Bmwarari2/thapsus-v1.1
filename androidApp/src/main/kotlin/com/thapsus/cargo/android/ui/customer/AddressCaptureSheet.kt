package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.InkButton
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.presentation.ProfileEditViewModel

// Post-authentication address-capture prompt — Android twin of
// AddressCaptureSheet.swift. ModalBottomSheet expanded to ~85% via the
// default skipPartiallyExpanded behaviour; "Save address" persists via
// the shared ProfileEditViewModel (same path the canonical Edit profile
// flow uses), "Skip for now" closes without writing. Either action
// flips the AddressPromptStore flag so subsequent cold launches don't
// re-show the sheet. The Account tab remains canonical for editing.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressCaptureSheet(
    onResolved: () -> Unit
) {
    val vm = remember { ThapsusSdk.profileEditViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val form by vm.form.collectAsStateWithLifecycle()

    var address by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSubmitting = form is ProfileEditViewModel.FormState.Submitting

    LaunchedEffect(form) {
        if (form is ProfileEditViewModel.FormState.Saved) {
            onResolved()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { /* dismissal is explicit via Save / Skip — swallow swipe-down */ },
        sheetState = sheetState,
        containerColor = Brand.cream
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Where should we deliver?",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Brand.ink
            )
            Text(
                text = "Add your Kenya delivery address so our riders know where to drop your parcels. You can change it any time from the Account tab.",
                color = Brand.ink.copy(alpha = 0.7f)
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Kenya delivery address") },
                placeholder = { Text("e.g. Westlands, Nairobi — Apartment 4B") },
                minLines = 3,
                maxLines = 6,
                supportingText = {
                    Text("Include landmarks, gate numbers, or anything that helps a rider find you in Nairobi.")
                },
                modifier = Modifier.fillMaxWidth()
            )

            when (val f = form) {
                is ProfileEditViewModel.FormState.Error -> CalloutBanner(
                    title = "Couldn't save",
                    message = f.message
                )
                else -> Unit
            }

            InkButton(
                text = if (isSubmitting) "Saving…" else "Save address",
                enabled = !isSubmitting && address.trim().isNotEmpty(),
                onClick = {
                    vm.save(
                        name = null,
                        phone = null,
                        languagePref = null,
                        deliveryAddress = address.trim()
                    )
                }
            )

            TextButton(
                onClick = onResolved,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = "Skip for now",
                    color = Brand.ink.copy(alpha = 0.65f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
