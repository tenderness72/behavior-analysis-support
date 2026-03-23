package com.example.behavioranalysis.data.dao

import androidx.room.*
import com.example.behavioranalysis.data.entity.BehaviorRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface BehaviorRecordDao {

    @Insert
    suspend fun insert(record: BehaviorRecord): Long

    @Update
    suspend fun update(record: BehaviorRecord)

    @Delete
    suspend fun delete(record: BehaviorRecord)

    @Query("SELECT * FROM behavior_records WHERE behaviorId = :behaviorId ORDER BY timestamp DESC")
    fun getRecordsByBehavior(behaviorId: Long): Flow<List<BehaviorRecord>>

    @Query("SELECT * FROM behavior_records WHERE id = :recordId")
    fun getRecordById(recordId: Long): Flow<BehaviorRecord?>

    @Query("SELECT COUNT(*) FROM behavior_records WHERE behaviorId = :behaviorId")
    fun getTotalCountByBehavior(behaviorId: Long): Flow<Int>

    @Query("DELETE FROM behavior_records WHERE behaviorId = :behaviorId")
    suspend fun deleteAllByBehavior(behaviorId: Long)
}
