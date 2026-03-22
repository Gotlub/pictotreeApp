package org.libera.pictotree.ui.editprofile

import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.data.database.entity.TreeEntity

sealed interface EditProfileUiState {
    data object Loading : EditProfileUiState
    data class Success(
        val profile: Profile,
        val trees: List<TreeEntity>
    ) : EditProfileUiState
    data class Error(val message: String) : EditProfileUiState
}
