package com.example.behavioranalysis.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.behavioranalysis.data.dao.BehaviorDao
import com.example.behavioranalysis.data.dao.BehaviorRecordDao
import com.example.behavioranalysis.data.dao.SubjectDao
import com.example.behavioranalysis.data.entity.Behavior
import com.example.behavioranalysis.data.entity.BehaviorRecord
import com.example.behavioranalysis.data.entity.Subject

@Database(
    entities = [Subject::class, Behavior::class, BehaviorRecord::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun subjectDao(): SubjectDao
    abstract fun behaviorDao(): BehaviorDao
    abstract fun behaviorRecordDao(): BehaviorRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 → v2: behaviors テーブルに recordType / trialSettings カラムを追加 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE behaviors ADD COLUMN recordType TEXT NOT NULL DEFAULT 'EVENT'")
                db.execSQL("ALTER TABLE behaviors ADD COLUMN trialSettings TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "behavior_analysis_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
