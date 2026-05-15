package com.thapsus.cargo.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thapsus.cargo.ThapsusSdk
import com.thapsus.cargo.android.ui.primitives.CalloutBanner
import com.thapsus.cargo.android.ui.primitives.EditorialHeader
import com.thapsus.cargo.android.ui.primitives.EyebrowPill
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.data.dto.AdminUserDto
import com.thapsus.cargo.data.dto.EmailLogRow
import com.thapsus.cargo.presentation.AdminUserDetailViewModel

private val ROLES = listOf(
    "customer" to "Customer",
    "operator" to "Operator",
    "clearing_agent" to "Clearing agent",
    "rider" to "Rider",
    "admin" to "Admin"
)

@Composable
fun AdminUserDetailScreen(
    userId: String,
    onBack: () -> Unit
) {
    val vm = remember(userId) { ThapsusSdk.adminUserDetailViewModel(userId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }

    val state by vm.state.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()

    var showResetDialog by remember { mutableStateOf(false) }
    var showRoleSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val user = (state as? AdminUserDetailViewModel.UiState.Loaded)?.user
    val emails = (state as? AdminUserDetailViewModel.UiState.Loaded)?.emails.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand.ink)
            }
        }
        EyebrowPill(label = "Admin")
        EditorialHeader(
            title = user?.name?.ifBlank { user.email } ?: "User",
            subtitle = "Profile, recent emails, and account controls."
        )

        when (val a = action) {
            is AdminUserDetailViewModel.ActionState.Done -> CalloutBanner(
                title = "Done",
                message = a.message,
                tint = Color(0xFF0F8A4F).copy(alpha = 0.14f)
            )
            is AdminUserDetailViewModel.ActionState.Error -> CalloutBanner(
                title = "Failed",
                message = a.message
            )
            AdminUserDetailViewModel.ActionState.InFlight -> Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Brand.ink) }
            AdminUserDetailViewModel.ActionState.Idle -> Unit
        }

        Button(
            onClick = { vm.resendWelcome() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand.Orange,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Icon(Icons.Filled.Email, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Resend welcome email", fontWeight = FontWeight.SemiBold)
        }

        when (val s = state) {
            AdminUserDetailViewModel.UiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Brand.ink) }
            is AdminUserDetailViewModel.UiState.Error -> CalloutBanner(
                title = "Couldn't load",
                message = s.message
            )
            is AdminUserDetailViewModel.UiState.Loaded -> {
                ProfileCard(user = s.user)
                Text(
                    if (emails.isEmpty()) "No emails sent yet" else "Recent emails",
                    color = Brand.ink,
                    fontWeight = FontWeight.SemiBold
                )
                emails.forEach { email -> EmailCard(email) }
                AccountControls(
                    user = s.user,
                    onReset = { showResetDialog = true },
                    onChangeRole = { showRoleSheet = true },
                    onToggleActive = { vm.setActive(!s.user.isActive) },
                    onDelete = { showDeleteDialog = true }
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (showResetDialog && user != null) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Send password reset email?") },
            text = {
                Text("${user.name.ifBlank { user.email }} will receive a one-time link to set a new password. The link expires in 1 hour.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.sendPasswordResetEmail()
                    showResetDialog = false
                }) { Text("Send to ${user.email}") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRoleSheet && user != null) {
        ChangeRoleSheet(
            currentRole = user.role,
            onDismiss = { showRoleSheet = false },
            onSubmit = { newRole ->
                vm.setRole(newRole)
                showRoleSheet = false
            }
        )
    }

    if (showDeleteDialog && user != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this user?") },
            text = {
                Text("Permanently removes ${user.name.ifBlank { user.email }} and every order, package, transaction, ticket and wallet row attached. Cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(onDeleted = {
                            showDeleteDialog = false
                            onBack()
                        })
                    }
                ) {
                    Text("Delete", color = Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileCard(user: AdminUserDto) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                user.name.ifBlank { user.email },
                color = Brand.ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp
            )
            Text(
                user.email,
                color = Brand.ink.copy(alpha = 0.65f),
                fontSize = 13.sp
            )
            user.phone?.let {
                Text(it, color = Brand.ink.copy(alpha = 0.65f), fontSize = 13.sp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Brand.Orange.copy(alpha = 0.16f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        user.role.uppercase(),
                        color = Brand.Orange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }
                if (!user.isActive) {
                    Text(
                        "INACTIVE",
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
            user.warehouseId?.let {
                Text(
                    it,
                    color = Brand.ink.copy(alpha = 0.45f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun EmailCard(email: EmailLogRow) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    email.subject,
                    modifier = Modifier.weight(1f),
                    color = Brand.ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    email.status.uppercase(),
                    color = if (email.status == "sent") Color(0xFF0F8A4F) else Color(0xFFD32F2F),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    email.emailType.uppercase(),
                    modifier = Modifier.weight(1f),
                    color = Brand.ink.copy(alpha = 0.5f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp
                )
                email.createdAt?.let {
                    Text(
                        it,
                        color = Brand.ink.copy(alpha = 0.45f),
                        fontSize = 10.sp
                    )
                }
            }
            email.errorMessage?.let {
                Text(
                    it,
                    color = Color(0xFFD32F2F),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun AccountControls(
    user: AdminUserDto,
    onReset: () -> Unit,
    onChangeRole: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    Text("Account controls", color = Brand.ink, fontWeight = FontWeight.SemiBold)
    SoftCard {
        Column {
            ControlRow(
                icon = Icons.Filled.Key,
                tint = Color(0xFF1976D2),
                title = "Reset password",
                onClick = onReset
            )
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            ControlRow(
                icon = Icons.Filled.VerifiedUser,
                tint = Brand.Orange,
                title = "Change role (${user.role})",
                onClick = onChangeRole
            )
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            ControlRow(
                icon = if (user.isActive) Icons.Filled.PersonOff else Icons.Filled.Person,
                tint = if (user.isActive) Color(0xFF6B6B6B) else Color(0xFF0F8A4F),
                title = if (user.isActive) "Deactivate account" else "Reactivate account",
                onClick = onToggleActive
            )
            HorizontalDivider(color = Brand.ink.copy(alpha = 0.08f))
            ControlRow(
                icon = Icons.Filled.Delete,
                tint = Color(0xFFD32F2F),
                title = "Delete account…",
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun ControlRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = false, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = Brand.ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Brand.ink.copy(alpha = 0.45f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeRoleSheet(
    currentRole: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by rememberSaveable(currentRole) { mutableStateOf(currentRole) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Change role",
                color = Brand.ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )
            ROLES.forEach { (key, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected == key,
                            onClick = { selected = key }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == key,
                        onClick = { selected = key }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Brand.ink, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Brand.ink.copy(alpha = 0.08f),
                        contentColor = Brand.ink
                    )
                ) { Text("Cancel") }
                Button(
                    onClick = { onSubmit(selected) },
                    enabled = selected != currentRole,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Brand.Orange,
                        contentColor = Color.White
                    )
                ) { Text("Save") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
