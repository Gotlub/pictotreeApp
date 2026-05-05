package org.libera.pictotree.ui.editprofile

import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.data.database.entity.TreeEntity

data class ProfileTreeUiModel(
    val tree: TreeEntity,
    val localThumbnailPath: String?,
    val colorCode: String = "#000000"
)

sealed interface EditProfileUiState {
    data object Loading : EditProfileUiState
    data class Success(
        val profile: Profile,
        val trees: List<ProfileTreeUiModel>
    ) : EditProfileUiState
    data class Error(val message: String) : EditProfileUiState
}
