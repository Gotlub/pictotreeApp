package org.libera.pictotree.ui.treeselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.libera.pictotree.data.database.dao.TreeWithColor
import org.libera.pictotree.data.database.entity.TreeEntity
import org.libera.pictotree.data.repository.ProfileRepository

sealed class TreeSelectionUiState {
    object Loading : TreeSelectionUiState()
    data class Success(val trees: List<TreeWithColor>) : TreeSelectionUiState()
    object Empty : TreeSelectionUiState()
    data class Error(val message: String) : TreeSelectionUiState()
}

class TreeSelectionViewModel(
    private val profileRepository: ProfileRepository,
    private val profileId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow<TreeSelectionUiState>(TreeSelectionUiState.Loading)
    val uiState: StateFlow<TreeSelectionUiState> = _uiState.asStateFlow()

    init {
        loadTrees()
    }

    private fun loadTrees() {
        viewModelScope.launch {
            try {
                _uiState.value = TreeSelectionUiState.Loading
                val trees = profileRepository.getTreesWithColorForProfile(profileId)
                if (trees.isEmpty()) {
                    _uiState.value = TreeSelectionUiState.Empty
                } else {
                    _uiState.value = TreeSelectionUiState.Success(trees)
                }
            } catch (e: Exception) {
                _uiState.value = TreeSelectionUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

class TreeSelectionViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val profileId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TreeSelectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TreeSelectionViewModel(profileRepository, profileId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
