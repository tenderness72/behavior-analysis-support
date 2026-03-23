package com.example.behavioranalysis.data.dao

import androidx.room.*
import com.example.behavioranalysis.data.entity.Behavior
import kotlinx.coroutines.flow.Flow

@Dao
interface BehaviorDao {

    @Insert
    suspend fun insert(behavior: Behavior): Long

    @Update
    suspend fun update(behavior: Behavior)

    @Delete
    suspend fun delete(behavior: Behavior)

    @Query("SELECT * FROM behaviors WHERE subjectId = :subjectId ORDER BY createdAt DESC")
    fun getBehaviorsBySubject(subjectId: Long): Flow<List<Behavior>>

    @Query("SELECT * FROM behaviors WHERE id = :behaviorId")
    fun getBehaviorById(behaviorId: Long): Flow<Behavior?>
}
