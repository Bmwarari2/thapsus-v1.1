package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.dto.ProhibitedItemDto
import com.thapsus.cargo.data.repository.ProhibitedCategoryBody
import com.thapsus.cargo.data.repository.ProhibitedCategorySummary
import com.thapsus.cargo.data.repository.ProhibitedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
            prohibited.categories()
                .onSuccess { _state.value = UiState.CategoriesLoaded(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Couldn't load categories") }
        }
    }

    fun openCategory(name: String) {
        scope.launch {
            _categoryDetail.value = null
            prohibited.categoryDetail(name)
                .onSuccess { _categoryDetail.value = it }
                .onFailure { /* swallow — UI keeps showing the categories list */ }
        }
    }

    fun closeCategory() { _categoryDetail.value = null }

    fun search(query: String, language: String = "en") {
        scope.launch {
            _state.value = UiState.Searching
            prohibited.check(query, language)
                .onSuccess { _state.value = UiState.SearchResults(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Search failed") }
        }
    }

    fun reset() { loadCategories() }
}
