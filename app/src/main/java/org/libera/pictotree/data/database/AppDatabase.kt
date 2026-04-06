package org.libera.pictotree.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.libera.pictotree.data.database.dao.ProfileDao
import org.libera.pictotree.data.database.entity.Profile
import org.libera.pictotree.data.database.entity.ProfileTreeCrossRef
import org.libera.pictotree.data.database.entity.TreeEntity
import org.libera.pictotree.data.database.dao.ImageDao
import org.libera.pictotree.data.database.dao.TreeDao
import org.libera.pictotree.data.database.entity.ImageEntity
import org.libera.pictotree.data.database.entity.TreeImageCrossRef

import org.libera.pictotree.data.database.dao.UserConfigDao
import org.libera.pictotree.data.database.entity.UserConfig

@Database(
    entities = [Profile::class, TreeEntity::class, ProfileTreeCrossRef::class, ImageEntity::class, TreeImageCrossRef::class, UserConfig::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun treeDao(): TreeDao
    abstract fun imageDao(): ImageDao
    abstract fun userConfigDao(): UserConfigDao

    companion object {
        @Volatile
        private var instances = mutableMapOf<String, AppDatabase>()

        fun getDatabase(context: Context, username: String): AppDatabase {
            val safeUsername = username.lowercase().replace(Regex("[^a-z0-9]"), "_")
            val dbName = "${safeUsername}_pictotree.db"

            return instances[safeUsername] ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                .fallbackToDestructiveMigration()
                .build()
                instances[safeUsername] = instance
                instance
            }
        }
    }
}
