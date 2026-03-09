package org.libera.pictotree.ui.dashboard

import org.libera.pictotree.data.database.entity.Profile

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object Empty : DashboardUiState
    data class Success(val profiles: List<Profile>) : DashboardUiState
}
