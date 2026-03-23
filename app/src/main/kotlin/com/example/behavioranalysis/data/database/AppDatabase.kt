package com.example.behavioranalysis.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.behavioranalysis.data.dao.BehaviorDao
import com.example.behavioranalysis.data.dao.BehaviorRecordDao
import com.example.behavioranalysis.data.dao.SubjectDao
import com.example.behavioranalysis.data.entity.Behavior
import com.example.behavioranalysis.data.entity.BehaviorRecord
import com.example.behavioranalysis.data.entity.Subject

@Database(
    entities = [Subject::class, Behavior::class, BehaviorRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun subjectDao(): SubjectDao
    abstract fun behaviorDao(): BehaviorDao
    abstract fun behaviorRecordDao(): BehaviorRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "behavior_analysis_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
