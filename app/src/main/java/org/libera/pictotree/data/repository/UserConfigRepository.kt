package org.libera.pictotree.data.repository

import kotlinx.coroutines.flow.Flow
import org.libera.pictotree.data.database.dao.UserConfigDao
import org.libera.pictotree.data.database.entity.UserConfig
import java.util.Locale

class UserConfigRepository(private val userConfigDao: UserConfigDao) {

    val userConfig: Flow<UserConfig?> = userConfigDao.getUserConfigFlow()

    suspend fun saveLocale(languageCode: String) {
        val current = userConfigDao.getUserConfig()
        if (current == null) {
            userConfigDao.insertUserConfig(UserConfig(locale = languageCode))
        } else {
            userConfigDao.updateUserConfig(current.copy(locale = languageCode))
        }
    }

    suspend fun savePin(pin: String?) {
        val current = userConfigDao.getUserConfig()
        if (current == null) {
            userConfigDao.insertUserConfig(UserConfig(locale = Locale.getDefault().language, offlineSettingsPin = pin))
        } else {
            userConfigDao.updateUserConfig(current.copy(offlineSettingsPin = pin))
        }
    }

    suspend fun saveGlobalDisplaySettings(startupView: String, orientation: String) {
        val current = userConfigDao.getUserConfig()
        if (current != null) {
            userConfigDao.updateUserConfig(current.copy(
                startupView = startupView,
                defaultOrientation = orientation
            ))
        }
    }

    suspend fun saveOfflineAccessAllowed(allowed: Boolean) {
        val current = userConfigDao.getUserConfig()
        if (current != null) {
            userConfigDao.updateUserConfig(current.copy(isOfflineAccessAllowed = allowed))
        }
    }

    suspend fun saveEnableSearch(enabled: Boolean) {
        val current = userConfigDao.getUserConfig()
        if (current != null) {
            userConfigDao.updateUserConfig(current.copy(enableSearch = enabled))
        }
    }

    suspend fun initializeDefaultIfNeeded() {
        if (userConfigDao.getUserConfig() == null) {
            userConfigDao.insertUserConfig(UserConfig(locale = Locale.getDefault().language))
        }
    }
}
