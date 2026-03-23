package com.example.behavioranalysis.data.dao

import androidx.room.*
import com.example.behavioranalysis.data.entity.Subject
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {

    @Insert
    suspend fun insert(subject: Subject): Long

    @Update
    suspend fun update(subject: Subject)

    @Delete
    suspend fun delete(subject: Subject)

    @Query("SELECT * FROM subjects ORDER BY createdAt DESC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE id = :subjectId")
    fun getSubjectById(subjectId: Long): Flow<Subject?>
}
