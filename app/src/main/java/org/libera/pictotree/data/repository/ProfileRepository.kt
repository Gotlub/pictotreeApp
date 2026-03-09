package org.libera.pictotree.data.repository

import kotlinx.coroutines.flow.Flow
import org.libera.pictotree.data.database.dao.ProfileDao
import org.libera.pictotree.data.database.entity.Profile

class ProfileRepository(private val profileDao: ProfileDao) {

    val allProfiles: Flow<List<Profile>> = profileDao.getAllProfilesFlow()

    suspend fun insertProfile(profile: Profile) {
        profileDao.insertProfile(profile)
    }

    suspend fun deleteProfile(profile: Profile) {
        profileDao.deleteProfile(profile)
    }
}
