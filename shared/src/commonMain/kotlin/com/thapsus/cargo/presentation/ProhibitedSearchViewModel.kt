package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.ProhibitedItemDto
import com.thapsus.cargo.data.repository.ProhibitedCategoryBody
import com.thapsus.cargo.data.repository.ProhibitedCategorySummary
import com.thapsus.cargo.data.repository.ProhibitedRepository
import com.thapsus.cargo.domain.prohibited.ProhibitedItemsCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Prohibited / Restricted search screen on iOS + Android.
 *
 * The server's `prohibited_items` table is the long-term source of
 * truth, but ships unseeded in many environments. To guarantee the
 * screen is never empty for a real customer, every call falls back to
 * the bundled `ProhibitedItemsCatalog` whenever the server returns an
 * empty result or fails.
 */
class ProhibitedSearchViewModel(
    private val prohibited: ProhibitedRepository
) : SharedViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Searching : UiState
        data class Error(val message: String) : UiState
        data class CategoriesLoaded(val categories: List<ProhibitedCategorySummary>) : UiState
        data class SearchResults(val items: List<ProhibitedItemDto>) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _categoryDetail = MutableStateFlow<ProhibitedCategoryBody?>(null)
    val categoryDetail: StateFlow<ProhibitedCategoryBody?> = _categoryDetail.asStateFlow()

    fun loadCategories() {
        scope.launch {
            _state.value = UiState.Searching
            val server = prohibited.categories().getOrNull().orEmpty()
            val merged = if (server.isEmpty()) {
                ProhibitedItemsCatalog.categoriesSummary()
            } else {
                // Server data wins, catalog tops up any categories the
                // server doesn't ship yet (keeps the customer's mental
                // map intact across rollouts).
                val seen = server.map { it.category.lowercase() }.toSet()
                server + ProhibitedItemsCatalog.categoriesSummary()
                    .filterNot { it.category.lowercase() in seen }
            }
            _state.value = UiState.CategoriesLoaded(merged)
        }
    }

    fun openCategory(name: String) {
        scope.launch {
            _categoryDetail.value = prohibited.categoryDetail(name).getOrNull()
                ?: ProhibitedItemsCatalog.categoryDetail(name)
        }
    }

    fun closeCategory() { _categoryDetail.value = null }

    fun search(query: String, language: String = "en") {
        scope.launch {
            _state.value = UiState.Searching
            val server = prohibited.check(query, language).getOrNull().orEmpty()
            val catalog = ProhibitedItemsCatalog.search(query)
            val merged = if (server.isEmpty()) {
                catalog
            } else {
                // Dedupe by lowercase term so a server entry and a
                // catalog entry for the same item don't both render.
                val seen = server.map { it.term.lowercase() }.toSet()
                server + catalog.filterNot { it.term.lowercase() in seen }
            }
            _state.value = UiState.SearchResults(merged)
        }
    }

    fun reset() { loadCategories() }
}
