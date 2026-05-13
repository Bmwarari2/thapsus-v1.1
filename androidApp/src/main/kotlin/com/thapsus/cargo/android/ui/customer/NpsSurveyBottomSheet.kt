package com.thapsus.cargo.android.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.presentation.NpsSurveyViewModel

/**
 * NPS post-delivery survey. Customer scores 0-10 + optional comment.
 * Mirrors iOS NpsSurveyView. Mounted as a ModalBottomSheet content
 * — caller handles the sheet wrapper.
 */
@Composable
fun NpsSurveyContent(parcelId: String?, onDismiss: () -> Unit) {
    val vm = remember(parcelId) { ThapsusSdk.npsSurveyViewModel(parcelId) }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsStateWithLifecycle()

    var score by remember { mutableStateOf<Int?>(null) }
    var comment by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EditorialHeader(
            eyebrow = "Feedback",
            title = "How was your delivery?",
            subtitle = "0 = not at all likely, 10 = extremely likely to recommend us."
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (0..10).forEach { n ->
                val selected = score == n
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(
                            if (selected) Brand.Orange else Brand.cream.copy(alpha = 0.65f),
                            CircleShape
                        )
                        .clickable { score = n },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        n.toString(),
                        color = if (selected) Color.White else Brand.ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("What worked, what didn't?") },
            modifier = Modifier.fillMaxWidth().height(110.dp)
        )

        when (val s = state) {
            NpsSurveyViewModel.State.Sent -> CalloutBanner(title = "Thanks for the feedback", message = "Logged. We'll use this to keep tightening the loop.")
            is NpsSurveyViewModel.State.Error -> CalloutBanner(title = "Couldn't submit", message = s.message)
            else -> {}
        }

        OrangeButton(
            text = "Submit",
            enabled = score != null && state !is NpsSurveyViewModel.State.Submitting,
            onClick = {
                score?.let { vm.submit(it, comment.trim().takeIf { it.isNotBlank() }) }
            }
        )
        TextButton(onClick = onDismiss) { Text("Maybe later") }
        Spacer(Modifier.height(20.dp))
    }
}
