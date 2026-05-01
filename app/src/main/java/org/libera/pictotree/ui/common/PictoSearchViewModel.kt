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
import org.libera.pictotree.utils.ConnectivityObserver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow as KStateFlow
import kotlinx.coroutines.flow.stateIn
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
    private val connectivityObserver: ConnectivityObserver,
    private val username: String,
    private val hostUrl: String
) : AndroidViewModel(application) {

    private val _localResults = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val localResults: StateFlow<SearchUiState> = _localResults.asStateFlow()

    private val _baseResults = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val baseResults: StateFlow<SearchUiState> = _baseResults.asStateFlow()

    private val _arasaacResults = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val arasaacResults: StateFlow<SearchUiState> = _arasaacResults.asStateFlow()

    // Observation du statut réseau en temps réel
    val networkStatus = connectivityObserver.observe().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConnectivityObserver.Status.Unavailable
    )

    private val lastQueries = mutableMapOf<Int, String>()
    private var currentGlobalQuery: String = ""

    fun updateQuery(query: String) {
        if (query != currentGlobalQuery) {
            currentGlobalQuery = query
            _baseResults.value = SearchUiState.Idle
            _arasaacResults.value = SearchUiState.Idle
            lastQueries.clear()
        }
    }

    fun search(type: Int) {
        if (currentGlobalQuery.isBlank()) return
        
        // Si on a déjà cherché ce mot clé pour ce type, on ne fait rien
        if (lastQueries[type] == currentGlobalQuery) return
        
        // Pour les types "Online", on vérifie la connexion avant de lancer
        if (type != SearchTabFragment.TYPE_LOCAL && networkStatus.value != ConnectivityObserver.Status.Available) {
            val errorState = SearchUiState.Error("Hors-ligne : Vérifiez votre connexion internet.")
            if (type == SearchTabFragment.TYPE_BASE) _baseResults.value = errorState
            if (type == SearchTabFragment.TYPE_ARASAAC) _arasaacResults.value = errorState
            return
        }

        lastQueries[type] = currentGlobalQuery

        when (type) {
            SearchTabFragment.TYPE_LOCAL -> searchLocal(currentGlobalQuery)
            SearchTabFragment.TYPE_BASE -> searchBase(currentGlobalQuery)
            SearchTabFragment.TYPE_ARASAAC -> searchArasaac(currentGlobalQuery)
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
                _localResults.value = SearchUiState.Error(e.localizedMessage ?: "Erreur")
            }
        }
    }

    private fun searchBase(query: String) {
        viewModelScope.launch {
            _baseResults.value = SearchUiState.Loading
            try {
                val response = treeApiService.searchPictograms(query)
                if (response.isSuccessful) {
                    _baseResults.value = SearchUiState.Success(response.body() ?: emptyList())
                } else {
                    _baseResults.value = SearchUiState.Error("Erreur serveur")
                }
            } catch (e: Exception) {
                _baseResults.value = SearchUiState.Error("Erreur réseau")
            }
        }
    }

    private fun searchArasaac(query: String) {
        viewModelScope.launch {
            _arasaacResults.value = SearchUiState.Loading
            try {
                val locale = userConfigRepository.userConfig.firstOrNull()?.locale ?: "fr"
                val results = arasaacRepository.search(query, locale)
                _arasaacResults.value = SearchUiState.Success(results)
            } catch (e: Exception) {
                _arasaacResults.value = SearchUiState.Error("Erreur réseau")
            }
        }
    }
}
