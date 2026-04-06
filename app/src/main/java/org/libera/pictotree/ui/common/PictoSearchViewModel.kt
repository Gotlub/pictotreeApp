package org.libera.pictotree.ui.common

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.repository.ArasaacRepository
import org.libera.pictotree.data.repository.UserConfigRepository
import org.libera.pictotree.network.TreeApiService
import org.libera.pictotree.network.dto.PictoSearchResultDTO
import kotlinx.coroutines.flow.firstOrNull

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data class Success(val results: List<PictoSearchResultDTO>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

class PictoSearchViewModel(
    application: Application,
    private val imageDao: ImageDao,
    private val treeApiService: TreeApiService,
    private val arasaacRepository: ArasaacRepository,
    private val userConfigRepository: UserConfigRepository,
    private val authToken: String?,
    private val username: String,
    private val hostUrl: String
) : AndroidViewModel(application) {

    private val _localResults = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val localResults: StateFlow<SearchUiState> = _localResults.asStateFlow()

    private val _baseResults = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val baseResults: StateFlow<SearchUiState> = _baseResults.asStateFlow()

    private val _arasaacResults = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val arasaacResults: StateFlow<SearchUiState> = _arasaacResults.asStateFlow()

    fun search(query: String, locale: String? = null) {
        if (query.isBlank()) return

        searchLocal(query)
        searchBase(query)
        
        viewModelScope.launch {
            val finalLocale = locale ?: userConfigRepository.userConfig.firstOrNull()?.locale ?: "fr"
            searchArasaac(query, finalLocale)
        }
    }

    private fun searchLocal(query: String) {
        viewModelScope.launch {
            _localResults.value = SearchUiState.Loading
            try {
                val entities = imageDao.searchImages(query)
                val results = entities.map {
                    val file = java.io.File(getApplication<Application>().filesDir, "$username/${it.localPath}")
                    PictoSearchResultDTO(
                        id = it.id,
                        name = it.name ?: "Picto",
                        imageUrl = "file://${file.absolutePath}"
                    )
                }
                _localResults.value = SearchUiState.Success(results)
            } catch (e: Exception) {
                _localResults.value = SearchUiState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    private fun searchBase(query: String) {
        if (authToken == null) {
            _baseResults.value = SearchUiState.Error("Offline: Connect to search on PictoTree.eu")
            return
        }
        viewModelScope.launch {
            _baseResults.value = SearchUiState.Loading
            try {
                val response = treeApiService.searchPictograms("Bearer $authToken", query)
                if (response.isSuccessful) {
                    _baseResults.value = SearchUiState.Success(response.body() ?: emptyList())
                } else {
                    _baseResults.value = SearchUiState.Error("Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                _baseResults.value = SearchUiState.Error(e.localizedMessage ?: "Network error")
            }
        }
    }

    private fun searchArasaac(query: String, locale: String) {
        viewModelScope.launch {
            _arasaacResults.value = SearchUiState.Loading
            try {
                val results = arasaacRepository.search(query, locale)
                _arasaacResults.value = SearchUiState.Success(results)
            } catch (e: Exception) {
                _arasaacResults.value = SearchUiState.Error(e.localizedMessage ?: "Network error")
            }
        }
    }
}
