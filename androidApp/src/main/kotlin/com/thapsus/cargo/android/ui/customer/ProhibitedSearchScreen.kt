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
import com.thapsus.cargo.android.ui.primitives.SoftCard
import com.thapsus.cargo.android.ui.theme.Brand
import com.thapsus.cargo.presentation.ProhibitedSearchViewModel

/**
 * Search the prohibited-items dictionary before buying. Customer
 * types a term; server returns matches; if blank, shows categories.
 * Mirrors iOS ProhibitedSearchView.
 */
@Composable
fun ProhibitedSearchScreen() {
    val vm = remember { ThapsusSdk.prohibitedSearchViewModel() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    LaunchedEffect(vm) { vm.loadCategories() }

    val state by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EditorialHeader(
            eyebrow = "Pre-flight",
            title = "Can I ship this?",
            subtitle = "Search what's restricted by Kenya Revenue Authority."
        )

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.length >= 2) vm.search(it) else vm.reset()
            },
            label = { Text("Search prohibited items") },
            placeholder = { Text("e.g. drone, vape, perfume") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        when (val s = state) {
            ProhibitedSearchViewModel.UiState.Searching -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand.ink)
            }
            is ProhibitedSearchViewModel.UiState.Error -> CalloutBanner(title = "Couldn't search", message = s.message)
            is ProhibitedSearchViewModel.UiState.CategoriesLoaded -> {
                Text("Categories", color = Brand.ink, style = MaterialTheme.typography.titleLarge)
                s.categories.forEach { cat ->
                    SoftCard(modifier = Modifier.clickable { vm.openCategory(cat.category) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cat.category, color = Brand.ink, fontWeight = FontWeight.SemiBold)
                                Text("${cat.itemCount} items", color = Brand.ink.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                            cat.riskLevel?.let { RiskChip(it) }
                        }
                    }
                }
            }
            is ProhibitedSearchViewModel.UiState.SearchResults -> {
                if (s.items.isEmpty()) {
                    SoftCard {
                        Text("No matches. Most items are shippable — drop a ticket if you're unsure.", color = Brand.ink.copy(alpha = 0.7f))
                    }
                } else {
                    s.items.forEach { item ->
                        SoftCard {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.term, color = Brand.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    RiskChip(item.severity.name)
                                }
                                item.reason?.let { Text(it, color = Brand.ink.copy(alpha = 0.7f), fontSize = 13.sp) }
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
private fun RiskChip(label: String) {
    val color = when (label.uppercase()) {
        "PROHIBITED" -> Color(0xFFB3261E)
        "RESTRICTED" -> Color(0xFFE08B00)
        "LICENSED" -> Color(0xFF1976D2)
        else -> Color(0xFF707070)
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label.uppercase(), color = color, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 2.sp)
    }
}
