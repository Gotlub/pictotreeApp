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

@Database(
    entities = [Profile::class, TreeEntity::class, ProfileTreeCrossRef::class, ImageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun treeDao(): TreeDao
    abstract fun imageDao(): ImageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pictotree_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
